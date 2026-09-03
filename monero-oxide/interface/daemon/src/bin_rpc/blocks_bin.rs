use core::{ops::RangeInclusive, future::Future};
use alloc::{borrow::ToOwned as _, format, vec, vec::Vec};

use monero_oxide::{
  transaction::{PotentiallyPruned, Transaction},
  block::Block,
};

use monero_interface::*;

use crate::{MAX_RESPONSE_SIZE, HttpTransport, MoneroDaemon};

use super::epee::{self, ExtractedBlocksBin};

/// Write an EPEE varint (`value << 2 | size_mark`) into `buf`.
fn write_epee_varint(buf: &mut Vec<u8>, value: usize) {
  if value <= 63 {
    #[expect(clippy::as_conversions, clippy::cast_possible_truncation)]
    buf.push(((value << 2) | 0b00) as u8);
  } else if value <= 16383 {
    #[expect(clippy::as_conversions, clippy::cast_possible_truncation)]
    buf.extend((((value << 2) | 0b01) as u16).to_le_bytes());
  } else if value <= 1073741823 {
    #[expect(clippy::as_conversions, clippy::cast_possible_truncation)]
    buf.extend((((value << 2) | 0b10) as u32).to_le_bytes());
  } else {
    #[expect(clippy::as_conversions, clippy::cast_possible_truncation)]
    buf.extend((((value << 2) | 0b11) as u64).to_le_bytes());
  }
}

impl<T: HttpTransport> MoneroDaemon<T> {
  fn get_blocks_request(
    blocks_request: BlocksRequest,
    requested_info: RequestedInfo,
    pool_info_since: u64,
    max_block_count: Option<u64>,
  ) -> Vec<u8> {
    let (start_height, block_ids) = match blocks_request {
      BlocksRequest::Height(height) => (height, None),
      BlocksRequest::Hashes(short_history) => (0, Some(short_history)),
    };

    let has_block_ids = block_ids.as_ref().is_some_and(|ids| !ids.is_empty());
    let field_count = 4 + usize::from(has_block_ids) + usize::from(max_block_count.is_some());

    let mut request = Vec::with_capacity(128);
    request.extend(epee::HEADER);
    request.push(epee::VERSION);
    write_epee_varint(&mut request, field_count);

    request.push(epee_key_len!("prune"));
    request.extend("prune".as_bytes());
    #[expect(clippy::as_conversions)]
    request.push(epee::Type::Bool as u8);
    request.push(1);

    request.push(epee_key_len!("start_height"));
    request.extend("start_height".as_bytes());
    #[expect(clippy::as_conversions)]
    request.push(epee::Type::Uint64 as u8);
    request.extend(start_height.to_le_bytes());

    request.push(epee_key_len!("requested_info"));
    request.extend("requested_info".as_bytes());
    #[expect(clippy::as_conversions)]
    request.push(epee::Type::Uint8 as u8);
    request.push(requested_info.node_value());

    request.push(epee_key_len!("pool_info_since"));
    request.extend("pool_info_since".as_bytes());
    #[expect(clippy::as_conversions)]
    request.push(epee::Type::Uint64 as u8);
    request.extend(pool_info_since.to_le_bytes());

    if let Some(ids) = &block_ids {
      if !ids.is_empty() {
        request.push(epee_key_len!("block_ids"));
        request.extend("block_ids".as_bytes());
        #[expect(clippy::as_conversions)]
        request.push(epee::Type::String as u8);
        write_epee_varint(&mut request, ids.len() * 32);
        for id in ids {
          request.extend(id);
        }
      }
    }

    if let Some(max_block_count) = max_block_count {
      /*
        This field was introduced in Monero 0.18.4.3, with the relevant pull request being
        https://github.com/monero-project/monero/pull/9901. Older version of Monero will ignore
        this field and return as many blocks as it wants in response to our request. Newer versions
        of Monero won't waste our mutual bandwidth however.
      */
      request.push(epee_key_len!("max_block_count"));
      request.extend("max_block_count".as_bytes());
      #[expect(clippy::as_conversions)]
      request.push(epee::Type::Uint64 as u8);
      request.extend(max_block_count.to_le_bytes());
    }

    request
  }

  /// Make a single `get_blocks.bin` request.
  ///
  /// `blocks_request` selects how the daemon locates blocks (by numeric height or by a short
  /// chain of known block hashes). `requested_info` selects blocks, the mempool, or both.
  /// `pool_info_since` is ignored when `BlocksOnly`.
  pub async fn get_blocks_bin(
    &self,
    blocks_request: BlocksRequest,
    requested_info: RequestedInfo,
    pool_info_since: u64,
    max_block_count: Option<u64>,
  ) -> Result<UnvalidatedScannableBlocksWithMempool, InterfaceError> {
    let request =
      Self::get_blocks_request(blocks_request, requested_info, pool_info_since, max_block_count);
    let epee = self.bin_call("get_blocks.bin", request, MAX_RESPONSE_SIZE).await?;
    let ExtractedBlocksBin { blocks, mempool, current_height, start_height } =
      epee::extract_blocks_from_blocks_bin(&epee)?;

    Ok(UnvalidatedScannableBlocksWithMempool {
      blocks: blocks.unwrap_or_default(),
      mempool: mempool.added,
      remaining_mempool_txids: mempool.remaining_added,
      removed_mempool_txids: mempool.removed,
      daemon_time: mempool.daemon_time,
      incremental: mempool.incremental,
      current_height,
      start_height,
    })
  }

  /// This MUST NOT be called with a start of `0`.
  ///
  /// This returns false if this methodology isn't applicable.
  async fn fetch_contiguous_blocks(
    &self,
    range: RangeInclusive<usize>,
    res: &mut Vec<UnvalidatedScannableBlock>,
  ) -> Result<bool, InterfaceError> {
    /*
      The following code uses `get_blocks.bin`, with the request specifying the `start_height`
      field. Monero only observes this field if it has a non-zero value, hence why we must bound
      the start is non-zero here. The caller is required to ensure they handle the zero case
      themselves.

      https://github.com/monero-project/monero/blob/b591866fcfed400bc89631686655aa769ec5f2dd
        /src/cryptonote_core/blockchain.cpp#L2745
    */
    if *range.start() == 0 {
      Err(InterfaceError::InternalError(
        "attempting to fetch contiguous blocks from 0".to_owned(),
      ))?;
    }

    let Some(requested_blocks_sub_one) = range.end().checked_sub(*range.start()) else {
      return Ok(true);
    };
    let Some(requested_blocks) = requested_blocks_sub_one.checked_add(1) else {
      Err(InterfaceError::InternalError(
        "requested more blocks than representable in a `usize`".to_owned(),
      ))?
    };
    res.reserve(requested_blocks);

    let Ok(mut start) = u64::try_from(*range.start()) else {
      Err(InterfaceError::InternalError("start block wasn't representable in a `u64`".to_owned()))?
    };
    let Ok(end) = u64::try_from(*range.end()) else {
      Err(InterfaceError::InternalError("end block wasn't representable in a `u64`".to_owned()))?
    };
    let Ok(mut remaining_blocks) = u64::try_from(requested_blocks) else {
      Err(InterfaceError::InternalError(
        "amount of requested blocks wasn't representable in a `u64`".to_owned(),
      ))?
    };

    while start <= end {
      let request = Self::get_blocks_request(
        BlocksRequest::Height(start),
        RequestedInfo::BlocksOnly,
        0,
        Some(remaining_blocks),
      );

      let epee = self.bin_call("get_blocks.bin", request, MAX_RESPONSE_SIZE).await?;

      let blocks_received = {
        let mut blocks_received = 0;
        let Some(blocks) = epee::extract_blocks_from_blocks_bin(&epee)?.blocks else {
          return Ok(false);
        };
        for block in blocks {
          res.push(block);
          blocks_received += 1;
          remaining_blocks -= 1;
          /*
            Manually implement a termination clause here as Monero will send _all_ blocks which fit
            in a response starting from the requested `start_height`, unless it respected
            `max_block_count`.
          */
          if remaining_blocks == 0 {
            break;
          }
        }
        blocks_received
      };
      if blocks_received == 0 {
        Err(InterfaceError::InvalidInterface(
          "received zero blocks when requesting multiple".to_owned(),
        ))?;
      }

      start = (end - remaining_blocks) + 1;
    }

    Ok(true)
  }
}

fn update_output_index<P: PotentiallyPruned>(
  next_ringct_output_index: &mut Option<u64>,
  output_index_for_first_ringct_output: &mut Option<u64>,
  tx: &Transaction<P>,
) -> Result<(), InterfaceError> {
  if !matches!(tx, Transaction::V1 { .. }) {
    if next_ringct_output_index.is_none() && (!tx.prefix().outputs.is_empty()) {
      Err(InterfaceError::InternalError(
        "RingCT transactions yet no RingCT output index".to_owned(),
      ))?;
    }

    // Populate the block's first RingCT output's index, if it wasn't already
    *output_index_for_first_ringct_output =
      output_index_for_first_ringct_output.or(*next_ringct_output_index);

    // Advance the next output index past this transaction
    if let Some(next_ringct_output_index) = next_ringct_output_index {
      *next_ringct_output_index = next_ringct_output_index
        .checked_add(
          u64::try_from(tx.prefix().outputs.len())
            .expect("amount of transaction outputs exceeded 2**64?"),
        )
        .ok_or_else(|| {
          InterfaceError::InvalidInterface("output index exceeded `u64::MAX`".to_owned())
        })?;
    }
  }

  Ok(())
}

async fn update_output_index_with_fetch<P: PotentiallyPruned, T: HttpTransport>(
  daemon: &MoneroDaemon<T>,
  next_ringct_output_index: &mut Option<u64>,
  output_index_for_first_ringct_output: &mut Option<u64>,
  tx_hash: [u8; 32],
  tx: &Transaction<P>,
) -> Result<(), InterfaceError> {
  if !matches!(tx, Transaction::V1 { .. }) {
    // If we don't currently have the initial output index, fetch it via this transaction
    if next_ringct_output_index.is_none() && (!tx.prefix().outputs.is_empty()) {
      let indexes = <MoneroDaemon<T> as ProvidesOutputs>::output_indexes(daemon, tx_hash).await?;
      if tx.prefix().outputs.len() != indexes.len() {
        Err(InterfaceError::InvalidInterface(format!(
          "TX had {} outputs yet `get_o_indexes` returned {}",
          tx.prefix().outputs.len(),
          indexes.len()
        )))?;
      }
      *next_ringct_output_index = Some(indexes[0]);
    }
  }

  update_output_index(next_ringct_output_index, output_index_for_first_ringct_output, tx)
}

async fn expand<T: HttpTransport>(
  daemon: &MoneroDaemon<T>,
  block: Block,
) -> Result<UnvalidatedScannableBlock, InterfaceError> {
  let transactions =
    ProvidesUnvalidatedTransactions::pruned_transactions(daemon, &block.transactions)
      .await
      .map_err(|e| match e {
        TransactionsError::InterfaceError(e) => e,
        TransactionsError::TransactionNotFound => InterfaceError::InvalidInterface(
          "daemon sent us a block it doesn't have the transactions for".to_owned(),
        ),
        TransactionsError::PrunedTransaction => InterfaceError::InternalError(
          "complaining about receiving a pruned transaction when".to_owned() +
            " requesting a pruned transaction",
        ),
      })?;
  let mut next_ringct_output_index = None;
  let mut output_index_for_first_ringct_output = None;
  update_output_index_with_fetch(
    daemon,
    &mut next_ringct_output_index,
    &mut output_index_for_first_ringct_output,
    block.miner_transaction().hash(),
    block.miner_transaction(),
  )
  .await?;
  for (hash, transaction) in block.transactions.iter().zip(&transactions) {
    update_output_index_with_fetch(
      daemon,
      &mut next_ringct_output_index,
      &mut output_index_for_first_ringct_output,
      *hash,
      transaction.as_ref(),
    )
    .await?;
  }
  Ok(UnvalidatedScannableBlock { block, transactions, output_index_for_first_ringct_output })
}

impl<T: HttpTransport> ProvidesUnvalidatedScannableBlocks for MoneroDaemon<T> {
  fn contiguous_scannable_blocks(
    &self,
    mut range: RangeInclusive<usize>,
  ) -> impl Send + Future<Output = Result<Vec<UnvalidatedScannableBlock>, InterfaceError>> {
    async move {
      let mut res = vec![];
      // Handle the exceptional case where we're also requesting the genesis block, which
      // `fetch_contiguous_blocks` cannot handle
      if *range.start() == 0 {
        res.push(ProvidesUnvalidatedScannableBlocks::scannable_block_by_number(self, 0).await?);
        range = 1 ..= *range.end();
      }
      let len_before_fetch = res.len();

      if !self.fetch_contiguous_blocks(range.clone(), &mut res).await? {
        // Update the range according to any blocks successfully fetched with this methodology
        let len_successfully_fetched =
          res.len().checked_sub(len_before_fetch).ok_or_else(|| {
            InterfaceError::InternalError(
              "`fetch_contiguous_blocks` shortened the length of `res`".to_owned(),
            )
          })?;
        let Some(new_start) = (*range.start()).checked_add(len_successfully_fetched) else {
          // If the new start is unrepresentable, it exceeds the representable end
          return Ok(res);
        };
        range = new_start ..= *range.end();

        // Fall back to what's presumably JSON methods
        for block in ProvidesUnvalidatedBlockchain::contiguous_blocks(self, range).await? {
          res.push(expand(self, block).await?);
        }
      }
      Ok(res)
    }
  }

  fn scannable_block(
    &self,
    hash: [u8; 32],
  ) -> impl Send + Future<Output = Result<UnvalidatedScannableBlock, InterfaceError>> {
    async move {
      let block = <Self as ProvidesUnvalidatedBlockchain>::block(self, hash).await?;
      expand(self, block).await
    }
  }

  fn scannable_block_by_number(
    &self,
    number: usize,
  ) -> impl Send + Future<Output = Result<UnvalidatedScannableBlock, InterfaceError>> {
    async move {
      ProvidesUnvalidatedScannableBlocks::scannable_block(
        self,
        ProvidesUnvalidatedBlockchain::block_hash(self, number).await?,
      )
      .await
    }
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  fn h(b: u8) -> [u8; 32] {
    [b; 32]
  }

  fn hashes_of(bs: &[u8]) -> Vec<[u8; 32]> {
    bs.iter().copied().map(h).collect()
  }

  #[test]
  fn from_height() {
    assert_eq!(BlocksRequest::from_height(42), BlocksRequest::Height(42));
  }

  #[test]
  fn from_hashes_empty() {
    assert_eq!(BlocksRequest::from_hashes(h(0x00), &[]), BlocksRequest::Hashes(vec![h(0x00)]));
  }

  #[test]
  fn from_hashes_single() {
    // One recent hash (the tip): tip first, then genesis.
    assert_eq!(
      BlocksRequest::from_hashes(h(0xff), &[h(0x01)]),
      BlocksRequest::Hashes(vec![h(0x01), h(0xff)])
    );
  }

  #[test]
  fn from_hashes_few() {
    // oldest-first: [0x01, 0x02, 0x03], tip = 0x03
    assert_eq!(
      BlocksRequest::from_hashes(h(0xff), &[h(0x01), h(0x02), h(0x03)]),
      BlocksRequest::Hashes(vec![h(0x03), h(0x02), h(0x01), h(0xff)])
    );
  }

  #[test]
  fn from_hashes_uses_doubling_offsets() {
    // 20 blocks, oldest-first; tip = 0x14. The first ten are sequential, then the stride doubles
    // (offset 11 = 0x0a, offset 13 = 0x08, offset 17 = 0x04), ending with genesis.
    assert_eq!(
      BlocksRequest::from_hashes(h(0xff), &hashes_of(&(1 ..= 20).collect::<Vec<_>>())),
      BlocksRequest::Hashes(vec![
        h(0x14),
        h(0x13),
        h(0x12),
        h(0x11),
        h(0x10),
        h(0x0f),
        h(0x0e),
        h(0x0d),
        h(0x0c),
        h(0x0b),
        h(0x0a),
        h(0x08),
        h(0x04),
        h(0xff)
      ]),
    );
  }
}

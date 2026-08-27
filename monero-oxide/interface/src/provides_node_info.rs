use std::future::Future;

use crate::InterfaceError;

/// A struct containing node info.
#[derive(Clone, PartialEq, Eq, Debug)]
pub struct NodeInfo {
  /// Current height of this node
  pub height: u32,
  /// Target height of this node
  pub target_height: u32,
  /// Network type of this node
  pub nettype: String,
  /// Status of this node
  pub status: String,
}

/// An interface which provides node info.
pub trait ProvidesNodeInfo: Sync {
  /// Get the node info.
  fn node_info(
    &self,
  ) -> impl Send + Future<Output = Result<NodeInfo, InterfaceError>>;
}

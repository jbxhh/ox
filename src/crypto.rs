// everything in this module should ideally be upstreamed either to monero-oxide or monero-wallet-util

use std::ops::Deref;

use curve25519_dalek::constants::ED25519_BASEPOINT_TABLE;
use monero_wallet::{
    ViewPair, WalletOutput,
    ed25519::{CompressedPoint, Point, Scalar},
};
use rand_core::{CryptoRng, RngCore};
use zeroize::Zeroizing;

use crate::MoneroBytes;

pub(crate) fn sc_reduce32(s: &mut MoneroBytes) {
    *s = curve25519_dalek::Scalar::from_bytes_mod_order(*s).to_bytes();
}

pub fn spend_key_to_view_key(spend: &MoneroBytes) -> Zeroizing<MoneroBytes> {
    let mut key2keccaked = monero_wallet::primitives::keccak256(spend);
    sc_reduce32(&mut key2keccaked);
    Zeroizing::new(key2keccaked)
}

pub fn seed_offset_hash(passphrase: &str) -> Zeroizing<MoneroBytes> {
    Zeroizing::new(cuprate_cryptonight::cryptonight_hash_v0(passphrase.as_bytes()))
}

pub fn add_seed_offset(key: &MoneroBytes, passphrase: &str) -> Zeroizing<MoneroBytes> {
    let hash = seed_offset_hash(passphrase);
    Zeroizing::new((curve25519_dalek::Scalar::from_bytes_mod_order(*key) + curve25519_dalek::Scalar::from_bytes_mod_order(*hash)).to_bytes())
}

pub fn remove_seed_offset(key: &MoneroBytes, passphrase: &str) -> Zeroizing<MoneroBytes> {
    let hash = seed_offset_hash(passphrase);
    Zeroizing::new((curve25519_dalek::Scalar::from_bytes_mod_order(*key) - curve25519_dalek::Scalar::from_bytes_mod_order(*hash)).to_bytes())
}

pub fn key_image_of_output(output: &WalletOutput, sender_spend_key: &Scalar) -> CompressedPoint {
    key_image_of_output_inner(output, sender_spend_key).expect("Failed to compute key image of an output")
}

fn random_scalar(rng: &mut (impl RngCore + CryptoRng)) -> Scalar {
    let mut raw = Zeroizing::new([0; 64]);
    rng.fill_bytes(raw.as_mut());
    Scalar::from(curve25519_dalek::Scalar::from_bytes_mod_order_wide(&raw))
}

pub fn random_view_pair(rng: &mut (impl RngCore + CryptoRng)) -> ViewPair {
    let spend_key: Zeroizing<curve25519_dalek::Scalar> = Zeroizing::new(random_scalar(rng).into());
    let spend = Point::from(&*spend_key * ED25519_BASEPOINT_TABLE);
    ViewPair::new(spend, Zeroizing::new(random_scalar(rng))).expect("random scalars never produce a torsioned spend key")
}

fn key_image_of_output_inner(output: &WalletOutput, sender_spend_key: &Scalar) -> Option<CompressedPoint> {
    let input_key = Zeroizing::new((*sender_spend_key).into() + output.key_offset().into());
    if !(input_key.deref() * ED25519_BASEPOINT_TABLE).eq(&output.key().into()) {
        return None;
    }
    let key_image = Point::from(input_key.deref() * Point::biased_hash(output.key().compress().to_bytes()).into());
    Some(key_image.compress())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parse(hex: &str) -> MoneroBytes {
        let mut bytes = MoneroBytes::default();
        for (index, byte) in bytes.iter_mut().enumerate() {
            *byte = u8::from_str_radix(&hex[index * 2..(index * 2) + 2], 16).unwrap();
        }
        bytes
    }

    #[test]
    fn an_empty_offset_passphrase_changes_the_key() {
        let key = parse("6dd6b2029bfdf1c44a36ce8b229f35dcaa5800b8d858da9facf4b0a778dc2800");

        // cn_slow_hash("") is not zero, so subtracting it moves the key
        let hash = seed_offset_hash("");
        assert_ne!(*hash, [0; 32]);
        assert_ne!(*remove_seed_offset(&key, ""), key);
    }

    // ground truth produced by Monero's cn_slow_hash and sc_add/sc_sub
    #[test]
    fn seed_offset_matches_monero() {
        let key = parse("6dd6b2029bfdf1c44a36ce8b229f35dcaa5800b8d858da9facf4b0a778dc2800");

        let hash = seed_offset_hash("mysecret");
        assert_eq!(*hash, parse("99972c0bb2611e34f787c84e59a86ab19f170d66c58dffd46c5112d6a101dccd"));

        let encrypted = add_seed_offset(&key, "mysecret");
        assert_eq!(*encrypted, parse("ea7e59b210ba33d83564fb360c912c9349700d1e9ee6d9741946c37d1ade040e"));

        assert_eq!(*remove_seed_offset(&encrypted, "mysecret"), key);
    }
}

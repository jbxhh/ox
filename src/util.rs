use std::ops::Div;

use bigdecimal::{BigDecimal, FromPrimitive};
use monero_wallet::ed25519::Scalar;
use zeroize::Zeroizing;

pub fn bytes_to_scalar(bytes: &[u8; 32]) -> Option<Zeroizing<Scalar>> {
    curve25519_dalek::Scalar::from_canonical_bytes(*bytes)
        .into_option()
        .map(Scalar::from)
        .map(Zeroizing::new)
}

pub fn bytes_to_hex(key: &[u8]) -> Zeroizing<String> {
    Zeroizing::new(
        key.iter()
            .map(|b| format!("{:02x?}", b))
            .collect::<String>(),
    )
}

pub fn hex_to_bytes(hex: &str) -> Option<Zeroizing<Vec<u8>>> {
    if !hex.len().is_multiple_of(2) {
        return None;
    }

    let mut result = Vec::with_capacity(hex.len() / 2);

    for i in (0..hex.len()).step_by(2) {
        let byte = u8::from_str_radix(&hex[i..i + 2], 16).ok()?;
        result.push(byte);
    }

    Some(Zeroizing::new(result))
}

pub fn atomic_units_to_big_decimal(atomic_units: u64) -> BigDecimal {
    BigDecimal::from_u64(atomic_units)
        .expect("Failed to convert atomic units to big decimal")
        .div(10u64.pow(12))
}

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use super::*;

    #[test]
    fn test_hex_roundtrip_32_bytes() {
        let original: Vec<u8> = (0..32).collect();
        let hex_string = bytes_to_hex(&original);
        let recovered = hex_to_bytes(&hex_string).unwrap();
        assert_eq!(original.as_slice(), recovered.as_slice());
    }

    #[test]
    fn test_hex_roundtrip_random_32_bytes() {
        let original: Vec<u8> = vec![0x1a, 0x2b, 0x3c, 0x4d, 0x5e, 0x6f, 0x70, 0x81, 0x92, 0xa3, 0xb4, 0xc5, 0xd6, 0xe7, 0xf8, 0x09, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x00];
        let hex_string = bytes_to_hex(&original);
        let recovered = hex_to_bytes(&hex_string).unwrap();
        assert_eq!(original.as_slice(), recovered.as_slice());
    }

    #[test]
    fn test_hex_to_bytes_invalid_odd_length() {
        assert!(hex_to_bytes("123").is_none());
    }

    #[test]
    fn test_hex_to_bytes_invalid_chars() {
        assert!(hex_to_bytes("1zz").is_none());
    }

    #[test]
    fn test_atomic_units_to_big_decimal() {
        const TEST_CASES: &[(u64, &'static str)] = &[(1, "0.000000000001"), (1_000, "0.000000001000"), (1_000_000, "0.000001000000"), (1_000_000_000, "0.001000000000"), (1_000_000_000_000, "1.000000000000"), (12_345_678_901_234, "12.345678901234")];

        for (atomic_units, exptected_str) in TEST_CASES {
            let result = atomic_units_to_big_decimal(*atomic_units);
            let expected = BigDecimal::from_str(exptected_str).unwrap();
            assert_eq!(result, expected);
        }
    }
}

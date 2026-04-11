pub fn bytes_to_hex(key: &[u8]) -> String {
    key.iter()
        .map(|b| format!("{:02x?}", b))
        .collect::<String>()
}

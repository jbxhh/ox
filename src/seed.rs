use strum::EnumString;

#[derive(Debug, Clone, Copy, EnumString)]
pub enum SeedType {
    #[strum(serialize = "Monero", serialize = "monero", serialize = "Original", serialize = "original")]
    Monero,
    #[strum(serialize = "Polyseed", serialize = "polyseed")]
    Polyseed,
}

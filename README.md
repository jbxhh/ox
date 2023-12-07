# Mysu
A no-bullshit, pure Monero wallet suitable for both newcomers and experienced users.
Originally forked from the Monerujo codebase, it has been entirely rewritten on the Java side into Kotlin.
The monerujo.cpp C++ file still exists but has been updated/modified.

### QUICKSTART
- Download the APK for the most current release [here](http://4hsesnr6mjb4qrflgf5gezjaszzoqnnisin7ywzbsv6pgcxysiaq.b32.i2p/pokkst/mysu/releases) and install it
- Run the app, enter a passphrase (optional, depending on seed type), recovery seed (optional), and restore height (optional) to create a wallet.
- See the [FAQ](doc/FAQ.md)

### Disclaimer
Be sure to back up your wallet recovery phrase (and seed offset/passphrase, if you set one. Mysu enforces seed offsets/passphrases for Polyseed wallets). We are NOT responsible for lost or stolen funds.

### Notable Features
- Tor and I2P support (no built-in daemons though)
- Coin control (UTXO management)
- Seed passphrases/offsets
- Polyseed

### HOW TO BUILD

See [the instructions](doc/BUILDING-external-libs.md)

Then, fire up Android Studio and build the APK.

### Donations
- Address: 87BqQYkugEzh6Tuyotm2uc3DzJzKM6MuZaC161e6u1TsQxxPmXVPHpdNRyK47JY4d1hhbe25YVz4e9vTXCLDxvHkRXEAeBC

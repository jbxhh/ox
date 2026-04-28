# FAQ

## What is it?
- A **Monero** wallet for Android. You get to choose what remote node you want to connect to. No need to download the full blockchain.

## What features does it have?

- Uses Monero v0.18.4.6
- Support for Android >= 8.0
- Generate new wallets
- Recover wallets from mnemonic seed
- View transactions including details and copy to clipboard
- Spend Moneroj (handle with care on mainnet!)
- QR code scanning - make sure to *ALWAYS* verify the scanned code is what it is advertised to be!
- Custom daemons (remote or local network)
- Tor and I2P support, with settings to customize proxy address and port.

## How does the wallet get an exchange rate?
It doesn't.

## How do I use a node with username & password?
Enter username and password when adding/editing a node.

## The app always crashes as soon as I open a wallet!
Users of Zenfone MAX & Zenfone 2 Laser (possibly others) **MUST** use the armeabi-v7a APK as the arm64-v8a build uses hardware AES
functionality these models don't have. If this does not help, please report the problem.

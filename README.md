# Mysu
A no-bullshit, pure Monero wallet suitable for both newcomers and experienced users.  
Originally forked from the Monerujo codebase, it has been entirely rewritten on the Java side into Kotlin.  
The monerujo.cpp C++ file still exists but has been updated/modified.  
Project was initially started by pokkst, and is now maintained by anoncontributorxmr.  

## QUICKSTART

Note: there are no production releases of mysu available yet. Only proceed if you would like to use a beta/testing release.  

- Download the most current release [here](https://codeberg.org/anoncontributorxmr/mysu/actions) (click on the latest green action for `master` branch and look for "apks" artifact there). Unzip the archive and install the APK.
- Run the app, enter a passphrase (optional, depending on seed type), recovery seed (optional), and restore height (optional) to create a wallet.
- See the [FAQ](doc/FAQ.md)

## Disclaimer
Be sure to back up your wallet recovery phrase (and seed offset/passphrase, if you set one.  
Mysu enforces seed offsets/passphrases for Polyseed wallets).  
We are NOT responsible for lost or stolen funds.  

## Notable Features
- Tor and I2P support (no built-in daemons though)
- Coin control (UTXO management)
- Seed passphrases/offsets
- Polyseed

## HOW TO BUILD

See [the instructions](doc/BUILDING-external-libs.md)  
Then, fire up Android Studio and build the APK.  

## Donations

### pokkst

pokkst is the original author of mysu.  

XMR address: `87BqQYkugEzh6Tuyotm2uc3DzJzKM6MuZaC161e6u1TsQxxPmXVPHpdNRyK47JY4d1hhbe25YVz4e9vTXCLDxvHkRXEAeBC`  

### anoncontributorxmr

anoncontributorxmr is the current maintainer of mysu.  

XMR address: `48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ`  

#### Expenses (excluding developer time)

Maintaining mysu incurs some costs, so please consider donating to the address above.  

- Build server for CI/CD: 10 EUR / month

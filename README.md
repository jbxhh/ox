# Monfluo

A no-bullshit, pure Monero wallet suitable for both newcomers and experienced users.  

Monfluo is a fork of Mysu (previously known as MyNero) originally developed by pokkst.
Mysu was a fork of Monerujo developed by m2049r.

## QUICKSTART

Note: there are no production releases of Monfluo available yet. Only proceed if you would like to use a beta/testing release.

- Download the latest release [here](https://codeberg.org/anoncontributorxmr/monfluo/actions)
    - Click on the newest action that is successful (has a green tick to the left) and originates from the `master` branch (has a label with the same name to the right).
        - If the action has a label different than `master` - this is a work in progress release not intended for general audience, proceed only if you know what you are doing.
    - Look for `apks` artifact and click on it (this will start the download of a zip file).
    - Unzip the archive, find the app (it has .apk extension) and install it on your device.
- Run the app, enter a passphrase (optional, depending on seed type), recovery seed (optional), and restore height (optional) to create a wallet.
- See the [FAQ](doc/FAQ.md)

## Disclaimer

Be sure to back up your wallet recovery phrase (and seed offset/passphrase, if you set one).  
Monfluo enforces seed offsets/passphrases for Polyseed wallets).  
We are NOT responsible for lost or stolen funds.

## Notable Features

- Coin control (UTXO/enote management)
- Seed passphrases/offsets
- Polyseed

## HOW TO BUILD

See [the instructions](doc/BUILDING-external-libs.md)  
Then, fire up Android Studio and build the APK like any other android app.

## Donations

Maintaining Monfluo incurs some costs, so please consider donating.

XMR address: `48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ`

#### Expenses (excluding developer time)

- Build server for CI/CD: 10 EUR / month
- Domain (monfluo.org): 15 EUR / year

# External lib build

## Requirements:

1. Docker

2. `sudo apt install make automake libtinfo5`

3. Huge amount of RAM and free disk space

## Building:

1. Clone the Monero repo required for this app to function. A specific fork for UTXO management is hosted here: http://git.mynero.i2p/pokkst/monero.git
Clone a specific branch, and shorten I2P download time with `git clone -b release-v0.18.1.2-mynero --single-branch --depth 1 http://git.mynero.i2p/pokkst/monero.git`

2. Update submodules: `git submodule update --init --force`.

3. Then go to folder with monero-wallet repo, then external-libs. Here you need to create symbol link to `monero` folder:

Linux: `ln -s ~/monero ~/monero-wallet/external-libs/monero`

Windows: `mklink /D "C:\Users\<USERNAME>\monero-wallet\external-libs\monero" "C:\Users\<USERNAME>\monero"`

5. Start Docker and then run `sudo make` in `external-libs` folder. It will fail at end on Windows, but if `wallet2_api.h` exists in `include` folder, the build was successful. 

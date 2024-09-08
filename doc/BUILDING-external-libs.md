# External lib build

## Requirements:

1. Docker

2. `sudo apt install make automake libtinfo5`

3. Huge amount of RAM and free disk space

## Building:

1. Clone the Monero repo required for this app to function. A specific fork for UTXO management is hosted here: https://codeberg.org/anoncontributorxmr/monero
Clone a specific branch, and shorten I2P download time with `git clone -b v0.18.3.4-mysu --single-branch --depth 1 https://codeberg.org/anoncontributorxmr/monero`

2. Change current directory to the monero repo: `cd monero`

3. Update submodules: `git submodule update --init --force`.

4. Then go into the mysu repo folder, then external-libs. Here you need to create symbol link to `monero` folder:

Linux: `ln -s ~/monero ~/mysu/external-libs/monero`

Windows: `mklink /D "C:\Users\<USERNAME>\mysu\external-libs\monero" "C:\Users\<USERNAME>\monero"`

5. Start Docker and then run `sudo make` (with podman sudo is not necessary) in `external-libs` folder. It will fail at end on Windows, but if `wallet2_api.h` exists in `include` folder, the build was successful. 

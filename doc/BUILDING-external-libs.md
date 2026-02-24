# Build external libraries (monero)

## Requirements:

1. Git, Make and Podman
   Arch: `sudo pacman -S git make podman`
   Debian: `sudo apt install git make podman`

2. 8+ GiB of RAM and 20+ GiB of free disk space available

## Building:

1. Clone Monfluo repo including submodules: `git clone --recurse-submodules https://codeberg.org/Monfluo/monfluo-android.git`

2. Run `make` in `external-libs` directory

# Building Rust libraries

## Requirements:

- Git, Nushell, Just, Ripgrep, Rustup

Arch: `sudo pacman -S --needed nushell just ripgrep rustup`    
Debian: `sudo apt install -y just ripgrep rustup ; cargo install nu`    

## Building:

1. Clone Monfluo repo, including submodules:

```bash
git clone https://codeberg.org/Monfluo/monfluo-android.git -b oxide
```

2. Run `just oxide-jni-build` and `just oxide-jni-test` in root directory

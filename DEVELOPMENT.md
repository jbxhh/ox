# Required tools for development

* Linux (other operating systems MIGHT work)
* Java >= 17
* Rustup
  * Stable toolchain for your platform
  * Additional targets must be added, depending on the architecture of the devices you want to compile for: `armv7-linux-androideabi` `aarch64-linux-android` `x86_64-linux-android`
* Android NDK & android tools (e.g. `adb`)
* `nu` (nushell) as used as scripting language
* `just` is used as command runner
* `rg` (ripgrep) is used in some scripts

## Quick start

* Install rustup targets for cross-compiling to Android: `rustup target add armv7-linux-androideabi aarch64-linux-android x86_64-linux-android`
* Run the setup script: `just setup`, and follow the instructions
* Build and test everything: `just test`

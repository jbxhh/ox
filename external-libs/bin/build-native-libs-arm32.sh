#!/usr/bin/env bash
set -exuo pipefail
IFS=$'\n\t'

SCRIPT_DIR=$(dirname "$0")

export ARCH=arm
export HOST=arm-linux-androideabi
export OPENSSL_TARGET=android-arm
export MONERO_TARGET=release-static-android-armv7-wallet_api

"$SCRIPT_DIR/build-native-libs.sh"

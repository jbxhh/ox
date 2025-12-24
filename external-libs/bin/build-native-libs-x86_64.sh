#!/usr/bin/env bash
set -exuo pipefail
IFS=$'\n\t'

SCRIPT_DIR=$(dirname "$0")

export ARCH=x86_64
export HOST=x86_64-linux-android
export OPENSSL_TARGET=android-x86_64
export MONERO_TARGET=release-static-android-x86_64-wallet_api

"$SCRIPT_DIR/build-native-libs.sh"

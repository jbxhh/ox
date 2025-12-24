#!/usr/bin/env bash
set -exuo pipefail
IFS=$'\n\t'

SCRIPT_DIR=$(dirname "$0")

export ARCH=arm64
export HOST=aarch64-linux-android
export OPENSSL_TARGET=android-arm64
export MONERO_TARGET=release-static-android-armv8-wallet_api

"$SCRIPT_DIR/build-native-libs.sh"

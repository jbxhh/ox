#!/usr/bin/env bash
set -xeuo pipefail
IFS=$'\n\t'

CHANGELOGS_FOLDER="fastlane/metadata/android/en-US/changelogs"
ABI_VERSION_CODES=(1 2 4)
VERSION_CODE=$(rg -N -o 'versionCode = (\d+)' --replace '$1' app/build.gradle.kts)

cd "$CHANGELOGS_FOLDER"

MAIN_CHANGELOG_FILE="$((VERSION_CODE * 10)).txt"
touch "$MAIN_CHANGELOG_FILE"

for abi_version_code in "${ABI_VERSION_CODES[@]}"; do
    ln -s "$MAIN_CHANGELOG_FILE" "$((VERSION_CODE * 10 + abi_version_code)).txt"
done

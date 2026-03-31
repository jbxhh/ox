#!/usr/bin/env nu

const CHANGELOGS_DIRECTORY = "fastlane/metadata/android/en-US/changelogs"
const ABI_VERSION_CODES = [1 2 4]

let version_code = ^rg -N -o 'versionCode = (\d+)' --replace '$1' app/build.gradle.kts | into int
cd $CHANGELOGS_DIRECTORY
let main_file = (($version_code * 10) | into string) + ".txt"

touch $main_file

for abi_version_code in $ABI_VERSION_CODES {
    let target = ((($version_code * 10) + $abi_version_code) | into string) + ".txt"
    ln -s $main_file $target
}

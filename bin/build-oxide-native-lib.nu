#!/usr/bin/env nu

const ANDROID_API_LEVEL = "26"

const NATIVE_TARGET = "native"

const TARGETS = [
    [name,      rust-linker,                                    rust-target,                android-abi                                     ];
    [arm32,     CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER,    armv7-linux-androideabi,    $"armv7a-linux-androideabi($ANDROID_API_LEVEL)" ],
    [arm64,     CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER,      aarch64-linux-android,      $"aarch64-linux-android($ANDROID_API_LEVEL)"    ],
    [x86_64,    CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER,       x86_64-linux-android,       $"x86_64-linux-android($ANDROID_API_LEVEL)"     ],
];

def main [target: string, profile: string = "dev"] {
    if $target == $NATIVE_TARGET {
        cd ($env.FILE_PWD | path join .. monfluo-oxide-jni)
        ^cargo build --profile $profile
        exit
    }

    let target_map = $TARGETS | where name == $target
    if ($target_map | length) == 0 {
        print -e (ansi red_bold) $"Unknown target '($target)'\nAvailable targets: ($TARGETS | select name | each { $in.name }) or ($NATIVE_TARGET)" (ansi reset)
        exit 1
    }
    let target_values = $target_map | get 0
    cd ($env.FILE_PWD | path join .. monfluo-oxide-jni)
    with-env {
        $target_values.rust-linker: $"($env.ANDROID_NDK_HOME)/toolchains/llvm/prebuilt/($env.ANDROID_NDK_HOST)/bin/($target_values.android-abi)-clang",
        # TODO: remove the following environment variables when `ring` is not our transitive dependency anymore
        TARGET_CC: $"($env.ANDROID_NDK_HOME)/toolchains/llvm/prebuilt/($env.ANDROID_NDK_HOST)/bin/($target_values.android-abi)-clang",
        TARGET_AR: $"($env.ANDROID_NDK_HOME)/toolchains/llvm/prebuilt/($env.ANDROID_NDK_HOST)/bin/llvm-ar",
    } {
        ^cargo build --profile $profile --target $target_values.rust-target
    }
}

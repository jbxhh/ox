set dotenv-load := true


default:
  @just --list --justfile {{justfile()}} --list-submodules --unsorted

[group('utils')]
setup:
    cp .env.example .env
    @echo '<=== ACTION REQUIRED! ===>'
    @echo 'Please edit the created .env file and fill/change the configuration inside'

[group('utils')]
create-changelog:
    ./bin/create-changelog.nu


[group('common')]
clean: oxide-jni-clean oxide-kotlin-clean app-clean

[group('common')]
test: oxide-jni-test oxide-kotlin-test app-test


[group('oxide-jni')]
[working-directory: 'monfluo-oxide-jni']
oxide-jni-clean:
    cargo clean

[group('oxide-jni')]
[working-directory: 'monfluo-oxide-jni']
oxide-jni-test:
    cargo test
    
[group('oxide-jni')]
[working-directory: 'monfluo-oxide-jni']
oxide-jni-doc:
    cargo doc --open

[group('oxide-jni')]
_oxide-jni-build PROFILE TARGET:
    ./bin/build-oxide-native-lib.nu {{TARGET}} {{PROFILE}}

[group('oxide-jni')]
oxide-jni-build: (_oxide-jni-build "dev" "native")

[group('oxide-jni')]
oxide-jni-build-release: (_oxide-jni-build "release" "native")

[group('oxide-jni')]
oxide-jni-build-android-arm32: (_oxide-jni-build "dev" "arm32")

[group('oxide-jni')]
oxide-jni-build-android-arm64: (_oxide-jni-build "dev" "arm64")

[group('oxide-jni')]
oxide-jni-build-android-x86_64: (_oxide-jni-build "dev" "x86_64")

[group('oxide-jni')]
oxide-jni-build-android-all: oxide-jni-build-android-arm32 oxide-jni-build-android-arm64 oxide-jni-build-android-x86_64

[group('oxide-jni')]
oxide-jni-build-android-arm32-release: (_oxide-jni-build "release" "arm32")

[group('oxide-jni')]
oxide-jni-build-android-arm64-release: (_oxide-jni-build "release" "arm64")

[group('oxide-jni')]
oxide-jni-build-android-x86_64-release: (_oxide-jni-build "release" "x86_64")

[group('oxide-jni')]
oxide-jni-build-android-all-release: oxide-jni-build-android-arm32-release oxide-jni-build-android-arm64-release oxide-jni-build-android-x86_64-release


[group('oxide-kotlin')]
oxide-kotlin-clean:
    ./gradlew :monfluo-oxide-kotlin:clean

[group('oxide-kotlin')]
oxide-kotlin-test: oxide-jni-build
    export LD_LIBRARY_PATH=$(realpath monfluo-oxide-jni/target/debug) && ./gradlew :monfluo-oxide-kotlin:test --rerun-tasks

[group('oxide-kotlin')]
oxide-kotlin-build:
    ./gradlew :monfluo-oxide-kotlin:build


[group('app')]
_app-copy-native-libs ABI ANDROID_ARCH PROFILE:
    mkdir -p app/src/main/jniLibs/{{ANDROID_ARCH}}
    cp monfluo-oxide-jni/target/{{ABI}}/{{PROFILE}}/libmonfluo_oxide_jni.so app/src/main/jniLibs/{{ANDROID_ARCH}}/

[group('app')]
app-copy-native-libs-arm32: oxide-jni-build-android-arm32 (_app-copy-native-libs "armv7-linux-androideabi" "armeabi-v7a" "debug")

[group('app')]
app-copy-native-libs-arm64: oxide-jni-build-android-arm64 (_app-copy-native-libs "aarch64-linux-android" "arm64-v8a" "debug")

[group('app')]
app-copy-native-libs-x86_64: oxide-jni-build-android-x86_64 (_app-copy-native-libs "x86_64-linux-android" "x86_64" "debug")

[group('app')]
app-copy-native-libs-all: app-copy-native-libs-arm32 app-copy-native-libs-arm64 app-copy-native-libs-x86_64

[group('app')]
app-copy-native-libs-arm32-release: oxide-jni-build-android-arm32-release (_app-copy-native-libs "armv7-linux-androideabi" "armeabi-v7a" "release")

[group('app')]
app-copy-native-libs-arm64-release: oxide-jni-build-android-arm64-release (_app-copy-native-libs "aarch64-linux-android" "arm64-v8a" "release")

[group('app')]
app-copy-native-libs-x86_64-release: oxide-jni-build-android-x86_64-release (_app-copy-native-libs "x86_64-linux-android" "x86_64" "release")

[group('app')]
app-copy-native-libs-all-release: app-copy-native-libs-arm32-release app-copy-native-libs-arm64-release app-copy-native-libs-x86_64-release

[group('app')]
app-lint:
    ./gradlew ktlintCheck

[group('app')]
app-format:
    ./gradlew ktlintFormat

[group('app')]
app-clean:
    ./gradlew :app:clean

[group('app')]
app-test:
    ./gradlew :app:test --rerun-tasks

[group('app')]
app-build: app-copy-native-libs-arm64
    ./gradlew -P monfluo-arm64 assembleDebug

[group('app')]
app-build-all: app-copy-native-libs-all
    ./gradlew -P monfluo-all -P monfluo-universal assembleDebug

[group('app')]
app-build-release: app-copy-native-libs-arm64-release
    ./gradlew -P monfluo-arm64 assembleRelease

[group('app')]
app-build-release-all: app-copy-native-libs-all-release
    ./gradlew -P monfluo-all -P monfluo-universal assembleRelease

[group('app')]
app-install: app-build
    adb install app/build/outputs/apk/debug/monfluo-*-arm64-v8a-debug.apk
    
[group('app')]
app-kill:
    adb shell am force-stop org.monfluo.wallet.android.debug

[group('app')]
app-kill-release:
    adb shell am force-stop org.monfluo.wallet.android

[group('app')]
app-run: app-install app-kill
    adb shell monkey -p org.monfluo.wallet.android.debug 1

[group('app')]
app-logcat:
    adb shell run-as org.monfluo.wallet.android.debug logcat

[group('app')]
app-logcat-release:
    adb shell run-as org.monfluo.wallet.android logcat

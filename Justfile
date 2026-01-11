clean-native: clean-native-all

clean-native-all:
    cd external-libs && make clean

build-native: build-native-debug

build-native-debug:
    cd external-libs && make debug

build-native-all:
    cd external-libs && make all

# (Clean) Build Install Run (Debug)
bird: build-debug install-debug run-debug
cbird: clean build-debug install-debug run-debug

clean:
    ./gradlew clean

build: build-debug

build-debug:
    ./gradlew -P monfluo-arm64 assembleDebug

build-release:
    ./gradlew -P monfluo-all -P monfluo-universal assembleRelease

test:
    ./gradlew test -i

install: install-debug

install-release:
    adb install app/build/outputs/apk/release/monfluo-*-arm64-v8a.apk

install-debug:
    adb install app/build/outputs/apk/debug/monfluo-*-arm64-v8a.apk

kill-release:
    adb shell am force-stop org.monfluo.wallet.beta

kill-debug:
    adb shell am force-stop org.monfluo.wallet.beta.debug

run: run-debug

run-release: kill-release
    adb shell monkey -p org.monfluo.wallet.beta 1

run-debug: kill-debug
    adb shell monkey -p org.monfluo.wallet.beta.debug 1

logcat: logcat-debug

logcat-release:
    adb shell run-as org.monfluo.wallet.beta logcat

logcat-debug:
    adb shell run-as org.monfluo.wallet.beta.debug logcat

create-changelog:
    ./bin/create-changelog.sh

NPROC=$(shell nproc)

all: build/arm64-v8a build/armeabi-v7a build/x86_64 build/include/wallet2_api.h
debug: build/arm64-v8a build/include/wallet2_api.h

build/arm64-v8a: monero android64.Containerfile build
	-rm -rf build/arm64-v8a
	-podman container rm monero-android-arm64 -f
	podman build -f android64.Containerfile -t monero-android-arm64 . --build-arg NPROC=$(NPROC)
	podman create -it --name monero-android-arm64 monero-android-arm64 bash
	mkdir build/arm64-v8a
	podman cp monero-android-arm64:/opt/android/monfluo-output-arm64/. build/arm64-v8a/.

build/armeabi-v7a: monero android32.Containerfile build
	-rm -rf build/armeabi-v7a
	-podman container rm monero-android-arm32 -f
	podman build -f android32.Containerfile -t monero-android-arm32 . --build-arg NPROC=$(NPROC)
	podman create -it --name monero-android-arm32 monero-android-arm32 bash
	mkdir build/armeabi-v7a
	podman cp monero-android-arm32:/opt/android/monfluo-output-arm32/. build/armeabi-v7a/.

build/x86_64: monero android64_x86.Containerfile build
	-rm -rf build/x86_64
	-podman container rm monero-android-x86_64 -f
	podman build -f android64_x86.Containerfile -t monero-android-x86_64 . --build-arg NPROC=$(NPROC)
	podman create -it --name monero-android-x86_64 monero-android-x86_64 bash
	mkdir build/x86_64
	podman cp monero-android-x86_64:/opt/android/monfluo-output-x86_64/. build/x86_64/.

build/include/wallet2_api.h: monero build build/include monero/src/wallet/api/wallet2_api.h
	cp monero/src/wallet/api/wallet2_api.h build/include/wallet2_api.h

build:
	mkdir -p build

build/include: build
	mkdir -p build/include

clean:
	-rm -rf build

distclean: clean
	-podman container rm monero-android-arm64 -f
	-podman container rm monero-android-arm32 -f
	-podman container rm monero-android-x86_64 -f
	-podman image rm monero-android-arm64 -f
	-podman image rm monero-android-arm32 -f
	-podman image rm monero-android-x86_64 -f

.PHONY: all debug clean distclean

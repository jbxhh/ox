NPROC=16

all: arm64-v8a armeabi-v7a x86_64 include/wallet2_api.h
debug: arm64-v8a include/wallet2_api.h

arm64-v8a: monero android64.Containerfile
	-rm -rf arm64-v8a
	-podman container rm monero-android-arm64 -f
	podman build -f android64.Containerfile -t monero-android-arm64 monero --build-arg NPROC=$(NPROC)
	podman create -it --name monero-android-arm64 monero-android-arm64 bash
	mkdir arm64-v8a
	podman cp monero-android-arm64:/opt/android/prefix/lib/. arm64-v8a/
	podman cp monero-android-arm64:/src/build/release/lib/. arm64-v8a/monero

armeabi-v7a: monero android32.Containerfile
	-rm -rf armeabi-v7a
	-podman container rm monero-android-arm32 -f
	podman build -f android32.Containerfile -t monero-android-arm32 monero --build-arg NPROC=$(NPROC)
	podman create -it --name monero-android-arm32 monero-android-arm32 bash
	mkdir armeabi-v7a
	podman cp monero-android-arm32:/opt/android/prefix/lib/. armeabi-v7a
	podman cp monero-android-arm32:/src/build/release/lib/. armeabi-v7a/monero

x86_64: monero android64_x86.Containerfile
	-rm -rf x86_64
	-podman container rm monero-android-x86_64 -f
	podman build -f android64_x86.Containerfile -t monero-android-x86_64 monero --build-arg NPROC=$(NPROC)
	podman create -it --name monero-android-x86_64 monero-android-x86_64 bash
	mkdir x86_64
	podman cp monero-android-x86_64:/opt/android/prefix/lib/. x86_64
	podman cp monero-android-x86_64:/src/build/release/lib/. x86_64/monero

include/wallet2_api.h: monero include monero/src/wallet/api/wallet2_api.h
	cp monero/src/wallet/api/wallet2_api.h include/wallet2_api.h

include:
	mkdir include

clean:
	-rm -rf arm64-v8a
	-rm -rf armeabi-v7a
	-rm -rf x86_64
	-rm -rf include
	-rm -f VERSION

distclean: clean
	-podman container rm monero-android-arm64 -f
	-podman container rm monero-android-arm32 -f
	-podman container rm monero-android-x86_64 -f
	-podman image rm monero-android-arm64 -f
	-podman image rm monero-android-arm32 -f
	-podman image rm monero-android-x86_64 -f

.PHONY: all clean distclean
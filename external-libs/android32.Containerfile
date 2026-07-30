FROM debian:trixie AS build

RUN set -x && apt-get update && apt-get install -y sdkmanager cmake unzip automake build-essential curl file pkg-config git python3 python-is-python3 libtool yacc wget

RUN set -x && sdkmanager --verbose --install 'build-tools;36.1.0'
RUN set -x && sdkmanager --verbose --install 'ndk;29.0.14206865'

COPY bin/download-native-libs-dependencies.sh /src/external-libs/bin/
COPY bin/envs.sh /src/external-libs/bin/

ENV MONFLUO_DEPENDENCIES_DIRECTORY=/opt/android/dependencies

RUN set -x \
    && cd /src \
    && . ./external-libs/bin/envs.sh \
    && ./external-libs/bin/download-native-libs-dependencies.sh

FROM build

COPY monero/ /src/external-libs/monero
COPY bin/build-native-libs.sh /src/external-libs/bin
COPY bin/build-native-libs-arm32.sh /src/external-libs/bin

ENV MONFLUO_BUILD_DIRECTORY=/opt/android/monfluo-build-arm32
ENV MONFLUO_OUTPUT_DIRECTORY=/opt/android/monfluo-output-arm32

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_NDK_ROOT=/opt/android-sdk/ndk/29.0.14206865

ARG NPROC
ENV NPROC=$NPROC

RUN set -x \
    && cd /src \
    && . ./external-libs/bin/envs.sh \
    && ./external-libs/bin/build-native-libs-arm32.sh

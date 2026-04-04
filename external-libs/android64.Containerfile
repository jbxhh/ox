FROM debian:trixie AS build

RUN set -x && apt-get update && apt-get install -y unzip automake build-essential curl file pkg-config git python3 python-is-python3 yacc wget

# libtinfo5 is not available in Debian 13 but it is needed for Android's standalone toolchain
RUN wget http://ftp.us.debian.org/debian/pool/main/n/ncurses/libtinfo5_6.4-4_amd64.deb && apt install ./libtinfo5_6.4-4_amd64.deb

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
COPY bin/build-native-libs-arm64.sh /src/external-libs/bin

ENV MONFLUO_BUILD_DIRECTORY=/opt/android/monfluo-build-arm64
ENV MONFLUO_OUTPUT_DIRECTORY=/opt/android/monfluo-output-arm64

ARG NPROC
ENV NPROC=$NPROC

RUN set -x \
    && cd /src \
    && . ./external-libs/bin/envs.sh \
    && ./external-libs/bin/build-native-libs-arm64.sh

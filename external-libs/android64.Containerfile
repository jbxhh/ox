FROM debian:bookworm

RUN set -x && apt-get update && apt-get install -y unzip automake build-essential curl file pkg-config git python3 python-is-python3 libtool libtinfo5 yacc wget

COPY monero/ /src/external-libs/monero
COPY bin/download-native-libs-dependencies.sh /src/external-libs/bin/
COPY bin/envs.sh /src/external-libs/bin/

ENV MONFLUO_DEPENDENCIES_DIRECTORY=/opt/android/dependencies

RUN set -x \
    && cd /src \
    && . ./external-libs/bin/envs.sh \
    && ./external-libs/bin/download-native-libs-dependencies.sh

COPY bin/build-native-libs.sh /src/external-libs/bin
COPY bin/build-native-libs-arm64.sh /src/external-libs/bin
COPY bin/envs.sh /src/external-libs/bin

ENV MONFLUO_BUILD_DIRECTORY=/opt/android/monfluo-build-arm64
ENV MONFLUO_OUTPUT_DIRECTORY=/opt/android/monfluo-output-arm64

ARG NPROC
ENV NPROC=$NPROC

RUN set -x \
    && cd /src \
    && . ./external-libs/bin/envs.sh \
    && ./external-libs/bin/build-native-libs-arm64.sh

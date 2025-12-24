#!/usr/bin/env bash
set -exuo pipefail
IFS=$'\n\t'

if [ -z "$MONFLUO_DEPENDENCIES_DIRECTORY" ]; then
	echo "MONFLUO_DEPENDENCIES_DIRECTORY is not set"
fi

mkdir -p "$MONFLUO_DEPENDENCIES_DIRECTORY"
cd "$MONFLUO_DEPENDENCIES_DIRECTORY"

(curl -O https://dl.google.com/android/repository/sdk-tools-linux-${ANDROID_SDK_REVISION}.zip \
    && echo "${ANDROID_SDK_HASH} sdk-tools-linux-${ANDROID_SDK_REVISION}.zip" | sha256sum -c \
    && unzip -q sdk-tools-linux-${ANDROID_SDK_REVISION}.zip \
    && rm -f sdk-tools-linux-${ANDROID_SDK_REVISION}.zip)

(curl -O https://dl.google.com/android/repository/android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip \
    && echo "${ANDROID_NDK_HASH} android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip" | sha256sum -c \
    && unzip -q android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip \
    && rm -f android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip)

(curl -L -O https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz \
    && echo "${CMAKE_HASH} cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz" | sha256sum -c \
    && tar -xzf cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz \
    && rm -f cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz)

(curl -L -o boost_${BOOST_VERSION}.tar.bz2 https://archives.boost.io/release/${BOOST_VERSION_DOT}/source/boost_${BOOST_VERSION}.tar.bz2 \
    && echo "${BOOST_HASH} boost_${BOOST_VERSION}.tar.bz2" | sha256sum -c \
    && tar -xf boost_${BOOST_VERSION}.tar.bz2 \
    && rm -f boost_${BOOST_VERSION}.tar.bz2)

(curl -O https://mirrors.kernel.org/gnu/libiconv/libiconv-${ICONV_VERSION}.tar.gz \
    && echo "${ICONV_HASH} libiconv-${ICONV_VERSION}.tar.gz" | sha256sum -c \
    && tar -xzf libiconv-${ICONV_VERSION}.tar.gz \
    && rm -f libiconv-${ICONV_VERSION}.tar.gz)

(curl -O https://zlib.net/zlib-${ZLIB_VERSION}.tar.gz \
    && echo "${ZLIB_HASH} zlib-${ZLIB_VERSION}.tar.gz" | sha256sum -c \
    && tar -xzf zlib-${ZLIB_VERSION}.tar.gz \
    && rm zlib-${ZLIB_VERSION}.tar.gz)

(curl -L -O https://www.openssl.org/source/openssl-${OPENSSL_VERSION}.tar.gz \
    && echo "${OPENSSL_HASH} openssl-${OPENSSL_VERSION}.tar.gz" | sha256sum -c \
    && tar -xzf openssl-${OPENSSL_VERSION}.tar.gz \
    && rm openssl-${OPENSSL_VERSION}.tar.gz)

(git clone https://github.com/zeromq/libzmq.git -b ${ZMQ_VERSION} \
    && cd libzmq \
    && test `git rev-parse HEAD` = ${ZMQ_HASH} || exit 1 \
    && ./autogen.sh)

(git clone https://github.com/jedisct1/libsodium.git -b ${SODIUM_VERSION} \
    && cd libsodium \
    && test `git rev-parse HEAD` = ${SODIUM_HASH} || exit 1 \
    && DO_NOT_UPDATE_CONFIG_SCRIPTS=1 ./autogen.sh)

(git clone https://github.com/libexpat/libexpat.git -b ${LIBEXPAT_VERSION} \
    && cd libexpat/expat \
    && test `git rev-parse HEAD` = ${LIBEXPAT_HASH} || exit 1 \
    && ./buildconf.sh)

(git clone https://github.com/NLnetLabs/unbound.git -b ${LIBUNBOUND_VERSION} \
    && cd unbound \
    && test `git rev-parse HEAD` = ${LIBUNBOUND_HASH} || exit 1)

(git clone https://github.com/tevador/polyseed.git \
    && cd polyseed \
    && git reset --hard ${POLYSEED_COMMIT})

(git clone https://github.com/JuliaStrings/utf8proc -b v2.8.0 \
    && cd utf8proc \
    && git reset --hard ${UTF8PROC_COMMIT})

#!/usr/bin/env bash
set -exuo pipefail
IFS=$'\n\t'

check_env() {
  if [ -z "${!1}" ]; then
    echo "Monfluo build error: $1 is not set, exiting"
    exit 1
  fi
}

check_env MONFLUO_DEPENDENCIES_DIRECTORY
check_env MONFLUO_BUILD_DIRECTORY
check_env MONFLUO_OUTPUT_DIRECTORY
check_env ANDROID_SDK_ROOT
check_env ANDROID_NDK_ROOT

export BASEDIR=$(pwd)
mkdir -p "$MONFLUO_BUILD_DIRECTORY"
cp -r "$MONFLUO_DEPENDENCIES_DIRECTORY"/* "$MONFLUO_BUILD_DIRECTORY"/
cd "$MONFLUO_BUILD_DIRECTORY"

export PREFIX=${MONFLUO_BUILD_DIRECTORY}/prefix

export ANDROID_PLATFORM=26
export NDK_BIN=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin
CLANG_BASE=${CLANG_TRIPLE:-${HOST}}
export CC=${NDK_BIN}/${CLANG_BASE}${ANDROID_PLATFORM}-clang
export CXX=${NDK_BIN}/${CLANG_BASE}${ANDROID_PLATFORM}-clang++
export AR=${NDK_BIN}/llvm-ar
export RANLIB=${NDK_BIN}/llvm-ranlib
export NM=${NDK_BIN}/llvm-nm
export STRIP=${NDK_BIN}/llvm-strip

export HOST_PATH="$NDK_BIN:$PATH"

(cd boost_${BOOST_VERSION} \
    && ./bootstrap.sh --prefix=${PREFIX})

printf 'using clang : 21 : %s ;\n' "${CXX}" > "${HOME}/user-config.jam"

(cd libiconv-${ICONV_VERSION} \
    && ./configure --build=x86_64-linux-gnu --host=${HOST} --prefix=${PREFIX} --disable-rpath \
    && make -j${NPROC} && make install)

(cd boost_${BOOST_VERSION} \
    && ./b2 --build-type=minimal cxxstd=11 link=static \
        cflags=-fPIC cxxflags=-fPIC \
        --with-chrono --with-date_time --with-filesystem --with-program_options --with-regex --with-serialization --with-system --with-thread --with-locale \
        --build-dir=android --stagedir=android toolset=clang threading=multi threadapi=pthread target-os=android \
        -sICONV_PATH=${PREFIX} install -j${NPROC})

(cd zlib-${ZLIB_VERSION} && ./configure --static \
    && make -j${NPROC})

export PRE_OPENSSL_PATH=$PATH
(cd openssl-${OPENSSL_VERSION} \
    && export PATH=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH \
    && ./Configure ${OPENSSL_TARGET} \
            -D__ANDROID_API__=${ANDROID_PLATFORM} \
            -fPIC \
            no-shared no-tests \
            --with-zlib-include=${MONFLUO_BUILD_DIRECTORY}/zlib-${ZLIB_VERSION}/include --with-zlib-lib=${MONFLUO_BUILD_DIRECTORY}/zlib-${ZLIB_VERSION}/lib \
            --prefix=${PREFIX} --openssldir=${PREFIX} \
    && make -j${NPROC} \
    && make install_sw)
export PATH=$PRE_OPENSSL_PATH

(cd libzmq \
    && CXXFLAGS="-std=c++11 -Wno-error -Wno-dtor-name" ./configure --prefix=${PREFIX} --host=${HOST} --enable-static --disable-shared \
    && make -j${NPROC} src/libzmq.la \
    && make -j${NPROC} install-libLTLIBRARIES install-includeHEADERS)

(cd libsodium \
    && ./configure --prefix=${PREFIX} --host=${HOST} --enable-static --disable-shared \
    && make -j${NPROC} \
    && make install)

(cd libexpat/expat \
    && ./configure --prefix=${PREFIX} --host=${HOST} --enable-static --disable-shared \
    && make -j${NPROC} \
    && make install)

(cd unbound \
    && ./configure --prefix=${PREFIX} --host=${HOST} --enable-static --disable-shared --disable-flto --with-ssl=${PREFIX} --with-libexpat=${PREFIX} \
    && make -j${NPROC} \
    && make install)

(cd polyseed \
    && cmake -DCMAKE_INSTALL_PREFIX=${PREFIX} . \
    && make \
    && make install)

(cd utf8proc \
    && mkdir build \
    && cd build \
    && rm -rf ../CMakeCache.txt ../CMakeFiles/ \
    && cmake -DCMAKE_INSTALL_PREFIX=${PREFIX} .. \
    && make \
    && make install)

(cd $BASEDIR/external-libs/monero \
    && yes | make clean-all || true \
    && MONFLUO_PREFIX=${PREFIX} CMAKE_INCLUDE_PATH="${PREFIX}/include" CMAKE_LIBRARY_PATH="${PREFIX}/lib" ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT} USE_SINGLE_BUILDDIR=1 PATH=${HOST_PATH} make ${MONERO_TARGET} -j${NPROC})

(cd $BASEDIR/external-libs/monero/build/release \
    && find . -path ./lib -prune -o -name '*.a' -exec cp '{}' lib \;)

mkdir -p "$MONFLUO_OUTPUT_DIRECTORY"
mkdir "$MONFLUO_OUTPUT_DIRECTORY"/monero

mv "$PREFIX"/lib/** "$MONFLUO_OUTPUT_DIRECTORY"/
mv "$BASEDIR"/external-libs/monero/build/release/lib/** "$MONFLUO_OUTPUT_DIRECTORY"/monero/

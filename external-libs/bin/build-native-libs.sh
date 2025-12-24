#!/usr/bin/env bash
set -exuo pipefail
IFS=$'\n\t'

if [ -z "$MONFLUO_DEPENDENCIES_DIRECTORY" ]; then
	echo "MONFLUO_DEPENDENCIES_DIRECTORY is not set"
fi

if [ -z "$MONFLUO_BUILD_DIRECTORY" ]; then
	echo "MONFLUO_BUILD_DIRECTORY is not set"
fi

if [ -z "$MONFLUO_OUTPUT_DIRECTORY" ]; then
	echo "MONFLUO_OUTPUT_DIRECTORY is not set"
fi

export BASEDIR=$(pwd)
mkdir -p "$MONFLUO_BUILD_DIRECTORY"
cp -r "$MONFLUO_DEPENDENCIES_DIRECTORY"/* "$MONFLUO_BUILD_DIRECTORY"/
cd "$MONFLUO_BUILD_DIRECTORY"

export ANDROID_SDK_ROOT=${MONFLUO_BUILD_DIRECTORY}/tools
export ANDROID_NDK_ROOT=${MONFLUO_BUILD_DIRECTORY}/android-ndk-r${ANDROID_NDK_REVISION}
export PREFIX=${MONFLUO_BUILD_DIRECTORY}/prefix
export TOOLCHAIN_DIR=${MONFLUO_BUILD_DIRECTORY}/toolchain
(${ANDROID_NDK_ROOT}/build/tools/make_standalone_toolchain.py \
    --arch ${ARCH} \
    --api 21 \
    --install-dir ${TOOLCHAIN_DIR} \
    --stl=libc++)

export PATH="$MONFLUO_BUILD_DIRECTORY/cmake-${CMAKE_VERSION}-Linux-x86_64/bin:$PATH"

export BOOST_VERSION=1_70_0
export BOOST_VERSION_DOT=1.70.0
export BOOST_HASH=430ae8354789de4fd19ee52f3b1f739e1fba576f0aded0897c3c2bc00fb38778
(cd boost_${BOOST_VERSION} \
    && ./bootstrap.sh --prefix=${PREFIX})
export HOST_PATH=$PATH
export PATH="$TOOLCHAIN_DIR/$HOST/bin:$TOOLCHAIN_DIR/bin:$PATH"

(cd libiconv-${ICONV_VERSION} \
    && CC=clang CXX=clang++ ./configure --build=x86_64-linux-gnu --host=${HOST} --prefix=${PREFIX} --disable-rpath \
    && make -j${NPROC} && make install)

(cd boost_${BOOST_VERSION} \
    && ./b2 --build-type=minimal link=static runtime-link=static --with-chrono --with-date_time --with-filesystem --with-program_options --with-regex --with-serialization --with-system --with-thread --with-locale --build-dir=android --stagedir=android toolset=clang threading=multi threadapi=pthread target-os=android -sICONV_PATH=${PREFIX} install -j${NPROC})

(cd zlib-${ZLIB_VERSION} && CC=clang CXX=clang++ ./configure --static \
    && make -j${NPROC})

export PRE_OPENSSL_PATH=$PATH
(cd openssl-${OPENSSL_VERSION} \
    && export PATH=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH \
    && ./Configure ${OPENSSL_TARGET} \
            -D__ANDROID_API__=21 \
            -static \
            no-shared no-tests \
            --with-zlib-include=${MONFLUO_BUILD_DIRECTORY}/zlib-${ZLIB_VERSION}/include --with-zlib-lib=${MONFLUO_BUILD_DIRECTORY}/zlib-${ZLIB_VERSION}/lib \
            --prefix=${PREFIX} --openssldir=${PREFIX} \
    && make -j${NPROC} \
    && make install_sw)
export PATH=$PRE_OPENSSL_PATH

(cd libzmq \
    && CC=clang CXX=clang++ ./configure --prefix=${PREFIX} --host=${HOST} --enable-static --disable-shared \
    && make -j${NPROC} \
    && make install)

(cd libsodium \
    && CC=clang CXX=clang++ ./configure --prefix=${PREFIX} --host=${HOST} --enable-static --disable-shared \
    && make -j${NPROC} \
    && make install)

(cd libexpat/expat \
    && CC=clang CXX=clang++ ./configure --prefix=${PREFIX} --host=${HOST} --enable-static --disable-shared \
    && make -j${NPROC} \
    && make install)

(cd unbound \
    && CC=clang CXX=clang++ ./configure --prefix=${PREFIX} --host=${HOST} --enable-static --disable-shared --disable-flto --with-ssl=${PREFIX} --with-libexpat=${PREFIX} \
    && make -j${NPROC} \
    && make install)

(cd polyseed \
    && CC=clang CXX=clang++ cmake -DCMAKE_INSTALL_PREFIX=${PREFIX} . \
    && make \
    && make install)

(cd utf8proc \
    && mkdir build \
    && cd build \
    && rm -rf ../CMakeCache.txt ../CMakeFiles/ \
    && CC=clang CXX=clang++ cmake -DCMAKE_INSTALL_PREFIX=${PREFIX} .. \
    && make \
    && make install)

(cd $BASEDIR/external-libs/monero \
    && CMAKE_INCLUDE_PATH="${PREFIX}/include" CMAKE_LIBRARY_PATH="${PREFIX}/lib" ANDROID_STANDALONE_TOOLCHAIN_PATH=${TOOLCHAIN_DIR} USE_SINGLE_BUILDDIR=1 PATH=${HOST_PATH} make ${MONERO_TARGET} -j${NPROC})

(cd $BASEDIR/external-libs/monero/build/release \
    && find . -path ./lib -prune -o -name '*.a' -exec cp '{}' lib \;)

mkdir -p "$MONFLUO_OUTPUT_DIRECTORY"
mkdir "$MONFLUO_OUTPUT_DIRECTORY"/monero

mv "$PREFIX"/lib/** "$MONFLUO_OUTPUT_DIRECTORY"/
mv "$BASEDIR"/external-libs/monero/build/release/lib/** "$MONFLUO_OUTPUT_DIRECTORY"/monero/

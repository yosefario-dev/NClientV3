#!/usr/bin/env bash
set -euo pipefail

here=$(dirname "$(realpath "$0")")
srclib=$(realpath "${SRCLIB_DIR:-$here/../../srclib}")
fc=$srclib/MozFennecComponents
mc=$srclib/MozFennec
llvm=$srclib/llvm
wasi=$srclib/wasi-sdk
gmscore=$srclib/gmscore
glean=$srclib/MozGlean

triplet=aarch64-linux-android
api=26
ndk_version=30.0.16248370
glean_ndk_version=$(sed -n 's/.*ndkVersion: "\([0-9.]*\)".*/\1/p' "$glean/build.gradle")
rust_version=1.95.0
cbindgen_version=0.29.4
clang_major=22
wasi_sdk_version=34.0
microg_version=$(git -C "$gmscore" describe --tags --abbrev=0 --match='v[0-9]*' | cut -c2-)

export JAVA_HOME=${GECKO_JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}
export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export ANDROID_NDK=${ANDROID_NDK:-$ANDROID_HOME/ndk/$ndk_version}
export TZ=UTC LC_ALL=C.UTF-8 LANG=C.UTF-8 PYTHONHASHSEED=0
export SOURCE_DATE_EPOCH=$(git -C "$mc" log -1 --format=%ct)
export MOZ_BUILD_DATE=$(date -u -d "@$SOURCE_DATE_EPOCH" +%Y%m%d%H%M%S)
export MOZ_SOURCE_REPO=https://github.com/mozilla-firefox/firefox
export MOZ_SOURCE_CHANGESET=$(git -C "$mc" rev-parse HEAD)
export MAVEN_REPO_URL=${MAVEN_REPO_URL:-https://only-for-validation.local}
export MAVEN_USERNAME=${MAVEN_USERNAME:-} MAVEN_PASSWORD=${MAVEN_PASSWORD:-}
export PATH=$JAVA_HOME/bin:$HOME/.cargo/bin:$PATH
umask 022

remap=-ffile-prefix-map=$srclib/=/srclib/
clang_bin=$llvm/build/stage1/clang/bin
clang_lib=$llvm/build/stage1/clang/lib/clang/$clang_major/lib

step() { echo "== $(date -u +%H:%M:%S) $*"; }

apply() {
    local stamp
    stamp=$srclib/.patched/$(echo "$1/$2" | sha256sum | cut -c1-16)
    [ -e "$stamp" ] && return
    patch -d "$1" -p1 -s -N --no-backup-if-mismatch -r - < "$2"
    mkdir -p "$srclib/.patched"
    touch "$stamp"
}

run_gradle() {
    if command -v gradle > /dev/null; then gradle "$@"; else ./gradlew "$@"; fi
}

prepare_sources() {
    step "patch sources"
    for p in gecko-dependencies gecko-localize_maven gecko-liberate \
        gecko-prevent-fingerprinting-via-crash-resources \
        gecko-release-artifacts gecko-remove-irrelevant-gradle-tasks; do
        apply "$mc" "$fc/$p.patch"
    done
    sed -i "s/org.microg.gms:play-services-fido:[0-9.]*/org.microg.gms:play-services-fido:$microg_version/" \
        "$mc/mobile/android/geckoview/build.gradle"
    sed -i '/version += "-SNAPSHOT"/d' "$mc/build.gradle"
    sed -i 's/"--sysroot="))/"--sysroot=", "-ffile-prefix-map="))/' "$mc/build/unix/elfhack/inject/moz.build"
    sed -i 's|{targets_path}, {defaults_base_path}, {defaults_fpp_path}|RFPTargets.inc, RFPTargetsDefaultBaseline.inc, RFPTargetsDefault.inc|' \
        "$mc/toolkit/components/resistfingerprinting/extract_rfp_targets.py"
    sed -i 's/@CC@/clang/; s/@CXX@/clang++/; s/@RUSTC@/rustc/; s/@CFLAGS@//; s/@CXXFLAGS@//; s/@RUSTFLAGS@//; s/@MOZ_CONFIGURE_OPTIONS@//' \
        "$mc/toolkit/content/buildconfig.html"
    sed -i 's/max_wait_seconds=600/max_wait_seconds=1800/' "$mc/mobile/android/gradle.py"
    sed -i 's|https://github.com|hxxps://github.com|' "$mc/python/mozboot/mozboot/android.py"
    sed -i 's|terser_path = terser_dir / ".*"|terser_path = Path("/usr/bin/terser")|' "$mc/python/mozbuild/mozpack/files.py"
    sed -i 's/allWarningsAsErrors, true/allWarningsAsErrors, false/' \
        "$mc/mobile/android/gradle/plugins/conventions/src/main/java/org/mozilla/conventions/ProjectPlugin.kt"

    local prefs=$mc/mobile/android/app/geckoview-prefs.js
    if ! grep -q 'media.gmp-manager.url.override' "$prefs"; then
        sed -i '/gmp-provider/d; /casting.enabled/d' "$prefs"
        cat >> "$prefs" << 'EOF'
pref("media.eme.enabled", false);
pref("media.gmp-provider.enabled", false);
pref("media.gmp-manager.url.override", "data:text/plain,");
pref("media.gmp-gmpopenh264.enabled", false);
pref("browser.crashReports.onDemand", false, locked);
pref("browser.crashReports.requestedNeverShowAgain", true, locked);
EOF
    fi

    apply "$gmscore" "$fc/gmscore-build-publish.patch"
    awk '/^diff --git /{keep = ($3 ~ /^a\/gmscore\//)} keep' "$fc/publishing.patch" > "$srclib/gmscore-publishing.patch"
    apply "$srclib" "$srclib/gmscore-publishing.patch"
    sed -i '/withSourcesJar()/d; /withJavadocJar()/d; /sourceReleaseJar/d' "$gmscore/gradle/publish-android.gradle"

    (cd "$wasi"
        grep -q '^set(wasi_sdk_version ' CMakeLists.txt ||
            sed -i "/^message(STATUS \"wasi-sdk version is/i set(wasi_sdk_version $wasi_sdk_version)" CMakeLists.txt
        apply . "$mc/taskcluster/scripts/misc/wasi-sdk-compat.patch"
        apply . "$mc/taskcluster/scripts/misc/wasi-sdk-no-llvm-patches.patch"
        for p in llvm-pr-168449 llvm-pr-186054 llvm-undo-part-of-194317; do
            apply src/llvm-project "src/$p.patch"
        done)

    mkdir -p "$ANDROID_HOME/emulator" "$HOME/.mozbuild/android-device/avd"
    cat > "$mc/mozconfig" << EOF
ac_add_options --disable-crashreporter
ac_add_options --disable-debug
ac_add_options --disable-tests
ac_add_options --disable-updater
ac_add_options --enable-application=mobile/android
ac_add_options --enable-release
ac_add_options --enable-update-channel=release
ac_add_options --enable-path-remapping
ac_add_options --target=$triplet
ac_add_options --with-android-ndk="$ANDROID_NDK"
ac_add_options --with-android-sdk="$ANDROID_HOME"
ac_add_options --with-java-bin-path=$JAVA_HOME/bin
ac_add_options --with-gradle=$(command -v gradle || echo "$mc/gradlew")
ac_add_options --with-wasi-sysroot="$wasi/build/sysroot/install/share/wasi-sysroot"
ac_add_options CC="$clang_bin/clang"
ac_add_options CXX="$clang_bin/clang++"
ac_add_options STRIP="$clang_bin/llvm-strip"
ac_add_options CFLAGS="$remap"
ac_add_options CXXFLAGS="$remap"
ac_add_options ASFLAGS="$remap"
ac_add_options RUSTFLAGS="--remap-path-prefix=$HOME/=/home/ --remap-path-prefix=$srclib/=/srclib/"
mk_add_options MOZ_OBJDIR=@TOPSRCDIR@/obj
MOZ_REQUIRE_SIGNING=
EOF
}

setup_tools() {
    step "android sdk, rust"
    sdkmanager 'build-tools;37.0.0' 'cmdline-tools;23.0' 'platform-tools' "ndk;$ndk_version" "ndk;$glean_ndk_version"
    "$here/platform.sh"
    rustup default "$rust_version"
    rustup target add "$triplet"
    if [ "$(cbindgen --version 2> /dev/null)" != "cbindgen $cbindgen_version" ]; then
        cargo install --locked --force --vers "$cbindgen_version" cbindgen
    fi
}

build_maven_deps() {
    step "glean gradle plugin"
    (cd "$glean" && run_gradle :glean-gradle-plugin:publishToMavenLocal)
    step "microg"
    (cd "$gmscore" && run_gradle \
        :play-services-ads-identifier:publishToMavenLocal \
        :play-services-base:publishToMavenLocal \
        :play-services-basement:publishToMavenLocal \
        :play-services-fido:publishToMavenLocal \
        :play-services-tasks:publishToMavenLocal)
}

build_llvm() {
    if [ ! -x "$clang_bin/clang" ]; then
        step "clang"
        (cd "$llvm" && python3 "$mc/build/build-clang/build-clang.py" \
            -c "$fc/use-system-clang-19.json" \
            -c "$mc/build/build-clang/clang-$clang_major.json" \
            -c "$fc/stage-1-aarch64.json" \
            --skip-tar)
    fi

    local common=(
        -GNinja
        -DCMAKE_C_COMPILER="$clang_bin/clang"
        -DCMAKE_CXX_COMPILER="$clang_bin/clang++"
        -DCMAKE_ASM_COMPILER="$clang_bin/clang"
        -DCMAKE_AR="$clang_bin/llvm-ar"
        -DCMAKE_RANLIB="$clang_bin/llvm-ranlib"
        -DCMAKE_C_COMPILER_TARGET="$triplet$api"
        -DCMAKE_CXX_COMPILER_TARGET="$triplet$api"
        -DCMAKE_ASM_COMPILER_TARGET="$triplet$api"
        -DCMAKE_BUILD_TYPE=Release
        -DCMAKE_C_FLAGS="$remap"
        -DCMAKE_CXX_FLAGS="$remap"
        -DCMAKE_ASM_FLAGS="$remap"
        -DCMAKE_INSTALL_PREFIX="$llvm/build/stage1/clang"
        -DLLVM_ENABLE_ASSERTIONS=OFF
        -DLLVM_CONFIG_PATH="$clang_bin/llvm-config"
        -DCMAKE_SYSROOT="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
        -DCMAKE_LINKER="$clang_bin/lld"
        -DCMAKE_SHARED_LINKER_FLAGS=-fuse-ld=lld
        -DANDROID=1
        -DANDROID_NATIVE_API_LEVEL=$api
        -DSANITIZER_ALLOW_CXXABI=OFF
        -DLLVM_DEFAULT_TARGET_TRIPLE="${triplet%-*-*}-unknown-linux-android"
        -DLLVM_ENABLE_PER_TARGET_RUNTIME_DIR=ON
    )

    local rt=$llvm/build-compiler-rt-$triplet
    if [ ! -f "$rt/.done" ]; then
        step "compiler-rt"
        mkdir -p "$rt"
        touch "$rt/libgcc.a"
        (cd "$rt" && cmake "$llvm/compiler-rt" "${common[@]}" \
            -DCMAKE_EXE_LINKER_FLAGS="-fuse-ld=lld --rtlib=libgcc -L$rt" \
            -DCOMPILER_RT_INSTALL_LIBRARY_DIR:PATH="lib/clang/$clang_major/lib" \
            -DCOMPILER_RT_DEFAULT_TARGET_ONLY=ON \
            -DCOMPILER_RT_BUILD_LIBFUZZER=OFF \
            -DCOMPILER_RT_BUILD_ORC=OFF \
            -DCOMPILER_RT_BUILTINS_HIDE_SYMBOLS=OFF \
            && ninja install)
        touch "$rt/.done"
    fi

    local unw=$llvm/build-runtimes-$triplet
    if [ ! -f "$unw/.done" ]; then
        step "libunwind"
        mkdir -p "$unw"
        (cd "$unw" && cmake "$llvm/runtimes" "${common[@]}" \
            -DCMAKE_EXE_LINKER_FLAGS=-fuse-ld=lld \
            -DCMAKE_C_COMPILER_WORKS=1 \
            -DCMAKE_CXX_COMPILER_WORKS=1 \
            -DCMAKE_ASM_COMPILER_WORKS=1 \
            -DLLVM_ENABLE_RUNTIMES=libunwind \
            -DLIBUNWIND_ENABLE_SHARED=OFF \
            && ninja install)
        touch "$unw/.done"
    fi
}

build_wasi_sysroot() {
    if [ ! -f "$clang_lib/wasm32-unknown-wasip1/libclang_rt.builtins.a" ]; then
        step "wasi sysroot"
        (cd "$wasi"
            cmake -G Ninja -B build/sysroot -S . \
                -DCMAKE_C_COMPILER="$clang_bin/clang" \
                -DCMAKE_CXX_COMPILER="$clang_bin/clang++" \
                -DCMAKE_INSTALL_PREFIX="$wasi/build/sysroot/install" \
                -DWASI_SDK_TARGETS=wasm32-wasip1 \
                -DWASI_SDK_LTO=OFF \
                -DWASI_SDK_EXCEPTIONS=OFF \
                -DWASI_SDK_BUILD_SHARED=OFF \
                -DWASI_SDK_CPU_CFLAGS="$remap" \
                -DCMAKE_C_FLAGS="$remap" \
                -DCMAKE_CXX_FLAGS="$remap"
            cmake --build build/sysroot --target compiler-rt
            cmake --build build/sysroot --target install)
        ln -sfn "$wasi/build/sysroot/install/wasi-resource-dir/lib/wasm32-unknown-wasip1" "$clang_lib/"
        test -f "$clang_lib/wasm32-unknown-wasip1/libclang_rt.builtins.a"
    fi
}

build_gecko() {
    step "gecko $MOZ_BUILD_DATE"
    export MOZCONFIG=$mc/mozconfig
    (cd "$mc"
        ./mach build
        rm -rf obj/gradle/maven
        run_gradle -x javadocRelease :geckoview:publishReleasePublicationToMavenRepository)
}

install_maven() {
    local m2=$HOME/.m2/repository
    local pick=(-type f \( -name '*.aar' -o -name '*.jar' -o -name '*.pom' -o -name '*.module' \) -not -name '*-sources.jar' -not -name '*-javadoc.jar')
    step "install geckoview to $m2"
    rm -rf "$m2/org/mozilla/geckoview"
    mkdir -p "$m2"
    (cd "$mc/obj/gradle/maven" && find org/mozilla/geckoview "${pick[@]}" -exec cp --parents {} "$m2/" \;)
    if [ -n "${GECKO_OUT:-}" ]; then
        step "export to $GECKO_OUT"
        rm -rf "$GECKO_OUT"
        mkdir -p "$GECKO_OUT"
        (cd "$m2" && find org/mozilla/geckoview org/microg "${pick[@]}" -exec cp --parents {} "$GECKO_OUT/" \;)
        (cd "$GECKO_OUT" && find . -type f | sort | xargs sha256sum)
    fi
}

prepare_sources
setup_tools
build_maven_deps
build_llvm
build_wasi_sysroot
build_gecko
install_maven

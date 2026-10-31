#!/usr/bin/env bash
set -euo pipefail

zip=platform-37.2_r02.zip
zip_sha256=17af98ec1427a1fed9ea278184aba71e53b87eca103655602f9fc64ef11ea4d3
jar_sha256=2507cf762abe2171a8cb3c289837fc71f2e66e598f396f1cc10bb7dc10020329

sdk=${ANDROID_HOME:?ANDROID_HOME is not set}
dir=$sdk/platforms/android-37.2

if [ -f "$dir/package.xml" ] && echo "$jar_sha256  $dir/android.jar" | sha256sum -c --status 2> /dev/null; then
    exit 0
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
curl -sSf -o "$tmp/$zip" "https://dl.google.com/android/repository/$zip"
echo "$zip_sha256  $tmp/$zip" | sha256sum -c --quiet
unzip -q "$tmp/$zip" -d "$tmp"
rm -rf "$dir" "$dir"-*
mkdir -p "$sdk/platforms"
mv "$tmp/android-37.2" "$dir"
echo "$jar_sha256  $dir/android.jar" | sha256sum -c --quiet
cat > "$dir/package.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns6="http://schemas.android.com/sdk/android/repo/repository2/04">
    <localPackage path="platforms;android-37.2" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns6:platformDetailsType">
            <api-level>37.2</api-level>
            <codename></codename>
            <extension-level>24</extension-level>
            <base-extension>true</base-extension>
            <layoutlib api="15"/>
        </type-details>
        <revision>
            <major>2</major>
        </revision>
        <display-name>Android SDK Platform 37.2</display-name>
    </localPackage>
</ns2:repository>
EOF

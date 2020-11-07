#!/usr/bin/env bash
set -euo pipefail

do_release() {
  export CURRENT_VERSION="$(sed -rn "s/^project\.version '([^']*)'$/\1/p" build.gradle)"
  echo "current version: ${CURRENT_VERSION}"
  export RELEASE_VERSION="${CURRENT_VERSION/-SNAPSHOT/}"
  echo "release version: ${RELEASE_VERSION}"
  (
    graalvm
    ./gradlew --no-daemon "-PreleaseVersion=${RELEASE_VERSION}" clean build
    native-image -H:IncludeResources="com/auzeill/shadow/copy/shadow-copy.version" -jar "build/libs/shadow-copy-${RELEASE_VERSION}.jar" build/shadow-copy
    echo "Binary file: build/shadow-copy is ready to be downloaded on"
    echo "https://github.com/alban-auzeill/shadow-copy/releases"
    echo "to create the release: v${RELEASE_VERSION}"
    echo
    echo "Then build.gradle need to be prepared for next release."
  )
}

do_release

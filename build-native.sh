#!/usr/bin/env bash
set -euo pipefail

export CURRENT_VERSION="$(sed -rn "s/^project\.version '([^']*)'$/\1/p" build.gradle)"
(
  graalvm &&
    ./gradlew --no-daemon clean build &&
    native-image -H:IncludeResources="com/auzeill/shadow/copy/shadow-copy.version" -jar "build/libs/shadow-copy-${CURRENT_VERSION}.jar" "build/shadow-copy"
)

#!/usr/bin/env bash
set -euo pipefail
source_zip=${1:-build/distributions/CipherChannels-2.0.0-source.zip}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
for run in one two; do
    mkdir "$work/$run"
    unzip -q "$source_zip" -d "$work/$run"
    root=$(find "$work/$run" -mindepth 1 -maxdepth 1 -type d -print -quit)
    (cd "$root" && sh ./gradlew clean releaseBuild --no-daemon)
    cp "$root/build/release/SHA256SUMS" "$work/$run.sha256"
done
diff -u "$work/one.sha256" "$work/two.sha256"

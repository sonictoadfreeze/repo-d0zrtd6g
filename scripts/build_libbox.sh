#!/usr/bin/env bash
# Builds the sing-box libbox.aar (gomobile) and copies it into app/libs/.
# Requirements: Go 1.23+, Android SDK + NDK (r26), JDK 17.
set -euo pipefail

SING_BOX_VERSION="${SING_BOX_VERSION:-v1.10.7}"
WORK="${WORK:-/tmp/sing-box-build}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/libs"

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to your NDK path}"

if [ ! -d "$WORK" ]; then
  git clone --depth 1 --branch "$SING_BOX_VERSION" https://github.com/SagerNet/sing-box.git "$WORK"
fi
cd "$WORK"

export PATH="$(go env GOPATH)/bin:$PATH"
go install -v github.com/sagernet/gomobile/cmd/gomobile@v0.1.4
go install -v github.com/sagernet/gomobile/cmd/gobind@v0.1.4

go run ./cmd/internal/build_libbox -target android -platform android/arm64,android/amd64

mkdir -p "$OUT"
cp libbox.aar "$OUT/libbox.aar"
echo "Copied libbox.aar -> $OUT/libbox.aar"

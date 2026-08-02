#!/usr/bin/env sh
set -eu

GRADLE_VERSION="9.5.1"
GRADLE_SHA256="bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f"
GRADLE_HOME_BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/clearup-bootstrap"
GRADLE_DIR="$GRADLE_HOME_BASE/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_DIR/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
    TMP_DIR="$(mktemp -d)"
    trap 'rm -rf "$TMP_DIR"' EXIT INT TERM
    ZIP_FILE="$TMP_DIR/gradle.zip"
    URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 "$URL" -o "$ZIP_FILE"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ZIP_FILE" "$URL"
    else
        echo "ClearUp bootstrap requires curl or wget." >&2
        exit 1
    fi

    if command -v sha256sum >/dev/null 2>&1; then
        printf '%s  %s\n' "$GRADLE_SHA256" "$ZIP_FILE" | sha256sum -c -
    elif command -v shasum >/dev/null 2>&1; then
        ACTUAL="$(shasum -a 256 "$ZIP_FILE" | awk '{print $1}')"
        [ "$ACTUAL" = "$GRADLE_SHA256" ] || { echo "Gradle checksum mismatch." >&2; exit 1; }
    else
        echo "A SHA-256 utility is required." >&2
        exit 1
    fi

    command -v unzip >/dev/null 2>&1 || { echo "ClearUp bootstrap requires unzip." >&2; exit 1; }
    unzip -q "$ZIP_FILE" -d "$TMP_DIR"
    mkdir -p "$GRADLE_HOME_BASE"
    rm -rf "$GRADLE_DIR"
    mv "$TMP_DIR/gradle-$GRADLE_VERSION" "$GRADLE_DIR"
fi

exec "$GRADLE_BIN" "$@"

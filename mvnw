#!/bin/sh
# Versioned Maven launcher for the SofaScore Local Lab.
# It follows the Maven Wrapper property convention and pins the distribution checksum.
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPERTIES_FILE="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$PROPERTIES_FILE" ]; then
  echo "Missing $PROPERTIES_FILE" >&2
  exit 1
fi

property() {
  sed -n "s/^$1=//p" "$PROPERTIES_FILE" | tail -n 1 | tr -d '\r'
}

DISTRIBUTION_URL=$(property distributionUrl)
EXPECTED_SHA256=$(property distributionSha256Sum)

if [ -z "$DISTRIBUTION_URL" ] || [ -z "$EXPECTED_SHA256" ]; then
  echo "distributionUrl and distributionSha256Sum are required" >&2
  exit 1
fi

ARCHIVE_NAME=${DISTRIBUTION_URL##*/}
DIST_DIR_NAME=${ARCHIVE_NAME%.zip}
DIST_DIR_NAME=${DIST_DIR_NAME%-bin}
MAVEN_USER_HOME=${MAVEN_USER_HOME:-"$HOME/.m2"}

if command -v sha256sum >/dev/null 2>&1; then
  URL_HASH=$(printf '%s' "$DISTRIBUTION_URL" | sha256sum | awk '{print substr($1,1,16)}')
elif command -v shasum >/dev/null 2>&1; then
  URL_HASH=$(printf '%s' "$DISTRIBUTION_URL" | shasum -a 256 | awk '{print substr($1,1,16)}')
else
  echo "sha256sum or shasum is required" >&2
  exit 1
fi

INSTALL_ROOT="$MAVEN_USER_HOME/wrapper/dists/$DIST_DIR_NAME/$URL_HASH"
MAVEN_HOME="$INSTALL_ROOT/$DIST_DIR_NAME"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"

if [ ! -x "$MAVEN_BIN" ]; then
  TMP_DIR=$(mktemp -d 2>/dev/null || mktemp -d -t mvnw)
  trap 'rm -rf "$TMP_DIR"' EXIT HUP INT TERM
  ARCHIVE="$TMP_DIR/$ARCHIVE_NAME"

  echo "Downloading Apache Maven from $DISTRIBUTION_URL" >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 2 --connect-timeout 15 -o "$ARCHIVE" "$DISTRIBUTION_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$DISTRIBUTION_URL"
  else
    echo "curl or wget is required to download Maven" >&2
    exit 1
  fi

  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256=$(sha256sum "$ARCHIVE" | awk '{print $1}')
  else
    ACTUAL_SHA256=$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')
  fi

  if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
    echo "Maven distribution checksum mismatch" >&2
    echo "Expected: $EXPECTED_SHA256" >&2
    echo "Actual:   $ACTUAL_SHA256" >&2
    exit 1
  fi

  EXTRACT_DIR="$TMP_DIR/extracted"
  mkdir -p "$EXTRACT_DIR"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ARCHIVE" -d "$EXTRACT_DIR"
  elif command -v python3 >/dev/null 2>&1; then
    python3 -m zipfile -e "$ARCHIVE" "$EXTRACT_DIR"
  elif command -v jar >/dev/null 2>&1; then
    (cd "$EXTRACT_DIR" && jar xf "$ARCHIVE")
  else
    echo "unzip, python3, or jar is required to extract Maven" >&2
    exit 1
  fi

  EXTRACTED_HOME="$EXTRACT_DIR/$DIST_DIR_NAME"
  if [ -f "$EXTRACTED_HOME/bin/mvn" ] && [ ! -x "$EXTRACTED_HOME/bin/mvn" ]; then
    chmod +x "$EXTRACTED_HOME/bin/mvn"
  fi
  if [ ! -x "$EXTRACTED_HOME/bin/mvn" ]; then
    echo "Unexpected Maven archive layout" >&2
    exit 1
  fi

  mkdir -p "$INSTALL_ROOT"
  if [ ! -d "$MAVEN_HOME" ]; then
    mv "$EXTRACTED_HOME" "$MAVEN_HOME"
  fi
fi

exec "$MAVEN_BIN" "$@"


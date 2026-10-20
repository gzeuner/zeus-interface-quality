#!/usr/bin/env sh
# Author: tiny-tool.de
# Copyright 2026 tiny-tool.de
# SPDX-License-Identifier: Apache-2.0
# See LICENSE in the project root.

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD=$(command -v java 2>/dev/null || true)
fi

if [ -z "$JAVA_CMD" ] || [ ! -x "$JAVA_CMD" ]; then
  echo "Java was not found. Please install JDK 21 or newer." >&2
  exit 2
fi

JAVA_VERSION=$("$JAVA_CMD" -version 2>&1 | awk -F '"' '/version/ { print $2; exit }')
JAVA_MAJOR=${JAVA_VERSION%%.*}
JAVA_MAJOR=${JAVA_MAJOR%%-*}
if [ "$JAVA_MAJOR" = "1" ]; then
  JAVA_MAJOR=${JAVA_VERSION#1.}
  JAVA_MAJOR=${JAVA_MAJOR%%.*}
fi

case "$JAVA_MAJOR" in
  ''|*[!0-9]*)
    echo "Could not determine the Java version. Please use JDK 21 or newer." >&2
    exit 2
    ;;
esac

if [ "$JAVA_MAJOR" -lt 21 ]; then
  echo "JDK 21 or newer is required; found Java $JAVA_VERSION." >&2
  exit 2
fi

JAR="target/zeus-interface-quality-0.1.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    :
  elif ! command -v javac >/dev/null 2>&1; then
    echo "A JDK is required to build the missing application JAR." >&2
    exit 2
  fi

  echo "Building ZEUS for the first start ..."
  "$SCRIPT_DIR/mvnw" -B -DskipTests package
fi

exec "$JAVA_CMD" -jar "$JAR" gui "$@"

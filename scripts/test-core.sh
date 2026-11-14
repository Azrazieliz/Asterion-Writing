#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v kotlinc >/dev/null || { echo "Install Kotlin JVM compiler (kotlinc) first" >&2; exit 2; }
command -v kotlin >/dev/null || { echo "Install Kotlin JVM runner (kotlin) first" >&2; exit 2; }
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
mapfile -t SOURCES < <(find "$ROOT/core/src/main/kotlin" "$ROOT/core/src/test/kotlin" -name '*.kt' -type f | sort)
kotlinc "${SOURCES[@]}" -d "$OUT/classes.jar"
kotlin -classpath "$OUT/classes.jar" com.asterion.writing.FoundationTestKt

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
DIR="$(mktemp -d)"
trap 'rm -rf "$DIR"' EXIT
javac -d "$DIR" tools/VerificarSintaxe.java
java -cp "$DIR" VerificarSintaxe $(find app/src/main/java -name '*.java' -print | sort)

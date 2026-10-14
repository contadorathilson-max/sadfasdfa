#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" app/src/main/java/br/com/rotacerta/app/TripMath.java app/src/main/java/br/com/rotacerta/app/TripPlanner.java app/src/main/java/br/com/rotacerta/app/TransportMode.java app/src/main/java/br/com/rotacerta/app/NumericInput.java tools/TripMathSmokeTest.java
java -cp "$OUT" TripMathSmokeTest

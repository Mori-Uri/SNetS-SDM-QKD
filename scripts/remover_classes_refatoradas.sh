#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for name in AmplifierGainLaw HabibiEgnHybrid HabibiEgnComparisonVerification LobatoSpectralOverlap PoggioliniGnIntegral Poggiolini2017HybridVerification; do
    rm -f "$ROOT/src/main/java/network/$name.java" "$ROOT/target/classes/network/$name.class"
done
printf '[OK] Old source/class files removed. Run a clean build.\n'

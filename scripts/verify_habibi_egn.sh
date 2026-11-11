#!/usr/bin/env bash
# Verifies Habibi GN fallback using a fresh Maven build (no stale class files).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CSV="${1:-$ROOT/build/habibi_gn_ab_results.csv}"
mkdir -p "$(dirname "$CSV")"
command -v mvn >/dev/null || { echo "Maven required for the complete refactored PhysicalLayer" >&2; exit 2; }
(cd "$ROOT" && mvn -q -DskipTests clean package)
java -cp "$ROOT/target/classes" verification.HabibiGnComparisonVerification "$CSV"
java -cp "$ROOT/target/classes" network.Poggiolini2017GnIntegral "${CSV%.csv}_poggiolini.csv"

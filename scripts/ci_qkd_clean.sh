#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.." || exit 2
for tool in mvn java; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "[ERROR] Required tool unavailable: $tool" >&2; exit 2
  fi
done
rm -rf target
mvn -B -q -DskipTests clean package || exit 1
mvn -B -q dependency:build-classpath -Dmdep.outputFile=target/qkd-deps.cp || exit 1
classpath="target/classes:$(cat target/qkd-deps.cp)"
mkdir -p target/qkd-regression
ok=0; failed=0
for file in src/main/java/qkd/verification/*Verification.java; do
  main="qkd.verification.$(basename "$file" .java)"
  echo "[RUN] $main"
  if java -cp "$classpath" "$main" > "target/qkd-regression/${main##*.}.log" 2>&1; then
    echo "[OK] $main"; ok=$((ok+1))
  else
    echo "[FAIL] $main"; failed=$((failed+1))
    cat "target/qkd-regression/${main##*.}.log"
  fi
done
# Independent classical physical-layer normalization regression (package network).
main=network.CascadedMaximumPowerReferenceVerification
echo "[RUN] $main"
if java -cp "$classpath" "$main" > target/qkd-regression/CascadedMaximumPowerReferenceVerification.log 2>&1; then
  echo "[OK] $main"; ok=$((ok+1))
else
  echo "[FAIL] $main"; failed=$((failed+1))
  cat target/qkd-regression/CascadedMaximumPowerReferenceVerification.log
fi
echo "[RESULT] OK=$ok FAILED=$failed"
[ "$ok" -gt 0 ] && [ "$failed" -eq 0 ]

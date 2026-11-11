#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP="$ROOT/simulations/backup_fontes_egn_removidas"
mkdir -p "$BACKUP/network" "$BACKUP/verification"
for name in EgnFullReference Poggiolini2017EgnReference AmplifierGainLaw HabibiEgnHybrid LobatoSpectralOverlap PoggioliniGnIntegral Poggiolini2017HybridVerification; do
  src="$ROOT/src/main/java/network/$name.java"
  if [[ -f "$src" ]]; then
    [[ -f "$BACKUP/network/$name.java" ]] || cp "$src" "$BACKUP/network/$name.java"
    rm -- "$src"
    echo "[INFO] Removida: network/$name.java"
  fi
done
for pkg in network verification; do
  src="$ROOT/src/main/java/$pkg/HabibiEgnComparisonVerification.java"
  if [[ -f "$src" ]]; then
    [[ -f "$BACKUP/$pkg/HabibiEgnComparisonVerification.java" ]] || cp "$src" "$BACKUP/$pkg/HabibiEgnComparisonVerification.java"
    rm -- "$src"
    echo "[INFO] Removida: $pkg/HabibiEgnComparisonVerification.java"
  fi
done
shopt -s nullglob
for dir in "$ROOT/bin" "$ROOT/target/classes" "$ROOT/build/classes"; do
  for name in EgnFullReference Poggiolini2017EgnReference AmplifierGainLaw HabibiEgnHybrid LobatoSpectralOverlap PoggioliniGnIntegral Poggiolini2017HybridVerification; do
    rm -f -- "$dir/network/$name"*.class
  done
  rm -f -- "$dir/network/HabibiEgnComparisonVerification"*.class "$dir/verification/HabibiEgnComparisonVerification"*.class
  rm -f -- "$dir/network/PhysicalLayer\$HabibiEgnDecision"*.class "$dir/network/PhysicalLayer\$HabibiEgnCacheKey"*.class
done
[[ -f "$ROOT/src/main/java/network/Poggiolini2017GnIntegral.java" && -f "$ROOT/src/main/java/verification/HabibiGnComparisonVerification.java" ]] || {
  echo '[ERRO] Aplique primeiro a versão GN atual do projeto.' >&2; exit 2;
}
echo '[OK] Fontes EGN antigas e bytecode local removidos.'

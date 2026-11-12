#!/usr/bin/env python3
"""Quarantine stale, unintegrated experimental TrackedRequestList safely.
This is NOT a substitute for importing its dependent Circuit and RequestForConnection changes.
"""
from pathlib import Path
import shutil
import sys
root = Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[1]
src = root / 'src/main/java/network/TrackedRequestList.java'
# The integrated variant introduced after Stage 5 has working consumers and must never be quarantined.
if (root/'src/main/java/network/Circuit.java').exists() and src.exists():
    circuit=(root/'src/main/java/network/Circuit.java').read_text(encoding='utf-8',errors='replace')
    request=(root/'src/main/java/request/RequestForConnection.java').read_text(encoding='utf-8',errors='replace')
    if 'TrackedRequestList' in circuit and 'markInTrackedList' in request:
        print('[OK] TrackedRequestList integrada; limpeza nao se aplica.')
        sys.exit(0)
if not src.exists():
    print('[OK] TrackedRequestList experimental ausente; nenhuma acao necessaria.')
    sys.exit(0)
consumers=[]
for java in (root/'src/main/java').rglob('*.java'):
    if java == src: continue
    data=java.read_text(encoding='latin-1',errors='replace')
    # A conservative scan: reject deletion if any other source even mentions the type.
    if 'TrackedRequestList' in data: consumers.append(str(java.relative_to(root)))
if consumers:
    print('[ERRO] TrackedRequestList possui consumidores; nao sera removida: '+', '.join(consumers))
    print('[INFO] E necessario integrar integralmente Circuit/RequestForConnection ou corrigir esses consumidores.')
    sys.exit(1)
backup=root/'simulations/backup_fontes_experimentais_incompletas/network/TrackedRequestList.java'
backup.parent.mkdir(parents=True,exist_ok=True)
if not backup.exists(): shutil.copy2(src,backup)
src.unlink()
print('[OK] Fonte TrackedRequestList sem consumidores retirada da arvore Java.')
print('[INFO] Backup: '+str(backup))
for base in ['bin','build/classes','target/classes']:
    for old in (root/base/'network').glob('TrackedRequestList*.class') if (root/base/'network').exists() else []:
        old.unlink()

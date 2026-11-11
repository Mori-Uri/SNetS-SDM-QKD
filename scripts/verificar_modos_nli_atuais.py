#!/usr/bin/env python3
"""Static, mandatory checks of the supported NLI mode configuration."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
keys={'johannissonSciUseIntegral','johannissonXciUseIntegral','habibiEgnFallbackEnabled','habibiEgnNarrowbandGHz','johannissonUseIntegral'}
for f in root.rglob('*'):
    if f.is_file() and (f.name=='physicalLayer' or f.name=='physicalLayer.json') and f.stat().st_size<100_000:
        if 'backup_' in str(f): continue
        obj=json.loads(f.read_text(encoding='utf-8'))
        old=keys.intersection(obj)
        if old: raise SystemExit('ERRO: %s: %s'%(f,old))
        print('[OK]',f.relative_to(root))
js=(root/'launcher/web/js/schema.js').read_text(encoding='utf-8')
for k in keys:
    if k in js: raise SystemExit('ERRO: campo legado no Launcher: '+k)
conf=(root/'src/main/java/simulationControl/parsers/PhysicalLayerConfig.java').read_text(encoding='latin-1')
for k in keys:
    if k in conf: raise SystemExit('ERRO: campo legado no parser: '+k)
print('PASS: configuracoes NLI atuais sem seletores antigos')

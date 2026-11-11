#!/usr/bin/env python3
"""Confere se não restou contrapropagação dentro da mesma fibra do SNetS.
Uso: python scripts/auditar_fibras_direcionais.py [raiz_projeto] [pasta_cenario]
Sem pasta_cenario, examina apenas o codigo e os exemplos JSON do projeto.
"""
from pathlib import Path
import json, re, sys

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
case = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else None
errors = []
java = root/'src'/'main'/'java'
patterns = [r'\bCOUNTER_PROPAGATING\b', r'\bintercoreBackwardLengthKm\b',
            r'\bbackwardLengthKm\b', r'\bisBidirectionalFiber\b',
            r'ClassicalChannel\.Direction']
for f in java.rglob('*.java'):
    contents = f.read_text(encoding='latin-1')
    for pattern in patterns:
        if re.search(pattern, contents):
            errors.append(f'{f.relative_to(root)} ainda menciona {pattern}')

schema = (root/'launcher'/'web'/'js'/'schema.js').read_text(encoding='utf-8')
if "'multiband.bidirectionalFiber':" in schema or 'bidirectionalFiber: false' in schema:
    errors.append('Launcher ainda oferece a opcao de fibra bidirecional')

def inspect_config(folder):
    for name in ('qkdPhysicalLayer.json', 'qkdPhysicalLayer'):
        f = folder/name
        if not f.is_file():
            continue
        try:
            obj = json.loads(f.read_text(encoding='utf-8-sig'))
        except (UnicodeDecodeError, ValueError) as exc:
            errors.append(f'{f}: JSON invalido: {exc}')
            continue
        mb = obj.get('multiband') if isinstance(obj, dict) else None
        if isinstance(mb, dict) and 'bidirectionalFiber' in mb:
            errors.append(f'{f}: remova multiband.bidirectionalFiber (inclusive false)')
    # Each directed link must have its own record; one-way links are permitted.
    for name in ('network.json', 'network'):
        f = folder/name
        if not f.is_file():
            continue
        try:
            net = json.loads(f.read_text(encoding='utf-8-sig'))
        except (UnicodeDecodeError, ValueError) as exc:
            errors.append(f'{f}: JSON invalido: {exc}')
            continue
        pairs = [(str(x.get('source')), str(x.get('destination'))) for x in net.get('links', []) if isinstance(x,dict)]
        if len(pairs) != len(set(pairs)):
            errors.append(f'{f}: enlaces dirigidos duplicados')
        print(f'[INFO] {f.name}: {len(pairs)} enlaces direcionais; reversos presentes: '
              f'{sum((b,a) in pairs for a,b in pairs)}/{len(pairs)}')
        break

for f in (root/'docs').rglob('*.json'):
    if 'qkdPhysicalLayer' in f.name:
        inspect_config(f.parent)
if case:
    if not case.is_dir(): errors.append('Cenario inexistente: '+str(case))
    else: inspect_config(case)
for e in errors: print('[ERRO]', e)
print(f'Auditoria de fibras direcionais: {len(errors)} problema(s).')
sys.exit(1 if errors else 0)

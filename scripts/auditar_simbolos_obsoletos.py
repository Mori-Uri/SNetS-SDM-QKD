#!/usr/bin/env python3
"""Audita referencias EGN removidas e o JAR compilado (opcional).
Uso: python scripts/auditar_simbolos_obsoletos.py . [--jar simulations/SNetS-SDM-QKD-exp.jar]
"""
from pathlib import Path
import re
import sys
import zipfile

args = sys.argv[1:]
root = Path(args[0]).resolve() if args and not args[0].startswith('--') else Path(__file__).resolve().parents[1]
if args and not args[0].startswith('--'):
    args = args[1:]
jar = None
if args:
    if len(args) == 2 and args[0] == '--jar':
        jar = Path(args[1])
    elif len(args) == 1 and args[0].startswith('--jar='):
        jar = Path(args[0].split('=', 1)[1])
    else:
        raise SystemExit('uso: auditoria.py [raiz] [--jar arquivo.jar]')
    if not jar.is_absolute():
        jar = root / jar

RETIRED = {
    'network/EgnFullReference',
    'network/Poggiolini2017EgnReference',
    'verification/HabibiEgnComparisonVerification',
    'network/HabibiEgnComparisonVerification',
    'network/AmplifierGainLaw',
    'network/HabibiEgnHybrid',
    'network/LobatoSpectralOverlap',
    'network/PoggioliniGnIntegral',
    'network/Poggiolini2017HybridVerification',
}

OLD_TYPES = {t.split('/')[-1] for t in RETIRED}
violations = []
sources = root / 'src/main/java'
java_files = list(sources.rglob('*.java')) + list((root / 'launcher/src').rglob('*.java'))
js_files = list((root / 'launcher/web/js').rglob('*.js'))
for symbol in RETIRED:
    old_java = sources / (symbol + '.java')
    if old_java.exists():
        violations.append(f'fonte Java removida ainda presente: {old_java.relative_to(root)}')

for file in java_files + js_files:
    source = file.read_text(encoding='iso-8859-1' if file.suffix == '.java' else 'utf-8', errors='replace')
    # Do not count documentation or commented-out references as executable uses.
    for lineno, line in enumerate(source.splitlines(), 1):
        if line.lstrip().startswith(('*', '//', '/*')):
            continue
        executable = re.sub(r'"(?:\\.|[^"\\])*"', '""', line)
        for name in OLD_TYPES:
            if re.search(r'\b' + re.escape(name) + r'\b', executable):
                violations.append(f'{file.relative_to(root)}:{lineno}: referencia a {name}')
        if re.search(r'\bisAdmissible2\s*\(', executable) and 'getDeclaredMethod' not in line:
            violations.append(f'{file.relative_to(root)}:{lineno}: isAdmissible2 removido')
        if 'PhysicalLayer.HabibiEgnDecision' in executable:
            violations.append(f'{file.relative_to(root)}:{lineno}: substitua pelo verificador GN atual')

physical = sources / 'network/PhysicalLayer.java'
if physical.exists():
    txt = physical.read_text(encoding='iso-8859-1', errors='replace')
    for sig in (r'public\s+static\s+double\s+legacySci\s*\(\s*Poggiolini2017GnIntegral\.Fiber',
                r'public\s+static\s+double\s+legacyXci\s*\(\s*Poggiolini2017GnIntegral\.Fiber',
                r'class\s+HabibiGnDecision\b'):
        if not re.search(sig, txt):
            violations.append('assinatura GN atual ausente em network/PhysicalLayer.java: ' + sig)

obsolete_entries = []
if jar is not None:
    if not jar.is_file():
        violations.append('JAR de saida nao encontrado: ' + str(jar))
    else:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if not n.endswith('.class'):
                    continue
                clazz = n[:-6]
                top = clazz.split('$', 1)[0]
                if top in RETIRED or clazz.startswith('network/PhysicalLayer$HabibiEgnDecision') or clazz.startswith('network/PhysicalLayer$HabibiEgnCacheKey'):
                    obsolete_entries.append(n)
        for n in obsolete_entries:
            violations.append('bytecode obsoleto no JAR: ' + n)

print('Auditoria: %d Java, %d JS, %d problemas%s' % (len(java_files), len(js_files),len(violations), ', JAR verificado' if jar else ''))
for item in violations:
    print('[ERRO]',item)
if violations:
    print('[INFO] Execute scripts/remover_egn_full_reference.bat, recompile o Launcher e o simulador.')
    sys.exit(1)
print('[OK] Nenhuma referencia executavel a tipos EGN descontinuados; assinatura legacySci GN valida.')

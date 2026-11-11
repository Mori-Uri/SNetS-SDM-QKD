#!/usr/bin/env python3
"""Insert only blank lines; KSPXT_v4-style visual separation, no logic changes.
Run from project root: python scripts/formatar_qkd_estilo_kspxt.py [--check]
Supports original SNetS source encoding ISO-8859-1. Idempotent.
"""
from pathlib import Path
import re, sys
root=Path(__file__).resolve().parents[1]/'src'/'main'/'java'
files=sorted((root/'qkd').rglob('*.java'))
files.extend(root/'network'/n for n in [
    'GnModelMath.java','LongitudinalClassicalGN.java',
    'LongitudinalClassicalQoT.java','Amplifier.java'
] if (root/'network'/n).exists())


def rewrite(old):
    src=old.decode('latin-1')
    end='\r\n' if src.count('\r\n')>src.count('\n')/2 else '\n'
    out=[]
    for line in src.splitlines(keepends=True):
        s=line.strip()
        if out and s and out[-1].strip():
            prev=out[-1].strip()
            control=bool(re.match(r'^(?:if|for|while|switch|try|return|throw)\b',s))
            after_block=(prev=='}' and not re.match(r'^(?:else\b|catch\b|finally\b|while\b|\)|\.|;|,|\])',s)
                         and not s.startswith(('}','//','*','*/','@')))
            before_control=(control and (prev.endswith(';') or prev=='}')
                            and not prev.startswith(('//','*','/*','@')) and not s.startswith('while ('))
            before_comment=(s.startswith('//') and prev.endswith(';') and not prev.startswith('//'))
            if after_block or before_control or before_comment:
                out.append(end)
        out.append(line)
    new=''.join(out).encode('latin-1')
    assert [x for x in src.splitlines() if x.strip()]==[x for x in new.decode('latin-1').splitlines() if x.strip()]
    return new

changed=[]
for file in files:
    old=file.read_bytes()
    new=rewrite(old)
    assert rewrite(new)==new, 'Formatter must be idempotent'
    if new!=old:
        changed.append(str(file.relative_to(root)))
        if '--check' not in sys.argv: file.write_bytes(new)
print('Files scanned:',len(files),'would change:' if '--check' in sys.argv else 'changed:',len(changed))
if '--check' in sys.argv and changed: sys.exit(1)

#!/usr/bin/env python3
"""Whitespace-only Java source layout, based on KSPXT_v3's tab indents and blank-line separation.
Never changes Java tokens, comments, string literals, or original newline encoding.
"""
from pathlib import Path
import argparse
import re, json, hashlib

SOURCE = Path(__file__).resolve().parents[1] / 'src' / 'main' / 'java'
TEMPLATE = SOURCE / 'gprmcsa/integrated/KSPXT_v3.java'

# Rule set targets organizational whitespace; no reflowing Java statements.
OPENERS = re.compile(r'^(?:if\s*\(|for\s*\(|while\s*\(|switch\s*\(|try\s*\{|synchronized\s*\(|do\s*\{|return\b|throw\b)')


def normalize_java(src):
    # Preserve byte-level encoding and original line terminator.
    newline = '\r\n' if '\r\n' in src else '\n'
    # split using line breaks preserving form later
    lines = src.splitlines()
    original_has_nl = src.endswith('\n')
    out = []
    state = 'code'
    per_line_state = []
    for oldline in lines:
        # Only normalize leading whitespace *outside* a block comment; don't change any token or comment body.
        indent = re.match(r'^[ \t]*', oldline).group(0)
        content = oldline[len(indent):]
        if state == 'code':
            # Convert leading 4 spaces to one tab, preserving visual alignment at 4-column tab stops.
            # Preserve partial indentation and odd multi-tab styling when existing.
            col = 0
            for ch in indent:
                col = (col // 4 + 1) * 4 if ch == '\t' else col + 1
            new_indent = '\t' * (col // 4) + ' ' * (col % 4)
            oldline = new_indent + content
        # Advance lexical state to prevent inserting blank lines *within* block comments.
        i = 0
        current = state
        while i < len(oldline):
            c = oldline[i]
            d = oldline[i+1] if i+1 < len(oldline) else ''
            if current == 'code':
                if c == '/' and d == '/':
                    break
                if c == '/' and d == '*':
                    current='block'; i+=2; continue
                if c == '"':
                    current='string'; i+=1; continue
                if c == "'":
                    current='char'; i+=1; continue
            elif current == 'block':
                if c == '*' and d == '/':
                    current='code'; i+=2; continue
            elif current in ('string','char'):
                if c == '\\': i+=2; continue
                if (current=='string' and c=='"') or (current=='char' and c=="'"):
                    current='code'
            i+=1
        # Ordinary strings and chars must close within same line in Java 8.
        # If malformed, don't reinterpret rest of file as string.
        if current in ('char', 'string'): current='code'
        per_line_state.append((state, current))
        state=current
        out.append(oldline.rstrip(' \t'))

    # Insert logical paragraph separation; no reordering of imports or statements.
    result=[]
    prev_i=None
    for i,line in enumerate(out):
        if not line.strip():
            if result and result[-1].strip():
                result.append('')
            continue
        if prev_i is not None and result and result[-1].strip() and per_line_state[i][0] == 'code' and per_line_state[prev_i][1] == 'code':
            prev=out[prev_i].strip()
            now=line.strip()
            prev_indent= len(out[prev_i])-len(out[prev_i].lstrip(' \t'))
            next_indent= len(line)-len(line.lstrip(' \t'))
            # Do not split compound constructs, chained expressions or annotations.
            continuation = bool(re.match(r'^(?:else\b|catch\b|finally\b|while\s*\(|\.|\)|\]|,|;|\+|\-|\?|:)',now))
            is_import_group = (prev.startswith('import ') and now.startswith('import '))
            is_doc_comment = now.startswith('*') or now.startswith('*/') or prev.startswith('/*')
            should_blank=False
            if prev.startswith('package ') and (now.startswith('import ') or now.startswith('public ') or now.startswith('/**')):
                should_blank=True
            elif prev.startswith('import ') and not now.startswith('import '):
                should_blank=True
            elif prev=='}' and not continuation and not is_doc_comment:
                # Separate completed blocks from following statements/methods at same nesting.
                # Don't insert if next closes outer block.
                should_blank=now!='}' and now!=');' and (next_indent<=prev_indent or now.startswith(('return ','throw ')))
            elif now.startswith('/**') and prev not in ('{','*/'):
                should_blank=True
            elif OPENERS.match(now) and not continuation and prev.endswith(';') and not prev.startswith(('import ','package ')):
                should_blank=next_indent<=prev_indent
            if not is_import_group and should_blank and not result[-1].endswith('\\'):
                result.append('')
        result.append(line)
        prev_i=i
    while result and not result[-1].strip(): result.pop()
    return newline.join(result) + (newline if original_has_nl else '')


def token_sig(txt):
    # Additional conservative semantic check: every non-whitespace character must remain in the same order.
    return hashlib.sha256(''.join(c for c in txt if not c.isspace()).encode('latin1')).hexdigest()

if __name__=='__main__':
    parser = argparse.ArgumentParser(description='Reproduce KSPXT_v3 indentation and logical spacing without changing Java tokens')
    parser.add_argument('--write', action='store_true', help='Apply formatting; otherwise check and report deviations')
    args = parser.parse_args()
    stats=[]
    for f in sorted(SOURCE.rglob('*.java')):
        if f==TEMPLATE: continue
        original=f.read_bytes().decode('latin1')
        changed=normalize_java(original)
        if token_sig(original)!=token_sig(changed):
            raise RuntimeError('Non-whitespace modification in '+str(f))
        if original!=changed:
            if args.write:
                f.write_bytes(changed.encode('latin1'))
            stats.append((str(f.relative_to(SOURCE)),len(original),len(changed)))
    action = 'changed' if args.write else 'need_formatting'
    print(action + '=' + str(len(stats)) + ' total_java=' + str(len(list(SOURCE.rglob('*.java')))))
    if stats and not args.write:
        for name, _, _ in stats[:20]:
            print('  ' + name)
        raise SystemExit(1)

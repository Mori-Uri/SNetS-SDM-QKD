#!/usr/bin/env python3
"""AB de resultados salvo com cenarios identicos (sem sobrescrever o cenario original).
Python 3, somente biblioteca padrao. Uso: python comparar_cenarios_salvos.py -h
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys

CONFIG_FILES = ('network', 'simulation', 'physicalLayer', 'traffic', 'others', 'qkdPhysicalLayer')


def load_configs(path):
    values = {}
    for name in CONFIG_FILES:
        candidates = [path / (name + '.json'), path / name]
        f = next((x for x in candidates if x.is_file()), None)
        if f is None:
            continue
        obj = json.loads(f.read_text(encoding='utf-8-sig'))
        if name == 'qkdPhysicalLayer' and isinstance(obj, dict) and isinstance(obj.get('multiband'), dict):
            mb = obj['multiband']
            # Obsolete false has no physical effect in the old code. Do NOT erase true.
            if mb.get('bidirectionalFiber', None) is False:
                del mb['bidirectionalFiber']
            if 'bidirectionalFiber' in mb:
                raise ValueError(f'{f}: multiband.bidirectionalFiber=true is unsupported')
        values[name] = obj
    return values


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'),
                                      ensure_ascii=False).encode('utf-8')).hexdigest()


def result_files(case):
    # Non-recursive and nested metric outputs supported; avoid input JSON.
    return {str(p.relative_to(case)).replace('\\', '/'): p for p in case.rglob('*.csv') if p.is_file()}


def numeric(a):
    try:
        x = float(a.strip().replace(',', '.')) if a.strip() else None
        return x if x is not None and math.isfinite(x) else None
    except ValueError:
        return None


def compare(reference, current, rtol, atol, max_details):
    errors = []
    left, right = load_configs(reference), load_configs(current)
    for name in sorted(set(left) | set(right)):
        if name not in left or name not in right:
            errors.append(f'CONFIG {name}: ausente em um dos cenarios')
        elif left[name] != right[name]:
            errors.append(f'CONFIG {name}: parametros diferentes; nao compare os CSVs como regressao exata')
    a, b = result_files(reference), result_files(current)
    if not a or not b:
        errors.append(f'RESULTADOS: CSV nao encontrado (base={len(a)}; atual={len(b)})')
    for name in sorted(set(a) ^ set(b)):
        errors.append('RESULTADO AUSENTE: ' + name)
    checked = 0
    diff = 0
    for name in sorted(set(a) & set(b)):
        with a[name].open(newline='', encoding='utf-8-sig', errors='replace') as fa, \
             b[name].open(newline='', encoding='utf-8-sig', errors='replace') as fb:
            ar, br = list(csv.reader(fa)), list(csv.reader(fb))
        if len(ar) != len(br):
            errors.append(f'{name}: linhas {len(ar)} != {len(br)}')
            diff += 1
        for row_idx, (ra, rb) in enumerate(zip(ar, br), 1):
            if len(ra) != len(rb):
                errors.append(f'{name}:{row_idx}: colunas {len(ra)} != {len(rb)}')
                diff += 1
                continue
            for col_idx, (va, vb) in enumerate(zip(ra, rb), 1):
                if va.strip() == vb.strip():
                    continue
                na, nb = numeric(va), numeric(vb)
                if na is not None and nb is not None and math.isclose(na, nb, rel_tol=rtol, abs_tol=atol):
                    continue
                diff += 1
                if len(errors) < max_details:
                    errors.append(f'{name}:linha={row_idx},coluna={col_idx}: {va!r} != {vb!r}')
        checked += 1
    return checked, diff, errors


def prepared_case(original, destination):
    if destination.exists():
        raise FileExistsError('Ja existe a pasta de saida: '+str(destination))
    destination.mkdir(parents=True)
    count = 0
    for name in CONFIG_FILES:
        for suffix in ('.json', ''):
            src = original / (name + suffix)
            if not src.is_file():
                continue
            dst = destination / src.name
            if name == 'qkdPhysicalLayer':
                obj = json.loads(src.read_text(encoding='utf-8-sig'))
                mb = obj.get('multiband') if isinstance(obj, dict) else None
                if isinstance(mb, dict) and 'bidirectionalFiber' in mb:
                    if mb['bidirectionalFiber'] is not False:
                        raise ValueError(f'{src}: a fibra bidirecional nao e suportada')
                    del mb['bidirectionalFiber']
                    dst.write_text(json.dumps(obj, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
                else:
                    shutil.copy2(src, dst)
            else:
                shutil.copy2(src, dst)
            count += 1
    if count < 4:
        raise ValueError(f'{original}: caso incompleto ({count} arquivos de entrada)')
    return destination


def execute(case, jar, log, simulations_root):
    with log.open('w', encoding='utf-8') as out:
        cmd = ['java', '-jar', str(jar), str(case)]
        out.write('COMANDO: '+subprocess.list2cmdline(cmd)+'\n')
        out.flush()
        r = subprocess.run(cmd, cwd=simulations_root, stdout=out, stderr=subprocess.STDOUT)
    print('  ', jar.name, case, 'returncode=', r.returncode, 'log=', log)
    if r.returncode:
        raise RuntimeError(f'JAR terminou com codigo {r.returncode}. Veja {log}')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='cmd', required=True)
    c = sub.add_parser('comparar', help='compara CSVs ja salvos (nao executa simulacao)')
    c.add_argument('--referencia', type=Path, required=True)
    c.add_argument('--atual', type=Path, required=True)
    c.add_argument('--rtol', type=float, default=1e-10)
    c.add_argument('--atol', type=float, default=0.0)
    c.add_argument('--relatorio', type=Path)
    a = sub.add_parser('executar-ab', help='clona entradas e executa um JAR antes e outro depois')
    a.add_argument('--cenario', type=Path, required=True)
    a.add_argument('--jar-antigo', type=Path, required=True)
    a.add_argument('--jar-novo', type=Path, required=True)
    a.add_argument('--saida', type=Path, required=True)
    a.add_argument('--rtol', type=float, default=1e-10)
    a.add_argument('--atol', type=float, default=0.0)
    args = p.parse_args()
    if not (math.isfinite(args.rtol) and args.rtol >= 0.0 and math.isfinite(args.atol) and args.atol >= 0.0):
        p.error('Tolerancias devem ser numeros finitos e nao negativos')
    try:
        if args.cmd == 'executar-ab':
            source = args.cenario.resolve()
            old_jar, new_jar = args.jar_antigo.resolve(), args.jar_novo.resolve()
            output = args.saida.resolve()
            if not source.is_dir(): raise ValueError('Cenario nao encontrado: '+str(source))
            if not old_jar.is_file() or not new_jar.is_file():
                raise ValueError('JAR antigo ou novo nao encontrado')
            if old_jar == new_jar:
                raise ValueError('Os dois JARs devem ser arquivos distintos (versoes diferentes)')
            if output.exists(): raise ValueError('Pasta de saida ja existe; nao sobrescrever: '+str(output))
            # input path must not be inside output to prevent recursion
            if source in output.parents or output in source.parents:
                raise ValueError('Saida nao pode englobar a pasta original do cenario')
            reference = prepared_case(source, output/'antigo'/source.name)
            current = prepared_case(source, output/'novo'/source.name)
            assert load_configs(reference) == load_configs(current)
            simulations_root = Path(__file__).resolve().parent.parent/'simulations'
            execute(reference, old_jar, output/'antigo.log', simulations_root)
            execute(current, new_jar, output/'novo.log', simulations_root)
            report = output/'comparacao.txt'
        else:
            reference, current = args.referencia.resolve(), args.atual.resolve()
            report = args.relatorio.resolve() if args.relatorio else None
        count, diff, errors = compare(reference, current, args.rtol, args.atol, 35)
        output = [f'Arquivos CSV comparados: {count}', f'Diferencas de celulas: {diff}',
                  f'Tolerancias: rel={args.rtol:g} abs={args.atol:g}',
                  'Observacao: resultados reais exigem MESMOS seeds, threads, entradas e algoritmo fisico.',
                  *errors]
        print('\n'.join(output))
        if report:
            report.parent.mkdir(parents=True, exist_ok=True)
            report.write_text('\n'.join(output)+'\n', encoding='utf-8')
        return 0 if count > 0 and not errors and diff == 0 else 1
    except (OSError, ValueError, RuntimeError) as exc:
        print('[ERRO]', exc, file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())

#!/usr/bin/env python3
"""Executa a regressao das tres familias, sem mascarar cenarios nao executados.

Uso:
  python tools/regressao/executar_regressao_integrada.py --jar CAMINHO_DO_JAR
  python tools/regressao/executar_regressao_integrada.py --verificar-casos

Nao gera ou altera automaticamente golden masters. Nao declara PASS sem executar.
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent
FAMILIAS = (
    ('classica', 'C*', 'main'),
    ('qkd', 'Q*', 'main'),
    ('mf_mb_mbsdm', 'M*', 'experimental'),
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--verificar-casos', action='store_true')
    parser.add_argument('--resultado', type=Path, default=ROOT/'resultado_regressao_integrada.json')
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    results = {'time_utc': datetime.now(timezone.utc).isoformat(), 'families': {}}
    valid = True
    for label, glob, reference in FAMILIAS:
        scenarios = sorted(p.name for p in (ROOT/'casos').glob(glob) if p.is_dir())
        master = ROOT/'referencias'/reference/'manifesto.json'
        entry = {'scenario_ids': scenarios, 'reference': reference,
                 'reference_exists': master.is_file(), 'status': 'NOT_RUN'}
        if not scenarios or not master.is_file():
            entry['status'] = 'CONFIGURATION_ERROR'
            valid = False
        results['families'][label] = entry
    if not args.verificar_casos:
        if args.jar is None or not args.jar.is_file():
            print('ERRO: JAR nao fornecido ou inexistente; regressao NAO executada.', file=sys.stderr)
            valid = False
        if valid:
            for label, glob, reference in FAMILIAS:
                cmd = [sys.executable, str(ROOT/'regressao.py'), 'comparar',
                       '--jar', str(args.jar.resolve()), '--referencia', reference,
                       '--casos', glob, '--java', args.java]
                run = subprocess.run(cmd, cwd=str(ROOT), text=True,
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                result = results['families'][label]
                result['status'] = 'PASS' if run.returncode == 0 else 'FAIL'
                result['exit_code'] = run.returncode
                result['output_tail'] = run.stdout[-12000:]
                print(label, result['status'])
                if run.returncode != 0:
                    valid = False
    args.resultado.parent.mkdir(parents=True, exist_ok=True)
    args.resultado.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding='utf-8')
    for label, entry in results['families'].items():
        print('%s: %s (%d casos; referencia %s)' %
              (label, entry['status'], len(entry['scenario_ids']), entry['reference']))
    print('Relatorio:', args.resultado)
    return 0 if valid else 1

if __name__ == '__main__':
    raise SystemExit(main())

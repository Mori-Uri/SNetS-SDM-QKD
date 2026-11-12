#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Etapa 5: reproducible scientific checks & dynamic replication campaigns.

Pure Python 3 stdlib; Java 8+ is required to run the provided JAR.
This harness NEVER interprets a test process exit=0 plus [PENDENTE] as fully
validated. Machine-readable outputs retain case, seed, mode, provenance.

Examples:
  python scripts/validar_cientificamente_etapa5.py verificar --modo rapido --saida out5
  python scripts/validar_cientificamente_etapa5.py campanha --cenario simulations/QKD_qualificacao/Exemplo_5nos_QTND30 --repeticoes 5 --pedidos 2000 --saida out-exp
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import sys
import time
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_JAR = ROOT / 'simulations' / 'SNetS-SDM-QKD-exp.jar'
CONFIG_FILES = ('network', 'simulation', 'traffic', 'physicalLayer', 'others', 'qkdPhysicalLayer')

QUICK = [
    'qkd.verification.QKDStage5QuantitativeSweepVerification',
    'qkd.verification.QKDStage5SimulationIntegrityVerification',
    'qkd.verification.QKDStage5MultibandQuantitativeVerification',
    'qkd.verification.QKDThesisFiguresVerification',
    'qkd.verification.QKDThesisSNetSPhysicalLayerVerification',
    'qkd.verification.QKDJohannissonExactXciVerification',
    'qkd.verification.QKDNarrowbandHabibiVerification',
    'qkd.verification.QKDGnScientificCorrectionsVerification',
    'qkd.verification.QKDStage2NliSafetyVerification',
    'qkd.verification.QKDStage3AmplifierChainVerification',
    'qkd.verification.QKDPhysicalVerification',
    'qkd.verification.QKDStage4InventoryDynamicsVerification',
    'qkd.verification.QKDReproducibilityVerification',
    'qkd.verification.QKDSpanGranularityVerification',
    'qkd.verification.QKDSlotContinuityVerification',
    'qkd.verification.QKDJointAdmissionVerification',
    'qkd.verification.QKDExistingSessionProtectionVerification',
    'qkd.verification.MultiBandIwcitVerification',
    'qkd.verification.MultiBandLiteratureVerification',
    'qkd.verification.MultiBandPhysicsVerification',
]
EXTRA = [
    'qkd.verification.QKDThesisCrosstalkVerification',
    'qkd.verification.QKDLobatoPerLinkPichVerification',
    'qkd.verification.QKDClassicalLongitudinalQoTVerification',
    'qkd.verification.QKDSharedAmplifierGainVerification',
    'qkd.verification.MultiBandNliVerification',
    'qkd.verification.MultiBandNetworkVerification',
    'qkd.verification.QKDTemporalKeyInventoryVerification',
    'qkd.verification.QKDPichSharedClassicalQoTVerification',
    'qkd.verification.QKDPichIntegratedAdmissionV15Verification',
    'qkd.verification.QKDUnifiedMultibandVerification',
    'qkd.verification.QKDRoutePhysicalEvaluationVerification',
    'qkd.verification.QKDDirectedFibresVerification',
    'qkd.verification.QKDSelectivePerformanceABVerification',
    'qkd.verification.QKDBoosterIntegratedVerification',
    'qkd.verification.QKDKeyIntervalSecondsVerification',
    'qkd.verification.QKDKeyReferenceProfilesVerification',
]

# Student-t two-sided 95% quantiles for df 1..30; normal limit for >30.
# CI is based on independent replications, not individual requests or load points.
T95 = [None, 12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365,
       2.306, 2.262, 2.228, 2.201, 2.179, 2.160, 2.145, 2.131,
       2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069,
       2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042]


def sha256_file(file: Path) -> str:
    h = hashlib.sha256()
    with file.open('rb') as f:
        for part in iter(lambda: f.read(1024*1024), b''):
            h.update(part)
    return h.hexdigest()


def write_csv(file: Path, columns: list[str], rows: list[dict]) -> None:
    file.parent.mkdir(parents=True, exist_ok=True)
    with file.open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=columns, extrasaction='ignore')
        writer.writeheader()
        writer.writerows(rows)


def save_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False, sort_keys=True) + '\n', encoding='utf-8')


def new_dir(path: Path) -> Path:
    if path.exists():
        raise ValueError('Saida ja existe; use outro caminho para preservar a rastreabilidade: '+str(path))
    path.mkdir(parents=True)
    return path


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding='utf-8-sig'))


def source_file(folder: Path, logical_name: str) -> Path | None:
    for name in (logical_name, logical_name + '.json'):
        file = folder / name
        if file.is_file():
            return file
    return None


def run_process(cmd: list[str], timeout: int, cwd: Path) -> tuple[str, int | None, float, bool]:
    started = time.perf_counter()
    try:
        p = subprocess.run(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           text=True, encoding='utf-8', errors='replace',
                           timeout=timeout, check=False)
        return p.stdout, p.returncode, time.perf_counter()-started, False
    except subprocess.TimeoutExpired as exc:
        s = exc.stdout or b''
        if isinstance(s, bytes):
            s = s.decode('utf-8', errors='replace')
        return s + '\n[ETAPA5] TIMEOUT\n', None, time.perf_counter()-started, True


def verifier_output_files(class_name: str, content: str, dest: Path) -> None:
    short = class_name.split('.')[-1]
    if short == 'QKDStage5QuantitativeSweepVerification':
        csv_lines = [s for s in content.splitlines() if s.startswith('reference,') or s.startswith('Johannisson2014,')]
        if len(csv_lines) > 1:
            (dest / 'johannisson_eq11_15_16.csv').write_text('\n'.join(csv_lines)+'\n', encoding='utf-8')
    if short == 'QKDStage5MultibandQuantitativeVerification':
        lines=[line for line in content.splitlines() if line.startswith('reference,')
               or line.startswith('Dibaj2024Fig5,')]
        if len(lines)>1:
            (dest/'dibaj2024_fig5_quantitativo.csv').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    if short == 'MultiBandLiteratureVerification':
        matches=re.findall(r'(\d+) nm/(\d+) km: ([0-9.]+) x ([0-9.]+)',content)
        rows=[]
        for nm,km,observed,published in matches:
            obs,ref=float(observed),float(published)
            rows.append({'reference':'Mehdizadeh2024_Fig5b_visual_reading',
                         'qch_wavelength_nm':int(nm),'length_km':int(km),
                         'qber_model':obs,'qber_read_from_plot':ref,
                         'absolute_difference':obs-ref,
                         'relative_difference':obs/ref-1.0 if ref!=0 else float('nan'),
                         'status':'PENDENTE'})
        if rows:write_csv(dest/'mehdizadeh2024_qber_divergencias.csv',list(rows[0]),rows)
    if short == 'QKDThesisFiguresVerification':
        csv_lines = [s for s in content.splitlines() if s.startswith('spans,') or re.match(r'^\d+,[-\d.]+,[-\d.]+$', s)]
        if len(csv_lines) > 1:
            (dest / 'jurandir_fig21_22_equacoes.csv').write_text('\n'.join(csv_lines)+'\n', encoding='utf-8')
    if short == 'QKDThesisSNetSPhysicalLayerVerification':
        csv_lines = [s for s in content.splitlines() if s.startswith('spans,engine_') or re.match(r'^\d+,[-\d.]+$',s)]
        if len(csv_lines) > 1:
            (dest / 'jurandir_motor_snets.csv').write_text('\n'.join(csv_lines)+'\n', encoding='utf-8')


def independent_jurandir_reference(out: Path) -> tuple[int, float]:
    """Thesis 2024 Table 6, Eqs 3.23-3.32, independently evaluated in Python.
    Is NOT a digitization of the 2017 full EGN, does NOT claim experimental validity.
    """
    alpha = 0.1 * math.log(10.0) / 10.0 / 1000.0 # field attenuation, 1/m
    beta2 = 21.3e-27
    gamma = 1.3e-3
    b = 32e9
    df = 33.6e9
    L = 100e3
    phi = 1.0  # PM-QPSK
    leff = -math.expm1(-2*alpha*L)/(2*alpha)
    xi = 8*gamma*gamma*leff*leff*(2*alpha)/(27*math.pi*beta2)
    psi_sci = xi*math.asinh((math.pi**2/(4*alpha))*beta2*b*b)
    omega_sci = xi*5*phi/(3*alpha*L)
    psi_xci = 0.0
    omega_xci = 0.0
    for idx in range(-7,8):
        if not idx: continue
        d = abs(idx)*df
        c = math.pi**2*beta2*b/(2*alpha)
        psi_xci += xi*(math.asinh(c*(d+b/2))-math.asinh(c*(d-b/2)))
        omega_xci += xi*5*phi*b/(6*alpha*d*L)
    gn = (psi_sci+psi_xci)/(b*b)
    egn = (psi_sci+psi_xci-omega_sci-omega_xci)/(b*b)
    if not (gn > egn > 0):
        raise AssertionError('Independent thesis equations gave invalid GN/EGN')
    observed = out/'jurandir_fig21_22_equacoes.csv'
    if not observed.is_file(): raise AssertionError('Thesis equations CSV was not generated')
    rows = []
    with observed.open(newline='', encoding='utf-8') as f:
        for line in csv.DictReader(f):
            n = int(line['spans'])
            for model, reference in (('GN',gn),('EGN_approx',egn)):
                calculated = float(line['gn_normalized_db'] if model=='GN' else line['egn_normalized_db'])
                expected = 10*math.log10(n*reference)
                error = calculated-expected
                rows.append({'reference':'Jurandir2024_Table6_Eqs3.23-3.32', 'spans':n,
                             'model':model,'engine_db':calculated,'oracle_db':expected,
                             'difference_db':error, 'tolerance_db':1e-6,
                             'status':'PASS' if abs(error)<=1e-6 else 'FAIL'})
    write_csv(out/'jurandir_vs_oraculo_python.csv',list(rows[0]),rows)
    return len(rows), max(abs(x['difference_db']) for x in rows)


def verify(args) -> int:
    jar = args.jar.resolve()
    if not jar.is_file(): raise ValueError('JAR nao encontrado: '+str(jar))
    target = new_dir(args.saida.resolve())
    (target/'logs').mkdir()
    suite = QUICK + (EXTRA if args.modo=='completo' else [])
    java_version, version_rc, _, _ = run_process([args.java,'-version'],20,ROOT)
    manifest = {'executed_utc':datetime.now(timezone.utc).isoformat(),
                'java_version':java_version.strip(), 'jar':str(jar),
                'jar_sha256':sha256_file(jar),'suite':args.modo,
                'timeout_s':args.timeout, 'cases':len(suite),
                'article_comparison_scope':'analytic / numerical; not experimental SSFM',
                'sources':['Johannisson/Agrell 2014 Eq11/15/16',
                           'Poggiolini/Jiang 2017 GN/EGN',
                           'Jurandir Lacerda Junior 2024 Table6 Fig21/22',
                           'Dibaj 2024 Fig3/5', 'Mehdizadeh 2024 Fig3/5']}
    save_json(target/'manifesto_execucao.json',manifest)
    rows=[]
    pending=[]
    for i, cls in enumerate(suite, 1):
        print(f'[{i}/{len(suite)}] {cls}', flush=True)
        out, code, elapsed, timed = run_process([args.java,'-cp',str(jar),cls],args.timeout,ROOT)
        short = cls.rsplit('.',1)[-1]
        (target/'logs'/(short+'.log')).write_text(out,encoding='utf-8')
        current_pending = [s.strip() for s in out.splitlines() if re.match(r'^\s*\[PENDENTE\]', s)]
        for line in current_pending:
            pending.append({'verificador':short,'pendencia':line, 'log':'logs/'+short+'.log'})
        if timed: status='TIMEOUT'
        elif code != 0: status='FAIL'
        elif current_pending: status='PENDENTE'
        else: status='PASS'
        verifier_output_files(cls,out,target)
        rows.append({'test':cls,'status':status,'exit_code':code if code is not None else 'timeout',
                     'duration_s':round(elapsed,3),'pending_items':len(current_pending),
                     'log':'logs/'+short+'.log'})
        print('    ',status,'(%.2fs)'%elapsed, 'pendencias='+str(len(current_pending)), flush=True)
    if (target/'jurandir_fig21_22_equacoes.csv').is_file():
        try:
            cases,error=independent_jurandir_reference(target)
            rows.append({'test':'ORACLE_PYTHON_JURANDIR_EQ3.23_3.32','status':'PASS',
                         'exit_code':0,'duration_s':0.0,'pending_items':0,
                         'log':f'jurandir_vs_oraculo_python.csv ({cases} casos, max|delta| {error:.3g} dB)'})
        except Exception as exc:
            rows.append({'test':'ORACLE_PYTHON_JURANDIR_EQ3.23_3.32','status':'FAIL',
                         'exit_code':2,'duration_s':0.0,'pending_items':0,'log':repr(exc)})
    write_csv(target/'RESULTADOS_VERIFICADORES.csv',list(rows[0]),rows)
    write_csv(target/'PENDENCIAS_CIENTIFICAS.csv',['verificador','pendencia','log'],pending)
    counts={s:sum(r['status']==s for r in rows) for s in ('PASS','PENDENTE','FAIL','TIMEOUT')}
    manifest['summary']=counts;manifest['pending_details']=pending
    save_json(target/'manifesto_execucao.json',manifest)
    report=[
        '# Etapa 5 — Relatorio reproducivel de verificacoes cientificas', '',
        f'- JAR SHA-256: `{manifest["jar_sha256"]}`',
        f'- Java: `{java_version.splitlines()[0] if java_version else "desconhecido"}`',
        f'- Escopo: {args.modo}; total {len(rows)} verificacoes.',
        f'- **PASS: {counts["PASS"]}; PENDENTE: {counts["PENDENTE"]}; FAIL: {counts["FAIL"]}; TIMEOUT: {counts["TIMEOUT"]}.**',
        '', '## Interpretacao', '',
        'PASS significa que os testes programados passaram; **nao significa validacao experimental**.',
        'PENDENTE significa que as comparacoes com a literatura NAO foram reproduzidas quantitativamente,',
        'mesmo que a classe Java tenha retornado codigo 0.',
        'O arquivo `jurandir_vs_oraculo_python.csv` compara equacoes da tese, nao pixels de graficos publicados.',
        'O arquivo `dibaj2024_fig5_quantitativo.csv` calcula o ganho com as convencoes explicitadas; nao corrige artigos.',
        'O arquivo `mehdizadeh2024_qber_divergencias.csv` inclui valores aproximados lidos de figuras pela verificacao original.',
        'O arquivo `johannisson_eq11_15_16.csv` compara integrais em retangulos com quadratura independente,',
        'nao experimentos SSFM; a Equacao 16 e uma aproximacao e pode ter diferenca fisica legitima.',
        '', '## Pendencias detectadas', '',
    ]
    report.extend('- '+r['verificador']+': '+r['pendencia'] for r in pending)
    if not pending: report.append('Nenhuma pendencia impressa pelos verificadores selecionados.')
    report+=['', '## Reprodutibilidade', '',
             'Entradas, parametros, codigo compilado e referencias de medicao precisam ser preservados.',
             'As referencias numericas extraidas de artigos por leitura visual nao devem ser consideradas',
             'mais precisas que a resolucao da figura original.',
             'Falta a validacao por instrumentacao experimental ou SSFM independente.', '']
    (target/'RELATORIO_ETAPA5.md').write_text('\n'.join(report), encoding='utf-8')
    print('Relatorio:',target/'RELATORIO_ETAPA5.md')
    return 1 if counts['FAIL'] or counts['TIMEOUT'] or (args.strict_pending and counts['PENDENTE']) else 0


def clone_case(source: Path, destination: Path, requests: int | None, points: int | None,
               seed: int, key_mode: str | None, protection_probability: float | None) -> dict:
    destination.mkdir(parents=True, exist_ok=False)
    files=[]
    for name in CONFIG_FILES:
        src=source_file(source,name)
        if src is None: continue
        obj=read_json(src)
        if name=='simulation':
            obj['replications']=1
            obj['threads']=1
            obj['trafficRandomSeed']=seed
            obj['qkdProtectionRandomSeed']=seed+170003
            demand=obj.get('qkdKeyDemand')
            if isinstance(demand,dict): demand['randomSeed']=seed+310007
            if requests is not None: obj['requests']=requests
            if protection_probability is not None:
                obj['qkdProtectedRequestProbability']=protection_probability
            if points is not None: obj['loadPoints']=points
            if key_mode is not None:
                inv=obj.setdefault('qkdKeyInventory',{})
                if not isinstance(inv,dict): raise ValueError('qkdKeyInventory invalido')
                inv['mode']=key_mode
            obj['saveState']={'enabled':False,'intervalMinutes':10.0, 'keepAfterFinish':False}
        if name=='qkdPhysicalLayer' and isinstance(obj,dict):
            mb=obj.get('multiband')
            if isinstance(mb,dict) and 'bidirectionalFiber' in mb:
                if mb['bidirectionalFiber'] is not False:
                    raise ValueError('Fibra bidirecional dentro do mesmo cabo nao e suportada: '+str(src))
                del mb['bidirectionalFiber']
        out=destination/src.name
        out.write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
        files.append({'file':src.name,'input_sha256':sha256_file(src),
                      'output_sha256':sha256_file(out)})
    if len(files)<4: raise ValueError('O cenario original precisa de no minimo 4 arquivos de configuracao: '+str(source))
    return {'files':files, 'seed':seed,'key_mode':key_mode,'requests':requests,'load_points':points,'qkd_protected_probability':protection_probability}


def find_metric_rows(folder: Path, run_id: str, label: str, key_mode: str,
                     replication: int, seed: int) -> list[dict]:
    metrics=[]
    files=[p for p in folder.rglob('*.csv') if ('_QKDMetrics.csv' in p.name or '_BlockingProbability.csv' in p.name or '_BitRateBlockingProbability.csv' in p.name)]
    if not files: return []
    for file in files:
        with file.open(newline='',encoding='utf-8-sig',errors='replace') as f:
            reader=csv.DictReader(f)
            if not reader.fieldnames or 'Metrics' not in reader.fieldnames or 'LoadPoint' not in reader.fieldnames:
                continue
            cols=[c for c in reader.fieldnames if c and re.fullmatch(r'rep\d+',c.strip())]
            if not cols: continue
            for row in reader:
                if not (row.get('Metrics') or '').strip(): continue
                if row.get('src','all')!='all' or row.get('dest','all')!='all' or row.get('BitRate','all')!='all': continue
                for col in cols:
                    raw=(row.get(col) or '').strip()
                    if not raw: continue
                    try: val=float(raw)
                    except ValueError: continue
                    if not math.isfinite(val): raise ValueError(f'Nonfinite simulated metric in {file}: {raw}')
                    metrics.append({'scenario':label,'key_mode':key_mode,'replication':replication,
                                    'seed':seed,'run_id':run_id, 'metric':row['Metrics'].strip(),
                                    'load_point':row['LoadPoint'].strip(),'value':val,
                                    'source_file':str(file.relative_to(folder)).replace('\\','/')})
    return metrics


def summarize_campaign(rows: list[dict], expected_reps: int) -> list[dict]:
    groups={}
    for row in rows:
        key=(row['scenario'],row['key_mode'],row['metric'],row['load_point'])
        groups.setdefault(key,[]).append(row)
    result=[]
    for (scenario,mode,metric,load),entries in sorted(groups.items()):
        if len({r['replication'] for r in entries})!=len(entries):
            raise ValueError('Metica duplicada por replicacao: '+repr((scenario,mode,metric,load)))
        data=[r['value'] for r in entries]
        n=len(data)
        mean=statistics.mean(data)
        sd=statistics.stdev(data) if n>1 else float('nan')
        critical=T95[n-1] if 2<=n<=31 else (1.96 if n>31 else float('nan'))
        margin=critical*sd/math.sqrt(n) if n>1 else float('nan')
        result.append({'scenario':scenario,'key_mode':mode,'metric':metric,
                       'load_point':load,'n':n,'expected_n':expected_reps,
                       'mean':mean,'sample_stddev':sd,'ci95_lower':mean-margin,
                       'ci95_upper':mean+margin,'status':'COMPLETE' if n==expected_reps and n>=2 else 'INCOMPLETE'})
    return result


def summarize_paired(rows: list[dict], expected_reps: int) -> list[dict]:
    """CI95 for within-seed STOCK-RATE differences, paired by replication."""
    pairs={}
    for row in rows:
        key=(row['scenario'],row['metric'],row['load_point'],row['replication'],row['seed'])
        modes=pairs.setdefault(key,{})
        if row['key_mode'] in modes:
            raise ValueError('Duplicate paired metric '+repr(key))
        modes[row['key_mode']]=row['value']
    groups={}
    for (sc,metric,load,rep,seed),modes in pairs.items():
        if 'rate' in modes and 'stock' in modes:
            groups.setdefault((sc,metric,load),[]).append(modes['stock']-modes['rate'])
    output=[]
    for (sc,metric,load),diffs in sorted(groups.items()):
        n=len(diffs)
        mean=statistics.mean(diffs)
        sd=statistics.stdev(diffs) if n>1 else float('nan')
        critical=T95[n-1] if 2<=n<=31 else (1.96 if n>31 else float('nan'))
        m=critical*sd/math.sqrt(n) if n>1 else float('nan')
        output.append({'scenario':sc,'metric':metric,'load_point':load,'n':n,
                       'expected_n':expected_reps,'mean_delta_stock_minus_rate':mean,
                       'sample_stddev_delta':sd,'ci95_lower_delta':mean-m,
                       'ci95_upper_delta':mean+m,
                       'status':'COMPLETE' if n==expected_reps and n>=2 else 'INCOMPLETE'})
    return output


def campaign(args) -> int:
    jar=args.jar.resolve()
    if not jar.is_file():raise ValueError('JAR nao encontrado: '+str(jar))
    if not args.cenario:raise ValueError('Forneca ao menos um --cenario')
    output=new_dir(args.saida.resolve())
    modes=['rate','stock'] if args.modo_chaves=='both' else [args.modo_chaves]
    results=[];runs=[];failures=[]
    java_version,_,_,_=run_process([args.java,'-version'],20,ROOT)
    data={'executed_utc':datetime.now(timezone.utc).isoformat(),
          'jar_sha256':sha256_file(jar),'java':java_version.strip(),'repeticoes':args.repeticoes,
          'seed_base':args.seed,'requests_per_load_point':args.pedidos,'load_points':args.pontos,
          'key_modes':modes,'qkd_protected_probability':args.qkd_prob,'runs':runs}
    for cs,source in enumerate(args.cenario):
        src=source.resolve()
        if not src.is_dir(): raise ValueError('Cenario nao encontrado: '+str(src))
        label=src.name
        # protect against case names colliding from different topologies
        label=('%02d_'%cs) + src.parent.name + '_' + label
        for mode in modes:
            for rep in range(args.repeticoes):
                seed=args.seed + cs*1000003 + rep*503
                run_id=label+'_'+mode+'_rep%03d'%(rep+1)
                case=output/'cases'/run_id
                config=clone_case(src,case,args.pedidos,args.pontos,seed,mode,args.qkd_prob)
                cmd=[args.java,'-jar',str(jar),str(case)]
                print('[CAMPAIGN]',run_id,flush=True)
                stdout,rc,sec,timeout=run_process(cmd,args.timeout,ROOT/'simulations')
                logfile=output/'logs'/(run_id+'.log');logfile.parent.mkdir(parents=True,exist_ok=True)
                logfile.write_text(stdout,encoding='utf-8')
                record={'run_id':run_id,'scenario':label,'replication':rep+1,'seed':seed,
                        'key_mode':mode,'status':'TIMEOUT' if timeout else ('PASS' if rc==0 else 'FAIL'),
                        'exit_code':rc,'duration_s':round(sec,3),
                        'configuration':config,'log':str(logfile.relative_to(output))}
                if rc==0:
                    observed=find_metric_rows(case,run_id,label,mode,rep+1,seed)
                    if not observed:
                        record['status']='NO_METRICS'
                        failures.append(record)
                    else:
                        results.extend(observed)
                        record['measurements']=len(observed)
                else:failures.append(record)
                runs.append(record)
    save_json(output/'manifesto_campanha.json',data)
    columns=['scenario','key_mode','replication','seed','run_id','metric','load_point','value','source_file']
    write_csv(output/'metricas_replicacoes.csv',columns,results)
    summary=summarize_campaign(results,args.repeticoes) if results else []
    paired = summarize_paired(results,args.repeticoes) if len(modes)==2 else []
    paired_cols=['scenario','metric','load_point','n','expected_n','mean_delta_stock_minus_rate',
                 'sample_stddev_delta','ci95_lower_delta','ci95_upper_delta','status']
    write_csv(output/'diferencas_pareadas_stock_rate_ic95.csv',paired_cols,paired)
    cols=['scenario','key_mode','metric','load_point','n','expected_n','mean','sample_stddev','ci95_lower','ci95_upper','status']
    write_csv(output/'resumo_ic95.csv',cols,summary)
    write_csv(output/'execucoes.csv',['run_id','scenario','key_mode','replication','seed','status','exit_code','duration_s','log'],runs)
    required=bool(summary) and all(x['status']=='COMPLETE' for x in summary)
    if len(modes)==2:
        required=required and bool(paired) and all(x['status']=='COMPLETE' for x in paired)
    failed=len(failures)>0 or not required
    summary_report=[
        '# Campanha dinamica — Etapa 5', '',
        f'- JAR SHA-256: `{data["jar_sha256"]}`',
        f'- Cenários fornecidos: {len(args.cenario)}; políticas: {", ".join(modes)}; replicações: {args.repeticoes}',
        f'- Execuções: {len(runs)}, com falha/sem métricas: {len(failures)}.',
        f'- Métricas agregadas: {len(summary)}; resultados completos: {sum(x["status"]=="COMPLETE" for x in summary)}.',
        f'- Comparações pareadas stock-rate (mesma semente): {len(paired)}.',
        '- IC95 t de Student para replicações independentes. Cada ponto de carga é tratado separadamente.',
        '- Os resultados deste estudo são simulações, não medições experimentais.',
        '', 'Arquivos: `metricas_replicacoes.csv`, `resumo_ic95.csv`, `diferencas_pareadas_stock_rate_ic95.csv`, `manifesto_campanha.json`.',
        'O uso de sementes iguais entre rate e stock permite comparação pareada de realização de tráfego,',
        'embora as trajetórias de estado resultantes naturalmente sejam diferentes.',
        '', '## Execuções não concluídas', '',
    ]
    summary_report.extend('- '+x['run_id']+' '+x['status'] for x in failures)
    if not failures: summary_report.append('Nenhuma falha de processo identificada.')
    (output/'RELATORIO_CAMPANHA.md').write_text('\n'.join(summary_report)+'\n',encoding='utf-8')
    print('Campanha:', 'INCOMPLETA' if failed else 'CONCLUIDA', 'relatorio=',output/'RELATORIO_CAMPANHA.md')
    return 1 if failed else 0


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__,formatter_class=argparse.RawDescriptionHelpFormatter)
    sub=parser.add_subparsers(dest='command',required=True)
    v=sub.add_parser('verificar',help='regressoes numericas e pendencias explicitamente rastreadas')
    v.add_argument('--modo',choices=['rapido','completo'],default='rapido')
    v.add_argument('--jar',type=Path,default=DEFAULT_JAR)
    v.add_argument('--java',default='java')
    v.add_argument('--timeout',type=int,default=180)
    v.add_argument('--saida',type=Path,required=True)
    v.add_argument('--strict-pending',action='store_true',help='retorna nao-zero se houver [PENDENTE]')
    c=sub.add_parser('campanha',help='executa cenarios reais com seeds e IC95 por load point')
    c.add_argument('--cenario',action='append',type=Path,required=True,help='caminho completo de cenario; repetivel')
    c.add_argument('--jar',type=Path,default=DEFAULT_JAR)
    c.add_argument('--java',default='java')
    c.add_argument('--saida',type=Path,required=True)
    c.add_argument('--timeout',type=int,default=1800)
    c.add_argument('--repeticoes',type=int,default=5)
    c.add_argument('--pedidos',type=int,default=None,help='sobrepoe simulation.requests')
    c.add_argument('--pontos',type=int,default=None,help='sobrepoe simulation.loadPoints')
    c.add_argument('--qkd-prob',type=float,default=None,
                   help='se informado, sobrepoe simulation.qkdProtectedRequestProbability [0,1]')
    c.add_argument('--modo-chaves',choices=['rate','stock','both'],default='both')
    c.add_argument('--seed',type=int,default=20261111)
    args=parser.parse_args()
    if args.timeout<=0:parser.error('--timeout deve ser positivo')
    if args.command=='campanha':
        if args.repeticoes<2:parser.error('Intervalo de confianca exige pelo menos 2 replicacoes')
        if args.pedidos is not None and args.pedidos<=0:parser.error('pedidos deve ser positivo')
        if args.pontos is not None and args.pontos<=0:parser.error('pontos deve ser positivo')
        if args.qkd_prob is not None and not (math.isfinite(args.qkd_prob) and 0<=args.qkd_prob<=1):
            parser.error('--qkd-prob deve estar em [0,1]')
    try:
        return verify(args) if args.command=='verificar' else campaign(args)
    except (OSError, ValueError, AssertionError, subprocess.SubprocessError) as e:
        print('[ERRO]',str(e),file=sys.stderr)
        return 2

if __name__=='__main__':
    sys.exit(main())

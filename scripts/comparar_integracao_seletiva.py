#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""A/B deterministic Stage-5 vs integrated SNetS runs. No third-party Python packages.

Example (Windows PowerShell):
 python scripts/comparar_integracao_seletiva.py --base-jar "simulations/SNetS-SDM-QKD-etapa5.jar" --integrated-jar "simulations/SNetS-SDM-QKD-exp.jar" --cenario "simulations/QKD_qualificacao/Exemplo_5nos_QTND30" --pedidos 100 --modo-chaves both --saida "resultados/comparacao_integracao"

The original baseline JAR must be preserved separately BEFORE installing this update.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time

CONFIGS = ('network', 'simulation', 'traffic', 'physicalLayer', 'others', 'qkdPhysicalLayer')

def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def read_case(directory: Path) -> dict[str, dict]:
    configs = {}
    for name in CONFIGS:
        file = directory / name
        if not file.is_file():
            file = directory / (name + '.json')
        if file.is_file():
            configs[file.name] = json.loads(file.read_text(encoding='utf-8-sig'))
    if len(configs) < 4:
        raise ValueError('Cenario deve conter pelo menos quatro configuracoes JSON: '+str(directory))
    return configs

def prepare(folder: Path, configs: dict[str, dict], seed: int, requests: int, mode: str, probability: float | None):
    folder.mkdir(parents=True)
    for name, value in configs.items():
        obj=json.loads(json.dumps(value))
        if name.startswith('simulation'):
            obj['replications']=1
            obj['threads']=1
            obj['loadPoints']=1
            obj['requests']=requests
            obj['trafficRandomSeed']=seed
            obj['qkdProtectionRandomSeed']=seed+170003
            obj['saveState']={'enabled':False, 'intervalMinutes':10.0,'keepAfterFinish':False}
            if probability is not None:
                obj['qkdProtectedRequestProbability']=probability
            if isinstance(obj.get('qkdKeyDemand'),dict):
                obj['qkdKeyDemand']['randomSeed']=seed+310007
            obj.setdefault('qkdKeyInventory',{})['mode']=mode
        if name.startswith('qkdPhysicalLayer'):
            multi=obj.get('multiband',{})
            if isinstance(multi,dict):
                if multi.get('bidirectionalFiber',False):
                    raise ValueError('Cenario com fibra bidirecional na mesma fibra nao e compativel com o modelo atual')
                multi.pop('bidirectionalFiber',None)
        (folder/name).write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')

def csv_contents(folder: Path, prefix: str) -> dict[str,str]:
    found={}
    for file in folder.rglob('*.csv'):
        key=file.name
        if key.startswith(prefix+'_'):
            key=key[len(prefix)+1:]
        found[key]=sha(file)
    return found

def main() -> int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base-jar',type=Path,required=True)
    p.add_argument('--integrated-jar',type=Path,required=True)
    p.add_argument('--cenario',type=Path,action='append',required=True)
    p.add_argument('--saida',type=Path,required=True)
    p.add_argument('--seed',type=int,default=20261112)
    p.add_argument('--pedidos',type=int,default=100)
    p.add_argument('--modo-chaves',choices=['rate','stock','both'],default='both')
    p.add_argument('--qkd-prob',type=float,default=None)
    p.add_argument('--timeout',type=int,default=90)
    p.add_argument('--java',default='java')
    a=p.parse_args()
    if a.pedidos<1 or a.timeout<1:p.error('pedidos/timeout devem ser positivos')
    if a.saida.exists():p.error('pasta de saida ja existe; escolha uma nova para preservar resultados')
    for jar in (a.base_jar,a.integrated_jar):
        if not jar.is_file():p.error('JAR inexistente: '+str(jar))
    modes=['rate','stock'] if a.modo_chaves=='both' else [a.modo_chaves]
    a.saida.mkdir(parents=True)
    results=[]
    manifest={'seed':a.seed,'pedidos':a.pedidos,'cenarios':[str(x) for x in a.cenario],
              'base_jar_sha256':sha(a.base_jar),'integrated_jar_sha256':sha(a.integrated_jar),
              'modes':modes,'runs':results}
    for index,scenario in enumerate(a.cenario):
        configs=read_case(scenario)
        for mode in modes:
            label=f'case{index+1:02d}_{scenario.name}_{mode}'
            observed={}
            for variant,jar,extra in [('etapa5',a.base_jar,[]),
                                      ('integrado',a.integrated_jar,[]),
                                      ('sem_cache',a.integrated_jar,['-Dsnets.noPerfCache=true'])]:
                folder=a.saida/'runs'/label/variant
                prepare(folder,configs,a.seed,a.pedidos,mode,a.qkd_prob)
                cmd=[a.java]+extra+['-jar',str(jar.resolve()),str(folder.resolve())]
                start=time.perf_counter()
                try:
                    proc=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                                        text=True,encoding='utf-8',errors='replace',timeout=a.timeout)
                    status='PASS' if proc.returncode==0 else 'FAIL'
                    log=proc.stdout
                except subprocess.TimeoutExpired as e:
                    status='TIMEOUT'
                    log=(e.stdout or b'').decode('utf-8','replace') if isinstance(e.stdout,bytes) else (e.stdout or '')
                elapsed=time.perf_counter()-start
                (a.saida/'logs').mkdir(exist_ok=True)
                (a.saida/'logs'/(label+'_'+variant+'.log')).write_text(log,encoding='utf-8')
                hashes=csv_contents(folder,variant)
                observed[variant]=(hashes,status)
                results.append({'cenario':label,'variante':variant,'status':status,
                                'segundos':round(elapsed,3),'arquivos_csv':len(hashes),
                                'numericos_identicos':None})
                print(label,variant,status,round(elapsed,3),'s',len(hashes),'CSVs',flush=True)
            original=observed['etapa5'][0]
            for variant in ('integrado','sem_cache'):
                current,status=observed[variant]
                ok=bool(original) and status=='PASS' and observed['etapa5'][1]=='PASS' and original==current
                for r in results:
                    if r['cenario']==label and r['variante']==variant:
                        r['numericos_identicos']=ok
                print('COMPARE',label,variant,'IDENTICO' if ok else 'DIVERGENTE/INCONCLUSIVO',flush=True)
    with (a.saida/'resultados.csv').open('w',newline='',encoding='utf-8') as stream:
        cols=['cenario','variante','status','segundos','arquivos_csv','numericos_identicos']
        w=csv.DictWriter(stream,fieldnames=cols);w.writeheader();w.writerows(results)
    (a.saida/'manifesto.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return 0 if all(r['numericos_identicos'] is True for r in results if r['variante']!='etapa5') else 1

if __name__=='__main__':
    raise SystemExit(main())

#!/usr/bin/env python3
"""Audita hipoteses fisicas e demanda QKD em cenarios SNetS sem bibliotecas externas.

Uso: python scripts/validar_perfis_qkd.py [diretorio-simulations] [--strict]
"""
import argparse,csv,json,sys
from pathlib import Path

PROFILES = {'moghaddam2021': (0.064, 0.128, 0.256),
            'dibaj2024': (0.000064, 0.000128, 0.000256)}

def inspect(p):
    issue=[]
    try:
        net=json.loads((p/'network').read_text(encoding='utf8'))
        sim=json.loads((p/'simulation').read_text(encoding='utf8'))
        traffic=json.loads((p/'traffic').read_text(encoding='utf8'))
        phys=json.loads((p/'qkdPhysicalLayer.json').read_text(encoding='utf8'))
    except (OSError, ValueError) as exc:
        return {'scenario':str(p),'status':'ERROR','notes':'failed to read JSON: '+str(exc)}
    nc=net.get('qkd',{}); sc=sim.get('qkdKeyDemand',{});cores=net.get('cores',[])
    if not nc.get('enabled',False): return None
    ids={x['id'] for x in cores}; quantum=nc.get('quantumCoreId')
    host=phys.get('multiband',{}).get('hostCoreId')
    arch=phys.get('multiband',{}).get('architecture','SDM').upper()
    profile=sc.get('profile','custom').lower(); mode=sc.get('mode','uniform').lower()
    intervals=tuple(sc.get('updateIntervalSeconds', (.064,.128,.256)))
    fixed=sum('keyRateMbps' in g and g['keyRateMbps'] is not None for g in traffic.get('requestGenerators',[]))
    prefer=sc.get('preferGeneratorFixedRate',True)
    effective='fixed generator override' if prefer and fixed else 'profile / selected intervals'
    if quantum not in ids: issue.append('ERR: quantum core not found')
    if arch=='SDM':
        if len(ids)!=7: issue.append('WARN: SDM not 7 physical cores')
        if quantum==0: issue.append('WARN: quantum core is central, not outer')
        if len(ids)==7 and quantum in ids:
            classical=len(ids)-1
            if classical != 6: issue.append('ERR: should have 6 classical cores')
    if arch in ('MF','MBSDM') and len(ids)==8 and quantum==7:
        issue.append('INFO: 8 *logical* core IDs, not 7 physical MCF cores')
    if arch=='MBSDM' and host is None: issue.append('ERR: missing MBSDM host core')
    if fixed and prefer: issue.append('WARN: traffic.keyRateMbps overrides literature key interval draws')
    if not nc.get('pichOsnrCheckEnabled',nc.get('pichSnrCheckEnabled', True)): issue.append('WARN: PICh QoT explicitly disabled')
    qber_gate=nc.get('qberThresholdEnabled',False)
    if not isinstance(qber_gate,bool): issue.append('ERR: qberThresholdEnabled must be boolean')
    if not 0<=nc.get('maximumQber',0.11)<=1: issue.append('ERR: invalid maximumQber')
    if profile in PROFILES and qber_gate and p.name != 'MCF7_Qualificacao_SDM':
        issue.append('WARN: independently imposed QBER cap changes article-inspired SKR-only admission')
    if profile not in ('custom',)+tuple(PROFILES): issue.append('ERR: unknown key profile')
    if profile=='custom' and len(intervals)==0: issue.append('ERR: no custom intervals')
    if profile in PROFILES and sc.get('keySizeBits',256)!=256:issue.append('ERR: named reference needs 256-bit key')
    if profile in PROFILES and fixed and prefer: issue.append('WARN: profile ignored due to generator override')
    if profile in PROFILES: interval=PROFILES[profile]
    else: interval=intervals
    if min(interval)<=0: issue.append('ERR: nonpositive intervals')
    if mode not in ('uniform','bybitrate'): issue.append('ERR: invalid key selection mode')
    status='ERROR' if any(x.startswith('ERR') for x in issue) else ('WARN' if any(x.startswith('WARN') for x in issue) else 'OK')
    return {'scenario':str(p),'status':status,'architecture':arch,
            'cores':len(ids),'quantumCore':quantum,'qtndKm':nc.get('qtndKm'),
            'keyProfile':profile,'demandMode':mode,'demandSource':effective,
            'keyRatesMbps':','.join('%.9g'%(sc.get('keySizeBits',256)/v/1e6) for v in interval),
            'fixedGenerators':fixed,'qberThresholdEnabled':qber_gate,
            'qberLimit':nc.get('maximumQber',0.11),
            'pichQot':nc.get('pichOsnrCheckEnabled',nc.get('pichSnrCheckEnabled', True)),
            'longitudinalXt':nc.get('longitudinalXtEnabled',False),
            'notes':' | '.join(issue)}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root',nargs='?',default='simulations/QKD_Referencias')
    parser.add_argument('--strict',action='store_true')
    parser.add_argument('--csv',help='optional CSV report path')
    args=parser.parse_args();root=Path(args.root)
    scenarios=sorted(p for p in root.rglob('network') if p.parent.joinpath('simulation').is_file())
    rows=[x for p in scenarios if (x:=inspect(p.parent)) is not None]
    if not rows:
        print('ERROR: no QKD scenario found',file=sys.stderr); return 2
    for row in rows:
        print('{status:5} {scenario} ({architecture}, cores={cores}, QCore={quantumCore}, '
              'keyProfile={keyProfile}, demand={demandSource}, QTND={qtndKm}km)'.format(**row))
        if row.get('notes'): print('  ',row['notes'])
    if args.csv:
        Path(args.csv).parent.mkdir(parents=True,exist_ok=True)
        with open(args.csv,'w',newline='',encoding='utf8') as f:
            keys=list(dict.fromkeys(k for row in rows for k in row.keys()))
            writer=csv.DictWriter(f,fieldnames=keys);writer.writeheader();writer.writerows(rows)
    return 1 if any(x['status']=='ERROR' or (args.strict and x['status']=='WARN') for x in rows) else 0
if __name__=='__main__': sys.exit(main())

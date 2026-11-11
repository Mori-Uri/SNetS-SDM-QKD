#!/usr/bin/env python3
"""Enumeracao de QTND global/per-link e ranking Pareto por resultados SNetS.

1) gerar: produz cenarios completos e manifest.csv; NAO prediz desempenho de rede.
2) avaliar: classifica métricas obtidas de simulacoes de trafego dinamico.

O planejador interno SNetS distribui igualmente os TNs por enlace. Para
posicoes arbitrarias ao longo de uma fibra, seria necessario outro modelo.
"""
from __future__ import annotations
import argparse,csv,itertools,json,math,shutil,sys
from pathlib import Path

FILENAME=('network','simulation','traffic','physicalLayer','others','qkdPhysicalLayer.json')

def parse_numbers(raw,conv=float):
    values=[conv(float(v.strip())) for v in raw.split(',') if v.strip()]
    if not values or any(v<0 for v in values):
        raise ValueError('supply a non-empty list of nonnegative values')
    return sorted(set(values))

def grouping(links):
    return {tuple(sorted((str(x['source']),str(x['destination'])))) for x in links}

def generate(args):
    source=Path(args.scenario)
    net=json.loads((source/'network').read_text(encoding='utf8'))
    if not net['qkd']['enabled']: raise ValueError('baseline has no QKD')
    out=Path(args.output); out.mkdir(parents=True,exist_ok=True)
    rows=[]
    if args.mode=='global':
        candidates=((f'qtnd_{v:g}',{'mode':'global','qtndKm':v})
                    for v in parse_numbers(args.values,float) if v>0)
    else:
        groups=sorted(grouping(net['links']))
        values=parse_numbers(args.values,int)
        def combinations():
            for i,points in enumerate(itertools.product(values,repeat=len(groups))):
                if i>=args.max_candidates: break
                yield (f'perlink_{i:04d}',
                       {'mode':'perLink','tnCounts':{a+'-'+b:n for (a,b),n in zip(groups,points)}})
        candidates=combinations()
    for id_,choice in candidates:
        destination=out/id_;destination.mkdir(parents=True,exist_ok=True)
        for fn in FILENAME:
            if (source/fn).is_file(): shutil.copy2(source/fn,destination/fn)
        n=json.loads((destination/'network').read_text(encoding='utf8'))
        q=n['qkd']; q['trustedNodeMode']=choice['mode']
        if choice['mode']=='global':
            q['qtndKm']=choice['qtndKm'];q.pop('trustedNodesPerLink',None)
        else:
            q['trustedNodesPerLink']=[{'link':k,'trustedNodes':v}
                     for k,v in choice['tnCounts'].items()]
        (destination/'network').write_text(json.dumps(n,indent=2,ensure_ascii=False)+'\n')
        # Count directed spans: these are potential physical QKD hops, not
        # actual allocated Q-TRx, which depends on traffic and key demands.
        count=0;tn=0;longest=0.0
        for link in n['links']:
            length=float(link['size'])
            if choice['mode']=='global':
                spans=math.ceil(length/choice['qtndKm']-1e-12)
            else:
                pair='-'.join(sorted((str(link['source']),str(link['destination']))))
                spans=choice['tnCounts'][pair]+1
            count+=spans;tn+=spans-1;longest=max(longest,length/spans)
        rows.append({'scenario':id_,'mode':choice['mode'],'directedSpans':count,
                 'logicalTrustedNodesDirected':tn,'maximumSegmentKm':longest,
                 'configuration':str(destination),'objective':'needs measured dynamic traffic results'})
    if not rows: raise ValueError('no candidates were generated')
    with (out/'manifest.csv').open('w',newline='',encoding='utf8') as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    print(f'Generated {len(rows)} runnable configurations and {out}/manifest.csv')

def dominates(a,b):
    # Pareto: all objective metrics must exist and be finite.
    directions={'blocking':1,'qtrx':1,'pichSlots':1,'maxQber':1,'minSkrMbps':-1}
    lhs=[float(a[k])*d for k,d in directions.items()]
    rhs=[float(b[k])*d for k,d in directions.items()]
    return all(x<=y for x,y in zip(lhs,rhs)) and any(x<y for x,y in zip(lhs,rhs))

def rank(args):
    with open(args.metrics,newline='',encoding='utf8') as f: rows=list(csv.DictReader(f))
    required=('scenario','blocking','qtrx','pichSlots','maxQber','minSkrMbps')
    if not rows: raise ValueError('empty metrics CSV')
    for i,row in enumerate(rows):
        for k in required:
            if k not in row: raise ValueError('missing column '+k)
            if k!='scenario' and not math.isfinite(float(row[k])):
                raise ValueError('row %d: nonfinite %s'%(i+2,k))
    eligible=[r for r in rows if float(r['maxQber'])<=args.maximum_qber
             and float(r['minSkrMbps'])>=args.min_skr_mbps]
    if not eligible: raise ValueError('no scenario satisfies QBER/SKR constraints')
    positive=['blocking','qtrx','pichSlots','maxQber'];weights={
        'blocking':args.w_blocking,'qtrx':args.w_qtrx,'pichSlots':args.w_pich,
        'maxQber':args.w_qber,'minSkrMbps':args.w_skr}
    # scale-free 0..1 regrets; all evaluated objectives use actual simulated values.
    extrema={k:(min(float(r[k]) for r in eligible),max(float(r[k]) for r in eligible))
             for k in weights}
    for r in eligible:
        score=0.0
        for k,w in weights.items():
            lo,hi=extrema[k];v=float(r[k]);normalized=(v-lo)/(hi-lo) if hi>lo else 0.0
            if k=='minSkrMbps': normalized=1.0-normalized if hi>lo else 0.0
            score+=w*normalized
        r['weightedRegret']=score
        r['pareto']=not any(other is not r and dominates(other,r) for other in eligible)
    eligible.sort(key=lambda r:(float(r['weightedRegret']),r['scenario']))
    dest=Path(args.output);dest.parent.mkdir(parents=True,exist_ok=True)
    with dest.open('w',newline='',encoding='utf8') as f:
        fieldnames=list(dict.fromkeys(k for r in eligible for k in r.keys()))
        writer=csv.DictWriter(f,fieldnames=fieldnames);writer.writeheader();writer.writerows(eligible)
    winner=eligible[0]
    print('Best measured feasible QTND/placement: '+winner['scenario']+
          '; weighted regret='+'%.6g'%winner['weightedRegret'])
    print(f'Feasible={len(eligible)} Pareto={sum(r["pareto"] for r in eligible)}; result={dest}')

def main():
    ap=argparse.ArgumentParser(description=__doc__);sub=ap.add_subparsers(dest='command',required=True)
    generate_p=sub.add_parser('gerar',help='create a QTND candidate grid')
    generate_p.add_argument('--scenario',required=True,help='path with network/simulation/traffic')
    generate_p.add_argument('--output',required=True)
    generate_p.add_argument('--mode',choices=['global','perLink'],default='global')
    generate_p.add_argument('--values',default='10,15,20,25,30,35,40')
    generate_p.add_argument('--max-candidates',type=int,default=100)
    generate_p.set_defaults(func=generate)
    ranking=sub.add_parser('avaliar',help='rank using measured SNetS performance')
    ranking.add_argument('--metrics',required=True)
    ranking.add_argument('--output',required=True)
    ranking.add_argument('--maximum-qber',type=float,default=0.11)
    ranking.add_argument('--min-skr-mbps',type=float,default=0.0)
    ranking.add_argument('--w-blocking',type=float,default=3.0)
    ranking.add_argument('--w-qtrx',type=float,default=1.0)
    ranking.add_argument('--w-pich',type=float,default=0.5)
    ranking.add_argument('--w-qber',type=float,default=1.0)
    ranking.add_argument('--w-skr',type=float,default=2.0)
    ranking.set_defaults(func=rank)
    args=ap.parse_args()
    try: args.func(args)
    except (ValueError,OSError,KeyError) as error:
        print('ERROR:',error,file=sys.stderr);return 2
    return 0
if __name__=='__main__':sys.exit(main())

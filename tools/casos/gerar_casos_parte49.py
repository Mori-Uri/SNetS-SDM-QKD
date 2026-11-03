"""Parte 49: gera simulations/QKD_Multibanda_Parte49 (QoT clássica por banda, ISRS, correção anti-Stokes e
política do QCh) a partir dos casos simulations/QKD_Multibanda/{Linha,Losango} da Parte 43.

Uso, na raiz do repositório:  python tools/casos/gerar_casos_parte49.py [pasta_base] [pasta_saida]
(sobrescreve os arquivos de configuração dos casos; não apaga resultados)."""
import json, os, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
BASE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'simulations', 'QKD_Multibanda')
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'simulations', 'QKD_Multibanda_Parte49')
SLOT = 12.5e9
# tamanho inicial (menor que os 100000 x 8 x 10 do QKD_Multibanda): ~10 h para os 26 casos com 10 threads
SIM = {'requests': 20000, 'loadPoints': 5, 'replications': 5}
GRIDS = {  # nome: (slots, centerFrequency Hz)
    'C': (320, 193.85e12),     # 191,85-195,85 THz (grade atual dos casos)
    'CL': (992, 190.6e12),     # 184,4-196,8 THz
}
def load(*p):
    return json.load(open(os.path.join(BASE, *p), encoding='utf-8'))

def dump(obj, path, indent=2):
    txt = json.dumps(obj, indent=indent, ensure_ascii=False)
    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(txt.replace('\n', '\r\n') if indent else txt)

def make(topo, name, fam='SSMF', arch='MB', raman='fisico', anti='medida', qch=1381.0, grid='C',
         mbClassico=None, policy=None):
    d = os.path.join(OUT, topo, name)
    os.makedirs(d, exist_ok=True)
    src = 'SSMF_MB_Raman-fisico' if fam == 'SSMF' else 'MCF7_MBSDM_Raman-fisico'
    net = load(topo, src, 'network')
    tra = load(topo, 'SSMF_MB_Raman-fisico', 'traffic')
    sim = load('Linha', 'SSMF_MB_Raman-fisico', 'simulation')
    sim.update(requests=SIM['requests'], loadPoints=SIM['loadPoints'], replications=SIM['replications'])
    phy = load('Linha', 'SSMF_MB_Raman-fisico', 'physicalLayer')
    oth = load('Linha', 'SSMF_MB_Raman-fisico', 'others')
    qpl = load(topo, src, 'qkdPhysicalLayer.json')
    slots, fc = GRIDS[grid]
    for l in net['links']:
        l['slots'] = slots
    phy['centerFrequency'] = fc
    phy['amplificationFrequency'] = fc
    if mbClassico is not None:
        phy['multiBand'] = mbClassico
    scale = slots / 320.0
    for g in tra['requestGenerators']:
        g['arrivalRate'] = round(g['arrivalRate'] * scale, 6)
        g['arrivalRateIncrease'] = round(g['arrivalRateIncrease'] * scale, 6)
    mb = qpl['multiband']
    mb['architecture'] = arch
    mb['ramanModel'] = raman
    mb['antiStokesCorrection'] = anti
    mb['qchCenterNm'] = qch
    mb['classicalBandStartThz'] = round((fc - SLOT * slots / 2) / 1e12, 4)
    if policy is not None:
        net['qkd']['qchGranularity'] = 'route'
        net['qkd']['wavelengthPolicy'] = 'grid'
        net['qkd']['qchSlotPolicy'] = policy
    for f, obj in (('network', net), ('traffic', tra), ('simulation', sim), ('physicalLayer', phy),
                   ('qkdPhysicalLayer.json', qpl)):
        dump(obj, os.path.join(d, f))
    dump(oth, os.path.join(d, 'others'), indent=None)

MB_ON = {'enabled': True, 'attenuationModel': 'g652d', 'waterPeakDbPerKm': 0.0,
         'noiseFigureDb': {'S': 7.0, 'C': 5.0, 'L': 6.0}, 'dispersionSlopePsNm2Km': 0.057,
         'gammaScalesWithFrequency': True, 'isrs': True, 'ramanGainSlopePerWKmTHz': 0.028}
MB_NO_ISRS = dict(MB_ON, isrs=False)

for topo in ('Linha', 'Losango'):
    # A: arquitetura, banda do QCh e correção anti-Stokes do SpRS (grade clássica C)
    make(topo, 'A0_SSMF_MF', arch='MF', raman='nenhum', anti='nenhuma')
    make(topo, 'A1_SSMF_MB_E1381_antiStokes-nenhuma', anti='nenhuma')
    make(topo, 'A2_SSMF_MB_E1381_antiStokes-medida', anti='medida')
    make(topo, 'A3_SSMF_MB_O1310_antiStokes-medida', anti='medida', qch=1310.0)
    make(topo, 'A4_MCF7_MBSDM_E1381_antiStokes-medida', fam='MCF7', arch='MBSDM', anti='medida')
    # B: QoT clássica por banda e ISRS (QCh: MB, físico, medida, 1381 nm; a referência banda C / QoT única é o A2)
    make(topo, 'B1_C_QoT-porBanda_ISRS', mbClassico=MB_ON)
    make(topo, 'B2_CL_QoT-unica', grid='CL')
    make(topo, 'B3_CL_QoT-porBanda_semISRS', grid='CL', mbClassico=MB_NO_ISRS)
    make(topo, 'B4_CL_QoT-porBanda_ISRS', grid='CL', mbClassico=MB_ON)
    # C: escolha do comprimento de onda do QCh (grade de QChs em torno de 1381 nm, C+L com ISRS)
    for i, pol in enumerate(('firstfit', 'abne', 'lowestloss', 'maxskr'), 1):
        make(topo, 'C%d_CL_QCh-%s' % (i, pol), grid='CL', mbClassico=MB_ON, policy=pol)
print('ok')

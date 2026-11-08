"""Parte 58: gera simulations/QKD_Multibanda_Parte58, a continuação dos casos das Partes 43 e 49.

Cada grupo responde a um dos "próximos testes" da análise dos casos multibanda:
  D  cargas menores (bloqueio clássico entre ~0,1 % e 5 %), Linha e Losango;
  E  MB-SDM (MCF-7) com a mesma carga por núcleo da fibra comum (7 vezes) e na mesma faixa de bloqueio;
  F  multibanda no cenário dos testes de multinúcleo (QKD_Test9: enlace de 200 km, MCF-7, QTND de 25 a 150 km);
  G  trechos longos com C+L: 1381 nm contra 1310 nm, para achar onde o Raman pesa mais que a perda;
  H  fibra bidirecional e ruído não linear (NLI) no QCh, separados e juntos.

Todos partem dos casos simulations/QKD_Multibanda_Parte49 (mesmos algoritmos, tráfego, demanda de chave, PICh e
camada física dos testes de multinúcleo). Mudam só os campos indicados em cada caso.

Uso, na raiz do repositório:  python tools/casos/gerar_casos_parte58.py [pasta_base] [pasta_saida]
(sobrescreve os arquivos de configuração dos casos; não apaga resultados)."""
import copy, json, os, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
BASE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'simulations', 'QKD_Multibanda_Parte49')
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'simulations', 'QKD_Multibanda_Parte58')
SLOT = 12.5e9
GRIDS = {'C': (320, 193.85e12), 'CL': (992, 190.6e12)}  # slots, centerFrequency (Hz)

# cargas: (fator da taxa inicial, fator do incremento) sobre o tráfego da Parte 49, que tem 300 E (Linha) e 1200 E
# (Losango) no primeiro ponto e o mesmo valor de incremento. Calibradas com simulações curtas (Parte 58).
LOADS = {
    ('Linha', 'SSMF'): (0.55, 0.05),     # 165, 180, 195, 210, 225 E
    ('Losango', 'SSMF'): (0.20, 0.0625),  # 240, 315, 390, 465, 540 E
    ('Linha', 'MCF7_7x'): (3.85, 0.35),   # 7 x SSMF: 1155 ... 1575 E
    ('Losango', 'MCF7_7x'): (1.40, 0.4375),  # 7 x SSMF: 1680 ... 3780 E
    ('Linha', 'MCF7'): (2.3333333, 0.3333333),  # 700 ... 1100 E (MCF-7 com bloqueio de ~0,1 a 5 %)
    ('Losango', 'MCF7'): (1.25, 0.2083333),     # 1500 ... 2500 E
    ('Linha', 'G'): (0.40, 0.05),         # x 3,1 do C+L: 372 ... 558 E (200 km; sem calibracao, foco na SKR)
}
SIM_SMALL = {'requests': 20000, 'loadPoints': 5, 'replications': 5}
SIM_TEST9 = {'requests': 100000, 'loadPoints': 8, 'replications': 10}  # igual ao QKD_Test9

MB_ON = {'enabled': True, 'attenuationModel': 'g652d', 'waterPeakDbPerKm': 0.0,
         'noiseFigureDb': {'S': 7.0, 'C': 5.0, 'L': 6.0}, 'dispersionSlopePsNm2Km': 0.057,
         'gammaScalesWithFrequency': True, 'isrs': True, 'ramanGainSlopePerWKmTHz': 0.028}


def load(*p):
    return json.load(open(os.path.join(BASE, *p), encoding='utf-8'))


def dump(obj, path, indent=2):
    txt = json.dumps(obj, indent=indent, ensure_ascii=False)
    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(txt.replace('\n', '\r\n') if indent else txt)


def make(folder, topo, name, fam='SSMF', arch='MB', raman='fisico', anti='medida', qch=1381.0, grid='C',
         loads=None, sim=SIM_SMALL, linkKm=None, qtnd=25.0, mbClassico=None, bidir=False, nli='nenhum'):
    """Writes one case. fam: SSMF (fibra comum, núcleo 0 + núcleo QKD virtual) ou MCF7 (7 núcleos + virtual)."""
    d = os.path.join(OUT, folder, name)
    os.makedirs(d, exist_ok=True)
    src = 'A2_SSMF_MB_E1381_antiStokes-medida' if fam == 'SSMF' else 'A4_MCF7_MBSDM_E1381_antiStokes-medida'
    net = load(topo, src, 'network')
    tra = load(topo, src, 'traffic')
    simj = load(topo, src, 'simulation')
    phy = load(topo, src, 'physicalLayer')
    oth = load(topo, src, 'others')
    qpl = load(topo, src, 'qkdPhysicalLayer.json')
    simj.update(sim)
    slots, fc = GRIDS[grid]
    for l in net['links']:
        l['slots'] = slots
        if linkKm is not None:
            l['size'] = linkKm
    net['qkd']['qtndKm'] = qtnd
    phy['centerFrequency'] = fc
    phy['amplificationFrequency'] = fc
    if mbClassico is not None:
        phy['multiBand'] = copy.deepcopy(mbClassico)
    grid_scale = slots / 320.0
    a, b = loads if loads is not None else (1.0, 1.0)
    for g in tra['requestGenerators']:
        r = g['arrivalRate']
        g['arrivalRate'] = round(r * a * grid_scale, 6)
        g['arrivalRateIncrease'] = round(r * b * grid_scale, 6)
    mb = qpl['multiband']
    mb['architecture'] = arch
    mb['ramanModel'] = raman
    mb['antiStokesCorrection'] = anti
    mb['qchCenterNm'] = qch
    mb['classicalBandStartThz'] = round((fc - SLOT * slots / 2) / 1e12, 4)
    mb['bidirectionalFiber'] = bidir
    mb['nliModel'] = nli
    for f, obj in (('network', net), ('traffic', tra), ('simulation', simj), ('physicalLayer', phy),
                   ('qkdPhysicalLayer.json', qpl)):
        dump(obj, os.path.join(d, f))
    dump(oth, os.path.join(d, 'others'), indent=None)


# ---------------------------------------------------------------- D e E: cargas menores, Linha e Losango
for topo in ('Linha', 'Losango'):
    f = os.path.join('D_E_CargasMenores', topo)
    L = LOADS[(topo, 'SSMF')]
    make(f, topo, 'D0_SSMF_MF', arch='MF', raman='nenhum', anti='nenhuma', loads=L)
    make(f, topo, 'D1_SSMF_MB_E1381_antiStokes-nenhuma', anti='nenhuma', loads=L)
    make(f, topo, 'D2_SSMF_MB_E1381_antiStokes-medida', loads=L)
    make(f, topo, 'D3_SSMF_MB_O1310_antiStokes-medida', qch=1310.0, loads=L)
    make(f, topo, 'E1_MCF7_MF_carga7x', fam='MCF7', arch='MF', raman='nenhum', anti='nenhuma',
         loads=LOADS[(topo, 'MCF7_7x')])
    make(f, topo, 'E2_MCF7_MBSDM_E1381_carga7x', fam='MCF7', arch='MBSDM', loads=LOADS[(topo, 'MCF7_7x')])
    make(f, topo, 'E3_MCF7_MBSDM_E1381_bloqueio-baixo', fam='MCF7', arch='MBSDM', loads=LOADS[(topo, 'MCF7')])

# ---------------------------------------------------------------- F: cenário do QKD_Test9 (200 km, MCF-7)
for q in (25.0, 50.0, 100.0, 150.0):
    f = 'F_CenarioMultinucleo_200km'
    make(f, 'Linha', 'F_MF_QTND%d' % q, fam='MCF7', arch='MF', raman='nenhum', anti='nenhuma',
         sim=SIM_TEST9, linkKm=200.0, qtnd=q)
    make(f, 'Linha', 'F_MBSDM_E1381_QTND%d' % q, fam='MCF7', arch='MBSDM', sim=SIM_TEST9, linkKm=200.0, qtnd=q)

# ---------------------------------------------------------------- G: trechos longos, C+L, 1381 x 1310 nm
for q in (25.0, 50.0, 75.0, 100.0):
    f = 'G_TrechosLongos_CL_200km'
    for nm, band in ((1381.0, 'E1381'), (1310.0, 'O1310')):
        make(f, 'Linha', 'G_%s_QTND%d' % (band, q), qch=nm, grid='CL', mbClassico=MB_ON,
             loads=LOADS[('Linha', 'G')], linkKm=200.0, qtnd=q)

# ---------------------------------------------------------------- H: bidirecional e NLI (Losango, cargas do D)
f = os.path.join('H_Bidirecional_NLI', 'Losango')
L = LOADS[('Losango', 'SSMF')]
make(f, 'Losango', 'H1_E1381_bidirecional', loads=L, bidir=True)
make(f, 'Losango', 'H2_E1381_NLI-gn', loads=L, nli='gn')
make(f, 'Losango', 'H3_E1381_bidirecional_NLI-gn', loads=L, bidir=True, nli='gn')
print('ok')

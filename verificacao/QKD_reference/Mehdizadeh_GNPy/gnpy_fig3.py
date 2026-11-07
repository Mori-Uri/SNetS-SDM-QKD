"""Parte 46 (VMB-12d): NLI que o GNPy calcula para o QCh do cenario da Fig. 3 de Mehdizadeh et al. (IEEE Commun.
Lett. 28(3), 2024): QCh em 1530,59 nm (-80 dBm, 12,5 GHz) e o comb da banda C (54 canais de 75 GHz ou 20 de
200 GHz, 0 dBm cada) depois de uma guarda de 200 GHz.

Uso (Python 3.10+):  pip install gnpy==2.14.2   e   python gnpy_fig3.py > resultados_gnpy.csv
"""
import numpy as np
from gnpy.core.info import create_arbitrary_spectral_information
from gnpy.core.elements import Fiber
from gnpy.core.parameters import SimParams

C = 299792458.0


def nli_dbm(length_km, bw, method, qch_dbm=-80.0):
    fq = C / 1530.59e-9
    n = 54 if bw == 75e9 else 20
    f = np.concatenate([[fq], [fq - 200e9 - bw / 2 - bw * i for i in range(n)]])
    p = np.concatenate([[1e-3 * 10 ** (qch_dbm / 10)], np.full(n, 1e-3)])
    b = np.concatenate([[12.5e9], np.full(n, bw)])
    o = np.argsort(f)
    f, p, b = f[o], p[o], b[o]
    si = create_arbitrary_spectral_information(f, pch=p, baud_rate=b, tx_osnr=60, slot_width=b, roll_off=0.0)
    SimParams.set_params({'raman_params': {'flag': False},
                          'nli_params': {'method': method, 'dispersion_tolerance': 1, 'phase_shift_tolerance': 0.1}})
    fiber = Fiber(uid='span', params={'length': length_km, 'length_units': 'km', 'loss_coef': 0.19,
                                      'dispersion': 1.75e-5, 'gamma': 1.2e-3, 'pmd_coef': 0,
                                      'ref_wavelength': 1550e-9, 'con_in': 0, 'con_out': 0, 'att_in': 0})
    fiber.ref_pch_in_dbm = 0.0
    out = fiber(si)
    iq = int(np.where(f == fq)[0][0])
    return 10 * np.log10(out.nli[iq] * 1e3), 10 * np.log10(out.nli[iq - 1] * 1e3)


print('metodo,largura_cch_GHz,comprimento_km,nli_qch_dBm,nli_cch_vizinho_dBm')
for method in ('gn_model_analytic', 'ggn_spectrally_separated'):
    for bw in (75e9, 200e9):
        for length in (5, 10, 25, 50, 100, 200, 300):
            q, c = nli_dbm(length, bw, method)
            print(f'{method},{bw / 1e9:.0f},{length},{q:.2f},{c:.2f}')

"""Parte 49 (VMB-13e): ISRS da aproximação triangular do simulador x equações de Raman acopladas com o ganho Raman
medido que o GNPy usa (gnpy.core.parameters.DEFAULT_RAMAN_COEFFICIENT, SSMF).

    dP_i/dz = -a P_i + sum_j c_ij P_j P_i
    c_ij = g0(|f_j - f_i|) f_i / f_ref         (j de frequência maior: o canal i ganha)
    c_ij = -g0(|f_j - f_i|) f_i / f_ref * f_i / f_j    (j de frequência menor: o canal i perde, com o fator de fótons)

g0 do GNPy já é g_R / A_eff (1/(W m)) na frequência de referência. Uso (venv com gnpy 2.14 e scipy):

    python isrs_gnpy.py      -> isrs_gnpy.csv
"""
import csv
import numpy as np
from gnpy.core.parameters import DEFAULT_RAMAN_COEFFICIENT as R
from scipy.integrate import solve_ivp


def ode_rho(f, p, alpha_db, length_km):
    """Potência no fim do span / potência sem ISRS, por canal (equações acopladas)."""
    a = alpha_db * np.log(10) / 10 / 1e3
    off = np.array(R['frequency_offset'])
    g0 = np.array(R['g0'])
    fref = R['reference_frequency']
    dfj = np.subtract.outer(f, f).T                     # [i, j] = f_j - f_i
    gr = np.interp(np.abs(dfj), off, g0) * (f[:, None] / fref)
    cr = np.where(dfj > 0, gr, -gr * np.outer(f, 1 / f))
    sol = solve_ivp(lambda z, P: P * (-a + cr @ P), [0, length_km * 1e3], p, rtol=1e-9, atol=1e-15)
    return sol.y[:, -1] / (p * np.exp(-a * length_km * 1e3))


def tri_rho(f, p_total, alpha_db, length_km, cr_slope=0.028, half_channel=25e9):
    """Aproximação triangular (network.ClassicalMultiBand.isrsRatio)."""
    a = alpha_db * np.log(10) / 10 / 1e3
    l = length_km * 1e3
    leff = -np.expm1(-a * l) / a
    x = p_total * cr_slope * 1e-15 * leff
    fmin = f.min() - half_channel
    b = f.max() + half_channel - fmin
    return x * b * np.exp(-x * (f - fmin)) / (-np.expm1(-x * b))


rows = []
for name, f0, f1, n, pdbm in [('C (4.8 THz)', 191.35e12, 196.10e12, 96, 0.0),
                              ('C+L (12 THz)', 184.5e12, 196.45e12, 240, 0.0),
                              ('C+L (12 THz) +3 dB', 184.5e12, 196.45e12, 240, 3.0)]:
    f = np.linspace(f0, f1, n)
    p = np.full(n, 1e-3 * 10 ** (pdbm / 10))
    o = 10 * np.log10(ode_rho(f, p, 0.2, 80))
    t = 10 * np.log10(tri_rho(f, p.sum(), 0.2, 80))
    ptot = 10 * np.log10(p.sum() * 1e3)
    print(f'{name}: P {ptot:.1f} dBm  inclinacao GNPy {o[0] - o[-1]:.2f} dB  triangular {t[0] - t[-1]:.2f} dB  '
          f'max|dif| {np.max(np.abs(o - t)):.2f} dB')
    rows.append([name, f'{ptot:.2f}', f'{o[0] - o[-1]:.3f}', f'{t[0] - t[-1]:.3f}', f'{np.max(np.abs(o - t)):.3f}'])
with open('isrs_gnpy.csv', 'w', newline='') as fh:
    w = csv.writer(fh)
    w.writerow(['cenario', 'P_total_dBm', 'tilt_ode_gnpy_dB', 'tilt_triangular_dB', 'max_abs_diff_dB'])
    w.writerows(rows)

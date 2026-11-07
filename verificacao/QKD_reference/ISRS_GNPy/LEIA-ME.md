# ISRS — aproximação triangular × equações acopladas com o ganho Raman do GNPy (Parte 49)

Referência do item **VMB-13e** (`qkd.verification.MultiBandClassicalQoTVerification`).

Este item compara a inclinação de potência por ISRS da QoT clássica por banda (`network.ClassicalMultiBand.isrsRatio`, aproximação triangular de Zirngibl 1998 e Semrau et al. 2018) com as equações de Raman acopladas. As equações usam o perfil de ganho Raman medido que o GNPy traz (`gnpy.core.parameters.DEFAULT_RAMAN_COEFFICIENT`, SSMF) e incluem a depleção e o fator de fótons f_i/f_j.

- `isrs_gnpy.py`: script; precisa de gnpy 2.14 e scipy.
- `isrs_gnpy.csv`: resultado (span de 80 km, 0,2 dB/km, 0 ou 3 dBm por canal).

| Cenário | P total (dBm) | GNPy (dB) | Triangular (dB) | Maior diferença por canal (dB) |
|---|---|---|---|---|
| C, 4,8 THz, 96 canais | 19,8 | 1,35 | 1,17 | 0,11 |
| C+L, 12 THz, 240 canais | 23,8 | 7,81 | 7,38 | 0,47 |
| C+L, 12 THz, +3 dB | 26,8 | 15,70 | 14,73 | 1,07 |

A aproximação triangular fica 5–13 % abaixo da inclinação com o perfil medido, que tem o pico em 13 THz. O VMB-13e aceita até 15 %.

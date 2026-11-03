# NLI do QCh com o GNPy (Parte 46, VMB-12d)

Reprodução, com o GNPy 2.14.2, da curva "NLI" da Fig. 3 de Mehdizadeh et al. (IEEE Commun. Lett. 28(3), 2024).

**Cenário:**
- QCh em 1530,59 nm, com −80 dBm e 12,5 GHz;
- comb da banda C a 0 dBm por canal, depois de uma guarda de 200 GHz: 54 canais de 75 GHz ou 20 de 200 GHz;
- fibra com 0,19 dB/km, D = 17,5 ps/(nm·km) (β2 ≈ −22,3 ps²/km) e γ = 1,2 /(W·km).

| Arquivo | Conteúdo |
|---|---|
| `gnpy_fig3.py` | script (`pip install gnpy==2.14.2`) |
| `resultados_gnpy.csv` | NLI no QCh e no canal clássico vizinho, de 5 a 300 km, métodos `gn_model_analytic` e `ggn_spectrally_separated` |

**O que se conclui:**

1. O pico do `gn_model_analytic` dá −129 dBm (canais de 75 GHz) e −138 dBm (200 GHz), a cerca de 25 km. Na figura
   do artigo são cerca de −130 e −137 dBm.
2. O GNPy calcula o NLI de cada canal como SPM + XPM sobre o próprio canal, proporcional à potência dele. Para um QCh
   de −80 dBm isso dá cerca de 2,6e-8 fótons por janela, praticamente nada.
3. O GNPy não calcula os produtos de FWM dos canais clássicos que caem na frequência do QCh. Esse termo é o que o
   modelo GN do simulador calcula (−95 dBm a 25 km neste cenário).
4. A diferença do artigo na banda S vem do SpRS, e não do NLI: o artigo conta todos os fótons de SpRS (fração 1),
   sem o fator 0,5 de Dibaj et al. (JLT 2025, eq. 6). Isso é verificado no VMB-12e.

A forma fechada usada pelo GNPy está implementada em `MultiBandLiteratureVerification.gnpyAnalyticNliW`. Ela confere
com este CSV dentro de 0,2 dB.

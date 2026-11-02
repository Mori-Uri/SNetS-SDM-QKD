# G5 — Seção de choque de Raman espontâneo (SpRS): Eraerds 2010 e Kawahara 2011

Arquivos auxiliares (mesma pasta):
- `G5_eraerds_fig1_raw_vector.csv`, `G5_eraerds_fig2_raw_vector.csv`: curvas da Fig. 1 e da Fig. 2 de Eraerds,
  **extraídas diretamente dos vetores do PDF** (pdftocairo → SVG → coordenadas calibradas pelas linhas de grade). Colunas: λ [nm], ρ [(km·nm)^-1].
- `G5_rho_eraerds_1550_resampled.csv`: as mesmas curvas reamostradas em Δν de −5,1 a +11,7 THz, passo de 0,1 THz, em (km·nm)^-1 e (km·GHz)^-1.

Convenções destas notas: Δν = ν_bomba − ν_espalhado. **Δν > 0 = Stokes** (λ maior que a bomba), **Δν < 0 = anti-Stokes**. c = 299 792,458 nm·THz.

---

## 1. Citações

**[E] Eraerds 2010.** P. Eraerds, N. Walenta, M. Legré, N. Gisin, H. Zbinden, "Quantum key distribution and 1 Gbit/s data encryption over a single fibre", *New Journal of Physics* **12**, 063027 (2010). DOI 10.1088/1367-2630/12/6/063027.
O PDF disponível é o preprint arXiv:0912.1798v1 (9 dez. 2009). Ele não traz volume, artigo nem DOI. Esses dados vêm da versão publicada e convém conferi-los. A numeração de equações e figuras abaixo segue o arXiv.

**[K] Kawahara 2011.** H. Kawahara, A. Medhipour, K. Inoue, "Effect of spontaneous Raman scattering on quantum channel wavelength-multiplexed with classical channel", *Optics Communications* **284**(2), 691–696 (2011). DOI 10.1016/j.optcom.2010.09.051 (consta no PDF). Recebido em 31/03/2010 e aceito em 21/09/2010.

---

## 2. Definições, unidades e equações

### 2.1 Eraerds [E]
- **ρ(λ)** é a "seção de choque Raman efetiva", em **[(km·nm)^-1]**, normalizada por comprimento de fibra e por largura espectral. Ela "já inclui a razão de captura da fibra" (*fibre caption ratio*), ou seja, a fração do espalhamento guiada no modo. A mesma ρ vale para os sentidos co- e contrapropagante, porque a dispersão dos fônons ópticos é quase plana e o espalhamento é quase isotrópico.
- Eq. (1): ∫_λ^{λ+Δλ} ρ(λ')dλ' ≈ ρ(λ)·Δλ.
- Eq. (6) (Apêndice A): dP_ram(λ,x) = P_in·e^{−αx}·ρ(λ)·Δλ·dx.
- **Eq. (2), progressivo:** P_ram,f = P_out·L·ρ(λ)·Δλ, com P_out = P_in·e^{−αL}.
- **Eq. (3), retroespalhado:** P_ram,b = P_out·[sinh(αL)/α]·ρ(λ)·Δλ, que equivale a P_in·(1−e^{−2αL})/(2α)·ρΔλ.
- Unidades: P em W, **α em km^-1 (neper)**, L em km, Δλ em nm. A atenuação é suposta igual para a bomba e para o sinal espalhado, o que vale num vão de 4 nm.
- **Erros tipográficos no apêndice:** a Eq. (8) está escrita "P_in e^{−αL} ρ Δλ", sem o fator L; o correto é a Eq. (2). A Eq. (10) traz "e^{−α·x}", que deveria ser e^{−αL}.
- **Eq. (11), probabilidade de detecção por gate:** p_ram,f = (P_ram,f / hν)·η·Δt_gate, e o mesmo vale para p_ram,b. η é a eficiência do detector (0,07) e Δt_gate a duração do gate (o artigo trabalha "por ns"; o gate real é de 1,5 ns). Na Fig. 3 entram também as perdas internas de Bob, t_B = 2,65 dB.
- QBER, Eq. (13): QBER = ½·[p_μ(1−V) + 2p_dc + p_AP + p_ram + p_ct] / [β p_μ + 2p_dc + p_AP + p_ram + p_ct], com p_ram = p_ram,f + p_ram,b, β_BB84 = 1 e β_SARG = (2−V)/2.

### 2.2 Kawahara [K]
- **η**, a "Raman efficiency", em **[nm^-1·km^-1]**. É a mesma grandeza física que o ρ de Eraerds.
- Eq. (1): dP(z) = η·P_p(z)·dz. Eq. (2): P_p(z) = P_p(0)e^{−α_p z}.
- **Eq. (5), progressivo:** P^f = η P_p(0)/(α_r − α_p)·[e^{−α_p L} − e^{−α_r L}]. Eq. (6), com α_p ≈ α_r: P^f ≈ η P_p(0) e^{−α_p L}·L.
- **Eq. (8), retroespalhado:** P^b = η P_p(0)/(α_p+α_r)·{1 − e^{−(α_p+α_r)L}}. O artigo imprime uma forma equivalente, com o integrando "exp(−α_p z)·exp(−α_p z)", em que o segundo α_p deveria ser α_r. Eq. (9), com α_p ≈ α_r: P^b ≈ η P_p(0)/(2α_p)·(1 − e^{−2α_p L}).
- **Atenção:** o artigo tem **duas equações numeradas (8)**. A segunda dá o número de fótons por janela: d_raman = P_raman(λ)·Δf·τ / (hν). Aqui P_raman é a densidade de potência por nm, **Δf é a largura do filtro em nm** e τ a janela temporal. A eficiência quântica do detector é aplicada à parte: 10,5% no APD e 1,4% no SSPD.
- O η é considerado independente do sentido de bombeio (cita Kidorf et al., IEEE PTL 11, 1041, 1999). Para DSF o artigo usa η_DSF = 1,5 × η_SMF, por conta da área efetiva menor.

---

## 3. Dependência com a temperatura

- **Nenhum dos dois artigos traz fórmula explícita** de Bose–Einstein, nem uma conversão g_R → ρ. Os dois **não citam g_R**.
- [E] diz apenas o seguinte:
  - (i) do lado anti-Stokes há "**10% menos ruído Raman** à temperatura ambiente" (Fig. 2). Por isso o canal quântico fica abaixo dos canais clássicos.
  - (ii) "calculando os **números médios de ocupação de fônons**", o ruído Raman em 1310 nm sairia "**~4000 vezes mais fraco**" que em 1550 nm. O artigo não mostra o cálculo. A estimativa considera só a ocupação térmica e ignora o formato de g_R; trate o número como ordem de grandeza.
  - (iii) A curva foi medida a "room temperature", sem valor de T.
- [K] não discute temperatura.
- **Teoria padrão (não vem destes artigos; é para implementação):**
  n_th(Δν,T) = 1/(exp(h|Δν|/k_BT) − 1), com h/k_B = 4,799×10^-11 K·s.
  ρ_S(Δν) ∝ g_R(Δν)·[n_th+1], ρ_AS(|Δν|) ∝ g_R(|Δν|)·n_th, logo ρ_AS/ρ_S = exp(−h|Δν|/k_BT), descontados fatores ν^n.
  A forma absoluta mais usada é dP/dz/dν ≈ P_p·(g_R/A_eff)·hν_s·(n_th+1)·(fator de polarização ~½ a 1). **O fator absoluto é incerto.** Recomendo calibrar a escala pela curva medida de [E] em vez de calculá-la a partir de g_R.
- **Teste com os dados de [E]** (razão AS/S por GHz no mesmo |Δν|, contra a previsão de Boltzmann a 300 K):

| abs(Δν) [THz] | 0,5 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| ρ_AS/ρ_S medido | 0,86 | 0,76 | 0,69 | 0,64 | 0,57 | 0,53 |
| exp(−h abs(Δν)/kT), T = 300 K | 0,92 | 0,85 | 0,73 | 0,62 | 0,53 | 0,45 |
| T efetiva correspondente | 154 K | 175 K | 258 K | 324 K | 345 K | 378 K |

Entre 2 e 4 THz o resultado é compatível com 300 K, com erro de ±15%. Abaixo de ~1 THz a razão se afasta do esperado, provavelmente por vazamento da bomba ou ASE, pela resolução do OSA ou pela assimetria do perfil. Para |Δν| < 1 THz trate os valores de [E] como incertos.

---

## 4. Curvas digitalizadas

### 4.1 Eraerds, Fig. 1 e Fig. 2 (extração vetorial, alta fidelidade)
**Condições de medida:** bomba laser "centrada em 1550 nm"; 50 km de SSMF padrão; temperatura ambiente. A Fig. 1 cobre 1510–1650 nm, isto é, **Δν de −5,12 THz (AS) a +11,72 THz (S)**. A Fig. 2 é um zoom de 1543–1550 nm. Na Fig. 1 o pico estreito entre ~1549,5 e 1550,3 nm, que sai da escala, é a própria bomba e **não deve ser usado**.

**Método:** as curvas são vetoriais no PDF. As coordenadas foram convertidas pelas linhas de grade: x de 1510 a 1650 nm; y de 0 a 8×10^-9 na Fig. 1 e de 0 a 3×10^-9 na Fig. 2. O erro de leitura é < 0,5% da escala. **A incerteza experimental não é informada pelos autores.** Estimo ±15–20% na escala absoluta, que é a calibração típica de OSA, e ±30% para |Δν| < 1 THz.

| Δν [THz] | λ [nm] | ρ [10^-9 (km·nm)^-1] | ρ [10^-11 (km·GHz)^-1] |
|---|---|---|---|
| −5,0 | 1510,9 | 2,16 | 1,65 |
| −4,0 | 1518,6 | 2,47 | 1,90 |
| −3,0 | 1526,3 | 3,03 | 2,35 |
| −2,0 | 1534,1 | 3,61 | 2,83 |
| −1,5 | 1538,1 | 3,94 (máx. AS local, 1538,5 nm: 3,96) | 3,11 |
| −1,0 | 1542,0 | 3,37 | 2,67 |
| −0,5 | 1546,0 | 1,94 | 1,55 |
| −0,3 | 1547,6 | 1,62 | 1,29 |
| −0,2 | 1548,4 | 1,59 (mín. AS em 1547,6 nm: 1,54) | 1,27 |
| +0,2 | 1551,6 | 1,80 (mín. S em 1552,0 nm: 1,79) | 1,44 |
| +0,5 | 1554,0 | 2,25 | 1,81 |
| +1,0 | 1558,1 | 4,35 | 3,52 |
| +1,5 | 1562,1 | 5,06 (máx. local em 1563,0 nm: 5,09) | 4,12 |
| +2,0 | 1566,2 | 5,02 | 4,11 |
| +3,0 | 1574,4 | 4,44 | 3,67 |
| +4,0 | 1582,7 | 3,97 | 3,32 |
| +5,0 | 1591,1 | 3,68 (mín. local em 1591,0 nm: 3,67) | 3,11 |
| +6,0 | 1599,6 | 3,86 | 3,30 |
| +7,0 | 1608,2 | 4,33 | 3,73 |
| +8,0 | 1616,9 | 4,93 | 4,30 |
| +9,0 | 1625,6 | 5,51 | 4,86 |
| +10,0 | 1634,5 | 5,88 | 5,24 |
| +11,0 | 1643,5 | 6,21 (máx. em 1641,0 nm: 6,25) | 5,59 |
| +11,7 | 1650,0 | 5,87 | 5,30 |

Conversão entre unidades: ρ_ν [(km·GHz)^-1] = ρ_λ·λ²/c ×10^-3, com λ em nm e c em nm·THz.

**Zoom (Fig. 2), na posição dos canais espelho da grade de 100 GHz.** Os retângulos +1…+6 do artigo estão centrados em 1549,32 / 1548,51 / 1547,72 / 1546,92 / 1546,12 / 1545,32 nm. O ρ médio aproximado em cada um é 1,75 / 1,60 / 1,60 / 1,72 / 1,85 / 2,1 (×10^-9). O mínimo está nos canais +2 e +3, como diz o próprio artigo.

**Observações:**
- O texto de [E] fala em máximo de intensidade a "~13 THz (~100 nm)". A curva medida, porém, termina em 1650 nm (+11,7 THz) e **já está caindo** depois de 1641 nm (+10,7 THz). O pico de ganho Raman da sílica, ~13,2 THz, **não aparece** na figura. A queda final pode ser artefato, por exemplo perda por curvatura ou resposta do OSA em λ longo. Isso não pode ser verificado.
- O anti-Stokes só vai até −5,1 THz. **Nada da curva serve para o O/E-band** (|Δν| ~ 25–40 THz).

### 4.2 Kawahara, Fig. 4 (η medido; raster, centroides detectados por pixel)
**Condições:** laser CW em 1,55 μm, com filtro de 0,8 nm na saída; **4 dBm** lançados; fibra **Furukawa SM332 (SMF)**; OSA com resolução de 1 nm; vários comprimentos de fibra (10, 12,5, 25, 37,5 e 50 km, Fig. 3); η obtido por ajuste à Eq. (5). A temperatura não é informada. A escala vale ×10^-8 nm^-1 km^-1. Calibração: resíduo dos ticks < 0,01 unidade; incerteza de leitura ±0,03×10^-8.

| Δλ [nm] | λ [nm] | Δν [THz] | η [10^-8 nm^-1 km^-1] |
|---|---|---|---|
| −130 | 1420 | −17,71 | 0,14 |
| −110 | 1440 | −14,78 | 0,85 |
| −90 | 1460 | −11,92 | 1,12 |
| −70 | 1480 | −9,15 | 1,13 |
| −50 | 1500 | −6,44 | 1,26 |
| +40 | 1590 | +4,86 | 2,43 |
| +60 | 1610 | +7,20 | 2,66 |
| +80 | 1630 | +9,50 | 3,08 |
| +100 | 1650 | +11,72 | 4,40 |
| +120 | 1670 | +13,90 | 5,45 |
| (+140) | 1690 | +16,02 | 0,21 ⚠ |

⚠ O ponto em +140 nm aparece na figura, mas o texto diz que a medida vale só "de −130 a +120 nm". Considere-o não confiável.

**Fig. 7 (η interpolado, só o lado anti-Stokes):** os pontos coincidem com a Fig. 4. O polinômio impresso é η(x) = 6,1e-16x⁴ + 2,8e-13x³ + 4,2e-11x² + 2,6e-9x + 6,9e-8, com x = deslocamento em nm (negativo). Ao avaliá-lo obtive: −110 → 7,8e-9; −90 → 1,11e-8; −70 → 1,14e-8; −50 → 1,28e-8; **−130 → −1,4e-10 (negativo)**. Os coeficientes estão arredondados demais para a borda. Não use o polinômio abaixo de −120 nm.
**Fig. 6 (perda da fibra):** o polinômio impresso, α = 5,0e-8λ³ − 2,3e-4λ² + 3,4e-1λ(²?) − 176,5 dB/km, tem erro tipográfico e dá ≈ −16 dB/km em 1550 nm. **É inutilizável.** Os pontos medidos ficam em 0,210–0,268 dB/km entre 1500 e 1650 nm, com mínimo ≈ 0,21 dB/km por volta de 1570–1580 nm.

### 4.3 Inconsistência entre [E] e [K] (importante)
- Nos mesmos Δν, **η de [K] é ~6–7× maior** que ρ de [E]. Em +4,86 THz: 2,43e-8 contra 3,68e-9 (×6,6). Em +11,7 THz: 4,40e-8 contra 5,87e-9 (×7,5). A forma das curvas é parecida e a razão AS(−6,4 THz)/S(+4,9 THz) ≈ 0,52 em [K], contra ≈ 0,58 em [E] nos extremos.
- O próprio [K] é **internamente inconsistente**. Lendo o espectro bruto da Fig. 2 (12,5 km, −71 dBm/nm em ~1670 nm, −77,5 dBm/nm em ~1500 nm, P_p = 2,51 mW, α ≈ 0,22 dB/km), η ≈ ρ ≈ 5×10^-9 em +120 nm e ≈ 1×10^-9 em −50 nm. Esses valores batem com [E] e ficam ~10× abaixo da Fig. 4. O eixo da Fig. 3 está rotulado "×10^7 mW/nm", certamente um erro por ×10^-7. Com esse rótulo corrigido, a Fig. 3 (2,1e-7 mW/nm em 10 km) leva a η ≈ 1,4e-8, ainda ~4× abaixo da Fig. 4.
- **Conclusão:** use [E] como escala absoluta. Use [K] apenas como **formato relativo** do lado anti-Stokes até −17,7 THz e do lado Stokes até +13,9 THz.

### 4.4 Kawahara, Fig. 3 (leitura visual, ±0,05; unidade presumida 10^-7 mW/nm)
| Δλ | 10 km | 12,5 km | 25 km | 37,5 km | 50 km |
|---|---|---|---|---|---|
| +120 nm | 2,10 | 2,12 | 1,70 | 1,39 | ~0,45 |
| +80 nm | 1,29 | 1,41 | 1,20 | 1,04 | ~0,43 |
| +40 nm | 0,96 | 1,10 | 0,99 | 0,86 | ~0,33 |
| −50 nm | 0,50 | 0,57 | 0,48 | 0,39 | ~0,13 |
| −90 nm | 0,39 | 0,50 | 0,41 | 0,33 | ~0,12 |
| −130 nm | ~0,03 | ~0,05 | ~0,03 | ~0,02 | ~0,01 |

---

## 5. Alvos numéricos de verificação

### 5.1 Eraerds
- **Fig. 3:** probabilidade de ruído por gate de 1 ns contra L, extraída dos vetores. Parâmetros: 4 canais clássicos, cada um com **−28 dBm no receptor**, o que equivale a **P_out = −26,05 dBm** nas Eqs. (2) e (3), já que o DWDM tem 1,95 dB de perda; α_dB = 0,21 dB/km; η = 0,07; perdas internas de 2,65 dB; isolação do DWDM de 82 dB. Q-ch em 1551,72 nm e clássicos acima dele, sendo o mais próximo a +200 GHz e os demais espaçados de 100 GHz.

| L [km] | 1 | 5 | 10 | 20 | 30 | 40 | 50 | 60 |
|---|---|---|---|---|---|---|---|---|
| p_ram,f [ns^-1] | 1,05e-6 | 5,26e-6 | 1,06e-5 | 2,12e-5 | 3,20e-5 | 4,28e-5 | 5,38e-5 | 6,49e-5 |
| p_ram,b [ns^-1] | 1,10e-6 | 5,55e-6 | 1,14e-5 | 2,55e-5 | 4,54e-5 | 7,55e-5 | 1,23e-4 | 1,97e-4 |

  Na mesma figura: linha de dark count = 1,00e-5 ns^-1 (= 2 × 5e-6) e crosstalk = 9,3e-6 ns^-1. O progressivo cresce como L (≈ 1,07e-6 por km), porque P_out é fixo; o retroespalhado cresce como e^{αL}.
  Verificação de consistência que eu fiz: com ρ ≈ 1,6e-9 nos canais espelho +2…+5, a inclinação de 1,07e-6/km exige **Δλ_eff ≈ 0,23 nm** sem a perda do DWDM de Bob, ou ≈ 0,36 nm contando 1,95 dB. O artigo **não informa** o Δλ usado, mas esses valores são plausíveis para a banda passante de um DWDM de 100 GHz.
- **Tabela I** (Rsec em bps / QBER em %, BB84/SARG):
  - Sem filtros: 1 km 2829 / 0,57; 5 km 2047 / 0,72; 10 km 1524 / 1,18; 25 km 134 e 511 / 4,53 e 2,12; 35 km 4,3 e 72 / 8,60 e 4,77; 50 km SARG 2,0 / 7,48.
  - Com filtros FBG (45 pm FWHM, 14 dB de extinção, rejeição de 85% do ruído, +2 dB de perda): 25 km 251/347 bps, 1,6/1,7%; 35 km 25/128 bps, 3,6/2,5%; 41 km 7,5/43 bps, 6,7/3,7%; 50 km 0/11 bps, 34,5/5,4%.
- Outros valores do texto: alcance máximo de 41 km (BB84 com filtros) e 50 km (SARG); limite de QBER de 9%; limite AES-256 de 8,6 bps; queda de Rsec < 50% até 25 km em relação à fibra escura; transceptor de 10 G com sensibilidade de −23 dBm (+3,2 dB de potência) reduz o alcance em 4–5 km; COW a 312,5 MHz com μ = 0,5 chegaria a 70 km e > 10 kbps até 43 km.
- **Fig. 6 (1310 contra 1550):** α1550 = 0,21 dB/km; α1310 = 0,35 dB/km; isolação de 100 dB em 1310 e 82 dB em 1550; ruído Raman em 1310 "~4000× menor". Só com p_dc = 5e-8 ns^-1 a opção em 1310 nm alcança distância maior. As curvas não foram digitalizadas.
- Detector: 220 K, gate de 1,5 ns, tempo morto de 10 μs, p_ap ≈ 0,008, p_dc ≈ 5e-6 ns^-1, f_rep = 5 MHz, linha de armazenamento de 10 km. Para a criptografia são necessários ~1,2×10^4 fótons/ns a −28 dBm, e para levar o crosstalk ao nível do dark count é preciso isolação de ~80 dB.

### 5.2 Kawahara (DPS-QKD; queda de 20% no alcance como critério)
- **Tabela 1:**
  - Progressivo, APD: −29 dBm, filtro de 0,5 nm, separação de 90 nm, capacidade de 5 Gbps.
  - Progressivo, SSPD: −48 dBm, 0,1 nm, 90 nm, 800 kbps.
  - Retroespalhado, APD: −45 dBm, 0,5 nm, 90 nm, 3,2 Mbps.
  - Retroespalhado, SSPD: −94 dBm, 0,1 nm, 90 nm, impossível.
- Valores do texto, todos com separação de 50 nm: progressivo/APD −27 dBm (0,5 nm); progressivo/SSPD −53 dBm (0,4 nm); retroespalhado/APD −54 dBm (0,5 nm); retroespalhado/SSPD −90 dBm (0,1 nm).
- Parâmetros: APD com QE 10,5%, dark count 5e-5/slot e janela de 300 ps. SSPD com QE 1,4%, 2,5e-9/slot e 50 ps, usado em DSF com η × 1,5. Perda de demux mais filtro de 2 dB; erro do interferômetro de 1%; ataque individual geral. Q-ch e C-ch com perdas iguais (por exemplo 1536 e 1626 nm para 90 nm); o Q-ch fica do lado anti-Stokes.
- **Fig. 8** (leitura visual ±1 km): taxa em L = 0 de ≈ 2×10^-2 bit/pulso. Alcance do "QKD only" ≈ 39,5 km. (a) Com filtro de 0,5 nm e 90 nm de separação: −32 dBm → ~33,5 km, −29 dBm → ~31,5 km, −26 dBm → ~28,5 km. (b) Filtros de 1,5/0,5/0,2 nm: ~27/~31,5/~34 km. (c) Separações de 50/90/130 nm: ~32/~31,5/~33 km. A ordem em (c) não é monotônica e é difícil de ler.
- **Fig. 13** (35 km, 0,5 nm, 90 nm; leitura visual): APD ≈ 5×10^4 bit/s, platô até ~−40 dBm, corte em ≈ −35 dBm. SSPD ≈ 8×10^5 bit/s, corte em ≈ −17 dBm.

---

## 6. Recomendação para o simulador (ρ(Δν) de 0 a 40 THz)

1. **Fonte primária da escala absoluta:** a curva de [E] (CSV reamostrado), tabulada em **ρ_ν [(km·GHz)^-1] contra Δν [THz]**, para a bomba de referência ν_0 = 193,41 THz (1550 nm). Interpolar linearmente (ou PCHIP) em Δν. Nunca interpolar em λ.
2. **Fora de −5,1…+11,7 THz**, esses dois artigos **não dão dados**. Para cobrir 0–40 THz, Stokes e anti-Stokes, use um perfil de ganho Raman da sílica, g_R(Δν), normalizado. Uma opção da literatura é o ajuste multi-Lorentziano de Hollenbeck & Cantrell (JOSA B 19, 2886, 2002); confira antes de usar. Construa:
   - ρ_ν(Δν>0; T) = K·g̃_R(Δν)·[n_th(Δν,T)+1]
   - ρ_ν(Δν<0; T) = K·g̃_R(|Δν|)·n_th(|Δν|,T)
   - Calibre K por mínimos quadrados contra [E] em +3…+11 THz, a região mais confiável. Valide o lado AS contra [E] em −2…−5 THz (ver §3) e contra o formato relativo de [K] até −17,7 THz.
3. **Mudança de bomba:** ρ_ν depende de Δν, mais um fator lento ∝ ν_p (g_R ∝ ν_p), ∝ ν_s (energia do fóton espalhado) e de A_eff(λ). Uma forma razoável é ρ_ν(ν_p, ν_s) = ρ_ν,ref(Δν)·(ν_p/ν_0)·(ν_s/ν_{s,ref}). Para obter por nm, use ρ_λ = ρ_ν·c/λ_s². A combinação desses fatores dá aproximadamente o (λ)^-4 usado por Mehdizadeh.
4. **Temperatura:** use T = 300 K por padrão e recalcule n_th para outra T. Para ir de T_1 a T_2, multiplique o lado S por (n_2+1)/(n_1+1) e o lado AS por n_2/n_1.
5. **Potência e fótons:** P_f = Σ_c P_c,in e^{−α_c L}·ρ_λ,c·Δλ·L (usar a Eq. (5) de [K] se α_c ≠ α_q). P_b = Σ_c P_c,in·ρ_λ,c·Δλ·(1−e^{−(α_c+α_q)L})/(α_c+α_q). Depois, N = P·η_det·Δt_gate/(hν_q), mais as perdas de Bob. Use α em neper/km.
6. **Comparação com a regra de Mehdizadeh** (ρ(λ_c,λ_q) = (λ_δ/λ_q)^4·ρ(1550,λ_δ), com |λ_c−λ_q| = 1550 − λ_δ):
   - (a) **O mapeamento é por diferença de comprimento de onda, não de frequência.** Exemplos meus: clássico em 1530 nm e Q em 1310 nm dão Δν_real = 32,9 THz, contra 32,0 THz pela regra (erro de 0,9 THz). Clássico em 1610 nm (banda L) e Q em 1310 nm dão Δν_real = 42,6 THz, contra 46,4 THz pela regra (erro de 3,8 THz). Na cauda do espectro de g_R esse erro é grande.
   - (b) Com λ_δ = 1550 − |Δλ| sempre < 1550, a regra **usa sempre o ramo anti-Stokes**. Para Q em O/E-band com clássicos em C+L isso está correto, porque Q fica sempre do lado AS. Para Q na banda C entre canais C+L, porém, os canais abaixo de λ_q geram Stokes e precisam do ramo λ_δ > 1550. Implemente com sinal.
   - (c) A curva de referência precisaria ir até λ_δ ≈ 1250–1310 nm. **[E] só vai até 1510 nm e [K] até 1420 nm.** Mehdizadeh deve ter usado outra medida, que não temos.
   - (d) A curva de referência "room temperature" incorpora n_th(T_med) implicitamente. A regra não permite variar T.
   - **Recomendação:** implementar ρ(Δν,T) em frequência, conforme os itens 2–4, e manter a regra de Mehdizadeh só como modo de compatibilidade para reproduzir os números daquele artigo.
7. **Faixa |Δν| < 0,3 THz:** [E] dá ≈ 1,6–1,8×10^-9 (km·nm)^-1. Em C-band DWDM com 100–200 GHz de espaçamento, use esse valor e assuma ±30% de incerteza.

# G2 — Ruído Raman (SpRS) em QKD: notas de a01, a05, a02

Fonte: extrações `pdftotext -layout` em
`/mnt/user-data/uploads/Simulador/SNetS-SDM-QKD/launcher/.work/txt_artigos/{a01,a05,a02}.txt`.
Convenção: **[TEXTO]** = valor lido literalmente; **[RECONSTRUÍDO]** = equação remontada a partir de
fragmentos ilegíveis (símbolos perdidos no pdftotext) — conferir no PDF; **[DERIVADO]** = número que eu
calculei com as equações (não é número publicado).

---

## a01 — da Silva, Xavier, Temporão, von der Weid (2014)

### 1. Identificação
- Título: "Impact of Raman scattered noise from multiple telecom channels on fiber-optic quantum key
  distribution systems".
- Autores: Thiago Ferreira da Silva, Guilherme B. Xavier, Guilherme P. Temporão, Jean Pierre von der Weid
  (PUC-Rio / INMETRO / Univ. de Concepción). [TEXTO]
- Revista/ano/DOI: **não aparecem no texto extraído** (cabeçalho só com "Member, IEEE"). Pelo formato IEEE e
  referências até 2014 é compatível com J. Lightwave Technol. 2014 (vol. 32, nº 13?) — **NÃO CONFIRMADO no
  texto; confirmar no PDF/página do artigo**.

### 2. Cenário
- Tudo na banda C, grade ITU-T DWDM 100 GHz. QCh no canal ITU 39 = **1546,12 nm**, sem sinal clássico.
- Experimento de canal único (Fig. 1–2): laser sintonizável (TLS) varrido nas bandas S, C, L, U,
  **contra-propagante**, **1 mW (0 dBm)**, bobina de **7,5 km**, filtro do QCh **50 GHz FWHM**.
- Experimento multicanal (Fig. 3–4): **14 lasers DFB CW** em grade ITU, acoplador 16:1, **−10,5 dBm por
  canal** medido em PM1 (total máx. **1,5 dBm** com os 14 ligados). Co- e contra-propagação.
- Fibra: **SMF-28**, comprimentos 0 (back-to-back) a **60 km em passos de 10 km**.
- Pré-filtragem dos clássicos: 3 pares isolador+FBG notch 100 GHz FWHM no λ_q → **60 dB**; + 40 dB
  (pico/ASE dos lasers) → **100 dB** de separação clássico/quântico.
- Filtragem do QCh: DEMUX DWDM + 2 FBGs (100 GHz e **10 GHz FWHM**) em reflexão com circulador 4 portas →
  ~60 dB de extinção, **perda de inserção in-band 8,4 dB**.
- Detector: SPD APD em modo Geiger gated, **η = 15 %**, gate efetivo **2,5 ns**, trigger **1 MHz**,
  dead-time "10 [µs?]" (unidade perdida), probabilidade de dark count **ilegível** no texto.
  Contagem totalizada em 3 intervalos de 20 s.

Tabela I (canais populados; desvio = ν_q − ν_d relativo ao QCh, [TEXTO]); interpretação das colunas
[RECONSTRUÍDA pela posição dos "×", progressão 2,4,…,14 canais — conferir]:

| Canal ITU | Desvio [GHz] | Configs |
|---|---|---|
| 50 | −1110 (provável −1100) | G |
| 49 | −1000 | G |
| 45 | −600 | E,F,G |
| 44 | −500 | C–G |
| 40 | −100 | B–G |
| 39 | 0 (QCh) | — |
| 38 | 100 | A–G |
| 37 | 200 | A–G |
| 36 | 300 | B–G |
| 35 | 400 | C–G |
| 30 | 900 | D–G |
| 29 | 1000 | D–G |
| 28 | 1100 | E,F,G |
| 27 | 1200 | F,G |
| 25 | 1400 | F,G |

Nº de canais: A=2, B=4, C=6, D=8, E=10, F=12, G=14.
Somas de separações (em nº de canais de 100 GHz) [DERIVADO]:
Stokes (dados em freq. MAIOR que o QCh: canais 40–50) Σ|k−j|: A=0, B=1, C=6, D=6, E=12, F=12, G=33.
Anti-Stokes (dados em freq. MENOR: canais 25–38) Σ|k−j|: A=3, B=6, C=10, D=29, E=40, F=66, G=66.

### 3. Equações (numeração do artigo)
Símbolos: L comprimento; P_in potência lançada por canal de dados; ᾱ atenuação média (dados ≈ quântico,
suposta igual); ρ(Δν) coeficiente SRS efetivo [km⁻¹] no desvio Δν; η eficiência do aparato de detecção
(inclui SPD); Δt (τ) janela de gate; hν̄ energia do fóton.

**(1)** contagens SRS por gate, canal único [RECONSTRUÍDO — forma padrão de Eraerds/Xavier, compatível
com os fragmentos "L·ρ(Δν)·exp(−ᾱL)" e "(…)[1−exp(−2ᾱL)]"]:

    co:      p_SRS = (η Δt / hν̄) · P_in · ρ(Δν) · L · exp(−ᾱ L)
    contra:  p_SRS = (η Δt / hν̄) · P_in · ρ(Δν)/(2ᾱ) · [1 − exp(−2ᾱ L)]

**(2)** dependência espectral linear em "V" em torno do QCh (aproximação válida só na vizinhança, banda C)
[RECONSTRUÍDO]: com j = nº ITU do QCh e k = nº ITU do canal de dados

    ρ = ρ_S  · (k − j)   se ν_dados > ν_q  (Stokes;  k > j)
    ρ = ρ_AS · (j − k)   se ν_dados < ν_q  (anti-Stokes; k < j)

ρ_S, ρ_AS = inclinações por canal de 100 GHz de separação.

**(3)** vários canais (soma das contribuições, mesma potência por canal):

    co:      p_SRS = (η Δt / hν̄) · P_in · L · exp(−ᾱL) · Σ_k ρ_k
    contra:  p_SRS = (η Δt / hν̄) · P_in · [1 − exp(−2ᾱL)]/(2ᾱ) · Σ_k ρ_k

(Perdas adicionais à atenuação da fibra foram consideradas "linearmente" no ajuste.)

**(4)** parâmetro não-linear: γ = n₂ ω / (c A_eff).
Critério de FWM desprezível (ref. Eraerds [12]): **γ·P·L < 0,1**.

**(5)** descasamento de fase (Shibata) [RECONSTRUÍDO]:
    Δβ = (2πλ²/c) |Δf_ik Δf_jk| [ D + (λ²/2c)(dD/dλ)(Δf_ik + Δf_jk) ]

**(6)** eficiência de FWM [RECONSTRUÍDO, forma padrão]:
    η_FWM = α²/(α² + Δβ²) · [ 1 + 4 e^{−αL} sin²(ΔβL/2) / (1 − e^{−αL})² ]

**(7)** t_AB = 10^{−αL/10} · t_Bob · η_D.
**(8)** Y_n ≈ Y_0 + η_n, η_n = 1 − (1 − t_AB)^n.
Y_0 = 2 p_dark + ξ · p_SRS (ruído SRS não polarizado dividido entre os 2 SPDs; ξ = fator de modulação/duty
cycle: **ξ = 1 para PSK**, **ξ = ¼ para OOK-RZ**).
**(9)** Q_μ = Σ_n Y_n e^{−μ} μ^n/n! (= Y_0 + 1 − e^{−t_AB μ}).
**(10)** E_μ = [e_0 Y_0 + e_det(1 − e^{−t_AB μ})]/Q_μ, e_0 = ½.
**(11)** R ≥ q { −Q_μ f(E_μ) H₂(E_μ) + Q_1 [1 − H₂(e_1)] } (decoy infinito, GLLP/Lo-Ma-Chen).

Não há equação de ρ(λ) com temperatura (fator de Bose–Einstein) — ρ é empírico (Tabela II).

### 4. Parâmetros
| Parâmetro | Valor [TEXTO] |
|---|---|
| ρ_S co | **6,9 ± 1,6 × 10⁻¹² km⁻¹** por canal de separação |
| ρ_AS co | **11,5 ± 1,0 × 10⁻¹² km⁻¹** |
| ρ_S contra | **6,8 ± 1,3 × 10⁻¹² km⁻¹** |
| ρ_AS contra | **10,8 ± 0,9 × 10⁻¹² km⁻¹** |
| Condição da Tab. II | "per spectral separation (in number of 100 GHz channels) for **10 GHz detection bandwidth and 2.5 ns detection gate**" |
| A_eff | 50 µm² |
| n₂ | 2,6 × 10⁻²⁰ m²/W |
| D | 16 ps km⁻¹ nm⁻¹ a 1550 nm; zero em 1310 nm |
| Inclinação de D | "66,7 s/m" (sic; ≈ 0,0667 ps nm⁻² km⁻¹ = 66,7 s/m³) |
| Razão η_FWM (não casado/casado), canais 37–38–39 | **2,2 × 10⁻⁵** |
| Simulação SKR: μ | 0,50 |
| e_det (misalignment) | 0,033 |
| Gate na simulação | **1 ns** |
| Filtro na simulação | 100, 10 e (Fig. 9) 1 GHz FWHM |
| Potência por canal na simulação | 0 dBm (Figs. 6–7); −10 a 0 dBm (Figs. 8–9) |
| f(e) correção de erro | de Lütkenhaus [24] (valor não dado) |
| η_D, p_dark, ᾱ da simulação | **símbolos perdidos no pdftotext**; "data presented in [23]" = Gobby, Yuan, Shields APL 2004 (GYS). Provavelmente os valores GYS usados por Ma et al. 2005 (η_Bob 4,5 %, Y_0 1,7×10⁻⁶, 0,21 dB/km) — **NÃO confirmado; ler no PDF** |

Interpretação de ρ: os valores de Tab. II já estão **integrados na largura de filtro de 10 GHz** (ρ ≈ β·Δλ).
Para outro filtro escalar linearmente (o artigo diz que o ruído cai linearmente com a largura do filtro).
Verificação de ordem de grandeza [DERIVADO]: com β≈1×10⁻⁹ (km·nm)⁻¹ e Δλ = 0,08 nm (10 GHz) obtém-se
~8×10⁻¹¹ km⁻¹, compatível com ρ_S·~10 canais. Também a SKR usa 1 ns de gate mas ρ foi medido com 2,5 ns;
como ρ multiplica η·Δt explicitamente em (1), ρ em si não depende do gate (a nota da Tab. II só registra a
condição).

### 5. Alvos de verificação
- **Tabela II** (inclinações) — principal alvo numérico.
- Linearidade: contagens ∝ potência total com inclinação unitária até 1,5 dBm total (Fig. 5) → sem FWM.
- FWM: razão de eficiência 2,2×10⁻⁵ (canais 37,38,39; D=16 ps/nm/km) — dá para reproduzir com (5)–(6).
- Qualitativo: mais canais → mais ruído; contra-propagação pior que co para L longo; melhor alocar clássicos
  perto do QCh e em λ maiores; filtro mais estreito reduz linearmente.
- Figs. 4, 6–9 (curvas de contagem vs L, SKR vs L, distância máxima vs potência) **não têm números no texto**
  — precisam ser digitalizadas do PDF.
- [DERIVADO, não publicado] contra-propagação, config G, condições do experimento (η=0,15, Δt=2,5 ns,
  P=−10,5 dBm/canal, hν=1,285×10⁻¹⁹ J, ᾱ assumido 0,046 km⁻¹, L→∞): Σρ = 6,8e-12·33 + 10,8e-12·66 =
  9,37×10⁻¹⁰ km⁻¹ → p ≈ 2,6×10⁻³ por gate. (Depende de η incluir ou não a perda de 8,4 dB.)

### 6. Implementação / lacunas
- Modelo barato: por QCh, Σ_k ρ(|k−j|)·P_k sobre canais clássicos ativos no mesmo enlace/núcleo; fator
  geométrico L·e^{−αL} (co) ou (1−e^{−2αL})/(2α) (contra) por span. O(N_clássicos) por QCh por enlace.
- Lacunas: ρ linear só vale para |Δν| ≲ 1,4 THz na banda C — **não serve para QCh em O/E com clássicos
  em C+L** (desvios de 15–30 THz, regime anti-Stokes longe do pico). Para multibanda é preciso a curva
  completa de seção de choque Raman (ex.: Eraerds 2010 Fig. 1, Kawahara 2011, Mlejnek / Lin-Agrawal) com
  fator térmico — nenhum dos três artigos fornece isso.
- Os valores de ρ_AS > ρ_S na vizinhança são empíricos (contraintuitivo; ver Tab. II) — manter como dado.

---

## a05 — Cai, Sun, Ji (2020)

### 1. Identificação
- "Intercore spontaneous Raman scattering impact on quantum key distribution in multicore fiber".
- Chun Cai, Yongmei Sun, Yuefeng Ji (BUPT). **New J. Phys. 22 (2020) 083020**,
  **DOI 10.1088/1367-2630/aba023**. Recebido 11/12/2019, publicado 10/08/2020. [TEXTO]

### 2. Cenário
- MCF fracamente acoplada, **7 núcleos**; cada núcleo só quântico OU só clássico. QCh no **núcleo 3
  (externo)**, **194,5 THz (ITU canal 45)** — frequência mais alta escolhida porque η_{m,n} anti-Stokes é
  menor. Demais núcleos: **16 canais clássicos unidirecionais por núcleo, 192,9–194,4 THz (canais 29–44),
  100 GHz**. Só os **3 vizinhos próximos** contam (h entre não-vizinhos ~1/64 dos vizinhos, ref. [37]).
- Clássico e quântico em λ diferentes na banda C → ICXT vira ruído fora de banda (filtrável); o ruído
  dominante é a **ICSRS** (Raman gerado por sinal de outro núcleo).
- Dois mecanismos por par (i,m)→(j,n): (①) ICXT para o núcleo j e depois SpRS em j; (②) SpRS em i e depois
  ICXT para j. Forward = co-propagação; backward = contra.
- ICSRS intra-core (SRS no mesmo núcleo) **não é modelado** no cenário (núcleo dedicado).

### 3. Equações
(α em escala linear [km⁻¹]; h_ij coeficiente de acoplamento de potência [km⁻¹]; η_{m,n} eficiência Raman
entre canal m e n, unidade **(km·nm)⁻¹**; P_0 potência de entrada do clássico.)

(1) P_FICSRS = P_ICXT–FSRS + P_FSRS–ICXT
(2) dP_ICXT–FSRS(z) = η_{m,n} P_ICXT(z) dz
(3) P_ICXT(z) = P_0 e^{−h z} sinh(h z) e^{−α_c z}
(4) dP^L = dP · e^{−α_q (L−z)}
(5) P_ICXT–FSRS = (η P_0 e^{−α_q L}/2) · { [e^{(α_q−α_c)L} − 1]/(α_q−α_c) − [e^{(α_q−α_c−2h)L} − 1]/(α_q−α_c−2h) }
(6) dP_FSRS–ICXT(z) = η P_CS(z) dz
(7) P_CS(z) = P_0 e^{−hz} cosh(hz) e^{−α_c z}
(8) dP^L = dP · XT(z) · e^{−α_q(L−z)}
(9) XT(z) = tanh(h z)
(10) P_FSRS–ICXT = idêntico a (5) (cosh·tanh = sinh).
**(11) Forward-ICSRS:**

    P_FICSRS = η P_0 e^{−α_q L} { [e^{(α_q−α_c)L} − 1]/(α_q−α_c) − [e^{(α_q−α_c−2h)L} − 1]/(α_q−α_c−2h) }

(12) P^{j,n}_FICSRS = Σ_{i=1..I} Σ_{m=1..M} P^{i,m;j,n}_FICSRS
(13)–(17) análogos backward; (14) dP = η P_ICXT dz; (15) × e^{−α_q z}; (16)=(17):
    (η P_0/2){ [e^{−(α_q+α_c+2h)L} − 1]/(α_q+α_c+2h) − [e^{−(α_q+α_c)L} − 1]/(α_q+α_c) }
**(18) Backward-ICSRS:**

    P_BICSRS = η P_0 { [e^{−(α_q+α_c+2h)L} − 1]/(α_q+α_c+2h) − [e^{−(α_q+α_c)L} − 1]/(α_q+α_c) }

(19) soma sobre i, m.
(Conferi analiticamente (5), (10), (16), (18): integrais corretas.)

(20) R = q { −Q_μ f_e H₂(E_μ) + Q_1 [1 − H₂(e_1)] }
(21) Y_0 = 2 p_dark + p_ICSRS
**(22)** p_ICSRS = P_ICSRS · Δf · Δt / (h f) (co: forward; contra: backward) — "low-power approximation",
ref. Eraerds [13].
  ⚠ Inconsistência dimensional: com η em (km·nm)⁻¹, P_ICSRS sai em W/nm; a multiplicação correta é por Δλ
  [nm] (100 GHz ≈ 0,8 nm a 1541 nm), não Δf [Hz]. Também **falta a eficiência do detector η_D** em (22)
  (não está claro se foi aplicada). Implementar como p = P'·Δλ·Δt·η_D/(h f) e testar as duas variantes
  contra a Fig. 7.
(23) Y_k = 1 − (1 − Y_0)(1 − t_l)^k
(24) e_k = [e_d (1 − (1 − t_l)^k) + 0,5 Y_0]/Y_k
(25) Q_μ = 1 − (1 − Y_0) e^{−t_l μ}
(26) E_μ = e_d + (½ − e_d) Y_0 / Q_μ
(27) P^{3,1}_ICSRS = Σ_{i=1..7, i≠3} Σ_{m=1..16} (forward ou backward)
(28) aproximação só vizinhos: Σ_{i ∈ 3 vizinhos} Σ_{m=1..16}
(29) single-core forward [TEXTO tem erro de sinal]; forma correta (Kawahara):
     P_FSRS = η P_0 (e^{−α_c L} − e^{−α_q L})/(α_q − α_c)
(30) single-core backward [TEXTO garbled]; forma correta: P_BSRS = η P_0 [1 − e^{−(α_c+α_q)L}]/(α_c+α_q)
(31) L_max (forward SRS) = ln(α_q/α_c)/(α_q − α_c)

Nenhuma dependência espectral/térmica de η é dada: usa-se η_{m,n} lido da **Fig. 1 de Eraerds 2010 [13]**
e α por canal da **Fig. 6 de Kawahara 2011 [32]**. FWM não é tratado.

### 4. Parâmetros [TEXTO]
| Parâmetro | Valor |
|---|---|
| h_ij (vizinhos) na análise de QKD | **10⁻⁶ km⁻¹** |
| h_ij da MCF de Dynes [25] | ~4 × 10⁻⁸ km⁻¹ |
| h_ij da MCF de Luís [34] | ~2 × 10⁻⁶ km⁻¹ (→ ICSRS ~50× maior) |
| Faixa de h p/ L'_max | 10⁻¹¹ a 10⁻⁶ km⁻¹ |
| η_{m,n} (Figs. 3–5) | **6 × 10⁻⁹ (km·nm)⁻¹** constante |
| α_c, α_q (Figs. 4–5) | 0,22 e 0,21 dB/km |
| α (Fig. 3) | 0,046 km⁻¹ (0,2 dB/km) a 0,07 km⁻¹ (~0,3 dB/km) |
| α banda C | 0,048–0,053 km⁻¹ |
| P_0 | 0 dBm (Figs. 3–5); 1 mW (Fig. 7, 9); varrido (Fig. 8) |
| L (Figs. 3–4) | 50 km |
| q | ½ |
| f_e | 1,15 |
| p_dark por SPD | **5 × 10⁻⁷** |
| η_D | **10 %** |
| Δt (gate) | **1 ns** |
| Δf (filtro) | **100 GHz** |
| h (Planck) | 6,63 × 10⁻³⁴ J s |
| μ, e_d | **não informados** (lacuna) |
| XT de projeto MCF | −30 a −60 dB; sinal quântico < −80 dBm |

### 5. Alvos de verificação
- **Fig. 5**: forward-ICSRS tem pico em **L_max ≈ 40 km**; backward satura. [DERIVADO com (11),(18),
  P_0=1 mW, η=6e-9, h=1e-6, α_c=0,22, α_q=0,21 dB/km: L_max = 40,1 km ✔; P_F(50 km)=1,24×10⁻¹⁵ W/nm,
  P_B(50 km)=1,17×10⁻¹⁵ W/nm, P_B(∞)≈1,22×10⁻¹⁵ W/nm.]
- **Fig. 10 / Eq. (31)**: SRS single-core forward com pico em **≈20 km** [DERIVADO: 20,2 km ✔ com os
  mesmos α]; texto diz L_max ≈ 25 km para "C-band" típico. Para ICSRS L'_max entre **30 e 45 km** para
  h ∈ [10⁻¹¹, 10⁻⁶] km⁻¹.
- **Fig. 4**: ICSRS ≈ linear em h_ij. [DERIVADO: ICSRS/SRS ≈ 3×10⁻⁵–5×10⁻⁵ em 50 km para h=10⁻⁶,
  i.e. ~ h·L.]
- **Fig. 7** (1 mW/canal, 16 canais × 3 núcleos vizinhos): SKR praticamente igual sem ICSRS até **70 km**;
  distância máxima reduzida em **10 km (forward)** e **63 km (backward)**.
- **Fig. 8** (L = 40 km): QBER/SKR constantes até **3 mW** por canal; queda forte em ~**30 mW**; SKR = 0
  em ~**40 mW**.
- **Fig. 9** (L = 40 km, 1 mW, T frequências em 6 núcleos clássicos = 6T sinais, f_m = 194,5 − 0,1m THz):
  com **T = 43**, SKR cai **10,6 % (forward)** e **8,4 % (backward)**.
  (Curioso: backward cai menos que forward a 40 km — consistente com P_F(40) > P_B(40) na Fig. 5.)

### 6. Implementação / lacunas
- Por QCh (núcleo j, canal n) e por enlace: soma sobre núcleos vizinhos i (lista de adjacência que o
  simulador já tem para XT) e canais clássicos ativos m: closed-form (11)/(18) — O(N_viz·N_ativos).
  Linear em P_0 e em η_{m,n}; ~linear em h. Pode-se pré-computar o "fator geométrico" G(L, α_c, α_q, h)
  por enlace (independe do canal se α constante) → custo O(N_ativos) com tabela η(Δν).
- Reusar o mesmo h_ij usado no XT do simulador (consistência: XT(L)=tanh(hL)).
- Por span amplificado: aplicar por span e propagar com perda do QCh (o artigo trata um único trecho).
- Lacunas: η_{m,n} vem de figura de outro artigo (Eraerds 2010) → precisa digitalizar; μ e e_d ausentes;
  inconsistência Δf vs Δλ e η_D em (22); não trata SpRS intra-core nem FWM; não trata multibanda.

---

## a02 — Ahmadian, Arpanaei, Hernandez-Hernandez, Lin, Monti (2025)

### 1. Identificação
- "Enhancing the reliability of multipath QKD over multi-band systems".
- Morteza Ahmadian, Farhad Arpanaei, Juan Carlos Hernandez-Hernandez, Rui Lin, Paolo Monti
  (Chalmers; UC3M). **J. Opt. Commun. Netw. 17(12), 1105–1116, dez/2025**; publicado 13/11/2025;
  **DOI 10.1364/JOCN.569098**. [TEXTO]

### 2. Cenário / contribuição
- Rede multibanda (MBON): clássicos em **C+L+S (20 THz = 6+6+8 THz)**, **268 canais de 75 GHz**
  (6×12,5 GHz), **guarda de 400 GHz** entre bandas; QKD na **banda O** (E-band como guarda).
  Fibra monocore (não é MCF).
- Camada física clássica: GSNR com GN/EGN closed-form (Poggiolini), ISRS, ASE (Eqs. 1–7).
- QKD BB84 decoy, trusted nodes, SKR por span (mínimo entre spans). Foco principal é **rede**:
  roteamento multipath disjunto, BPM (XOR) vs CM (concatenação), atraso de propagação, key pool,
  bloqueio B_CQ e "compromised secrecy" C. Topologia US long-haul 14 nós/22 enlaces.

### 3. Equações relevantes p/ ruído
(8) γ ≥ max{0, [Q_1(1 − H(e_1)) − η_ec Q_μ H(E_μ)]/T_s}
(9) Q_μ = 1 − (1 − p_b) e^{−μη}
(10) Q_1 = μ e^{−μ}(p_b + η); e_1 = (2η e_d + p_b)/[2(p_b + η)]
(11) E_μ = [p_b/2 + e_d(1 − e^{−μη})]/Q_μ
p_b = p_SpRS + p_NLI + p_dark-current (NLI = SRS estimulado + Kerr, via GN).
**A equação de p_SpRS NÃO é dada** — remete a Mehdizadeh et al., IEEE Commun. Lett. 28, 488–492 (2024)
("Quantum-classical coexistence in multi-band optical networks: a noise analysis of QKD") [ref. 6].
(12)–(20) são de rede (SKR multipath com atraso, buffer, bloqueio, segredo).

### 4. Parâmetros [TEXTO]
| Parâmetro | Valor |
|---|---|
| Largura do QCh | 12,5 GHz |
| Potência lançada do QCh | −80 dBm |
| Potência clássica | 0 dBm/canal |
| Dead time SPD | 10 ns |
| η_ec | 1,16 |
| e_d | 0,015 |
| η_D | 0,3 |
| Jitter | 100 ps |
| μ | 0,5 |
| p_dark | 10⁻⁸ |
| γ não-linear | 1,20 W⁻¹ km⁻¹ |
| Atenuação | 0,18–0,38 dB/km (dependente da frequência) |
| Taxa de repetição (T_s) | valor perdido na extração ("pulse repetition … time … 10 ns" — ambíguo) |
| Nº QCh banda O | 1400 possíveis; 1000 usados em 223–238 THz (750 no cenário alternativo) |
| Atraso | 5 µs/km; chave 256 bits; 10 % das requisições pedem QKD; 51 kbit/s por requisição |
| Comprometimento | TN 10 %, Qponder 1 % |

### 5. Alvos de verificação
- SKR máx. no O-band longe dos clássicos: **800, 100, 25 kbit/s** para spans de **50, 80, 100 km**;
  nas frequências mais altas < 25 e < 1 kbit/s (atenuação). SKR cai a partir de **226 THz** (proximidade
  com clássicos/NLI).
- Spans 70→100 km (2500 km totais): SKR cai por fator 4 a 35.
- Rede: 750 QCh → 1º bloqueio em 311 Tb/s clássico / ~5 Mb/s QKD; 1000 QCh → bloqueio −55 %, 482 Tb/s /
  ~7 Mb/s. Multipath sem buffer: SKR cai >3× (2400 km) e >6× (7000 km). BPM armazena 3× mais bits que CM
  a 7000 km. CM até 20 % de segredo comprometido vs BPM < 1 %.

### 6. Relevância para nós
- **Baixa para a física do SpRS** (não dá equação, nem ρ(λ), nem η_Raman): serve apenas como
  cenário-referência multibanda (O-band QKD + C+L+S clássico), conjunto de parâmetros de detector e alvos
  grosseiros de SKR vs span. Para o modelo SpRS multibanda, buscar a ref. [6] (Mehdizadeh 2024).
- **Média para a camada de rede** se quisermos KPIs de bloqueio QKD/clássico e multipath — fora do escopo
  de ruído.

---

## Síntese para o simulador
1. Ruído por QCh: p_b = 2p_dark + p_XT + p_SpRS,intra + p_SpRS,inter (+ p_FWM se aplicável).
2. Intra-core (mesma fibra/núcleo): P' = η(Δν)·P·{L e^{−αL} (co, α iguais) | (1−e^{−2αL})/(2α) (contra)}
   ou forma geral com α_c ≠ α_q (Kawahara: (e^{−α_cL} − e^{−α_qL})/(α_q−α_c); [1−e^{−(α_c+α_q)L}]/(α_c+α_q)).
   p = P'·Δλ·η_D·Δt/(hν).
3. Inter-core: (11)/(18) de a05 com h_ij do simulador.
4. Necessário: tabela η_Raman(Δν) de banda larga (desvios até ~30 THz para O/E vs C+L) com dependência
   térmica — **não está em nenhum dos três artigos**.
5. FWM: critério γPL < 0,1 e eficiência de Shibata (a01) — relevante só perto do zero de dispersão
   (QCh na banda O perto de 1310 nm com clássicos? os mixings caem em C+L, não em O, mas verificar).

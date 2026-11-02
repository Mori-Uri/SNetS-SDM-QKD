# G4 — MB-EON: modelos de rede, QoT (GSNR/ISRS) e algoritmos RMLSA/RBMLSA

Fontes (extrações pdftotext -layout; equações reconstruídas — ver marcas **[INCERTO]**):
- `a07.txt` — Mehrabi, Beyranvand, Emadi, JLT 2021
- `a03.txt` — Arpanaei et al., IEEE JSAC 2025

Convenção: tudo que está entre aspas ou com valor numérico foi lido no texto. Onde o valor só existe em figura/tabela (não extraída), está marcado **[SÓ EM FIGURA]**. Nada foi inventado.

---

## Artigo A07 — Mehrabi, Beyranvand, Emadi (JLT 2021)

### 1. Identificação
- **Título:** "Multi-Band Elastic Optical Networks: Inter-Channel Stimulated Raman Scattering-Aware Routing, Modulation Level and Spectrum Assignment"
- **Autores:** Mahdieh Mehrabi, Hamzeh Beyranvand, Mohammad Javad Emadi (Amirkabir University of Technology, Teerã)
- **Revista:** Journal of Lightwave Technology, vol. 39, nº 11, pp. 3360–3370, 1º jun. 2021
- **DOI:** 10.1109/JLT.2021.3065297
- Natureza: **estática** (conjunto D de demandas conhecido; ILP + heurísticas sequenciais). Não há chegada Poisson nem carga em Erlang.

### 2. Modelo de rede
- **Bandas:** C+L, "de 1530 nm a 1625 nm". Grade flexível, FS de **Δ = 12,5 GHz** → **N = 916 FS por fibra** (C+L juntos, numeração única; não há divisão explícita de slots por banda). Conferência: c/1530 nm ≈ 195,94 THz, c/1625 nm ≈ 184,49 THz → 11,45 THz / 12,5 GHz ≈ 916. ✔
- **Indexação:** FSs indexados a partir do menor comprimento de onda (maior frequência), "since we want the C-band spectrum to be allocated first". Logo, **first-fit sobre o índice global ≡ política "C primeiro, depois L"**. f_end = 196,04 THz é a frequência mais alta.
- **Guarda:** g = 1 FS após o último FS de cada demanda.
- **Enlaces:** bidirecionais, uma fibra por sentido; enlace l com N_l spans, comprimento máximo L_max = 80 km.
- **Amplificação:** no fim de cada span, C e L são demultiplexadas e amplificadas por **EDFA por banda**, compensando totalmente a perda do span; **filtro de equalização ajustável (TEF)** após cada EDFA compensa o tilt do ISRS (recupera o espectro transmitido). Sem regeneradores (transparente).
- **Potência de lançamento:** **fixa e igual para todos os canais/demandas, P** (PSD retangular). Varrida como parâmetro (gráficos vs P; texto cita −4, −3, 0, 3, 4 dBm). Não há otimização de potência por banda.
- **Modulações:** BPSK, 4-, 8-, 16-, 32-QAM → m = 1..5. Taxa por FS em BPSK R_s = 10 Gb/s, logo capacidade por FS = m·10 Gb/s.
- **Nº de FS:** N_m^d = ⌈Λ_d / (m·R_s)⌉ (o teto está implícito; o texto mostra a fração Λ_d/(m R_s) — **[INCERTO: ceil]**).
- **Limiares de SNR (BER = 10⁻³):** m=1: **9 dB**; m=2: **12 dB**; m=3: **16 dB**; m=4: **18,6 dB**; m=5: **21,6 dB**.
- Seleção da modulação: a mais alta cujo limiar é atendido pelo SNR estimado.

### 3. Modelo físico
**(1) SNR por demanda:**
SNR_d = P / (P_ASE^d + P_NLI^d)

**(2) ASE** (perda totalmente compensada por EDFA):
P_ASE^d = Σ_{l∈p_d} Σ_{s=1}^{N_l} 2·n_sp·h·f_d·B_d·(e^{α·L_s^l} − 1)
(α em unidades lineares neper/km ao usar a exponencial; B_d = N_m^d·Δ; o fator 2 = duas polarizações.)

**NLI:** "ISRS GN-model" em forma fechada de Semrau et al. [ref 7: JLT 37(9), 2019], com espectro de ganho Raman aproximado linearmente até 15 THz (inclinação C_r). SCI e XCI calculados por enlace e **acumulados incoerentemente** entre enlaces (os spans de um enlace multiplicam por N_l).

**(3) SCI da demanda d no enlace l** (reconstruída; a forma confere com a versão ILP da Seção III):
P_SCI^{d,l} = N_l · (8/81) · γ²P³ / (π α² φ_d B_d²) ·
  [ ((2α − D_l P C_r f_d)² − α²)/α · asinh( (3π/(2α)) φ_d B_d² )
  + (4α² − (2α − D_l P C_r f_d)²)/(2α) · asinh( (3π/(4α)) φ_d B_d² ) ]
com φ_d = β₂ + 2πβ₃ f_d.

**(4) XCI da demanda d no enlace l**, soma sobre d' ≠ d que usam l:
P_XCI^{d,l} = N_l · (16/81) · γ²P³/(π² α²) · Σ_{d'} 1/(φ_{d,d'} B_{d'}) ·
  [ ((2α − D_l P C_r f_{d'})² − α²)/α · atan( (2π/α) φ_{d,d'} B_d )
  + (4α² − (2α − D_l P C_r f_{d'})²)/(2α) · atan( (π/α) φ_{d,d'} B_d ) ]
com φ_{d,d'} = (β₂ + πβ₃(f_d + f_{d'}))·(f_{d'} − f_d).

**(5)** P_NLI^d = Σ_{l∈p_d} (P_SCI^{d,l} + P_XCI^{d,l}).

- D_l = nº de demandas no enlace l; **potência total no enlace = D_l·P** (entra no termo ISRS).
- **[INCERTO]** (a) constantes 8/81, 16/81 e argumentos 3π/(2α), 3π/(4α), 2π/α, π/α lidos do texto garbled — conferir com Semrau 2019 (lá φ_i = (3/2)π²(β₂+2πβ₃f_i) e a XCI tem um fator de "modulation-format"/coerência diferente); (b) **f_d no termo ISRS deve ser frequência relativa ao centro do espectro WDM** (como em Semrau), não absoluta (~190 THz) — o artigo não explicita; usar absoluto gera resultado absurdo; (c) os γ²P³ indicam potência por canal igual para todos; (d) não há penalidade de transceptor nem de ROADM.
- **Parâmetros (exatos):** f_end = 196,04 THz; n_sp = 1,5; α = 0,2 dB/km; β₂ = −21,6 ps²/km; β₃ = 0,14 ps³/km; γ = 1,2 1/(W·km); C_r = 0,028 1/(W·km·THz); g = 1; L_max = 80 km. n_sp igual em C e L (simplificação).
- **Ativação do ISRS no ILP:** c_l = 0 se banda ocupada no enlace < 5 THz; c_l = 1 se > 5 THz (até 15 THz, limite da aproximação linear). Nas heurísticas, **c_l = 1 em todos os enlaces**.
- Centro de frequência da demanda (ILP): f_{d,m,k} = f_end − (k − 1 + (N_m^d + g)/2)·Δ **[INCERTO: posição exata do g no numerador]**.

### 4. Algoritmos
**ILP path-based** (objetivo: min S_max = maior índice de FS ocupado; restrições (7)–(23): caminho único, não-sobreposição com g, ativação de c_l, contagem D_l, NLI por tabelas pré-calculadas, modulação SNR-aware). NP-difícil; variáveis/restrições O(|D|·M·N·(|D|−1)·M·N·|L|·|D|·2). Resolvido só para |D| = 10, N = 15, CPLEX/MATLAB.

**Heurísticas (comuns):** k_p caminhos pré-calculados por Yen; demandas numa fila servidas sequencialmente; first-fit no espectro; para cada candidato calcula-se o maior índice de FS que a demanda usaria e **escolhe-se o caminho com menor índice máximo** (MinMax index ≈ "pack to C"). Estimativa do SNR **antecipa demandas futuras**: FSs vazios preenchidos com canais interferentes fictícios; D_l = N_served^l + N_unserved^l + 1 (N_unserved^l = demandas da fila cujos caminhos candidatos usam l); banda dos interferentes distribuída uniformemente entre as larguras possíveis com modulação M_max; interferentes mais estreitos colocados mais perto da demanda (pior caso).
- **PE (Pre-Estimation):** SNR pior caso de cada caminho estimado antes de tudo, supondo a demanda em BPSK no **meio da banda C+L**, demais FSs cheios com interferentes de M_max = M, D_l = N_d^l. Passos: (1) SNR pior caso; (2) maior m com limiar < SNR; (3) first-fit.
- **IPE (In-Process Estimation) — Algoritmo 1:** m ← M; laço: first-fit com m; se há FSs → índice k, estima SNR com recursos escolhidos (M_max = M); se SNR ≥ SNR_m retorna (m,k); senão m ← m−1 (se m=1 falhou, caminho inviável). Se não há FSs, caminho inviável.
- **EIPE (Enhanced IPE):** igual ao IPE, mas M_max dos interferentes depende da categoria do SNR médio: linear → M; não linear (perto do pico) → M−1; altamente não linear → M−2. **[LACUNA: limites numéricos das categorias não são dados.]**
- **Não há verificação de QoT dos circuitos já estabelecidos** (a antecipação por pior caso substitui isso). Comparam com o "updating approach" de Mitra et al. [13,14] (considera só NLI de lightpaths existentes e reconfigura os degradados — não serve para dinâmico).
- **Tempo de execução:** updating approach **45 s** vs heurísticas propostas **20 s** (cenário |D| = 500, sem descrição de máquina).
- "Outage": SNR real (recalculado no estado final) < limiar da modulação atribuída.

### 5. Cenário e resultados
- **Topologia:** Deutsche Telekom (DT), **14 nós, 23 enlaces bidirecionais** [ref 34: Azodolmolky 2011]; L_max = 80 km. **Comprimentos dos enlaces não estão no texto.**
- **Tráfego:** origens/destinos/taxas uniformes aleatórias.
  - Pequena escala (ILP): |D| = 10, N = 15, taxas 70–120 Gb/s passo 10, k_p = 2.
  - Larga escala: |D| = 500, N = 916, taxas 100–500 Gb/s passo 50, k_p = 2, 4, 6. Teste ISRS: |D| = 600 e 1000, k_p = 4 (o texto diz "três valores" mas lista dois).
  - Cada heurística rodada com **20 ordens aleatórias** de demandas; média reportada.
- **Métricas:** S_max, nível médio de modulação, SNR real médio, % de demandas na banda L, % de bloqueio (SNR < limiar BPSK ou falta de FS), precisão da estimativa (1 − erro relativo), nº de demandas bem-sucedidas/em outage.
- **Resultados citáveis (texto):**
  - Pequena escala: ILP, IPE e EIPE atribuem 32-QAM a todas as demandas; IPE≈EIPE≈ILP; PE pior (gap diminui com P).
  - |D| ≤ 150: IPE ≈ EIPE; |D| > 150: EIPE melhor.
  - EIPE: mínimo S_max e máxima modulação média em **P ≈ 0 dBm**; "on average **27 and 15 demands** are served in C- and L-band", potências totais **14,3 dBm (C)** e **11,7 dBm (L)** (coerente com 10·log10(27) e 10·log10(15) com P = 0 dBm por canal → interpretável como por enlace). Ótimo para k_p = 6 com EIPE ≈ 0 dBm.
  - Aumentar k_p reduz S_max mas reduz SNR/modulação média; mais demandas vão para L.
  - P > 3 dBm: PE serve menos demandas em L que IPE.
  - k_p = 4: PE usa majoritariamente 4-QAM; IPE/EIPE usam 32-QAM em P baixa e 8/4-QAM em P alta; BPSK raro no EIPE.
  - |D| = 1000, P = 0 dBm, **PE**: 895 (com ISRS) vs 900 (sem ISRS) demandas bem-sucedidas.
  - |D| = 1000, P = 4 dBm, **EIPE**: **840** servidas com ISRS; sem ISRS, todas as 1000 servidas mas só **246** sem outage (> 75% em outage).
  - P = −4 e −3 dBm: sem diferença com/sem ISRS, sem outage.
  - Valores de S_max, SNR, % bloqueio por P: **[SÓ EM FIGURA]** (Figs. 1–5).

### 6. Reprodução no SNetS e lacunas
- Necessário: grade 916 FS indexada de alta→baixa frequência; guarda 1 FS; modulações BPSK..32QAM com 10 Gb/s·m por FS; Yen k-SP; EDFA por banda + TEF (ASE com perda totalmente compensada); NLI ISRS-GN fechada (Semrau 2019) com D_l·P; flag ISRS on/off; recalculo final do SNR de todos (para "outage").
- Modo estático (fila de |D| demandas, 20 permutações) — o simulador é dinâmico: implementar um "modo batch" (todas as demandas com holding infinito, sem partidas) é o caminho mais direto de verificação.
- **Lacunas:** comprimentos/spans do DT; limites das categorias do EIPE; constantes das eqs. (3)–(4) e referência de frequência do termo ISRS; arredondamento de N_m^d; forma exata do "preenchimento" de interferentes (distribuição de larguras); seeds. Resultados quantitativos principais estão em figuras.
- Verificação recomendada: (i) checagem de unidade: 27/15 demandas ↔ 14,3/11,7 dBm; (ii) tendência qualitativa P ótimo ≈ 0 dBm; (iii) experimento com/sem ISRS em |D| = 1000, P = 4 dBm (840 vs 246) — alvo mais "duro" do texto.

---

## Artigo A03 — Arpanaei et al. (IEEE JSAC 2025)

### 1. Identificação (confirmada no texto)
- **Título:** "Synergizing Hyper-Accelerated Power Optimization and Wavelength-Dependent QoT-Aware Cross-Layer Design in Next-Generation Multi-Band EONs"
- **Autores:** Farhad Arpanaei, Mahdi Ranjbar Zefreh, Yanchao Jiang, Pierluigi Poggiolini, Kimia Ghodsifar, Hamzeh Beyranvand, Carlos Natalino, Paolo Monti, Antonio Napoli, José M. Rivas-Moscoso, Óscar González de Dios, Juan Pedro Fernández-Palacios, Octavia A. Dobre, José Alberto Hernández, David Larrabeiti (UC3M, Cisco, PoliTo, AUT/Waterloo, Chalmers, Infinera, Telefónica, Memorial Univ.)
- **Revista:** IEEE Journal on Selected Areas in Communications, vol. 43, nº 5, maio 2025, pp. 1840–(≈1853+)
- **DOI:** 10.1109/JSAC.2025.3543528. Versão preliminar (só CL): Arpanaei et al., OFC 2024 [ref 10].
- Natureza: planejamento **semiestático** (matriz desconhecida, holding infinito) — não é Poisson dinâmico.

### 2. Modelo de rede
- **Bandas:** cenários C, LC (C+L) e LCS (L+C+S). LCS: **20 THz total = 6 (L) + 6 (C) + 8 (S) THz**, **gap de 400 GHz entre bandas**.
- **Grade:** **fixa**, LCIs a **64 GBd**, roll-off **0,05**, espaçamento **75 GHz (6 × 12,5 GHz)**. B_ch = ⌈R_sym(1+ρ)/B_base⌉·B_base (= ⌈67,2/12,5⌉·12,5 = 75 GHz ✔). Nº de canais por banda não declarado (6 THz/75 GHz = 80; 8 THz/75 GHz ≈ 106) **[INFERIDO, não declarado]**.
- **Taxa:** R_ch,(i,m) = m·R_sym·(1+ρ)·(1−θ) — LCIs de **100 a 600 Gb/s**, m = 1..6 (100 Gb/s·m); FEC SD com 25% de overhead; pre-FEC BER 1,5·10⁻². **[INCERTO]** a fórmula com (1+ρ) e sem fator de polarização não fecha numericamente 100 Gb/s·m — usar a tabela m→100m Gb/s.
- **Limiares de GSNR (m = 1..6):** **3,45; 6,5; 8,4; 12,4; 16,5; 19,3 dB** (de [12], Arpanaei JOCN 2024).
- **Amplificadores (agrupados):** EDFA C: NF 4,5 dB; EDFA L: NF 5 dB; TDFA S: NF 6 dB. Booster nos ROADMs com ganho **20 dB**; pré-amplificadores e ILAs compensam perda do span + ISRS (G = P/P(z=L)). ILAs e ROADMs com **DGE** (WSS + VOA) em todo span.
- **Penalidades:** perdas de conector aleatórias 0,2–0,5 dB; PDL de ROADM 0,5 dB por nó; perda de emenda aleatória 0,01–0,06 "dB/km" com seções de emenda de 2 km; Π_TRx, Π_Penfil, Π_Penage na eq. (3) — **valores numéricos de Π_TRx, filtragem e envelhecimento não aparecem no texto [LACUNA]**.
- Fibra SSMF "zero water peak"; perda espectral em figura (Fig. 2).
- Canais ociosos preenchidos com **ruído ASE** (ASE noise loading) → espectro sempre cheio (estado fully loaded).
- **Potência:** otimizada por span (HPO), modos **FLP** (potência de lançamento plana) e **FRP** (potência recebida plana, i.e. pré-tilt). Limite **P_max = 6 dBm** por canal. Potências ótimas: FLP em [−0,8; 0,9] dBm; FRP em [−6; 6] dBm (span-level, 50–100 km).

### 3. Modelo físico
**(1)** Γ_{l,s,i} ≈ P / (P_ASE^{l,s,i} + P_NLI^{l,s,i}) (P definido por (2) como a potência lançada no span seguinte / enlace seguinte).

**(3) GSNR fim a fim do LP no canal i (incoerente):**
Γ_LP,i[dB] = 10·log10( [ Σ_{l=1}^{N_L} Σ_{s=1}^{N_s^l} 1/Γ_{l,s,i} + Π_TRx⁻¹ ]⁻¹ ) − Π_Penfil[dB] − Π_Penage[dB]

**(4) ASE:** P_ASE^{l,s,i} = n_F·h·f_i·(G^{l,s,i} − 1)·R_sym,i (n_F = NF linear; nota: usa R_sym como banda de ruído).
**(5)** G^{l,s,i} = P / P^{l,s,i}(z = L_s^{l,s}).

**(7) Perda efetiva com ISRS:** α(z,f_i) = α₀(f_i) + α₁(f_i)·exp(−σ(f_i)·z), ajustada à PEP numérica (Zefreh & Poggiolini [23], eqs. 30.1–30.2) — passo semi-fechado.

**(10) PEP (equações de Raman acopladas):**
∂P_i(z)/∂z = κ·P_i(z)·[ Σ_{j∈C} ζ(f_i/f_j)·C_r(f_j, f_j − f_i)·P(f_j, z) − α(f_i) ], κ = +1 (FLP, propagação direta), κ = −1 (FRP, resolve "para trás" a partir de recepção plana). ζ(x) = x se x>1, 0 se x=0, 1 se x<1 (fator de fótons). **[INCERTO: ordem f_i/f_j e sinais conforme a extração]**. C_r medido em SSMF e deslocado em frequência (Fig. 4, só em figura).

**(6) NLI — CFM/EGN "UWB" de Poggiolini/Zefreh com correção ML ρ_j** (reconstrução):
P_NLI^{l,s,i} = (16/27)·P_tx,i · Σ_{j=1}^{N_ch} Σ_{p=0}^{1} Σ_{k=0}^{M} Σ_{q=0}^{M}
  ρ_j·γ_{i,j}²·P_tx,j²·(2 − δ_{ij})·(−1)^p·e^{−4α₁(f_j)/σ(f_j)} / [2π·R_{s,j}²·k!·q!·(4α₀(f_j) + (k+q)σ(f_j))·β̄₂(f_j)] · (2α₁(f_j)/σ(f_j))^{k+q} · ψ_{i,j,p,k}

(8) ψ_{i,j,p,k} = asinh( π²·β̄₂(f_j)·R_{s,i}·(f_j − f_i + (−1)^p R_{s,j}/2) / (2α₀(f_j) + kσ(f_j)) ) (o texto diz "kα(f_j)", provável erro de digitação → kσ **[INCERTO]**).

γ_{i,j} = (2π f_i / c)·2n₂/(A_eff(f_i) + A_eff(f_j));
β̄₂ = β₂ + πβ₃(f_i + f_j − 2f₀) + (2π²/3)·β₄·[(f_i−f₀)² + (f_i−f₀)(f_j−f₀) + (f_j−f₀)²] **[INCERTO: fator 2π²/3]**;
M = max⌊10·|2α₁(f_i)/σ(f_i)|⌋ + 1.
Parâmetros: f₀ ↔ 1550 nm; β₂ = −21,86·10⁻²⁷ s²/m; β₃ = 0,1331·10⁻³⁹ s³/m; β₄ = −2,7·10⁻⁵⁵ s⁴/m; n₂ = 2,6·10⁻²⁰ (m²/W); A_eff(f) e C_r(f) só em figura. MCI desprezada. Valores de ρ_j (correção ML) não fornecidos — ver [19].

**Capacidade total do span (objetivo da HPO), (9):**
max TC^{l,s} = 2·Σ_{i∈C} R_s^{l,s,i}·log2(1 + Γ^{l,s,i}) s.a. P^{l,s,i} ≤ P_max.

### 4. Algoritmos
**HPO (Algoritmo 1)** — mesmo laço para FLP e FRP:
1. Max_TC ← 0.
2. Para P_i de P_start a P_end em passos de **0,1 dB**: calcula TC(P_i) por (9) (resolvendo (10) forward p/ FLP ou backward p/ FRP); se max(PEP) > P_max → para; se TC(P_i) > Max_TC → guarda (TC, PEP, Γ); senão → para (primeiro máximo local).
3. FLP: P_start = P_LOGO (eq. 83 de Poggiolini 2014, ótimo LOGON Nyquist-WDM C-band), P_end = P_LOGO + 3 dB. FRP: P_start = P_FLP,opt − α_max·L_span, P_end = 0 dBm.
- Complexidade: O( (P_end − P_start)/0,1 × |C|² ). Tempo: **≈ 3 s** por span (50–100 km), MATLAB R2023a, Intel i7-12700K, 32 GB.
- **LOGON** (potência ótima por span) vs **GON** (mesma potência em todos os spans da rede; testados 0 e −1 dBm em USB6012 FLP).

**Provisionamento (RMBSA semiestático, grade fixa, Flexponder):**
- k = 3 caminhos mais curtos por requisição; fluxo de uma requisição inteiro num único caminho (sem split).
- Grooming Flexponder: tráfego de mesma origem-destino agrupado em LCIs já instaladas com capacidade ociosa; "minimum LCI deployment" (só instala nova LCI se não houver capacidade nas existentes). Cada LCI tem taxa dependente do GSNR do canal (CCR = channel-connection resource pré-calculado por caminho×canal).
- **Seleção de caminho:** **MaxMinGF** (maximiza o GSNR mínimo das LCIs; [9],[24]) e **MinMaxF** (minimiza a frequência máxima das LCIs, [54]). MinMaxF foi melhor (menos fragmentação).
- **Seleção de modulação:** **CBG** (channel-based GSNR: modulação por canal conforme CCR), **WAB** (worst-case em todas as bandas: distância-adaptativo, menor GSNR entre todas as bandas — como Sambo [11], Mehrabi [20], Calderón [26]), **WPB** (worst-case por banda).
- Ordem de bandas: não explicitada; com MinMaxF minimiza-se frequência máxima → favorece L→C→S (L em frequência mais baixa) **[INFERIDO]**.
- QoT de LPs existentes: com grade fixa, ASE noise loading e potência por span pré-otimizada, o GSNR de cada canal é independente da ocupação → **não há re-verificação** (QoT pré-calculado por CCR).

### 5. Cenário e resultados
- **Span-level:** C, LC, LCS; spans 50–100 km; fully loaded.
  - LCS, 70 km: FRP ganha **≈ 6,3 Tbps** de TC sobre FLP; S-band: GSNR +**2,5 dB**, OSNR +**6 dB** (máx.). C e LC: ganho desprezível.
  - Tabela II (TC vs span): ganho FRP–FLP de **0,4 Tbps (50 km)** a **≈ 10 Tbps (100 km)** — valores absolutos da tabela **[SÓ EM TABELA/não extraída]**. Até 6 dB máx. de melhoria GSNR/OSNR em 100 km; média ≈ **0,7 dB** em 100 km.
- **Network-wide:**
  - **SPNB3014:** Espanha, 30 nós, 56 enlaces, 14 nós core; caminhos < 1000 km; span máx. 60 km. FLP: potência entre −1,5 e −0,5 dBm.
  - **JPNB4812:** Japão, 48 nós, 82 enlaces, 12 core; caminhos < 2350 km; span máx. 80 km. FLP: −1,7 a 0,1 dBm.
  - **USB6012:** EUA, 60 nós, 79 enlaces, 12 core; caminhos < 6150 km; span máx. 100 km. FLP: −1,7 a 0,7 dBm.
  - Pares origem-destino com probabilidade enviesada por população e grau ([9]); N(N−1)/2 conexões; taxas aleatórias 100–600 Gb/s; **1000 iterações aleatórias**; métrica **BBP** (soma das taxas bloqueadas / soma das taxas oferecidas) vs **carga oferecida (OTL)**, avaliada a **BBP = 1%**; também nº de LCIs e GSNR médio.
  - FRP vs FLP: ganho de throughput ≈ **5%** a BBP 1% em todas as redes (abstract: ganho do FRP+CBG de 12% a 75% conforme a escala).
  - **CBG vs WAB/WPB: até 75%** de throughput a BBP 1%.
  - USB6012: FRP degrada L-band ≈ 3 dB de GSNR médio e 1,3 de cardinalidade; JPNB4812: 1 dB e 1; SPNB3014 sem mudança de modulação.
  - GON a 0 e −1 dBm ≈ LOGON FLP (USB6012).
  - Curvas BBP×OTL: **[SÓ EM FIGURA]** (Fig. 13); unidades de OTL não aparecem no texto.

### 6. Reprodução no SNetS e lacunas
- Necessário: modelo de grade fixa 75 GHz por banda com gaps; tabela de GSNR por (caminho, canal) pré-calculada (CCR) → modulação por canal (CBG) ou pior caso (WAB/WPB); grooming com LCIs de 100–600 Gb/s; BBP por taxa; k = 3 caminhos; MaxMinGF/MinMaxF.
- O cálculo físico completo (Raman acoplado + CFM com correção ML + A_eff(f), C_r(f), α(f)) **não é reproduzível só com o texto**: faltam curvas de perda/A_eff/C_r (figuras), ρ_j, Π_TRx, penalidades, comprimentos de enlaces/spans das topologias (refs [51]–[53]). Alternativa: importar GSNR por canal de GNPy ou implementar ISRS-GN fechado (Semrau) como aproximação — mas isso não reproduz os números do artigo.
- Semiestático: implementável no SNetS como chegadas sem partida (holding infinito) com carga crescente até BBP = 1%.
- Alvos de verificação viáveis: qualitativos (CBG >> WAB; MinMaxF > MaxMinGF; FRP ≈ FLP +5% na rede); HPO: potências ótimas FLP na faixa [−0,8; 0,9] dBm e tempo de ~3 s; limiares de GSNR e granularidade 75 GHz/100–600G como parâmetros de entrada.

# G1 — QKD multibanda (MB) e multifibra (MF): notas para extensão do SNetS-SDM-QKD

Fontes (texto extraído via pdftotext -layout; equações reconstruídas, incertezas marcadas com **[INCERTO]**):
- **a09** — Dibaj, Mehdizadeh, Beyranvand, Arpanaei, "Traffic-Aware Trusted Node Placement and Resource Allocation in Multi-Band EONs Secured With QKD", JLT vol. 43, n. 1, pp. 6–18, jan. 2025 (DOI 10.1109/JLT.2024.3445732).
- **a10** — Mehdizadeh, Dibaj, Beyranvand, Arpanaei, "Quantum-Classical Coexistence in Multi-Band Optical Networks: A Noise Analysis of QKD", IEEE Commun. Lett. vol. 28, n. 3, pp. 488–492, mar. 2024.
- **a11** — Dibaj et al., "Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in Multi-Band and Multi-Fiber Realms", IWCIT 2024 (Invited).

Os três artigos são do mesmo grupo (AUT Teerã + UC3M) e compartilham o **mesmo modelo físico** (SpRS + NLI via GNPy/GGN + dark count; BB84 decoy de Moghaddam 2021 [JSAC] / Bahrani 2018) e **o mesmo conjunto de parâmetros** (com exceção de μ — ver conflitos). Legenda: "[TEXTO]" = valor escrito no texto; "[FIG]" = só legível na figura (não citável como número); "[CÁLC]" = cálculo meu de verificação, não do artigo.

---

## 0. Modelo físico comum (consolidado dos três artigos)

### 0.1 Equações (notação legível)

**(SpRS para frente, soma sobre CChs)** — a09 eq. (2), a10 eq. (1):

    P_SpRS^q = l · Δλ_q · Σ_{c=1}^{N_c} e^{−α(f_c)·l} · p_c · ρ(λ_c, λ_q)

- l = comprimento da fibra; Δλ_q = largura de banda do sistema de filtragem do QCh (em nm se ρ estiver em (km·nm)^−1 — unidade de ρ **não** dada no texto **[INCERTO]**); α(f_c) = atenuação na frequência do **canal clássico** c (note: a atenuação usada é a do CCh, não a do QCh — forma simplificada de Peters 2009 válida quando α_c ≈ α_q; a forma exata seria p_c·ρ·(e^{−α_c l}−e^{−α_q l})/(α_q−α_c); os artigos usam a forma l·e^{−αl}); p_c = potência de lançamento do CCh; N_c = nº de CChs.
- **Atenção:** α aqui é em 1/km (linear, neperiano) dentro da exponencial; o texto dá α em dB/km → converter α_lin = α_dB·ln(10)/10.

**(Escalonamento da seção de choque Raman)** — a10 eq. (2):

    ρ(λ_c, λ_q) = (λ_δ / λ_q)^4 · ρ(1550 nm, λ_δ),   com |λ_c − λ_q| = 1550 nm − λ_δ

- Interpretação: λ_δ é o comprimento de onda "equivalente" que tem, em relação a uma bomba em 1550 nm, o mesmo afastamento |λ_c−λ_q|; usa-se a curva de referência ρ(1550 nm, ·) medida (Subacius 2005 / Eraerds 2010 / Niu 2018) e escala-se por (λ_δ/λ_q)^4.
- **[INCERTO]**: (i) o texto só escreve λ_δ = 1550 − |λ_c−λ_q| (deslocamento sempre para comprimentos menores = lado anti-Stokes?). Para casos Stokes (λ_c < λ_q) seria λ_δ = 1550 + |Δλ|. Como no cenário MB o QCh fica **abaixo** do CCh (λ_q < λ_c → anti-Stokes), a fórmula literal é a relevante. (ii) Escalar por diferença em **nm** e não em **frequência (THz)** é uma aproximação; para O/E vs C/L (Δλ ≈ 150–350 nm) o deslocamento Raman em THz é que importa fisicamente (pico Raman ~13 THz). Para separações > ~40 THz o ρ é muito pequeno; a curva ρ(1550, λ) precisa cobrir até ~1200 nm. **A tabela ρ(1550 nm, λ) não está em nenhum dos três textos — precisa vir de Eraerds 2010 / Niu 2018 / dados GNPy**.
- Os artigos afirmam: ρ anti-Stokes (λ_c > λ_q) < ρ Stokes (λ_c < λ_q) ⇒ colocar QCh **abaixo** (λ menor) dos CChs; separação mínima de **200 GHz** entre CCh/PICh e QCh (pico do ρ perto do sinal clássico).

**(NLI — GGN)** — a10 eq. (3) (a09 cita mas não escreve):

    G_NLI(l, f) = (16/27) γ² ∫∫_{−∞}^{+∞} G_TX(f1) G_TX(f2) G_TX(f1+f2−f)
                  · | ∫_0^l [ Ψ(ζ,f1) Ψ(ζ,f1+f2−f) Ψ(ζ,f2) / Ψ(ζ,f) ]
                      · exp( j 4π² (f1−f)(f2−f) [β2 + π β3 (f1+f2)] ζ ) dζ |² df1 df2

- Ψ(z,f) = perfil de perda dependente de frequência e posição (inclui SRS inter-canal), obtido resolvendo as EDOs de Raman (Ferrari 2020). Implementação de referência: **GNPy**. P_NLI no QCh = ∫ G_NLI sobre a banda do QCh (12,5 GHz).
- a10 Fig. 3: NLI(GNPy) é **22,45 dB** (CCh 200 GHz) e **24,33 dB** (CCh 75 GHz) maior que o modelo FWM aproximado [TEXTO]; mudar BW do CCh de 75→200 GHz reduz NLI em **4,90 %** e FWM em **3,04 %** [TEXTO].
- Observação prática: para QCh em O/E com CCh em C+L, o NLI que cai na banda O/E (FWM entre canais C+L) é desprezível na prática (a09 diz que no O-band o SKR é dominado pela atenuação, "low level of background noise"). Para MB-SDM no simulador pode-se começar com P_NLI = 0 fora da banda clássica e SpRS como termo dominante — **mas isso é uma decisão nossa, não dos artigos**.

**(Conversão potência → fotões de ruído por janela)** — a09 eq. (6):

    p_noise = (1 / 2hc) · T_d · η_d · λ_q · P_noise

aplicada a P_SpRS, P_NLI (e P_dark-current, segundo a09). Fator 1/2 = (provavelmente) só uma das duas bases/detectores de polarização recebe o ruído **[INCERTO quanto à justificativa; está no texto]**. Unidades: P em W, λ em m, T_d em s → nº médio de fotões detectados.

**Ruído de fundo total**: p_b = p_SpRS + p_NLI + p_dark (a09: p_dark-current; a10/a11: p_dark). Em **MF** (fibra escura): p_b = p_dark apenas (a11, texto explícito).
- **[INCERTO]** a09 diz que p_dark-current também é obtido por (6) a partir de P_dark-current, mas os parâmetros dão "p_dark-current = 10^−8" já como probabilidade por janela. Recomendo usar p_dark = 1e−8 diretamente (bate com os alvos numéricos — ver [CÁLC] abaixo).

**(SKR BB84 decoy, limite inferior)** — a09 eq. (3), a10 eq. (4), a11 eq. (1):

    SKR ≥ max{ 0, ( Q1·[1 − H(e1)] − η_ec·Q_μ·H(E_μ) ) / T_s }

**(Ganhos e erros)** — a09 eqs. (4)–(5), a10 eq. (5):

    η   = η_d · e^{−α(f_q)·l}               (a10; a09 só descreve η em palavras)
    Q_μ = 1 − (1 − p_b) · e^{−η μ}
    E_μ = [ p_b/2 + e_d (1 − e^{−η μ}) ] / Q_μ
    Q1  = μ e^{−μ} (p_b + η)
    e1  = (2 η e_d + p_b) / [ 2 (p_b + η) ]
    H(x) = −x log2 x − (1−x) log2(1−x)

- **Nota**: em a10 eq. (5) aparece "η = η_d e^{−α(f_c) l}" (com f_c) — é a atenuação **na frequência do QCh**; o subscrito "c" é ambiguidade tipográfica. Usar α(λ_q).
- Mesmas fórmulas já existentes no SNetS (Moghaddam 2021) — **não há mudança no BB84**, só em p_b e em α(λ) fora da banda C.

**(Taxa do PICh — upper bound)** — a09 eq. (1):

    PIR ≤ 4/T_s + 2 × SKR

Decomposição (a09 §III-A, Fig. 3): CLK ≤ 1/T_s; base de medição + confirmação de base ≤ 2/T_s; bits de nível de intensidade (IL, decoy, 2 bits por medida correta, 50 % de acerto) ≤ 1/T_s; bits "reveal" ≤ SKR; bits de confirmação ≤ SKR. Com T_s = 250 ps: 4/T_s = **16 Gbit/s** [CÁLC] (por QCh).

**(Relay de chave por nós confiáveis / XOR)** — a11 (sem número de equação; via exemplo Fig. 3): chave transportada hop-a-hop com one-time pad (XOR) em cada nó confiável. O exemplo numérico de a11 Fig. 3 é reproduzido exatamente por:

    SKR_fim-a-fim = min_i SKR_hop,i ;   N_Q-TRx = nº de hops QKD ;   SKR/Q-TRx = SKR_fim-a-fim / N_hops

(30 km, 0 TN: 40/1 = 40; 15 km, 1 TN: 100/2 = 50; 5 km, 5 TN: 185/6 = 30,83 Mbit/s — **bate exato** [CÁLC]). O texto fala em "XOR reduz SKR", mas numericamente o efeito é só o divisor N_hops (consumo de Q-TRx).

**TDM (a09, a11)**: cada QCh (1 Q-TRx) tem capacidade = SKR do hop; várias demandas de chave são multiplexadas no tempo no mesmo QCh até esgotar o SKR; idem PICh (capacidade do PICh-TRx vs PIR por QCh). Grooming fim-a-fim/single-path para CChs.

### 0.2 Parâmetros comuns (valores exatamente como no texto)

| Parâmetro | Valor | Fonte |
|---|---|---|
| Largura do QCh | 12,5 GHz | a09, a10, a11 |
| Potência do QCh | −80 dBm | a09, a10, a11 |
| Largura CCh/PICh | 75 GHz | a09, a11 (a10: 75 GHz nos cenários 1–4, **112,5 GHz** no estudo O+E+S, Fig. 5) |
| Potência CCh/PICh | 0 dBm (a10 Fig. 4a varia; −5 dBm citado) | a09, a10 |
| T_s | 250 ps | todos |
| η_ec | 1,16 | todos |
| e_d | 0,015 | todos |
| η_d | 0,3 | todos |
| T_d | 100 ps | todos |
| μ | **0,5** (a09) / **0,7** (a10, a11; ótimo em a10 Fig. 2) | conflito |
| p_dark | 10^−8 | todos |
| γ | 1,20 1/W/km | todos |
| α | depende da frequência, faixa [0,18–0,38] dB/km (ref. Ferrari 2020, ITU-T G.652.D) | todos |
| β2 | −22,317 ps²/km | todos |
| β3 | 0,1331 ps³/km | todos |
| Guarda CCh↔QCh | 200 GHz | a09, a10 |
| Fibra | dual-fiber (uma fibra por sentido) ⇒ só ruído para frente (forward) | todos |
| Crosstalk de canal adjacente e ASE | desprezados (filtro passa-faixa/TBPF em todos os nós) | todos |

**α(λ)**: nenhum dos três dá a curva; referem Ferrari 2020 (G.652.D, modelo de perda multibanda do GNPy: Rayleigh + absorção UV/IR + pico OH ~1383 nm). **Precisamos implementar α(λ) de 1260–1626 nm** (substituir a quadrática em 1550 nm). Indícios no texto: mínimo ~1550 nm (a11), extremos 0,38 (≈1260 nm) e 0,18 dB/km; a09 diz que 1360 nm é o "comprimento de onda ótimo do O-band, com a menor atenuação".

**[CÁLC] Calibração de α por retro-cálculo (só dark count, p_b = 1e−8, fórmulas acima):**
- α = 0,28 dB/km, μ = 0,5: SKR(100 km) = 387,9 kbit/s; SKR(150 km) = 15,3 kbit/s; SKR(200 km) = 0,46 kbit/s. Compare a09: limiares de Fig. 10 em **≈15 kbit/s** e **≈385 kbit/s** e SKR(1360 nm, 200 km) ≈ **0,354 kbit/s** ⇒ a09 usa α(1360 nm) ≈ 0,28–0,29 dB/km. Bom alvo de verificação.
- α = 0,28 dB/km, μ = 0,7: SKR(5)=194,7; (15)=100,8; (30)=37,9; (70)=2,86 Mbit/s. Compare a11 Fig. 3 (1380 nm): 185 / 100 / 40 Mbit/s; a10 (1381 nm, 70 km): 3,08 Mbit/s ⇒ α(1380 nm) ≈ 0,27–0,29 dB/km (com SpRS pequeno).
- α = 0,38 dB/km, μ = 0,7: SKR(70 km)=0,57 Mbit/s vs a10 1260 nm @70 km = **0,53 Mbit/s**; SKR(5 km) = 173 Mbit/s vs a10 160,82 Mbit/s (diferença atribuível a SpRS).
- α = 0,18 dB/km, μ = 0,7: SKR(40 km) = 50,1 Mbit/s (MF @1550 nm).

---

## 1. a09 — Dibaj et al., JLT 2024/2025 (MAIS IMPORTANTE)

### 1.1 Arquitetura / cenário
- **MB-EON**: CCh e PICh na **banda C+L estendida** (12 THz, 6 THz por banda), dividida em **160 canais de 75 GHz** por fibra; QCh na **banda O**, dividida em **1400 canais de 12,5 GHz** (1400×12,5 GHz = 17,5 THz ≈ 1260–1360 nm [CÁLC]). Mesma fibra (Fig. 1: GB entre bandas).
- Enlaces com **duas fibras** (uma por sentido).
- Nós (Fig. 2):
  - **Fonte/destino**: Qponder (Q-TRx + PICh-TRx + PICh manager + RNG + filtro de polarização PF) + transponder CCh com cifragem/decifragem.
  - **Nó QKD bypass** (= site de amplificador/óptico): DMUX separa QCh de CCh/PICh; amplificadores em linha separados **C** e **L** só para CCh/PICh; QCh passa sem amplificar; MUX recombina. Filtro passa-faixa (contra crosstalk adjacente e ASE); acoplador + monitor + **equalizador de potência** (garantir potência exata dos CChs, pois SpRS/NLI dependem dela); **isoladores (ISO)** contra reflexões/LO/ASE.
  - **Nó QKD confiável**: igual ao bypass para CCh (amplificado), mas QCh e PICh terminam no **Qponder intermediário** (gera chave com o nó anterior, relay hop-a-hop).
- Nós confiáveis só em **sites de amplificadores em linha** (span L_s = 50 km ou 80 km).
- PICh: canal clássico de 75 GHz na C+L, compartilhado por TDM.
- Continuidade espectral: CCh fim-a-fim (transparente); QCh/PICh só entre nós confiáveis consecutivos.
- Canais vazios na C+L são preenchidos com **ASE noise loader** ("dummy") ⇒ **C+L sempre totalmente carregada** para o cálculo de ruído ⇒ SKR do QCh **independe da carga clássica** (premissa importante: simplifica e torna o SKR uma função só de (λ_q, l)).

### 1.2 Equações
Eq. (1) PIR; (2) P_SpRS; (3) SKR; (4) Q_μ, Q1; (5) E_μ, e1; (6) p_noise — todas na Seção 0. NLI via GGN/GNPy (G_NLI(l,f) por EDOs, sem equação explícita no a09).
- a09 eq. (4) escreve Q1 = μ e^{−μ}(p_b + η) — igual a a10.

### 1.3 Parâmetros (a09 §III-C, texto)
QCh 12,5 GHz; QCh −80 dBm; CCh/PICh 75 GHz; CCh/PICh 0 dBm; T_s = 250 ps; η_ec = 1,16; e_d = 0,015; η_d = 0,3; T_d = 100 ps; **μ = 0,5**; p_dark-current = 10^−8; γ = 1,20 1/W/km; α em [0,18–0,38] dB/km; β2 = −22,317 ps²/km; β3 = 0,1331 ps³/km.
Tráfego: θ_r ~ U[100, 600] Gbit/s; σ_r = 0 (sem QKD) com prob. 0,25, senão σ_r ∈ {1,2,3} uniforme ↔ período de renovação de chave de bloco 256 bits τ_r ∈ {256, 128, 64} **ms** ⇒ demanda de chave 256/τ = **1, 2, 4 kbit/s** (o texto cita "required SKR demands (1, 2, and 4) kbit/sec").

### 1.4 Algoritmos
**FDP (fixed-distance placement)**: TNs a múltiplos de L_s: {50,100,150} km (L_s=50) ou {80,160} km (L_s=80), "randomly … with uniform distribution"; ignora tráfego.

**HLA (hot-link algorithm)** — passos (texto §IV-A):
1. Pré-processamento: rotear todas as conexões (tráfego uniforme aleatório) na topologia ⇒ carga de cada enlace ⇒ **SKR total requerido por enlace**.
2. Classificar enlaces em SKR requerido baixo/moderado/alto (critério inspirado na análise FDP — Fig. 10).
3. Em cada enlace, colocar TNs na **maior distância possível** (L_s, 2L_s, 3L_s) cujo SKR atingível atende o requerido: L_s=50 ⇒ 150 km (baixo), 100 km (moderado), 50 km (alto). SKR por distância = cenário da Fig. 5 (O-band, C+L cheia).
4. Prioridade secundária: minimizar NTN.
- Não há regra numérica explícita de corte (limiares só via Fig. 10, ver resultados).

**RMSTA** (Fig. 7 = fluxograma; texto §IV-B):
- Caminhos pré-calculados por **Yen k-shortest paths** para cada par.
- Três políticas de roteamento CCh × canais QKD (QCh+PICh):
  - **CQDR** (disjoint): QKD e CCh em caminhos **disjuntos em nós e enlaces** (segurança contra Eve num TN).
  - **CQJR** (joint): mesmo caminho para CCh, PICh, QCh (controle simples).
  - **CQFR** (flexible): cada um no melhor caminho, sem restrição.
- Modulação distance-adaptive para CCh e PICh (ref. Arpanaei 2023) — **tabela de alcance por formato não está no texto**.
- QCh: alocar QCh por demanda de SKR com **TDM** (vários pedidos por QCh até capacidade = SKR do hop).
- PICh: alocar banda proporcional ao PIR (eq. 1) com TDM na capacidade do PICh-TRx.
- CCh: grooming **single-path** (mesma origem, destino e caminho escolhido) — capacidade restante do line card usada por outras requisições.
- Política de escolha de slot (first-fit etc.) **não explicitada no texto do a09** (provavelmente no Fig. 7). a11 diz first-fit.

### 1.5 Topologias, tráfego, métricas, premissas
- Enlace ponto-a-ponto de **450 km** (Fig. 10); **JP** 12 nós/17 enlaces; **DT** 12 nós/20 enlaces; **NSFNET** 14 nós/21 enlaces (Fig. 9; comprimentos dos enlaces só na figura).
- Tráfego **semi-estático** (requisições chegam gradualmente, conexões permanentes, sem término).
- 95 % de confiança (repetições).
- Métricas: Q-TRx médio, NTN médio vs demanda de SKR; nº máximo de canais CCh/PICh usados e bloqueio vs carga clássica ofertada (limiar 1 % na Fig. 15).

### 1.6 Resultados numéricos (alvos de verificação)
Física (pré-rede):
- **Fig. 4** (QCh na C+L estendida 1524–1605 nm; 30 CChs no fim da L-band, 1607–1626 nm; guard 200 GHz; l = 5–50 km): alcance máximo **47 km** no λ ótimo **1529 nm** [TEXTO]. Anomalia em **1596 nm** (ρ menor) — tendência "mais longe do CCh ⇒ maior SKR" não vale lá [TEXTO]. Valores de SKR só na figura [FIG].
- **Fig. 5** (QCh no O-band 1260–1360 nm; C+L cheia; l = 50, 80, 100, 150, 160, 200 km): λ ótimo **1360 nm** (menor atenuação do O-band); SKR ≈ **0,354 kbit/s a 200 km** [TEXTO]; abaixo das demandas 1, 2, 4 kbit/s. Atenuação é o fator dominante (ruído de fundo baixo no O-band) [TEXTO].
- **Fig. 10** (450 km, L_s=50, FDP 50/100/150): demanda < ~**15 kbit/s** ⇒ 150 km ótimo; **15–385 kbit/s** ⇒ 100 km; **≈385–770 kbit/s** ⇒ 50 e 100 km empatam em Q-TRx; > ~**770 kbit/s** ⇒ 50 km [TEXTO]. [CÁLC] Com α ≈ 0,28 dB/km, μ=0,5: SKR(150)=15,3 kbit/s e SKR(100)=388 kbit/s ⇒ os limiares 15/385 são o SKR de um QCh no hop de 150/100 km; 770 ≈ 2×385. **Ótimo alvo de verificação.**
- L_s = 80: 240 km (3L_s) inviável (SKR muito baixo, Fig. 5) [TEXTO].

Rede:
- **DT (Fig. 11b)**: demanda < 0,75 Mbit/s: HLA melhor que FDP50 e FDP100 em até **130** e **30** Q-TRx, igual a FDP150; 0,75–12 Mbit/s: HLA ≈ FDP100, melhor que FDP50/FDP150 em até **100** e **995**; 12–30 Mbit/s: melhor em até ~**21, 110, 2229** Q-TRx vs FDP50/100/150 [TEXTO].
- **JP (Fig. 11a)**: 0,2–0,4 Mbit/s: até ~**188, 31, 22**; 0,4–0,8: até ~**166, 18, 63**; 6–12: ~**113, 31, 2590**; 12–30: ~**56, 392, 6754** (vs FDP50/100/150) [TEXTO].
- **NSFNET (Fig. 11c)**: ganho do HLA menor (graus uniformes). L_s = 80 (Figs. 13–14): HLA = FDP80 ótimo para SKR > **0,2 Mbit/s**; FDP160 usa até ~**61 000** Q-TRx a mais que FDP80 [TEXTO].
- L_s=50 superior especialmente para demandas < **4,5 Mbit/s** e > **25 Mbit/s** [TEXTO].
- **NTN (Fig. 12)**: HLA melhora NTN em média **20, 30, 74** (DT, JP, NSFNET, nesta ordem no texto) vs FDP50 para demanda 19,5–30 Mbit/s [TEXTO]; FDP150/160 tem menos TNs que HLA.
- **Fig. 15** (canais máx. usados, bloqueio 1 %): DT @140 Tbit/s: CQFR **40**, CQJR **50**, CQDR **110**; JP @105 Tbit/s: **64 / 77 / 112**; NSFNET @35 Tbit/s: **25 / 32 / 88** [TEXTO].
- **Fig. 16** (carga máxima a 5 % de bloqueio, CQFR/CQJR/CQDR): DT **675 / 495 / 230** Tbit/s; JP **317 / 265 / 175**; NSFNET **305 / 225 / 73** Tbit/s [TEXTO].
- Nota: as demandas de SKR nos eixos das Figs. 11–14 vão até 30 Mbit/s (demanda agregada, não por requisição) — **escala do eixo não definida no texto [INCERTO]**.

### 1.7 O que implementar / lacunas
- α(λ) G.652.D 1260–1626 nm (Ferrari 2020/GNPy) — essencial.
- Curva ρ(1550 nm, λ) + escalonamento eq. (2) de a10; SpRS forward com soma sobre todos os CChs (C+L cheia = 160 canais × 0 dBm).
- p_b com eq. (6); NLI: GGN completo é caro — opções: (a) tabela pré-calculada com GNPy por (λ_q, l), (b) desprezar NLI no O/E (justificar com ordem de grandeza).
- Grade O-band 1400×12,5 GHz para QCh; C+L 160×75 GHz para CCh/PICh; guard 200 GHz.
- PIR (eq. 1) para dimensionar PICh; TDM de QCh e PICh.
- HLA + FDP; nós confiáveis só em múltiplos de L_s.
- Roteamentos CQDR/CQJR/CQFR; Yen k-SP; grooming single-path.
- Tráfego semi-estático (incremental, sem saída) — o SNetS é dinâmico; precisa modo incremental.
- Lacunas: k de Yen, política de espectro, capacidade do PICh-TRx, tabela de modulação, comprimentos de enlace, critério numérico baixo/moderado/alto do HLA, potência ótima de lançamento por banda — **não estão no texto**.

---

## 2. a10 — Mehdizadeh et al., IEEE Commun. Lett. 2024

### 2.1 Arquitetura / cenário
- Uma fibra por sentido (dual-fiber), DWDM; CCh = DCh (dados) + PICh, ambos clássicos.
- Fig. 1: CChs na **C+L estendida**, QChs em **O+E+S** (TBPF no receptor).
- Bandas (texto): **O 1260–1360 nm, E 1360–1460 nm, S 1460–1530 nm** (legenda Fig. 5) / **S 1460–1523 nm** (texto §III-B) — inconsistência menor.
- Cenários da Fig. 2 (SKR vs μ):
  1. QCh em C-band de **fibra separada**, 20 km;
  2. QCh no O-band **1345 nm**, CChs na C+L estendida (cheia, 75 GHz), 20 km;
  3. QCh **1530,59 nm** + **54 CChs** na C-band (75 GHz, guarda 200 GHz, 0 dBm), 5 km;
  4. como 1, 70 km;
  5. como 2, 70 km.
- §III-A: C-band estendida inteira para QCh (**480 QChs** de 12,5 GHz) e L-band estendida para CCh (**80 CChs** de 75 GHz).
- §III-B: C+L cheia com CCh de **112,5 GHz**; QCh em O+E+S (12,5 GHz cada).
- §III-B Fig. 6: carga parcial 50 %; C-band em 10 e C+L em 20 partições, cada sub-banda = 5 CChs; 10 padrões (Sc.A…Sc.J).

### 2.2 Equações
(1) P_SpRS; (2) ρ(λ_c,λ_q) por escalonamento; (3) G_NLI (GGN); (4) SKR; (5) η, Q_μ, E_μ, Q1, e1. p_b = p_dark + p_NLI + p_SpRS (conversão potência→fotões não escrita no a10; usar a09 eq. 6).

### 2.3 Parâmetros (texto)
QCh 12,5 GHz; −80 dBm; T_s = 250 ps; η_ec = 1,16; e_d = 0,015; η_d = 0,3; T_d = 100 ps; p_dark = 10^−8; γ = 1,20 1/W/km; α 0,38–0,18 dB/km; β2 = −22,317 ps²/km; β3 = 0,1331 ps³/km; **μ ótimo = 0,7** (usado nas simulações após Fig. 2). CCh 75 GHz/0 dBm salvo indicação.

### 2.4 Algoritmos
Não há RMSA. Política de atribuição de λ do QCh (conclusão): **começar em 1381 nm e descer até 1260 nm** (ordem decrescente). Política de CCh com carga parcial: **maximizar o gap CCh–QCh**; padrão "decussado" (alternado) de sub-bandas de CCh reduz interferência.

### 2.5 Premissas
Análise ponto-a-ponto (sem topologia/tráfego). Ruído só forward. Crosstalk adjacente e ASE desprezados (TBPF).

### 2.6 Resultados numéricos (alvos)
- **Fig. 2**: μ ótimo = **0,7** para todos os cenários [TEXTO]; MF (cen. 1, 4) tem o maior SKR para mesmo l [TEXTO]; valores só na figura [FIG].
- **Fig. 3**: NLI GNPy vs FWM aprox.: +**22,45 dB** (200 GHz) e +**24,33 dB** (75 GHz); 75→200 GHz: NLI −**4,90 %**, FWM −**3,04 %** [TEXTO].
- **Fig. 4a** (480 QCh em C-ext, 80 CCh 75 GHz em L-ext, soma do SKR): **−5 dBm: 702 Mbit/s a 40 km**; **0 dBm: 370 Mbit/s a 13 km**; alcance máx. com toda a C-band quântica = **40 km @ −5 dBm**; com 0 dBm nada além de **13 km** (abstract) [TEXTO].
- **Fig. 4b** (0 dBm, L-ext 6 THz): CCh 50 GHz ⇒ **120 canais**, SKR total **32,4 Gbit/s a 5 km**; CCh 300 GHz ⇒ **20 canais**, **0,17 Gbit/s a 70 km**; CCh 100 GHz/0 dBm ⇒ SKR ≈ 0 para **l ≥ 15 km** [TEXTO].
- **Fig. 5** (C+L cheia, CCh 112,5 GHz; QCh 1260–1523 nm):
  - l ≤ 5 km: todos os λ têm SKR > 0; **1260 nm: 160,82 Mbit/s (QBER 0,015)**; **1522 nm: 25,82 Mbit/s (QBER 0,066)** [TEXTO].
  - 10 km: **1466–1522 nm ⇒ SKR = 0** (QBER **0,072–0,107**) [TEXTO].
  - Corte quando QBER médio > **0,07** [TEXTO].
  - **70 km: 0,974 Mbit/s em 1392 nm (QBER 0,056)**; **30 km: 9,87 Mbit/s em 1410 nm (QBER 0,057)** — ditos "máximos" [TEXTO] (ver conflito).
  - 70 km: tilt de perda 1260–1381 nm ⇒ redução de **82 %** [TEXTO].
  - Conclusão/abstract: 70 km: **1260 nm = 0,53 Mbit/s**, **1381 nm = 3,08 Mbit/s** (maior SKR), **> 1395 nm ≈ 0** [TEXTO]. (0,53/3,08 = 0,172 ⇒ 82,8 % de redução — consistente com o "82 %" [CÁLC].)
- **Fig. 6c**: Sc.F vs Sc.G (mesma ocupação) ⇒ **+45,27 %** na soma do SKR em O+E+S [TEXTO]; Sc.A/Sc.F: gap maior ⇒ melhor; Sc.D, E, I, J: padrão decussado melhora [TEXTO]. Distâncias 5, 20, 70 km (e 50 km na legenda) [TEXTO].

### 2.7 O que implementar / lacunas
- Mesmo que a09; adicionalmente: varrer λ_q em O+E+S; política "1381 nm → 1260 nm".
- **Inconsistência interna**: 0,974 Mbit/s @1392 nm como máximo a 70 km contradiz 3,08 Mbit/s @1381 nm a 70 km (abstract + conclusão + 82 %). Hipóteses: os valores 0,974/9,87 vêm de outra configuração (p.ex. CCh 112,5 GHz vs 75 GHz) ou erro. **Usar 3,08 e 0,53 como alvo** (três evidências consistentes; e [CÁLC] α≈0,27–0,28 dB/km @1381 nm reproduz 3,08). Também 9,87 Mbit/s @30 km contradiz a11 (40 Mbit/s @1380 nm, 30 km).
- Não fica claro se nas Figs. 5 a soma inclui PICh; a curva α(λ) usada (pico OH em 1383 nm?) não é dada — o fato de 1381 nm ser ótimo sugere fibra **low-water-peak** (G.652.D) sem pico OH significativo.

---

## 3. a11 — Dibaj et al., IWCIT 2024

### 3.1 Arquitetura / cenário
- **MB-scenario** (Fig. 2a): CCh+PICh na **C+L estendida 1523–1626 nm**, **160 canais** (1–80 C, 81–160 L; 75 GHz implícito); QCh em **O+E 1260–1380 nm** na **mesma fibra**; GB entre 1380 e 1523 nm. Figura sugere ~**1650** canais QCh de 12,5 GHz em 1260–1380 nm [FIG, texto garbled; CÁLC: 1260–1380 nm ≈ 20,7 THz ⇒ ~1656 canais].
- **MF-scenario** (Fig. 2b): QCh em **fibra separada (escura)**, também na **C+L estendida 1523–1626 nm**, ~**960** canais de 12,5 GHz [FIG; CÁLC: 12 THz/12,5 GHz = 960]; CCh+PICh na outra fibra C+L (160 canais). Em MF **p_b = p_dark** apenas.
- Nós: fonte/destino (camada IP, CCh-Xponder com cifragem, Q-TRx e PICh-TRx, ROADM, TBF); **bypass**: DMUX, amplificadores C e L só p/ CCh/PICh, MUX, TBF; **trusted**: CChs amplificados, PICh+QCh terminam no Q-TRx/PICh-TRx intermediário. Em MF a fibra quântica não tem amplificador (QCh contínuo) [FIG].
- Em redes metro, **nós bypass ignorados** (impacto mínimo, ref. Zhang 2023).
- Chave relay por XOR (one-time pad), hop-by-hop.

### 3.2 Equações
Eq. (1) SKR (Q1, e1, Q_μ, E_μ "de (5) em [8]" = a10). p_b = p_dark + p_NLI + p_SpRS (MB) / p_dark (MF). NLI pelo GNPy/GGN. Métrica principal: **SKR/Q-TRx** (Seção 0.1).

### 3.3 Parâmetros (texto)
QCh 12,5 GHz; −80 dBm; **μ = 0,7**; T_s = 250 ps; η_ec = 1,16; e_d = 0,015; η_d = 0,3; T_d = 100 ps; p_dark = 10^−8; γ = 1,20 1/W/km; α(f_c) = 0,38–0,18 dB/km; β2 = −22,317 ps²/km; β3 = 0,1331 ps³/km.
Tráfego: θ_r ~ U[100, 600] Gbit/s; chave AES-256; τ_r ∈ {256, 128, 64} **microssegundos** (texto) ⇒ 1, 2, 4 **Mbit/s** por requisição [CÁLC]. (a09 usa ms ⇒ kbit/s — conflito.)

### 3.4 Algoritmo RMSTA (Fig. 4 + texto §III)
1. Entrada: topologia, R(s_r, d_r, θ_r, τ_r), tabela SKR(λ, distância), λs de QCh e CCh/PICh, K, limiar de bloqueio, QTND.
2. Yen k-SP para todos os pares.
3. **Primeiro canal da C-band reservado ao PICh em todos os enlaces** (todos os pares de fibra clássica).
4. Para cada r: CCh first-fit em todos os K caminhos (modulação distance-adaptive); escolher o caminho com **menor índice de canal** usado.
5. Se há canais: alocar; grooming single-path / fim-a-fim.
6. QCh no **mesmo caminho do CCh** (joint): first-fit de canal + time slot para QCh; first-fit de time slot para PICh (TDM).
7. Se recursos insuficientes ⇒ bloqueia.
8. Repete aumentando carga até bloqueio clássico ou quântico > limiar (1 %); reporta uso de Q-TRx, carga clássica ofertada, demanda de SKR ofertada, bloqueio.
- **Colocação de TNs**: a cada **QTND** km ao longo do caminho; QTND escolhido para maximizar SKR/Q-TRx (MB: 20 km @1380 nm; MF: 25 km @1550 nm). Continuidade espectral QCh/PICh só entre TNs.
- Alocação de λ do QCh no MB: prioridade **1380 nm → menores λ**.

### 3.5 Topologias / premissas
- Fig. 6: (a) small core **5 nós, 6 enlaces**; (b) butterfly **6 nós, 8 enlaces**; (c) metro **8 nós, 11 enlaces**. Comprimentos de enlace só na figura (texto garbled: valores como 76, 40, 90, 86, 55, 100, 80, 70, 50, 45, 40, 35, 30, 25, 20 km — **não atribuíveis com segurança** [FIG]).
- Tráfego semi-estático. Limiar de bloqueio 1 %.

### 3.6 Resultados numéricos (alvos)
- **Fig. 3** (1380 nm, C+L cheia): link 30 km sem TN: **40 Mbit/s**; hops de 15 km: **100 Mbit/s** cada; hops de 5 km: **185 Mbit/s** cada; SKR/Q-TRx: **30 km → 40**, **15 km → 50**, **5 km → 30,83 Mbit/s** [TEXTO/FIG legível]. ⇒ **alvo mais limpo para validar o relay**: SKR/Q-TRx = min SKR_hop / N_hops.
- **Fig. 5** (enlace de 40 km): QTND ótimo **10 km @1260 nm**, **20 km @1380 nm** (MB); MF: λ ótimo **1550 nm**, QTND ótimo **25 km**; se só {15, 30, 45} km forem possíveis, **30 km** [TEXTO]. Ganho em SKR/Q-TRx no QTND ótimo vs sem TN em 40 km: **MB +40 %**, **MF +15 %** [TEXTO].
- **Fig. 1**: (a) O+E SKR 1–40 km, eixo log 5–300 Mbit/s; (b) C+L MF, 1550 nm máximo [FIG].
- **Fig. 7** (QTND 5–50 km): small core @ **625 Tbit/s** clássico + **4,1 Gbit/s** SKR; butterfly @ **612 Tbit/s** + **4,028 Gbit/s**; metro @ **654 Tbit/s** + **4,248 Gbit/s**; bloqueio 1 % [TEXTO]. Mínimo de Q-TRx em MF é **31 %, 28 %, 28 %** melhor que MB (small core, butterfly, metro), normalizado pelo mínimo do MB [TEXTO]. Contagens absolutas de Q-TRx só na figura (eixo 0–1400) [FIG].

### 3.7 [CÁLC] Verificação da convenção de contagem de hops (enlace 40 km, só dark count)
- MF, α = 0,18 dB/km, μ = 0,7:
  - convenção **fracionária** (SKR/Q-TRx = SKR(d)·d/L): ótimo em **d = 25 km** (58,7 Mbit/s) vs 40 km (50,1) ⇒ **+17 %** ≈ "15 %". ✔
  - convenção **inteira** (N_hops = ⌈L/d⌉): ótimo em 20 km (58,0) — **não** reproduz 25 km.
- MB, α ≈ 0,28 dB/km, μ = 0,7:
  - convenção **inteira**: ótimo em **d = 20 km** (36,3) ✔, mas ganho vs 40 km (19,9) = +83 % (texto: 40 %);
  - convenção fracionária: ótimo em 15 km ✘.
- Conclusão: **a convenção de contagem de Q-TRx não é única/clara**; o exemplo da Fig. 3 (divisões exatas) não discrimina. Registrar como ambiguidade; para MF a fracionária reproduz 25 km e 15 %. O "40 %" do MB não é reproduzido por nenhuma convenção só com dark count (o ruído SpRS cresce com l e pioraria o caso 40 km, aumentando o ganho, não diminuindo) — talvez o % seja calculado como (a−b)/a: (36,3−19,9)/36,3 = 45 % [CÁLC, especulativo].

### 3.8 O que implementar / lacunas
- Arquitetura MF: segunda fibra (ou núcleo/fibra dedicado) com QCh na C+L, p_b = p_dark, α(1550) ≈ 0,18 dB/km; grade de 960 canais de 12,5 GHz.
- MB: QCh 1260–1380 nm (~1650 canais), atribuição a partir de 1380 nm descendo.
- PICh fixo no 1º canal da C-band em todos os enlaces (TDM).
- QTND fixo ao longo do caminho; métrica SKR/Q-TRx; convenção de hops a decidir.
- Lacunas: comprimentos dos enlaces, K, tabela de modulação, capacidade PICh-TRx, quantas fibras por enlace no MF (1 par clássico + 1 par quântico presumido).

---

## 4. Conflitos e inconsistências entre os três artigos

| Item | a09 | a10 | a11 |
|---|---|---|---|
| μ | 0,5 | 0,7 (ótimo) | 0,7 |
| τ_r (bloco 256 bits) | {256,128,64} **ms** ⇒ 1/2/4 kbit/s | — | {256,128,64} **µs** ⇒ 1/2/4 Mbit/s |
| Banda do QCh (MB) | O 1260–1360 nm (1400 canais) | O+E+S 1260–1523 nm; ótimo 1381 nm | O+E 1260–1380 nm (~1650 canais) |
| λ ótimo O-band | 1360 nm (menor atenuação) | 1381 nm (maior SKR a 70 km) | 1380 nm |
| BW do CCh | 75 GHz | 75 GHz (Figs. 2–4), **112,5 GHz** (Fig. 5) | 75 GHz (160 canais) |
| Limite C+L | 1607–1626 nm p/ 30 CChs no fim da L | C-ext 480 QCh, L-ext 80 CCh | 1523–1626 nm |
| Escopo | backbone, L_s 50/80 km | ponto-a-ponto | metro, QTND 5–50 km |
| p_dark | via eq. (6) "P_dark-current", mas valor 10^−8 | p_dark = 10^−8 | p_dark = 10^−8 |
| S-band | — | 1460–1530 (legenda) vs 1460–1523 (texto) | — |

- **a10 interno**: "máximo 0,974 Mbit/s @1392 nm a 70 km" vs "3,08 Mbit/s @1381 nm a 70 km" (incompatíveis); "9,87 Mbit/s @1410 nm a 30 km" vs a11 "40 Mbit/s @1380 nm a 30 km".
- **α(λ)**: todos dizem 0,18–0,38 dB/km mas nenhum dá a curva; a09 chama 1360 nm de "menor atenuação do O-band" enquanto a10/a11 usam até 1380 nm (sugere fibra sem pico OH).
- **Fórmula de SpRS** usa α(f_c) (do CCh) e fator l — aproximação que só vale quando α_c ≈ α_q; para QCh em O (α≈0,3–0,38) e CCh em C+L (α≈0,18–0,2) a diferença é relevante. Os artigos usam a forma simplificada — para reproduzi-los, usar a mesma.

## 5. Checklist de implementação no SNetS-SDM-QKD
1. `AttenuationModel`: α(λ) 1260–1626 nm (G.652.D, Ferrari 2020/GNPy) substituindo a quadrática de 1550 nm. Calibrar: α(1360–1381) ≈ 0,27–0,29; α(1260) ≈ 0,38; α(1550) ≈ 0,18 dB/km.
2. `RamanCrossSection`: tabela ρ(1550 nm, λ) + escalonamento (λ_δ/λ_q)^4; regra λ_δ = 1550 − |λ_c − λ_q|.
3. `SpRSNoise`: P_SpRS = l·Δλ_q·Σ e^{−α_c l}·p_c·ρ; somar 160 CChs a 0 dBm (C+L cheia por ASE loader).
4. `NLINoise`: tabela GNPy pré-computada ou zero fora da banda clássica (documentar).
5. `NoiseToPhotons`: eq. (6) de a09.
6. `BB84Decoy`: já existente; parâmetro μ configurável (0,5 / 0,7).
7. `PICh`: PIR ≤ 4/T_s + 2·SKR; TDM.
8. Arquiteturas: **MF** (fibra/núcleo escuro, p_b = p_dark, QCh em C+L), **MB** (QCh O/E na mesma fibra, SpRS+NLI dos CChs C+L), **MB-SDM** (bandas em cada núcleo da MCF — **não coberto por nenhum dos três artigos**; combinaria SpRS intra-núcleo + XT inter-núcleo do modelo atual).
9. Nós confiáveis: FDP (L_s múltiplos), HLA (a09), QTND fixo (a11); métrica SKR/Q-TRx.
10. Roteamento CQDR/CQJR/CQFR + Yen k-SP; tráfego semi-estático incremental.

## 6. Alvos de verificação priorizados
1. a11 Fig. 3 (relay): 40 / 50 / 30,83 Mbit/s — trivial, exato.
2. a10: SKR @70 km: **1381 nm = 3,08 Mbit/s**, **1260 nm = 0,53 Mbit/s** (redução 82 %); 1260 nm @5 km = **160,82 Mbit/s (QBER 0,015)**; 1522 nm @5 km = **25,82 Mbit/s (QBER 0,066)**; 1466–1522 nm = 0 a 10 km.
3. a09: SKR(1360 nm, 200 km) ≈ **0,354 kbit/s** (μ = 0,5); limiares Fig. 10 ≈ **15 kbit/s** (150 km) e **385 kbit/s** (100 km).
4. a10 Fig. 4: **702 Mbit/s @40 km (−5 dBm)**, **370 Mbit/s @13 km (0 dBm)**, **32,4 Gbit/s @5 km (CCh 50 GHz/120 canais)**, **0,17 Gbit/s @70 km (300 GHz/20 canais)** — testa SpRS dentro da C+L (soma de 480 QChs).
5. a09 Fig. 4: alcance máx. **47 km @1529 nm** (30 CChs em 1607–1626 nm).
6. a11: QTND ótimo MB **20 km @1380 nm**, MF **25 km @1550 nm**; ganhos 40 % / 15 %; Q-TRx MF 31/28/28 % menor que MB.
7. a09 rede: canais 40/50/110 (DT), 64/77/112 (JP), 25/32/88 (NSFNET); carga @5 % bloqueio 675/495/230, 317/265/175, 305/225/73 Tbit/s (dependem de detalhes não publicados — alvo qualitativo).

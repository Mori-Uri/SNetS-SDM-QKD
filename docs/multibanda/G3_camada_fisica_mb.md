# G3 — Camada física multibanda (MB) e MB-SDM: notas de leitura

Fontes (extrações `pdftotext -layout`, em
`/mnt/user-data/uploads/Simulador/SNetS-SDM-QKD/launcher/.work/txt_artigos/`):
- **a08.txt**: Ferrari et al., JLT 2020 (O→L em G.652.D, GGN + SRS).
- **a06.txt**: Semrau, Sillekens, Killey, Bayvel (correção de formato de modulação para o ISRS GN, forma fechada).
- **a04.txt**: Rademacher et al., OFC 2020 (C+L em fibra de 3 núcleos fortemente acoplados).

Convenções usadas nestas notas:
- **[TXT]** = valor/texto lido diretamente na extração.
- **[RECONSTR.]** = equação reconstruída a partir de texto embaralhado; a forma foi checada por consistência (dimensões/limites), mas deve ser conferida no PDF.
- **[DERIVADO]** = conta minha a partir de números do texto (não aparece no artigo).
- **[AUSENTE]** = a informação está numa tabela/figura que é imagem no PDF e **não** veio na extração. Nenhum número foi inventado para preencher essas lacunas.

---

## 1. Ferrari et al. — "Assessment on the Achievable Throughput of Multi-Band ITU-T G.652.D Fiber Transmission Systems"

### 1.1 Identificação
- **Autores [TXT]:** Alessio Ferrari, Antonio Napoli, Johannes K. Fischer, Nelson Costa, Andrea D'Amico, João Pedro, Wladek Forysiak, Erwan Pincemin, Andrew Lord, Alexandros Stavdas, Juan Pedro F.-P. Gimenez, Gunther Roelkens, Nicola Calabretta, Silvio Abrate, Bernd Sommerkorn-Krombholz, Vittorio Curri (Politecnico di Torino, Infinera, Fraunhofer HHI, Aston, Orange, BT, Telefónica etc.).
- **Revista [TXT]:** Journal of Lightwave Technology, vol. 38, n. 16, pp. 4279–4291, 15 ago. 2020.
- **DOI [TXT]:** 10.1109/JLT.2020.2989620.
- Ferramenta usada: biblioteca **GNPy** [21] para a QoT; NLI pelo **modelo GGN** (generalized GN) com SRS.

### 1.2 Sistema e parâmetros físicos

**Bandas.** O texto define as bandas {O, E, S, C, L} "as per ITU [9]" (Fig. 1, imagem). Os limites em nm **não aparecem na extração**. Para referência, a definição ITU-T usual (G-series Suppl. 39 / manual ITU de 2009) é: O 1260–1360, E 1360–1460, S 1460–1530, C 1530–1565, L 1565–1625, U 1625–1675 nm. Esses limites são conhecimento padrão e **não foram lidos do artigo**; confira na Fig. 1. O artigo **não usa a banda U**.

Números citáveis [TXT]:
| Item | Valor |
|---|---|
| Região ≤ 0,4 dB/km em G.652.D | 1260 nm até pelo menos 1625 nm, ~53,5 THz (~11× a banda C) |
| Banda C | ~5 THz; C+L com reuso de EDFA: ~+7 THz, total ~11,5 THz |
| Ocupação total do pente WDM O→L | ~53 THz, ~900 canais |
| Grade / taxa de símbolo | 50 GHz fixa, 32 GBaud, RRC com roll-off 0,15 |
| Guarda entre bandas | 2 nm ("valor de benchmark, ainda não viável com filtros maduros") |
| Faixa sem transmissão no ZDW | 30 nm (5 THz) centrada em D(λ)≈0, escolhida para maximizar a capacidade da banda O [28] |
| Canais: O vs S | 240 vs 182 (a banda O e a S carregam tráfego total comparável) |
| Canais na banda E | 295, e 252 quando há bombas Raman na banda O (guarda extra de 2 THz contra FWM bomba-bomba) |
| Atenuação | ~0,38 dB/km (banda O) até ~0,18 dB/km (banda C) (Fig. 2, medida) |
| Dispersão D(λ) | de −5 ps/nm/km a ≥ 20 ps/nm/km (O→L) |
| γ | "praticamente constante em cada banda", valores na Tabela II **[AUSENTE]** |
| Amplificadores por banda | O: dopado com praseodímio (PDFA) [32]; E: bismuto (BDFA) [33]; S: túlio (TDFA) [34]; C e L: érbio (EDFA) |
| Figuras de ruído por banda | Tabela II **[AUSENTE]**. O texto diz apenas que são valores "viáveis, da literatura", com ganho e NF planos por banda e GFF ideal depois do amplificador |
| Ganho do amplificador | constante, igual à **maior perda dentro da banda** (amp + GFF fazem a equalização) |
| Perda do MB-DEMUX | **3 dB** por demux (um no fim de cada vão) |
| Perdas de ROADM | não modeladas (só o MB-MUX/DEMUX); MB-ROADM aparece só como arquitetura (Fig. 3) |
| Comprimento de vão L_s | 50 km e 75 km |
| Cenários de alcance | DCI (1 vão, ≤80 km; nos resultados, 75 km), metro 150 km, metro estendido 300 km, regional 600 km |
| Potência de lançamento | LOGO por banda, plana dentro da banda (Tabela III, potência por canal por banda e por L_s: **[AUSENTE]**) |
| Potência total máxima | ≤ 1,5 W (caso E→L com bombas Raman) |
| Limite de segurança citado | IEC 60825-2: até 500 mW |
| Bombas Raman | 5 bombas contrapropagantes na banda O (Tabela IV **[AUSENTE]**) |
| Retroespalhamento Rayleigh | κ = 10⁻⁷ 1/m; desprezado no SRS (bombas < 800 mW [62]) |
| Overhead (FEC + pilotos) | 12% |
| Banda do SRS | ~15 THz ("guarda de ~15 THz evitaria o tilt") |

> **Lacuna crítica:** as Tabelas II (parâmetros por banda: NF, γ, tipo de amplificador), III (potência por canal por banda), IV (bombas), V (SE média por banda), VI (ganho relativo a C e C+L) e VII (capacidade máxima em Tb/s) são imagens. **É preciso abrir o PDF original** para tirar NF, γ e potências por banda. Não preenchi nada de memória.

### 1.3 Equações (numeração do artigo)

**(1) GGN, densidade espectral de NLI após um vão [RECONSTR.]**

    G_NLI(L_s, f) = (16/27) γ² ∫∫_{−∞}^{+∞} G_TX(f1) G_TX(f2) G_TX(f1+f2−f)
                    · | ∫_0^{L_s} sqrt[ ρ(ζ,f1) ρ(ζ,f2) ρ(ζ,f1+f2−f) / ρ(ζ,f) ]
                        · exp( +j 4π² (f1−f)(f2−f) [β2 + π β3 (f1+f2)] ζ ) dζ |² df1 df2

- ρ(z,f) é o perfil de perda/ganho que depende de frequência e posição. Ele inclui a atenuação não plana e o SRS (diafonia Raman e ganho Raman, se houver bombas).
- **Incerteza:** a extração mostra a razão ρρρ/ρ e um "2" solto. Como a eq. (4) define ρ como razão de **potências**, a forma consistente com o GGN de Cantono et al. usa a **raiz quadrada** da razão dentro da integral de campo. Confira no PDF se o artigo escreve sqrt(·) ou define ρ como perfil de campo. O "2" junto de π é o π² da fase.

**(2) GSNR [TXT]**

    GSNR = P_RX / (P_ASE + P_NLI) = (OSNR⁻¹ + SNR_NL⁻¹)⁻¹,   OSNR = P_RX/P_ASE,  SNR_NL = P_RX/P_NLI

- Com operação LOGO no ótimo, o texto diz que a GSNR é dominada pela ASE, com **OSNR = SNR_NL/2** (equivale a P_ASE = 2·P_NLI no ótimo).

**(3) Equações de potência com SRS (sistema de N EDOs, apêndice A) [RECONSTR.]**, com f1 < f2 < … < fN:

    ± dP(z,f_i)/dz = −α(f_i) P(z,f_i)
                     + Σ_{j=i+1}^{N}  C_R(f_i,f_j) P(z,f_j) P(z,f_i)            (ganho vindo de freq. maiores)
                     − Σ_{j=1}^{i−1} (f_i/f_j) C_R(f_j,f_i) P(z,f_j) P(z,f_i)   (depleção para freq. menores)

- "+" indica copropagação, "−" contrapropagação (bombas).
- Condições de contorno: P(z0,f_i) com z0 = 0 para sinais e bombas co-, e z0 = L_s para bombas contra. É um problema de dois pontos, resolvido numericamente.
- **Incerteza:** o texto escreve "C_R(f_i,f_n)" (erro de digitação, deve ser f_j), e a fração de energias de fóton aparece embaralhada (f_j/f_i ou f_i/f_j). A forma acima é a fisicamente correta: o canal de frequência maior perde f_i/f_j fótons-equivalentes de potência.

**(4) Perfil de perda [TXT]**

    ρ(z, f_i) = P(z, f_i) / P(z0, f_i)

**(5)–(7) GSNR por canal (apêndice B) [TXT]**

    GSNR = P_S / (P_ASE + P_NLI)                      (5)
    P_ASE = h · f · G_eq · F_eq · B_ref               (6)
    P_NLI = B_ref · G_NLI(L_s, f)                     (7)

- B_ref = R_S (taxa de símbolo).
- G_eq e F_eq são o ganho e a NF equivalentes Raman+amplificador concentrado; sem Raman, são o G e a NF do amplificador concentrado.
- **Observação:** a eq. (6) é a aproximação de alto ganho, sem o fator (G−1) nem n_sp explícito. No simulador, use `P_ASE = h f NF (G−1) B_ref` ou `h f NF G B_ref` de forma consistente com o alvo de verificação (com G ≈ 20 dB a diferença é < 0,05 dB).
- No multivão, NLI e ASE se somam de forma incoerente por vão ("abordagem totalmente desagregada", cada vão contribui sua NLI independentemente).

**(8)–(9) Eficiência espectral média por banda (apêndice C) [TXT]**

    SE_banda = Σ_{i=1}^{Nch} Rb_net,i / B = Σ_n SE_n R_S / (Nch Δf)     (8)
    SE = 2 log2(1 + SNR) / (1 + OH),   OH = 0,12                          (9)

(O SNR na eq. (9) é a GSNR.)

### 1.4 Resultados numéricos citáveis (ALVOS)
| Resultado | Valor [TXT] |
|---|---|
| Tilt total (atenuação + SRS) sobre toda a banda O→L | **8 dB após 50 km**, **10 dB após 75 km** (Fig. 7) |
| Cruzamento SNR_ASE = SNR_NL | **~1450 nm** (entre E e S), L_s = 75 km, um vão, todas as bandas (Fig. 8) |
| Regime | Frequências altas (sobretudo a banda O) limitadas por ASE; S, C e L limitadas pela NLI (Kerr) |
| Detalhe na borda | A borda superior (λ) da banda L tem uma leve subida de GSNR por falta de vizinhos |
| Descontinuidades | Na SNR_ASE entre bandas (NFs diferentes), visível em 1450 nm |
| Capacidade O→L, DCI | **450 Tb/s** (L_s = 50 km) → **375 Tb/s** (L_s = 75 km), queda de 75 Tb/s (~15%) |
| Capacidade regional 600 km | **220 Tb/s** (resumo); "bem acima de 200 Tb/s" |
| Ganho vs C e C+L | O→L ≈ **8–10× a banda C** e **2,5–3× C+L** (resumo: ~10× e 8× vs C, ~2,5× vs C+L) |
| Raman na banda O (E→L) | melhora de no máximo **10%** em todos os casos |
| Usar a banda O para dados (DCI, 50 km) | **~30%** de throughput adicional |
| Banda O para bombas em vez de dados (75 km) | throughput cai **~20%** |
| Projeção de fibras (20 anos, CAGR 30%) | {96, 48, 22} fibras para C, C+L e todas as bandas (hipótese de {20, 20, 50} Tb/s) |
| SE média por banda, capacidade por banda | Tabelas V e VII **[AUSENTE]**, Figs. 9–10 (só gráfico) |

### 1.5 Avaliação para o simulador
- O GGN (eq. 1) com EDOs de Raman (eq. 3) é **caro demais por requisição**: integral dupla mais um solver de EDO de N≈900 canais por vão.
- Uso viável: **pré-computar offline** (por comprimento de vão, carga cheia, LOGO por banda) a GSNR por slot e por vão, e guardar em tabela. É a mesma filosofia do LOGO do artigo: a carga cheia é o pior caso.
- Parâmetros que faltam (NF, γ, P_ch por banda) precisam vir do PDF da Tabela II/III.

---

## 2. Semrau et al. — "A Modulation Format Correction Formula for the Gaussian Noise Model in the Presence of Inter-Channel Stimulated Raman Scattering"

### 2.1 Identificação
- **Autores [TXT]:** Daniel Semrau, Eric Sillekens, Robert I. Killey, Polina Bayvel (Optical Networks Group, University College London). **Confirmado:** são Semrau/Bayvel et al.
- **Versão extraída [TXT]:** arXiv:1903.02506v3 [eess.SP], 29 set. 2019 (cabeçalho "October 1, 2019").
- **Publicação em revista:** o texto **não traz** revista nem DOI. Pelo que sei, saiu no JLT em 2019 (vol. 37, n. 19). Isso é **memória não verificada**; confirme antes de citar.
- Financiamento: EPSRC TRANSNET (EP/R035342/1).

### 2.2 Ideia central
A NLI se escreve como η = η_GN + η_corr. A correção de formato de modulação considera só os termos **XPM** (dominantes em fibra de alta dispersão e com taxa de símbolo/espaçamento altos). Ela fica em duas partes, uma do **1º vão** e outra **assintótica** (linear em n), e tem forma fechada com ISRS para banda ≤ 15 THz (aproximação linear/triangular do ganho Raman).

### 2.3 Equações (numeração do artigo)

**(1) SNR [TXT]:** `SNR_i ≈ P_i / (P_ASE + η_n(f_i) P_i³)`. P_ASE pode depender do canal (inflação de ASE por equalização de ganho).

**(2)** `η_n(f_i) = η_GN,n(f_i) + η_corr,n(f_i)`

**(3)** `η_corr,n(f_i) = Σ_{k=1,k≠i}^{Nch} η_corr,n^{(k)}(f_i)`, isto é, a soma sobre os pares COI–interferente.

**(4) Correção XPM em forma integral [RECONSTR.]** (de [29, Eq. 17]):

    η_corr,n^{(k)}(f_i) = (80/81) (P_k/P_i)² γ² Φ / B_k  ∫_{−B_i/2}^{B_i/2} df1 ∫_{−B_k/2}^{B_k/2} df2
                         | μ(f1+f_i, f2+f_i+Δf, f_i) Σ_{m=0}^{n−1} e^{j m f1 (f2+Δf) φ} |²

  com Δf = f_k − f_i e **φ = −4π² [β2 + π β3 (f_i + f_k)] L**. Atenção: este φ **inclui o comprimento do vão L**.

  **Φ = E[|X|⁴]/E²[|X|²] − 2** é a curtose em excesso do formato.

  (Incerteza: o fator 1/B_k fora da integral pode estar como "1/(B_k − B_i/2)" na extração embaralhada. Pela eq. (5), γ̃ = (P_k/P_i)² (80/81) γ² Φ / B_k.)

**(5)** Com a hipótese Δf ≫ B_k/2, f2+Δf ≈ Δf:

    η_corr,n^{(k)} ≈ γ̃ ∫_{−B_i/2}^{B_i/2} df1 |μ(f1+f_i, f_k, f_i)|² · |1 + Σ_{m=1}^{n−1} sinc(mφf1 B_k/2) e^{jmφf1Δf}|²
    γ̃ = (P_k/P_i)² (80/81) γ² Φ / B_k

**(6)–(7)** Aproximação assintótica: `lim_{n→∞} |1+Σ…|² ≈ 1 + δ(f1) lim C_n`, com `C_n = ∫ df1 |Σ_{m=1}^{n−1} sinc(mφf1B_k/2) e^{jmφf1Δf}|²` e sinc(x) = sin(x)/x.

**(8)–(11)** Decomposição:

    η_corr,n^{(k)}(f_i) ≈ η_corr,1^{(k)}(f_i) + ñ · η_corr,a^{(k)}(f_i)
    η_corr,1^{(k)} = γ̃ ∫_{−B_i/2}^{B_i/2} |μ(f1+f_i, f_k, f_i)|² df1                   (9)
    η_corr,a^{(k)} = γ̃ |μ(f_i, f_k, f_i)|² · lim_{n→∞} ∂C_n/∂n                         (10)
    ñ = 0 se n = 1; ñ = n caso contrário                                               (11)

**(12) Correção assintótica genérica (qualquer função de enlace μ) [TXT, legível]**

    η_corr,a^{(k)}(f_i) = γ̃ |μ(f_i,f_k,f_i)|² · (2π / (|φ| B_k²)) · [ (2Δf − B_k) ln((2Δf − B_k)/(2Δf + B_k)) + 2B_k ]

**(13) Função de enlace do ISRS GN [RECONSTR.]** ([34, Eq. 9]):

    μ(f1,f2,f_i) = ∫_0^L dζ  [ P_tot e^{−αζ − P_tot C_r L_eff(ζ) (f1+f2−f_i)} / ∫ G_Tx(ν) e^{−P_tot C_r L_eff(ζ) ν} dν ] · e^{j φ̃(f1,f2,f_i,ζ)}
    L_eff(ζ) = (1 − e^{−ᾱζ}) / ᾱ
    φ̃ = −4π² (f1 − f_i)(f2 − f_i) [β2 + π β3 (f1 + f2)] ζ

  O termo entre colchetes, para (f1+f2−f_i) → f, é a **evolução de potência normalizada sob ISRS** (perfil "tilt"). Isso é útil por si só, veja a seção 4.3.

**(14) Função de enlace aproximada (XPM + ISRS de 1ª ordem) [RECONSTR.]**

    μ(f1+f_i, f_k, f_i) ≈ −(1 + T̃_k)/(−α + jφ_{i,k} f1) + T̃_k/(−A + jφ_{i,k} f1)
    T̃_k = −P_tot C_r f_k / ᾱ,   φ_{i,k} = −4π² (f_k − f_i)[β2 + πβ3(f_i + f_k)],   A = α + ᾱ,   ᾱ = α (padrão)

  (Nota de rodapé do artigo: φ_{i,k} é **redefinido** nas eqs. (15)–(16) com −2π².)

  Para ir **além de 15 THz** (fora da região triangular do ganho Raman) ou usar potências inclinadas e atenuação dependente de λ, reinterprete α, ᾱ e C_r como **parâmetros por canal**, ajustados por regressão ao perfil de potência real. O perfil vem da solução numérica das EDOs de Raman. É mais caro, mas fica offline.

  Checagem [DERIVADO]: em f1 = 0, |μ(f_i,f_k,f_i)|² = T_k/(α²A²), com T_k = (α+ᾱ−P_tot C_r f_k)². Isso confere com o 2º termo de (15).

**(15) Correção de formato de modulação do ISRS GN, forma fechada [RECONSTR., estrutura verificada]**

    η_corr,n(f_i) ≈ (80/81) Φ Σ_{k≠i} (P_k/P_i)² (γ²/B_k) · {
          1/(φ_{i,k} ᾱ (2α+ᾱ)) · [ (T_k − α²)/α · atan(φ_{i,k} B_i / α) + (A² − T_k)/A · atan(φ_{i,k} B_i / A) ]
        + (2π ñ T_k)/(|φ| B_k² α² A²) · [ (2|Δf| − B_k) ln((2|Δf| − B_k)/(2|Δf| + B_k)) + 2B_k ] }

    Δf = f_k − f_i;  φ_{i,k} = −2π² (f_k − f_i)[β2 + πβ3(f_i + f_k)];  T_k = (α + ᾱ − P_tot C_r f_k)²;  A = α + ᾱ
    φ (sem índice) = −4π² [β2 + πβ3(f_i + f_k)] L   (da eq. 4)

  - A 1ª parcela corrige o vão único; a 2ª, o acúmulo multivão.
  - Vale para enlaces com amplificação concentrada e banda óptica de até **15 THz**.
  - As frequências f são **relativas ao centro do pente WDM** (o termo de Raman linear P_tot C_r f assume referência central).

**(16) ISRS GN completo, forma fechada, para formato arbitrário (SPM + XPM corrigida) [RECONSTR.]**

    η_n(f_i) ≈ (4/9) (γ²/B_i²) · π n^{1+ε} / (φ_i ᾱ (2α+ᾱ))
               · [ (T_i − α²)/α · asinh(φ_i B_i² / (π α)) + (A² − T_i)/A · asinh(φ_i B_i² / (π A)) ]
             + (32/27) Σ_{k≠i} (P_k/P_i)² (γ²/B_k) · {
               (n + (5/6)Φ)/(φ_{i,k} ᾱ (2α+ᾱ)) · [ (T_k − α²)/α · atan(φ_{i,k}B_i/α) + (A² − T_k)/A · atan(φ_{i,k}B_i/A) ]
             + (5/3) (Φ π ñ T_k)/(|φ| B_k² α² A²) · [ (2|Δf| − B_k) ln((2|Δf| − B_k)/(2|Δf| + B_k)) + 2B_k ] }

    φ_i = (3/2) π² (β2 + 2π β3 f_i);  T_i = (α + ᾱ − P_tot C_r f_i)²;  demais como em (15)

  - Consistência verificada [DERIVADO]: (32/27)·(5/6) = 80/81 e (32/27)·(5/3)·π = (80/81)·2π. Isso reproduz exatamente (15), o que confirma os fatores 5/6 e 5/3 lidos do texto embaralhado ("n + 6Φ" com "5" sobrescrito).
  - **ε** (fator de coerência da SPM, "n^{1+ε}") **não está definido neste texto** (aparece como "πn1+" na extração). Ele vem de [38] (Semrau, Killey, Bayvel, JLT 2019, closed-form ISRS GN). **Lacuna:** a expressão de ε precisa vir de [38]. Na falta dela, ε = 0 (acúmulo incoerente) é conservador para muitos vãos.
  - "asinh(φ_i B_i²/(π a))": o "a" do texto é erro de digitação de α.
  - Com Φ = 0 (gaussiano), (16) se reduz ao ISRS GN fechado de [38]. Com C_r = 0, T = α² (com ᾱ = α, A = 2α) e o termo em A² − T vira 3α², o que recupera o GN clássico com atenuação.
  - **Unidades:** α e ᾱ em 1/m (neper de **potência**, não dB/km); C_r em 1/(W·m·Hz); β2 em s²/m; f em Hz.

**(17) Identidade (apêndice A) [TXT]:** `C'_∞ = lim_{n→∞} ∂/∂n ∫ dx |Σ_{m=1}^{n} sinc(m a x) e^{jmbx}|² = (π/a²)[(b−a) ln((b−a)/(a+b)) + 2a]`, para b ≥ a ≥ 0.

### 2.4 Tabelas [TXT]

**Tabela I — curtose em excesso Φ**
| Formato | Φ |
|---|---|
| QPSK uniforme | −1 |
| 16-QAM uniforme | −0,6800 |
| 64-QAM uniforme | −0,6190 |
| 256-QAM uniforme | −0,6050 |
| ∞-QAM uniforme | −0,6000 |
| 64-QAM geometricamente moldado (12 dB SNR, GMI) [23] | −0,3403 |
| 64-QAM probabilisticamente moldado (12 dB SNR, MI) [41] | −0,1871 |
| Gaussiano | 0 |

**Tabela II — parâmetros do sistema**
| Parâmetro | SMF | NZDSF |
|---|---|---|
| Perda α [dB/km] | 0,2 | 0,19 |
| Dispersão D [ps/nm/km] | 17,0 | 4,5 |
| Inclinação S [ps/nm²/km] | 0,067 | 0,05 |
| γ [1/W/km] | 1,2 | 1,3 |
| Área efetiva [µm²] | 80 | 72 |
| Inclinação do ganho Raman C_r [1/W/km/THz] | 0,028 | 0,031 |
| Ganho Raman (C_r · 14 THz) [1/W/km] | 0,39 | 0,44 |
| Potência por canal P_i [dBm] | 0 | −2 |
| Potência total P_tot [dBm] | 24 | 22 |
| Taxa de símbolo [GBd] | 40 | |
| Banda do canal B_i [GHz] | 40,004 | |
| Espaçamento [GHz] | 40,005 | |
| Número de canais | 251 | |
| Banda óptica B_tot [THz] | 10,05 | |
| λ de referência [nm] | 1550 | |
| Roll-off [%] | 0,01 | |
| Número de símbolos [2^x] | 17 | |
| Passos de simulação por vão [10⁶] | 2,5 | |

(No texto aparecem "0,25·10⁶ passos suficientes" e, na tabela, "2,5·10⁶ por vão". Há inconsistência na extração; não afeta o modelo.)

Outras condições [TXT]:
- SSFM (Manakov) com ISRS aplicado como perda dependente de frequência a cada passo; passo logarítmico.
- Potências escolhidas para maximizar o canal central com EDFA de **NF 5 dB**.
- Amplificadores sem ruído na validação.
- 4 realizações de dados.
- Canais de interesse em f_i = 0 THz e f_i = −4,0 THz.
- Fig. 4: **6 × 100 km**, todos os canais em Gaussiano, 16-QAM ou 64-QAM.

### 2.5 Resultados citáveis (ALVOS)
| Comparação | Erro [TXT] |
|---|---|
| Correção em forma integral (5) vs. SSFM (QPSK) | erro médio **0,26 dB** |
| Forma fechada (15) vs. SSFM, Figs. 2–3 | erro absoluto médio **0,45 dB**; diminui com n; zero em 1 vão e no limite n→∞ |
| Forma fechada (15) vs. forma integral | **< 0,1 dB após 100 vãos** |
| Eq. (16) vs. SSFM (com e sem ISRS) | **0,3 dB (SMF)** e **0,2 dB (NZDSF)** |
| Valores absolutos de η | só nos gráficos (eixos 25–45 dB(1/W²) nas Figs. 2–3 e 34–49 dB(1/W²) na Fig. 4); **não há números em texto**. Para verificação absoluta, digitalizar a Fig. 4 |

Qualitativo [TXT]: Φ < 0 na maioria dos formatos, o que dá correção negativa (menos NLI). O GN superestima a NLI para formatos de baixa cardinalidade e baixa dispersão acumulada. A fórmula [21] de Poggiolini (sem ISRS, assintótica) é imprecisa nos primeiros vãos.

---

## 3. Rademacher et al. — "172 Tb/s C+L Band Transmission over 2040 km Strongly Coupled 3-Core Fiber"

### 3.1 Identificação
- **Autores [TXT]:** Georg Rademacher, Ruben S. Luís, Benjamin J. Puttnam, Roland Ryf, Sjoerd van der Heide, Tobias A. Eriksson, Nicolas K. Fontaine, Haoshuo Chen, René-Jean Essiambre, Yoshinari Awaji, Hideaki Furukawa, Naoya Wada (NICT, Japão; Nokia Bell Labs, EUA; TU/e, Holanda).
- **Evento [TXT]:** OFC 2020, artigo **Th4C.5**, © OSA 2020. DOI não aparece no texto.

### 3.2 Configuração [TXT]
| Item | Valor |
|---|---|
| Fibra | CC-MCF (coupled-core) de **3 núcleos**, casca padrão de 125 µm, **acoplamento forte** (aleatório) |
| Enlace | vão único de **60 km** em 3 laços recirculantes sincronizados (< 50 ps); total **2040 km** (= 34 voltas [DERIVADO]) |
| Por núcleo | A_eff = **129 µm²**, perda **0,18 dB/km**, CD **19,5 ps/nm/km** @1550 nm |
| Perda média por canal espacial | **14,2 dB** (incluindo multiplexadores, fan-in/out por *photonic lantern*) |
| Potência de lançamento | **24 dBm por núcleo** (≈ −1,55 dBm por canal WDM se uniforme [DERIVADO]: 24 − 10 log10 359) |
| Amplificação | EDFAs de banda C e banda L separados, com processadores ópticos para equalizar o ganho (sem Raman) |
| WDM | **359 canais × 24,5 GBaud DP-16QAM**, grade de 25 GHz (pente óptico > 100 nm), RRC com roll-off 0,01 |
| Faixa | **1529,16 – 1608,5 nm** (> 75 nm, C+L) |
| Receptor | **MIMO 6×6** no domínio do tempo, 350 taps espaçados de meio símbolo; LO de 60 kHz; osciloscópio de 36 GHz/80 GS/s |
| FEC | LDPC DVB-S2 com puncionamento (granularidade de taxa 0,01), alvo BER < 2,19×10⁻⁵, + 2,8% de OH externo (HD) |

### 3.3 Resultados [TXT] (alvos experimentais)
- Fator Q entre **3,8 dB e 7,2 dB** sobre os 359 canais. A queda no L alto se deve a ruído de fase, OSNR menor longe da semente do pente e forma do ganho dos amplificadores.
- Taxa por canal: **400–540 Gb/s** (codificação implementada) e **450–560 Gb/s** (GMI).
- Total: **172,25 Tb/s** (codificado) e **185,96 Tb/s** (GMI). Produto taxa × distância: **351 Pb/s·km**, mais que o dobro do recorde anterior para SDM com casca padrão.
- MDL cresce levemente com λ. FWHM da resposta ao impulso curta em todos os canais, o que confirma acoplamento forte em C+L. SMD e MDL crescem com **√L** (contra crescimento linear em fibras fracamente acopladas).
- Contexto: antes, CC-MCF só tinha sido demonstrada em < 5 nm da banda C. A dependência de SMD com λ em C+L é grande, e o projeto ótimo de acoplamento depende de λ.

### 3.4 O que isso significa para MB-SDM no simulador
- **Mostra:** transmissão **multibanda (C+L, ~9 THz, 359 × 25 GHz [DERIVADO])** em **fibra multinúcleo de casca padrão** por **longa distância (2040 km)**, com acoplamento forte mantido em toda a faixa e EDFAs C/L convencionais.
- **Não mostra:**
  1. bandas O/E/S/U em MCF;
  2. fibra **fracamente acoplada** (o caso do SNetS-SDM, com XT inter-núcleo como ruído e alocação independente por núcleo);
  3. ISRS ou NLI medidos e modelados separadamente;
  4. XT dependente de λ.
- Na CC-MCF, os 3 núcleos formam **um único canal MIMO 6×6**. Não há "diafonia" como penalidade, e a NLI é **reduzida** pelo acoplamento aleatório forte. Os núcleos **não são** recursos independentes de RSA: um superconjunto espacial é alocado junto.
- **Uso como embasamento:**
  - Serve para justificar cenários **C+L sobre MCF** em longa distância e parâmetros de fibra (0,18 dB/km, 19,5 ps/nm/km, A_eff = 129 µm²).
  - Num modelo de MCF fracamente acoplada, ainda é preciso **XT dependente de frequência** (o acoplamento cresce com λ, então L > C). Esse modelo não está neste artigo e precisa de outra fonte.
  - Se o simulador quiser um modo "CC-MCF", trate os 3 núcleos como um canal espacial único com capacidade ×3 e NLI de SMF como limite superior conservador, sem termo de XT.

---

## 4. Recomendação de modelo implementável

### 4.1 Estrutura (por slot de frequência f, por núcleo c, por enlace ℓ)

    1/GSNR_caminho(f,c) = Σ_{vãos s ∈ caminho} [ P_ASE,s(f)/P_ch(f) + η_s(f)·P_ch(f)² ] + Σ_enlaces XT_ℓ(f)  (+ 1/SNR_TRX)

1. **Atenuação α(f):** tabela por banda ou curva de G.652.D. O Ferrari dá só os extremos no texto (~0,38 dB/km na banda O, ~0,18 dB/km na C); a curva completa está na Fig. 2 e precisa ser digitalizada. A a06 (Semrau) dá 0,2 dB/km (SMF, C+L), e a a04 dá 0,18 dB/km (MCF).
2. **ASE (Ferrari eq. 6):** `P_ASE = h f NF_b G_b B_ref`, com G_b = maior perda de vão na banda b mais 3 dB do MB-DEMUX. NF_b vem por banda (PDFA/BDFA/TDFA/EDFA). **Valores pendentes da Tabela II do Ferrari.**
3. **ISRS (perfil de potência e tilt):** use a forma fechada triangular do ISRS GN (eq. 13), que implica para espectro plano de largura B_tot:

       P(z,f)/P(0,f) = e^{−α(f) z} · P_tot C_r L_eff(z) B_tot e^{−P_tot C_r L_eff(z) f} / (2 sinh(P_tot C_r L_eff(z) B_tot / 2))   [DERIVADO de (13)]

   com f relativo ao centro do pente. O tilt em dB na saída é, aproximadamente, 4,34·P_tot C_r L_eff B_tot. **Validade ≤ 15 THz.**

   Para O→L (~53 THz), a aproximação linear falha. Nesse caso, **resolva as EDOs (Ferrari eq. 3) offline** (ex.: RK4 sobre ~900 canais, carga cheia) e ajuste α, ᾱ, C_r **por canal** (procedimento sugerido por Semrau na seção II-B).
4. **NLI:** use o **ISRS GN em forma fechada (Semrau eq. 16)**, com SPM + XPM e correção de formato via Φ (Tabela I).
   - Custo: O(N_ch) por canal e por vão (soma sobre interferentes), ou seja, O(N_ch²) para todos os canais.
   - **Para simulação dinâmica, precompute η_s(f) sob carga cheia (pior caso, estilo LOGO) por tipo de vão e por slot.** Assim cada requisição custa só **O(H·S)** (H = vãos do caminho, S = slots do caminho) em somas de tabela, mais a verificação de degradação das conexões vizinhas, que pode ser evitada sob a hipótese de carga cheia.
   - Φ depende do formato **dos interferentes**. Na carga cheia, assuma o formato mais "gaussiano" esperado (Φ = −0,6 para QAM uniforme, ou 0 para ser conservador).
5. **Potência de lançamento:** LOGO por banda, plana na banda (Ferrari). A potência ótima clássica é `P_opt = (P_ASE/(2η))^{1/3}`, consistente com OSNR = SNR_NL/2. Calcule por banda no centro da banda, como no artigo.
6. **MCF fracamente acoplada:** mantenha o XT inter-núcleo existente no SNetS, mas torne-o dependente de λ. Nenhum dos três artigos fornece esse modelo (lacuna).

### 4.2 Complexidade
| Variante | Custo por requisição |
|---|---|
| GGN integral + EDOs (Ferrari) | inviável online (integral 3D + EDO de N≈900) |
| ISRS GN fechado on-line, recomputando a carga real | O(H · N_ativos) por canal afetado; aceitável só com poucos milhares de requisições |
| **Tabela pré-computada a carga cheia (recomendado)** | **O(H·S)** de lookups; pré-processamento O(N_ch²) por tipo de vão, uma única vez |

### 4.3 Alvos de verificação (ordem de prioridade)
1. **Semrau, Tabela II (SMF: 251 × 40 GBd, 0 dBm/canal, 24 dBm total, 6 × 100 km, C_r = 0,028 1/W/km/THz):**
   - implementar (16) e comparar com a Fig. 4 digitalizada;
   - checagem interna: (15) fechada vs. (5) integral < 0,1 dB após 100 vãos;
   - limites: C_r = 0 recupera o GN; Φ = 0 recupera o ISRS GN.
2. **Ferrari:**
   - tilt total de **8 dB (50 km) / 10 dB (75 km)** em O→L, a carga cheia, com atenuação + SRS;
   - cruzamento SNR_ASE/SNR_NL em **~1450 nm** (75 km, 1 vão);
   - capacidade **450 Tb/s (50 km) / 375 Tb/s (75 km) DCI** e **220 Tb/s em 600 km**;
   - ganho com Raman **≤ 10%**.

   Requer as Tabelas II/III do PDF (NF, γ, potências).
3. **Rademacher:** ordem de grandeza **172,25 Tb/s em 2040 km** (C+L, 3 núcleos, 24 dBm/núcleo, 0,18 dB/km). É só sanidade de capacidade, porque se trata de CC-MCF com MIMO e não de XT.

### 4.4 Lacunas
- NF, γ e potência por banda do Ferrari (Tabelas II e III) **não extraídos**. Bombas Raman (Tabela IV) também não.
- Curva α(λ) e D(λ) completa da G.652.D: só na Fig. 2.
- **Banda U:** nenhum dos três artigos a trata.
- Fator de coerência ε do termo SPM (eq. 16) precisa de [38].
- Fórmula fechada válida até 15 THz: além disso, ajuste por canal (offline).
- Perdas de ROADM multibanda: só "3 dB por MB-DEMUX"; não há dados de WSS por banda.
- XT inter-núcleo dependente de λ e efeito do ISRS em MCF fracamente acoplada: sem dados nestas fontes.
- Revista e DOI do Semrau: não aparecem no texto extraído (arXiv v3).

## ADENDO — Tabelas do Ferrari 2020 lidas das imagens do PDF (pág. 4283–4287)

Tab. II (parâmetros por banda): O 1260–1360 nm, 220,59–238,10 THz, 17,25 THz, 240 canais, f0 229,07 THz, PDFA NF 7 dB, γ 1,6 /W/km;
E 1360–1460, 205,48–220,59, 14,81 THz, 296 ch, 212,79, BDFA NF 6, γ 1,5; S 1460–1530, 196,08–205,48, 9,13 THz, 182 ch, 200,65, TDFA NF 7, γ 1,4;
C 1530–1565, 191,69–196,08, 4,13 THz, 82 ch, 193,89, EDFA NF 5,5, γ 1,3; L 1565–1625, 184,62–191,69, 6,96 THz, 139 ch, 188,07, EDFA NF 6, γ 1,28.
Tab. III (potência por canal, LOGO): Full MBT Ls=50: O −4,0 E −3,4 S −2,7 C −3,1 L −2,5 dBm; Ls=75: −0,9 −1,1 −1,0 −1,7 −1,0.
  O-off sem Raman: Ls=50 E −3,4 S −2,7 C −3,1 L −2,5; Ls=75 −1,1 −1,0 −1,7 −1,0. Raman na O: Ls=50 E −4,2 S −2,7 C −3,1 L −2,5; Ls=75 −1,9 −1,0 −1,7 −1,0.
Tab. IV bombas Raman (THz/mW): 222,22/200, 227,27/200, 229,89/350, 232,56/350, 235,29/350.
Tab. V eficiência espectral média (bps/Hz), O→L [O E S C L]: Ls50 DCI 8,7 10,0 10,0 9,9 9,4; Metro 6,9 8,2 8,3 8,1 7,7; ExtMetro 5,8 7,1 7,1 7,0 6,6; Regional 4,7 6,0 6,0 5,9 5,5.
  Ls75 DCI 6,8 8,5 8,7 8,5 7,8; Metro 5,7 7,4 7,6 7,4 6,7; ExtMetro 4,6 6,3 6,5 6,3 5,6; Regional 3,6 5,2 5,4 5,2 4,5.
  No Raman E→L [E S C L]: Ls50 DCI 10,0 10,3 9,9 9,6; Metro 8,2 8,5 8,1 7,8; ExtM 7,1 7,4 7,0 6,7; Reg 6,0 6,3 5,9 5,6. Ls75 DCI 8,5 9,0 8,5 7,9; Metro 7,4 7,9 7,4 6,8; ExtM 6,3 6,7 6,3 5,7; Reg 5,2 5,6 5,2 4,6.
  Raman in O, E→L: Ls50 DCI 11,2 11,1 10,8 10,6; Metro 9,4 9,3 9,0 8,8; ExtM 8,3 8,2 7,9 7,7; Reg 7,2 7,1 6,8 6,6. Ls75 DCI 9,3 9,3 8,9 8,7; Metro 8,2 8,1 7,8 7,6; ExtM 7,1 7,0 6,7 6,5; Reg 6,0 5,9 5,6 5,4.
Tab. VI ganho vs C cheia [O→L, E→L sem Raman, E→L com Raman]: Ls50 DCI 10× 8× 8×; Metro 10× 7× 8×; ExtM 9,5× 7× 8×; Reg 9× 7× 8×. Ls75 DCI 9× 7× 7×; Metro 9× 7× 7×; ExtM 9× 7× 7×; Reg 8× 7× 7×.
  vs C+L: Ls50 4× 3× 3× (Metro com Raman 7× — provável erro de impressão); Ls75 DCI 4× 3× 3×; Metro 3× 2,7× 3×; ExtM 3× 3× 3×; Reg 3× 3× 3×.
Tab. VII capacidade máxima (Tb/s) [O→L, E→L sem Raman, com Raman]: Ls50 DCI 450 348 359; Metro 367 268 302; ExtM 314 229 265; Reg 263 208 229.
  Ls75 DCI 375 296 298; Metro 323 258 261; ExtM 272 219 225; Reg 222 181 189.
Cenários: DCI = 1 vão; Metro = 150 km; Extended Metro = 300 km; Regional = 600 km. Fig. 8 (Ls=75, 1 vão): GSNR ~15 dB em 1260 nm, ~23–24 dB em 1450–1550, ~20–21 dB em L (só gráfico). 32 GBd, roll-off 0,15, grade 50 GHz, 3 dB por MB-DEMUX, GFF após cada amplificador.

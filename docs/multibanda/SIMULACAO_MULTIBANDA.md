# Simulação multibanda no SNetS-SDM-QKD (Partes 43 a 45, níveis N0, N1 e N2)

## O que foi implementado

**N0 — estrutura.**

- As três arquiteturas (MF, MB e MB-SDM) entram no simulador de rede.
- O QCh fica numa banda escolhida (`qchCenterNm`, ex.: 1381 nm na banda E).
- A perda vem da curva G.652.D de 1260 a 1675 nm.
- Os QChs continuam usando os recursos de hoje: slots do núcleo QKD, TDM, PICh, Q-TRx e nós confiáveis.

**N1 — coexistência.**

- A admissão QKD e a proteção das sessões ativas calculam o ruído Raman espontâneo (SpRS) dos circuitos clássicos
  ativos e do circuito candidato.
- O ruído entra em p_b junto com o dark count e o crosstalk:

```
p_b = 1 − (1 − p_dark)(1 − p_XT)(1 − p_SpRS)
p_SpRS = 1 − exp(−N),   N = η_d · η_f · P_SpRS · T_g / (h ν) · f_det
P_SpRS = Δλ · Σ_n P_n · ρ(λ_n → λ_q) · G_n(L)
```

- `P_n` é a potência de lançamento do circuito clássico n, a mesma usada no crosstalk.
- `λ_n` é o centro dos slots do circuito na grade clássica.
- `ρ` é a seção de choque do SpRS (`SpontaneousRamanModel`).
- `G_n` é o comprimento efetivo de cada fonte:
  - circuito no mesmo núcleo do QCh (`hostCoreId`): forward, mesmo núcleo;
  - circuito num núcleo vizinho ao `hostCoreId` (só MB-SDM): forward intercore de Cai et al. (2020), com o h do
    crosstalk da camada física.
- `Δλ` é a largura do filtro do QCh.

## Modelos de Raman (`ramanModel`)

| Valor | G (mesmo núcleo) | f_det | Origem |
|---|---|---|---|
| `nenhum` | — (p_SpRS = 0) | — | Dibaj et al., JLT 2025: ruído desprezível no O-band |
| `artigo` | L·e^(−α_c L) | 0,5 | Mehdizadeh et al. 2024; Dibaj et al. 2025, eq. 6 |
| `fisico` | (e^(−α_q L) − e^(−α_c L))/(α_c − α_q) | 1,0 | Eraerds et al. 2010; Kawahara et al. 2011 |

Como nas variantes de XT e de modulação, o modelo é escolhido por caso, e a comparação é feita com casos iguais que
só mudam essa opção. Exemplo: `QKD_Multibanda/*/SSMF_MB_Raman-*`.

## Arquiteturas

| `architecture` | O que o núcleo QKD (virtual) representa | SpRS | Crosstalk linear no QCh |
|---|---|---|---|
| `MF` | fibra escura dedicada | não | não |
| `MB` | outra banda da fibra monomodo clássica (`hostCoreId`) | mesmo núcleo | só se o QCh estiver dentro da grade clássica |
| `MBSDM` | outra banda de um núcleo da MCF (`hostCoreId`) | mesmo núcleo + vizinhos (`intercoreRaman`) | idem |

Regras do arquivo `network`:

- declarar o núcleo QKD com `"adjacentCores": []` e `"qkdReserved": true`;
- não colocar o núcleo QKD na lista de vizinhos de nenhum núcleo clássico;
- a fibra clássica fica com todos os seus núcleos (MF e MB-SDM) ou com o único núcleo (MB).

## Hipóteses (para revisar com o orientador)

- **Sentido.** Os circuitos clássicos do mesmo enlace (a→b) andam no mesmo sentido do QCh: SpRS forward. Os enlaces
  do SNetS são unidirecionais. Com `"bidirectionalFiber": true` (Parte 44), os circuitos do enlace inverso (b→a)
  passam pela mesma fibra em sentido contrário e somam SpRS backward (Eraerds 2010, eq. 3; Cai 2020, eq. 18).
- **Potência.** Em cada trecho QKD (entre nós confiáveis), a potência clássica vale a potência de lançamento. Os
  amplificadores repõem a potência, e o QCh passa por fora deles.
- **Grade clássica.** Slot k da grade clássica = [f₀ + (k−1)·12,5 GHz, f₀ + k·12,5 GHz], com f₀ =
  `classicalBandStartThz`.
  - Com 320 slots e f₀ = 191,3 THz, a grade cobre a banda C.
  - Para o comb C+L dos artigos, use 992 slots e f₀ = 184,4 THz.
- **NLI/FWM (Parte 45, nível N2).** Com `"nliModel": "gn"`, o ruído não linear dos circuitos do núcleo hospedeiro
  na frequência do QCh vem do modelo GN (`NonlinearInterferenceModel`) e entra em p_b como mais um fator
  (1 − p_NLI). Só pesa com o QCh perto do comb (banda S). Com o QCh em O/E e o comb em C+L, é 0. Detalhes no
  CHANGELOG, Parte 45.
- **Ordem da soma.** As contribuições são somadas na ordem do id do circuito, então o resultado não depende da ordem
  dos conjuntos de circuitos dos núcleos.

## Verificação

- `MultiBandPhysicsVerification` (VMB-1 a VMB-8): física do enlace contra os artigos.
- `MultiBandNetworkVerification` (VMB-9a a VMB-9h):
  - o simulador, com enlaces e circuitos do SNetS, dá a mesma potência de SpRS da calculadora de enlace (erro 0)
    e a mesma SKR (erro ≤ 1e-7, diferença entre p = N e p = 1 − e^(−N));
  - MF e `nenhum` dão a SKR da fibra escura;
  - o XT só entra in-band;
  - a SKR segue a ordem `nenhum` > `artigo` > `fisico`.
- `MultiBandNliVerification` (VMB-11a a VMB-11f, Parte 45): NLI do modelo GN contra a forma fechada de
  Poggiolini, e simulador = calculadora.
- `MultiBandIwcitVerification` (VMB-10a/b, Parte 45) e `QKDNativeReferenceMain --arquitetura mb` (VMB-10c):
  MB × MF de Dibaj (IWCIT 2024), em enlace e em rede.
- Teste de regressão: casos sem o grupo `multiband` dão resultados idênticos aos da main.

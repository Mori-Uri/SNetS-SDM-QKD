# QKD_Multibanda_Parte49 — casos iniciais (Parte 49)

Casos para as primeiras simulações com o que entrou nas Partes 48 e 49:

- a correção anti-Stokes medida do SpRS (`multiband.antiStokesCorrection`);
- a QoT clássica por banda com ISRS (`physicalLayer.multiBand`);
- a escolha do λ do QCh pelo ruído (`qchSlotPolicy = maxskr`).

Eles partem dos casos `QKD_Multibanda/{Linha,Losango}` da Parte 43, com os mesmos algoritmos, tráfego por par, demanda de chave, PICh e QTND de 25 km. Mudam só os campos da tabela abaixo. Os casos foram gerados pelo script `tools/casos/gerar_casos_parte49.py`.

## Tamanho

- 20 000 requisições, 5 pontos de carga e 5 replicações. Os casos `QKD_Multibanda` usam 100 000 × 8 × 10.
- Estimativa no PC com 10 threads:
  - grupo A: alguns minutos por caso;
  - grupo B (C+L): ~15–30 min por caso;
  - grupo C: 1–3 h por caso (um QCh por sessão e muitos canais clássicos).
- Para aumentar o tamanho, use a edição em lote do launcher: selecione a pasta e mude a aba Simulação.

## Topologias

| Pasta | Enlaces | Carga |
|---|---|---|
| `Linha` | 1–2: 50 km | 25 por gerador (C); ×3,1 no C+L |
| `Losango` | 70–100 km | 10 por gerador (C); ×3,1 no C+L |

No C+L a carga é multiplicada por 992/320 = 3,1. Assim a ocupação relativa do espectro fica parecida com a da banda C.

## Grupo A — arquitetura, banda do QCh e correção anti-Stokes (grade clássica C)

| Caso | QCh | Raman | antiStokesCorrection |
|---|---|---|---|
| `A0_SSMF_MF` | fibra escura | — | — |
| `A1_SSMF_MB_E1381_antiStokes-nenhuma` | 1381 nm, mesma fibra | físico | nenhuma |
| `A2_SSMF_MB_E1381_antiStokes-medida` | 1381 nm, mesma fibra | físico | medida |
| `A3_SSMF_MB_O1310_antiStokes-medida` | 1310 nm, mesma fibra | físico | medida |
| `A4_MCF7_MBSDM_E1381_antiStokes-medida` | 1381 nm no núcleo 1 da MCF7 | físico + vizinhos | medida |

**A1 × A2** mostra o efeito da correção: a 1381 nm o QCh fica a ~23 THz da banda C, onde o fator é ~2,2.

**A3** está a ~36 THz da banda C. Nessa distância a correção não age (fator 1 acima de 30 THz) e o SpRS é bem menor; em troca, a perda é maior (~0,32 dB/km).

## Grupo B — QoT clássica por banda e ISRS (QCh: MB, físico, medida, 1381 nm)

| Caso | Grade clássica | `physicalLayer.multiBand` |
|---|---|---|
| `A2` (referência) | C: 320 slots, 191,85–195,85 THz | ausente (QoT única) |
| `B1_C_QoT-porBanda_ISRS` | C | ligado, com ISRS |
| `B2_CL_QoT-unica` | C+L: 992 slots, 184,4–196,8 THz | ausente |
| `B3_CL_QoT-porBanda_semISRS` | C+L | ligado, `isrs: false` |
| `B4_CL_QoT-porBanda_ISRS` | C+L | ligado, com ISRS |

O C+L usa `centerFrequency` = 190,6 THz (e `amplificationFrequency` igual), `slots` = 992 nos enlaces e `classicalBandStartThz` = 184,4.

Nos enlaces curtos destas topologias a margem de OSNR é grande: a OSNR fica em ~26 dB e o 64QAM pede 21,3 dB. Por isso a QoT por banda e o ISRS mudam pouco o bloqueio. O efeito aparece:

- na OSNR média (PhysicalLayerStatistics);
- no SpRS que chega ao QCh, porque o C+L tem 3× mais canais clássicos.

## Grupo C — λ do QCh (C+L com QoT por banda e ISRS)

`qchGranularity = route` e `wavelengthPolicy = grid`. A grade de QChs tem 992 slots de 12,5 GHz em torno de 1381 nm (≈ 1342–1421 nm).

| Caso | `qchSlotPolicy` |
|---|---|
| `C1_CL_QCh-firstfit` | primeiro slot livre (λ mais longo, mais perto do C+L) |
| `C2_CL_QCh-abne` | regra ABNE do núcleo QKD (núcleo 1: igual a firstfit) |
| `C3_CL_QCh-lowestloss` | slot de menor atenuação |
| `C4_CL_QCh-maxskr` | faixa livre com a maior SKR mínima da rota (Parte 49) |

Teste curto em Linha (800 requisições, 1 ponto de carga):

| Métrica | firstfit | maxskr |
|---|---|---|
| SKR média de admissão | 46,8 Mbit/s | 54,5 Mbit/s |
| SKR mínima de admissão | 12,6 Mbit/s | 50,9 Mbit/s |

Em `route` cada sessão tem o seu QCh, então há centenas de QChs ativos. Por isso estes casos são os mais lentos.

## Observação sobre os casos da Parte 43

Nos casos `QKD_Multibanda` o `classicalBandStartThz` é 191,3. A grade clássica da camada física desses casos começa em 193,85 − 320 × 12,5 GHz / 2 = **191,85 THz**. Com isso, o SpRS é calculado com os canais 0,55 THz abaixo de onde a QoT clássica os põe.

O efeito é pequeno, mas nos casos novos o valor já está coerente. Para corrigir os antigos, use a edição em lote e ponha 191,85.

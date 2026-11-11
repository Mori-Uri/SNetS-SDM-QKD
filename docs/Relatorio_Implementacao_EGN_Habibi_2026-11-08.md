# Relatório de implementação — EGN numérico, Habibi híbrido e comparação

**Data:** 08/11/2026  
**Base:** SNetS-SDM-QKD v1 integrada (com maxSKR, correção Raman, cache e launcher 1.22.0)  
**Novo Launcher:** 1.23.0

## 1. Entrega e escopo científico

A versão implementa três comportamentos verificáveis:

1. **Habibi original**: mantidas as Eqs. 3.21–3.31 de Lacerda Júnior (2024) com truncamento legado de valores negativos, usando `habibiEgnFallbackEnabled=false`.
2. **Habibi com ajuste EGN**: na produção, para CUT com largura física ≤25 GHz ou resultado Habibi bruto negativo, substitui SCI e as XCI individuais suportadas por integração EGN (Carena 2014); conserva as outras fontes e o caminho clássico. O parâmetro fica `true` por padrão, podendo ser desligado para reprodutibilidade.
3. **EGN numérico de referência**: integração das contribuições SCI, XCI e MCI com termos completos de correção de quarta e sexta ordem para uma configuração *controlada de 1–3 canais Nyquist, mesma largura e sem sobreposição*, com canais laterais simétricos ao CUT quando há três.

**Limitação fundamental:** o EGN de referência cobre explicitamente esse subconjunto físico, não qualquer rede WDM heterogênea ou uma implementação genérica do modelo EGN rigoroso. O Habibi híbrido soma correções por interferente, **não** inclui MCI simultânea, portanto pode diferir do EGN de 3 canais mesmo com fallback. Não se deve publicar comparações como "validação universal do EGN".

Os momentos de modulação definidos pela referência estão implementados somente para **QPSK, 16-QAM e 64-QAM**; 8-QAM e 32-QAM não tiveram seus momentos de sexta ordem inferidos. Nas modulações não suportadas, a simulação usa o Habibi antigo e emite aviso único.

## 2. Modificações

| Arquivo | Alteração |
|---|---|
| `network/EgnFullReference.java` | Quadratura numérica de SCI (GN e correções de 4ª/6ª ordem), XCI (GN e correções X1–X4) e MCI (GN e M1) até 3 canais em domínio controlado. |
| `network/HabibiEgnHybrid.java` | Ajuste Habibi seletivo, resultado original preservado sob flag, cache por parâmetros físicos e estatísticas. |
| `network/PhysicalLayer.java` | Aciona o híbrido nos caminhos clássicos relevantes de Habibi, sem modificar Johannisson; mantém PICh no tratamento legado. |
| `network/LongitudinalClassicalGN.java` | Acrescenta variante Habibi com ajuste EGN e mantém sobrecarga antiga para retrocompatibilidade. |
| `simulationControl/parsers/PhysicalLayerConfig.java` | Novos parâmetros configuráveis. |
| `network/HabibiEgnComparisonVerification.java` | Matriz quantitativa de três modelos, separação de SCI/XCI/MCI, teste de convergência. |
| `qkd/verification/QKDNarrowbandHabibiVerification.java` | Regressão integrada de NLI para canal QPSK 12,5 GHz no PhysicalLayer. |
| `launcher/...` | Exposição de todos os campos no schema e template; Launcher compilado 1.23.0. |
| `scripts/verify_habibi_egn.*`, `docs/EGN_CARENA_HABIBI_VALIDACAO.md` | Execução no Windows/Linux sem Maven e documentação científica/limitações. |

## 3. Configuração

```json
{
  "physicalLayerModel": 0,
  "habibiEgnFallbackEnabled": true,
  "habibiEgnNarrowbandGHz": 25.0,
  "habibiEgnQuadratureOrder": 16,
  "habibiEgnReceiverOrder": 6
}
```

Configuração para reproduzir os resultados históricos da tese: `habibiEgnFallbackEnabled=false`. O limiar de 25 GHz é **política de seleção operacional**, não limiar universal de validade do EGN aproximado. A largura é a largura física efetiva do canal e não somente o espaçamento de slots.

## 4. Experimento comparativo controlado

Parâmetros: QPSK, fibra com α=0,2 dB/km, γ=1,3 (W km)⁻¹, β₂=−21,3 ps²/km, 1 span de 100 km, potência total por canal 1 mW, espaçamento de 1,05×largura para múltiplos canais. Para 3 canais, um CUT central e interferentes simétricos. Resultados em **PSD NLI (W/Hz)**, quadratura 24 por banda e 8 frequências de recepção. "Habibi original" na tabela já inclui truncamento para zero.

| Banda efetiva (GHz) | Nº canais | Habibi antigo (W/Hz) | Habibi + ajuste EGN (W/Hz) | EGN integral 1–3 canais (W/Hz) | Híbrido − EGN (dB) |
|---:|---:|---:|---:|---:|---:|
| 12.5 | 1 | 0.000e+00 | 9.431e-18 | 9.431e-18 | +0.000 |
| 12.5 | 2 | 0.000e+00 | 1.596e-17 | 1.596e-17 | +0.000 |
| 12.5 | 3 | 6.419e-18 | 2.249e-17 | 2.972e-17 | -1.210 |
| 25 | 1 | 4.378e-18 | 3.569e-18 | 3.569e-18 | +0.000 |
| 25 | 3 | 1.692e-17 | 7.530e-18 | 7.823e-18 | -0.166 |
| 32 | 1 | 4.186e-18 | 4.186e-18 | 2.318e-18 | +2.566 |
| 32 | 3 | 1.060e-17 | 1.060e-17 | 4.550e-18 | +3.674 |
| 50 | 1 | 2.189e-18 | 2.189e-18 | 9.915e-19 | +3.440 |

**Interpretação:** em **12,5 GHz, um canal QPSK**, a formulação Habibi apresenta PSD negativa antes da correção e o SNetS histórico trunca para zero. O EGN integrado calcula cerca de **9,43×10⁻¹⁸ W/Hz**; o híbrido fornece o mesmo valor no domínio de um canal. Em **12,5 GHz e 3 canais**, EGN completo da configuração prevê cerca de **2,97×10⁻¹⁷ W/Hz**, enquanto o híbrido dá **2,25×10⁻¹⁷ W/Hz**, devido sobretudo a contribuições multi-interferente não presentes no híbrido. Para **32 GHz**, o híbrido permanece inativo quando o Habibi bruto é positivo, como exigido pela compatibilidade; o EGN numérico desse cenário de um span ainda difere. Esta diferença não invalida a tese (comparação de 50 spans em condições diferentes).

## 5. Regressões realmente executadas

- `network.HabibiEgnComparisonVerification`: **25 linhas** de comparação, incluindo 15 casos QPSK, 8 casos QAM16/QAM64 e 2 casos multispan de referência sem híbrido.
- Verificação 20 vs 24 pontos na quadratura: maior diferença relativa observada **1.73%** nos casos de um span; critério automatizado 5% aprovado. Isso **não** demonstra convergência para uma solução analiticamente exata.
- No fixture de um span, Habibi bruto teve **4 casos com resultado negativo** antes do truncamento.
- `QKDNarrowbandHabibiVerification` invoca o PhysicalLayer real: canal 12,5 GHz, Habibi legado **zero** e híbrido **9,469613×10⁻¹⁸ W/Hz**; PASS. Diferença pequena frente aos 9,431×10⁻¹⁸ W/Hz decorre de diferentes ordens de quadratura/saída.
- Regressões existentes executadas com os fontes Java modificados em classpath preferencial e classes preexistentes para as demais dependências: `QKDThesisSNetSPhysicalLayerVerification`, `QKDThesisFiguresVerification`, `QKDLongitudinalGNVerification`, `QKDBoosterIntegratedVerification`, `QKDv25SharedNoiseFactorVerification`, `CascadedMaximumPowerReferenceVerification`: **PASS**.
- Scripts Linux/Windows de verificação autônoma incluídos. `scripts/verify_habibi_egn.sh` executado com PASS.
- Launcher 1.23.0: `launcher/build.sh` compilou JAR; `node --check` e `launcher/tests/test_schema_contract.py` passaram.
- Compilação `javac` isolada dos sete arquivos de produção/verificação modificados: **PASS**, usando classes precompiladas para dependências existentes. **Compilação Maven limpa e regressão total do simulador NÃO executadas** por ausência de dependências Maven completas.

## 6. Referências

- A. Carena et al., *EGN model of non-linear fiber propagation*, Optics Express 22(13), 16335–16362, 2014. DOI **10.1364/OE.22.016335**. Modelo EGN de referência e decomposição SCI/XCI/MCI.
- P. Poggiolini e Y. Jiang, *Recent Advances in the Modeling of the Impact of Nonlinear Fiber Propagation Effects on Uncompensated Coherent Transmission Systems*, Journal of Lightwave Technology 35(3), 458–480, 2017. Especialmente Seções II–III, aproximações EGN e ressalvas a baixas taxas de símbolo.
- J. C. Lacerda Júnior, *Alocação de Recursos em Redes Ópticas Elásticas com Multiplexação por Divisão Espacial Considerando Limitações da Camada Física*, tese de doutorado, 2024, Seção 3.1.2, Eqs. 3.21–3.31 e Figuras 21–22.

## 7. Pendências científicas

A referência EGN implementada exige **extensão para N canais, bandas de larguras distintas e MCI geral** e comparação com resultados publicados/SSFM para caracterização de erro. Em experimentos dinâmicos maiores, o híbrido não substitui EGN completo: seus resultados devem ser apresentados como **aproximação híbrida**. É imprescindível compilar e testar toda a árvore de código em ambiente com todas as dependências antes de publicar resultados de rede.

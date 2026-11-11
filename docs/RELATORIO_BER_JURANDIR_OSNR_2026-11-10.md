# SNetS-SDM-QKD v1 — Correção OSNR → BER pela tese de Jurandir

**Versão de referência:** projeto completo Launcher 1.24.3, sem alterar o motor físico de ruído/OSNR e mantendo a compatibilidade dos JSONs. **Launcher atualizado:** 1.24.4.

## 1. Problema e correção

O método `PhysicalLayer.getBER(double SNR,double M)` recebia uma razão **OSNR** do motor físico, já presente nos campos de limiar `network.modulations[].SNR`. A implementação anterior tomava esse argumento como **SNR por símbolo (`Es/N0`)**, criando ambiguidade física e resultados distintos dos cálculos da tese para 8-QAM e 32-QAM.

A assinatura pública de `getBER(double, double)` foi preservada por compatibilidade, mas seu argumento se chama agora `osnrLinear` e é interpretado como razão óptica **linear**, não decibéis. A função pode receber explicitamente o número de polarizações via `getBER(double osnrLinear,double M,double p)`.

| Equação da tese | Implementação |
|---|---|
| 3.8 e 3.9 | `snrBit = 2 * osnrLinear / (p * log2(M))` |
| 3.10 | Para p=2, `snrBit = osnrLinear/log2(M)` |
| 3.4 | QAM quadrada: 4, 16, 64, ... |
| 3.6 | 8-QAM, expressão específica `11/16 erfc(...)` |
| 3.7 | 32-QAM, expressão específica `91/240 erfc(...)` |

- `PhysicalLayer.isAdmissible()` usa **OSNR** tanto para comparar o limiar configurado (modo 0) quanto para estimar BER (modo 1).
- `PhysicalLayer.getBERthreshold()` utiliza `berThreshold` quando definido; caso contrário, calcula a BER no **limiar de OSNR da própria modulação**.
- `isAdmissible2()` mantém a assinatura histórica, mas **não converte novamente** uma OSNR recebida do motor.
- `Modulation`, `NetworkConfig.ModulationConfig` e `Circuit` agora têm *getters* de OSNR explícitos, sem remover os antigos. O fluxo `Mesh` utiliza `getOSNRthresholdDb()`.
- A função `erfc()` numericamente estável foi preservada.
- O caso BPSK preserva uma extensão AWGN que não faz parte das equações 3.4/3.6/3.7 da tese; ordens ímpares sem fórmula na tese, como 128-QAM, retornam `NaN`.

## 2. Configuração do simulador e Launcher

No `physicalLayer`, por exemplo:

```json
{
  "typeOfTestQoT": 1,
  "berModel": "jurandir",
  "berThreshold": 0.001
}
```

- `typeOfTestQoT=0`: teste direto de OSNR contra `network.modulations[].SNR` (campo legado cujo conteúdo é **OSNR em dB**).
- `typeOfTestQoT=1`: estima BER **pré-FEC** a partir da OSNR linear.
- `berModel="jurandir"` (padrão se ausente): equações da tese.
- `berModel="legacyRectangularAwgn"`: restaura a fórmula legada do SNetS para comparações A/B e reprodução de campanhas históricas.
- `berThreshold` omitido: o limite BER é derivado do limiar OSNR configurado na modulação. Isso resulta praticamente na **mesma decisão** que o modo 0, pois BER é monotônica com OSNR. Para usar BER como limite independente, definir `berThreshold` explicitamente.
- O Launcher 1.24.4 disponibiliza a escolha do modelo e esclarece a natureza óptica do parâmetro `SNR` de `network.modulations`.

Os campos de intervalo `updateIntervalsMiliseconds`, `updateIntervalsMicroseconds` e `updateIntervalSeconds` e os aliases legados permanecem inalterados nesta atualização.

## 3. Margens e crosstalk

A tese adiciona **1 dB de margem operacional + 1 dB de penalidade de XT** nos exemplos numéricos de OSNR (Eq. 3.12). **Esses 2 dB não são somados automaticamente** pelo SNetS: o arquivo JSON pode já incorporar margens e, se `XTModel=1`, o crosstalk participa da razão óptica. Somar o valor novamente poderia contar duas vezes a penalidade. Os 2 dB foram incluídos apenas no teste que reproduz as Tabelas 3/4.

A razão espectral do simulador é uma OSNR/SNR óptica **efetiva**, que pode conter ASE, NLI e XT. O uso direto da relação da tese pressupõe a normalização de PSD e largura de sinal equivalente à Eq. 3.15 e `Btot=Rs`; validação independente ainda é necessária para configurações ópticas com largura de banda de referência diferente ou DSP detalhado.

## 4. Testes executados

- Compilação Java `--release 8` das oito classes relacionadas com apoio de `.class` anteriores: **PASS**.
- `ClassicalBerQotVerification`: modo legado e Jurandir, OSNR p=1/p=2, rejeição de entradas inválidas, limiares explícitos e comparações de admissão: **PASS**.
- `JurandirBerThesisVerification`: **25 pontos** de referência (20 da Tabela 2, 20 da Tabela 3 pela mesma combinação de casos, 5 da Tabela 4). Diferença menor que 0,015 dB dos valores impressos: **PASS**.
- `BerModelABComparisonVerification`: 30 combinações M × OSNR, registradas em CSV; sem pressupor igualdade: **PASS**.
- `QKDThesisSNetSPhysicalLayerVerification`: referência de um span **28,086036330 dB**, sem regressão: **PASS**.
- `QKDLongitudinalGNVerification`: **PASS**.
- `QKDv26AseFrequencyParityVerification`: 48 comparações, `maxRel=7.54e-16`: **PASS**.
- Contratos do Launcher, JS e empacotamento Java 8 (`--release 8`), versão **1.24.4**: **PASS**.

A compilação limpa de **todo o simulador** com o JAR base da sua instalação Windows e as 67+ verificações de regressão ainda estão **pendentes**. Os testes acima não provam desempenho ou equivalência para campanhas de rede não analisadas.

## 5. A/B e impacto

No cenário controlado 8-QAM, OSNR=14,1 dB, com BER máxima `1e-3`:

| Modelo | BER aproximada | Decisão |
|---|---:|---|
| Jurandir, Eq. 3.6 | 6,74189e-4 | Admitir |
| Legado retangular | 1,42552e-3 | Rejeitar |

Logo, é **esperado** que o bloqueio e a distribuição de modulações mudem nos modos BER com limiar explícito, sobretudo para 8-QAM/32-QAM. O cálculo de ruído/OSNR não foi alterado.

## 6. Aplicação no Windows 11

1. Extrair o ZIP *Somente alterações* na raiz de sua v1, substituindo os arquivos correspondentes.
2. Reconstruir o JAR do simulador com o mecanismo do Launcher já existente, por exemplo em PowerShell na pasta scripts: `./recompilar_e_retestar_qkd.bat full`.
3. Executar `./verificar_qot_ber.bat` para testar o novo cálculo, as tabelas científicas e imprimir a comparação A/B.
4. Registrar o modelo `berModel`, o `berThreshold`, os limiares ópticos por modulação e a política XT nos resultados científicos.

**Fonte:** Tese Jurandir C. Lacerda Júnior, capítulo 3, páginas impressas 47–50, Eqs. 3.4, 3.6–3.12, Tabelas 2–4.

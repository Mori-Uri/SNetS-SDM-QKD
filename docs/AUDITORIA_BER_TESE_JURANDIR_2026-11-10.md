# SNetS v1 / Launcher 1.24.3 — Correção de milissegundos e auditoria da BER da tese de Jurandir

Data: 10/11/2026. Fonte primária: **Tese de Jurandir Lacerda Júnior**, seção **3, Modelagem da Camada Física**, páginas impressas 47–50 (páginas PDF 48–51). O diagnóstico se refere ao código-fonte recebido como SNetS v1 / Launcher 1.24.2.

## 1. Campo na interface

- `simulation.qkdKeyDemand.updateIntervalsMiliseconds` (nome solicitado em 09–10/11): **visível e editável** no formulário, com unidade `ms`.
- `simulation.qkdKeyDemand.updateIntervalsMileseconds`: grafia obsoleta; **não aparece** no Launcher e segue rejeitada explicitamente pelo parser/validador (não se removeu a proteção de dados).
- `simulation.qkdKeyDemand.updateIntervalsMilliseconds`: alias em inglês correto; continua aceito pelo backend e editável se um JSON existente contiver somente ele, **sem criar um segundo campo de milissegundos**. Na lista de inclusão de novos campos, aparece apenas a variante `Miliseconds`.
- `updateIntervalsMicroseconds` (µs) e `updateIntervalSeconds` (s) continuam sem alteração.
- O backend e as regras de conversão de unidades não sofreram modificação nesta atualização. Não combinar campos de unidade diferentes no mesmo objeto.

## 2. Equações da tese

Para M-QAM quadrada (M = 2^(2n)), a Eq. 3.4 relaciona BER e **SNR por bit** linear (snr_b):

`BER = [2/log2(M)] [(sqrt(M)-1)/sqrt(M)] erfc(sqrt(3 log2(M) snr_b / [2(M-1)]))`.

O caso 8-QAM usa a Eq. 3.6:

`BER_8 = (11/16) erfc(sqrt(3 snr_b/(3+sqrt(3))))`.

O caso 32-QAM usa a Eq. 3.7:

`BER_32 = (91/240) erfc(sqrt(snr_b/4))`.

A tese inclui ainda as equações 3.8–3.11 para obter limiares de OSNR a partir da SNR por bit, considerando a taxa de símbolos e duas polarizações, e a Eq. 3.12 para margens de OSNR. A tese usa 1 dB de margem de operação e 1 dB de penalidade relacionada ao crosstalk para suas Tabelas 3/4 e, na Tabela 4, BER de referência 1,3e-2. A Eq. 3.15 modela a OSNR via PSD de sinal/ASE/NLI; XT é também verificado por limite independente (Eq. 3.13–3.14).

## 3. Código que foi auditado

`src/main/java/network/PhysicalLayer.java`:

- `getBER(double SNR, double M)` (linhas ~1810–1826 na base 1.24.2) usa aproximação AWGN para M-QAM retangular com argumento definido como **Es/N0**.
- Para M = 4, 16 e 64, ao adotar `snr_b = (Es/N0)/log2(M)`, o cálculo coincide com a Eq. 3.4 da tese.
- Para M = 8 e 32, usa retângulos 2×4 e 4×8, respectivamente, **não** as fórmulas específicas 3.6 e 3.7; os limiares BER são diferentes.
- `getBERthreshold(Modulation)` retorna BER derivada do limiar SNR da própria modulação quando não se informa `berThreshold`. Por monotonicidade da função, os modos 0=SNR e 1=BER tendem a ser apenas duas parametrizações do mesmo teste nessa configuração.
- `isAdmissible2` inclui fator de conversão OSNR referência de 12,5 GHz para SNR, mas sob a convenção **Es/N0** declarada em `getBER`, a relação OSNR-SNR por símbolo precisa considerar `log2(M)`. Na implementação atual, a conversão é idêntica à fórmula de SNR por bit e está restrita a um método auxiliar que **não aparece invocado no caminho de produção** (apenas no verificador e em chamadas comentadas). Não tratar a correção de 1.24.2 como validação completa das unidades.
- `computeSNRSegment` calcula uma razão de potências/PSDs que pode incluir ASE, NLI e XT; a tese trata OSNR e limite XT em condições específicas. Antes de implementar a Eq. 3.12, decidir se a penalidade por XT seria redundante com o XT já integrado na SNR.
- A função `erfc` está numericamente estável no regime testado, mas isso por si só não valida a física das constelações.

## 4. Comparação numérica — BER 1e-3

SNR por bit (dB), **mesma métrica de entrada** para ambos os modelos:

| Modulação | Tese: equações 3.4/3.6/3.7 | SNetS 1.24.2 | diferença SNetS - tese |
|--|--:|--:|--:|
| 4-QAM | 6,790 | 6,790 | 0,000 |
| 8-QAM | 9,027 | 9,645 | +0,618 |
| 16-QAM | 10,522 | 10,522 | 0,000 |
| 32-QAM | 12,573 | 13,575 | +1,002 |
| 64-QAM | 14,767 | 14,767 | 0,000 |

Os valores da tese reproduzem os limites da Tabela 2, p. 48: 6,79; 9,03; 10,52; 12,57; 14,77 dB. Os limiares são obtidos resolvendo `BER(snr_b) = 1e-3` por bisseção. Os números do SNetS foram obtidos aplicando a expressão atual de `PhysicalLayer.getBER` com `SNR = log2(M)*snr_b`.

## 5. Recomendação científica e decisão desta atualização

1. Preservar o modelo `legacyRectangularAwgn` como padrão para reproduzir campanhas antigas.
2. **Propor**, em uma atualização futura com validação, o modelo opcional `jurandirIp2008`: Eq. 3.4 para 4/16/64-QAM, Eq. 3.6 para 8-QAM, Eq. 3.7 para 32-QAM.
3. Normalizar explicitamente `SNR_symbol`, `SNR_bit`, `OSNR` com as unidades e as hipóteses de polarização, FEC e referência de ruído; testar a equivalência entre a Eq. 3.8, a Eq. 3.10 e o modelo de PSD para os formatos suportados.
4. Dar ao modelo um `berThreshold` explícito (por exemplo 1,3e-2 para replicar a Tabela 4), e comparar a decisão em A/B com limiares SNR/OSNR e XT. Não aplicar automaticamente 2 dB de margem quando o limiar de modulação já os contém.
5. Validar numericamente as Tabelas 2, 3 e 4 e executar uma campanha real com os dois modelos. Registrar os resultados de BER, bloqueio, formato de modulação e QoT.

**Nesta alteração não foram substituídas as equações de BER no motor físico:** o usuário perguntou sobre a correção e sobre a conveniência de usar a tese. O resultado é uma auditoria e a restauração do campo de milissegundos no Launcher, sem alterar silenciosamente as campanhas anteriores.

## 6. Verificações realizadas

- Testes de interface `launcher/tests/test_launcher_qot_ui.js` PASS: Miliseconds visível, alias sem duplicidade e JSON preservado.
- Testes de esquema `launcher/tests/test_schema_contract.py` PASS.
- `node --check` dos arquivos JavaScript do Launcher PASS.
- `launcher/build.sh` PASS em JDK disponível, `snets-launcher.jar` bytecode Java 8 (major 52), versão 1.24.3.
- Auditoria numérica independente: Eq. 3.4, 3.6 e 3.7 reproduzem a Tabela 2 com erro absoluto máximo inferior a 0,005 dB nos 20 valores de limiar.
- Não houve recompilação total do SNetS nem regressão de QKD nesta etapa; o código Java de BER e o parser de intervalos não foram modificados.

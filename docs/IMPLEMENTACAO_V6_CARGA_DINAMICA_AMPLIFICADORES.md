# SNetS-SDM-QKD v6 — carga óptica dinâmica nos amplificadores

## Implementação

O cálculo longitudinal QKD passa a utilizar **instantâneos locais de carga por enlace dirigido e núcleo clássico**. Cada instantâneo contabiliza DChs estabelecidos, PIChs registrados e o circuito candidato, se estiver no enlace/núcleo. As fontes PICh têm posições explícitas de inserção e retirada. Nenhuma fonte de um enlace reverso é incluída.

Para cada amplificador em z_i = i * amplifierSpacingKm, são calculadas a potência agregada na **entrada** (antes do ganho), a compressão de ganho e a potência resultante ao longo da cadeia. O cálculo de Raman, GN-NLI e ASE da QKD passa a consultar os mesmos ganhos desse instantâneo. A configuração global `ClassicalPowerEvolution` não é modificada durante o cálculo. Fontes em núcleos adjacentes, usadas pelo Raman intercore, recebem instantâneos próprios.

O seletor existente `multiband.amplifierModel` continua aceitando `ideal` e `saturated`; não há uma nova opção obrigatória. Se o modelo é ideal, o ganho independe da potência. Se é saturado, a carga real substitui a antiga potência de referência durante avaliações físicas da QKD.

A equação adotada, como **aproximação fenomenológica**, é G_i = max(1, G_0 / (1 + G_0 * P_entrada,i / P_sat)). Não deve ser confundida com a curva completa de um EDFA comercial; exige calibração do parâmetro de saturação e do ganho não saturado. Na implementação, o amplificador é indexado globalmente ao longo do enlace dirigido, e um PICh inserido na posição exata de um amplificador é inserido após esse equipamento.

A ASE gerada em cada EDFA é propagada com a perda clássica e os ganhos **dos EDFAs posteriores** antes da aplicação do fator empírico de transferência para o detector QKD. Não se pressupõe que o QCh seja amplificado. O fator `amplifierAseLeakageDb` permanece indispensável e o vazamento está desligado por padrão.

## Limitações que permanecem

- O cálculo de QoT clássico do SNetS e o cálculo longitudinal QKD ainda não compartilham um único estado de ganho por amplificador. A carga real por núcleo é usada para **Raman/GN/ASE na camada QKD**; a modelagem clássica `network.Amplifier` mantém seus métodos existentes.
- Supõe-se uma cadeia óptica independente por núcleo e um coeficiente de atenuação de referência para calcular a carga agregada. Não há modelo de bombeio compartilhado de amplificadores multicore, tilt espectral, saturação dependente do comprimento de onda, ISRS ou ASE realimentada no próprio termo de saturação.
- O modelo GN continua aproximado, com soma incoerente de seções, e não equivale ao GGN completo.
- A potência ASE na banda QKD ainda depende de um coeficiente de vazamento empírico; a emissão de EDFAs na banda quântica não é presumida.
- Não foi realizada compilação Maven integral com todas as dependências externas nem regressão completa de tráfego dinâmico.

## Verificação

Compilação incremental executada com Java 21 (`javac --release 8 -encoding ISO-8859-1`), classes de suporte compiladas da v1 e fonte atualizada da v6. As verificações `QKDDynamicAmplifierVerification`, `QKDAmplifierLongitudinalVerification`, `QKDPhysicalVerification`, `QKDCrosstalkPowerVerification` e `QKDReproducibilityVerification` passaram. As verificações existentes que não fazem uso do novo modelo não substituem simulações fim-a-fim.

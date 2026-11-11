# v8 - Contrato de estado compartilhado de amplificador (incremental)

## Arquivos
- `qkd/multiband/AmplifierOperatingPoint.java` (novo): estado imutavel (posicao, frequencia, potencia total de entrada, ganho e PSD de ASE).
- `qkd/multiband/ClassicalPowerEvolution.java`: extracao de estado operacional por amplificador a partir do snapshot longitudinal QKD; valida geometria e a frequencia real das fontes multibanda.
- `network/Amplifier.java`: APIs explicitas para consumir exatamente o ganho/ASE do estado comum, evitando recalcular com potencia media.
- `qkd/verification/QKDAmplifierOperatingPointVerification.java`: teste integrado deterministico de estado, bandas C/L e restricoes fisicas.

## Contrato de unidades
- Coordenada: km a partir da origem do enlace dirigido.
- Potencia total de entrada: W (todos os canais classicos presentes no mesmo nucleo).
- Ganho: razao linear.
- ASE: W/Hz (convencao de duas polarizacoes da lei compartilhada).
- QCh: nunca e amplificado por este EDFA.

## Uso
O modelo de propagacao e construido via `withDynamicLoad(...)` antes da avaliacao. Em seguida
`operatingPointAt(i, alpha, frequenciaHz, h)` fornece o estado unico para o i-esimo equipamento.
A camada classica pode consumir esse mesmo objeto por `Amplifier.getGainFromOperatingPoint`
e `Amplifier.getAseFromOperatingPoint` sem recomputar ganho ou ASE de outra carga.

## Validacao executada
- Compilacao incremental com javac, alvo Java 8: OK.
- QKDAmplifierOperatingPointVerification: PASS.
- QKDUnifiedMultibandVerification (regressao v7): PASS.
- As classes previamente compiladas do projeto serviram de dependencias. Nao foi feita compilacao Maven limpa ou regressao completa de cenarios MF/MB/MB-SDM.

## Limites e proximos passos
**Ainda nao existe a ligacao automatica de `network.PhysicalLayer.computeSNRSegment` ao mesmo
snapshot longitudinal**: este incremento formaliza o contrato e sua utilizacao pela classe
Amplifier, mas nao substitui os calculos QoT tradicionais do SNetS. Integrar isso exige
compatibilizar a colocacao de booster/line/preamp, polarisacoes, numero de spans e ordem
de propagacao do sinal/ASE com os snapshots QKD e o gerenciamento de circuitos candidatos.
O EDFA multibanda continua um modelo empirico de saturacao por potencia compartilhada,
nao um modelo completo de equacoes de taxa de ions de erbio ou de bombeamento.
A verificacao e numerica/interna, nao uma validacao experimental contra dados publicados.
Nao ha canais contrapropagantes na mesma fibra.

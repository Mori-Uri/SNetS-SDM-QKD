# SNetS-SDM-QKD v5 — GN longitudinal, ASE por amplificador e saturação

## Alterações

* A versão de rede utiliza somente **copropagação** em cada fibra dirigida. Não existe a opção `multiband.bidirectionalFiber`: a versão atual eliminou as fórmulas e APIs de Raman contrapropagante, e o calculador aceita apenas canais copropagantes por fibra dirigida.
* `multiband.longitudinalNliEnabled=true` habilita o GN espectral em **seções longitudinais**, com atenuação e ganho dos amplificadores de canais clássicos, perda quântica até o receptor e posições de inserção e retirada dos PIChs. As contribuições de seções diferentes são somadas **incoerentemente**, não modelando correlação de fase entre seções. O modelo GN é aplicado **apenas à presença dos canais no mesmo núcleo do QCh**; não é um modelo Kerr intercore completo.
* `multiband.amplifierModel` permite `ideal` (ganho fixo) ou `saturated` (ganho comprimido por potência total de referência). A seleção `physicalLayer.typeOfAmplifierGain=0|1` já existia para os amplificadores **clássicos** do SNetS e foi mantida. Na classe `network.Amplifier`, o ganho saturado passa a ter redução contínua em função da potência; o ASE saturado é proporcional a `G-1`, não a `G`.
* `multiband.amplifierAseToQchEnabled=true` computa, amplificador por amplificador, um **termo de ASE equivalente na banda quântica**. É obrigatório calibrar `amplifierAseLeakageDb`: a fonte de ASE é na banda do amplificador clássico, e **não** se presume emissão do EDFA em 1381 nm, por exemplo. A transferência/filtragem para o QCh fica explicitamente representada pelo fator de vazamento. Por padrão, ela está desligada.

## Exemplo — objeto `multiband` em `qkdPhysicalLayer.json`

Adicione as seguintes chaves **dentro do objeto já existente**, sem apagar as demais:

```json
{
  "longitudinalRamanEnabled": true,
  "longitudinalNliEnabled": true,
  "nliModel": "gn",
  "amplifierSpacingKm": 80.0,
  "amplifierGainDb": 16.0,
  "amplifierModel": "saturated",
  "amplifierSaturationPowerDbm": 16.0,
  "amplifierReferenceLaunchPowerDbm": 0.0,
  "amplifierNoiseFigureDb": 5.0,
  "amplifierAseToQchEnabled": false,
  "amplifierAseLeakageDb": 100.0
}
```

Os valores são meramente ilustrativos e não representam calibração física. Para selecionar amplificação ideal, use `"amplifierModel": "ideal"` no `qkdPhysicalLayer.json` e `"typeOfAmplifierGain": 0` no arquivo de camada física clássica correspondente; para saturação, use `"saturated"` e `1`, respectivamente. A seleção é explícita em cada camada e deve ser **consistente no experimento**.

## Hipóteses e limitações

1. O GN longitudinal utiliza a soma **incoerente** dos segmentos separados pelos amplificadores, pelas posições dos PIChs e por uma malha de no máximo 20 km. Não reproduz automaticamente um modelo GGN coerente com toda a acumulação por fase.
2. O modelo de saturação multibanda usa `amplifierReferenceLaunchPowerDbm` como **potência agregada de referência** na entrada da cadeia. Ainda não recalcula esse agregado dinamicamente a partir da potência e do comprimento de cada canal DCh/PICh em todos os núcleos. A camada clássica usa o `totalPin` dos próprios métodos existentes. Esta discrepância requer calibração ou integração adicional antes de resultados de precisão.
3. ASE gerada em amplificadores anteriores, sua amplificação subsequente na banda clássica, dependência espectral da figura de ruído, resposta do filtro quântico e ganho de espectro não são resolvidos detalhadamente; cada contribuição equivalente é propagada passivamente ao QCh. Em O-band, o vazamento efetivo pode ser praticamente nulo.
4. A QCh **não é amplificada** pelos EDFAs da cadeia clássica. `residualAseReceiverW` do orçamento óptico continua sendo um valor independente e pode **duplicar** o ASE por amplificador se ambos representarem o mesmo ruído; desabilite ou recalibre esse valor ao ativar o novo modelo.
5. Não há alteração na representação conservadora de ocupação espectral por QKDSpan nesta entrega.
6. O custo do GN longitudinal aumenta com o número de seções; para redes grandes, recomenda-se desabilitá-lo em experimentos rápidos e habilitá-lo em calibrações controladas.

## Validação incremental

Compilação incremental Java 8 via `javac` usando classes pré-compiladas da versão anterior. Executar `qkd.verification.QKDAmplifierLongitudinalVerification` para verificar ganho comprimido, ASE, preservação do GN passivo e início do PICh. Execução Maven limpa e regressão completa dos cenários ainda são necessárias antes de uso científico quantitativo.

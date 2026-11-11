# SNetS-SDM-QKD v23 — integração seletiva da v2 e correções de perdas

## Escopo aplicado

Base: SNetS-SDM-QKD-v22. Comparação: SNetS-SDM-QKD-v2.zip. Foram priorizadas as mudanças pedidas no cálculo físico; não houve substituição indiscriminada de classes da linha v22 por classes da v2.

1. `computePowerThatMaximizesOSNR2()` removido de `network.PhysicalLayer`. A API principal é `computePowerThatMaximizesOSNR()`; o teste que chamava a API removida foi atualizado. Quem consumir o método `...2()` externamente precisa migrar a chamada.
2. O componente óptico SWITCH/OXC deixa de ter perda de inserção no cálculo de QoT. `muxInsertionLoss` e `demuxInsertionLoss` são configurados em dB independentemente (ambos opcionais). Configurações antigas com somente `switchInsertionLoss` são aceitas: o valor legado é interpretado SOMENTE como perda de MUX e DEMUX, jamais switch. Booster nominal compensa MUX+DEMUX. Para migrar, prefira preencher os dois campos novos e não depender do campo legado.
3. A cascata clássica e a longitudinal clássica agora incluem o mesmo par MUX/DEMUX por enlace, inclusive entre dois saltos. No longitudinal, o estado de carga de DCh é referenciado ao ponto após o MUX. QKDPhysicalLayer também aplica a transmissão do MUX às potências de DChs quando utiliza o MESMO template de amplificadores, mantendo os PIChs como fontes injetadas em seus QKDSpans.
4. `computePowerThatMaximizesOSNR()` utiliza a estimativa cúbica de Vale/Almeida para o modo clássico e uma busca limitada sobre a SNR efetivamente longitudinal quando o modo compartilhado está ativo. A busca considera saturação e fontes em operação e restaura a potência original do circuito. `checkMaximumPower` também restaura a potência temporariamente testada.
5. Da linha v2 foi incorporado `network.ClassicalMultiBand` como modo CLASSICO opcional no bloco `physicalLayer.multiBand`: atenuação por banda (G.652.D ou constante), NF por banda, variação de dispersão e gamma com frequência e redistribuição ISRS aproximada. A cascata clássica foi adaptada para esse contexto físico sem alterar os campos compartilhados de `PhysicalLayer`. Sem a configuração, a execução segue o modo clássico original da v22 com a correção de MUX/DEMUX.
6. O cache seguro de coeficientes integrais de Johannisson foi adicionado a `GnModelMath`: limitado a 8192 entradas LRU, indexado exclusivamente por parâmetros numéricos físicos, sem armazenar QoT dependente de ocupação. Seleção SCI e XCI por flags v21 preservada, assim como `GnModelMath.nonnegative()`.

## Configuração recomendada

```json
{
  "muxInsertionLoss": 5.0,
  "demuxInsertionLoss": 5.0,
  "johannissonSciUseIntegral": false,
  "johannissonXciUseIntegral": false,
  "multiBand": {
    "enabled": false,
    "attenuationModel": "g652d",
    "noiseFigureDb": {"S": 7.0, "C": 5.0, "L": 6.0},
    "dispersionSlopePsNm2Km": 0.057,
    "gammaScalesWithFrequency": true,
    "isrs": true,
    "ramanGainSlopePerWKmTHz": 0.028
  }
}
```

São campos a **incorporar ao JSON existente**, não substituem os demais parâmetros do `physicalLayer`. `multiBand.enabled=false` significa DESATIVADO. Ao habilitá-lo o modelo deve ser utilizado somente em cenários clássicos sem o QoT longitudinal QKD. A ativação simultânea com `enableLongitudinalAmplifierQoT()` é rejeitada explicitamente para evitar a combinação de duas leis de propagação incompatíveis.

## Quais canais usam o longitudinal?

- **DCh:** a função pública `computeSNRSegment()` seleciona o caminho `computeSNRSegmentLongitudinal()` quando o template longitudinal está habilitado; caso contrário, usa `propagateLegacyCascade()`.
- **PICh:** `evaluatePich()` calcula o canal óptico auxiliar no interior do enlace; não é um `DCh` de rota completa. Fontes PICh permanecem em seu próprio QKDSpan, incluindo contribuições de NLI e crosstalk.
- **QCh:** o quantum channel NÃO é amplificado por EDFA clássico. É propagado pelo avaliador QKD, que aproveita o estado dos amplificadores e suas fontes clássicas como causadores de ruído (Raman e outros), mas não aplica a cascata clássica ao QCh.

Não foi substituído o longitudinal pelo método legado, pois isso descartaria trusted nodes internos e evolução física local. O que foi unificado são as convenções de fronteira MUX/DEMUX e o modelo de potência de DCh alimentando os EDFAs.

## Resultados e limites

- Compilação incremental Java 8 aprovada, com substitutos locais SOMENTE para tipos de Gson/OpenCSV exigidos por classes de suporte. Esses substitutos NÃO estão incluídos no pacote. O Maven e os binários reais das bibliotecas não estão disponíveis neste ambiente.
- 16 verificadores Java aprovados no conjunto atual. `QKDv23LossAndBandVerification` cobre equilíbrio óptico ideal (1 e 2 saltos, perdas 5/5 dB e 1/3 dB), resposta física da NF e atenuação por banda, redistribuição ISRS positiva, determinismo do cache de integrais e restauração de potência na busca longitudinal.
- `QKDCascadedOsnrVerification` foi adaptado à remoção da API duplicada e verifica determinismo do estimador, SNR, saturação, modelos GN e ausência de mutação após a busca clássica.
- A igualdade entre sinal de entrada/saída vale SOMENTE nos cenários de compensação ideal. Saturação, MUX/DEMUX não compensados e perfil espectral podem alterar a transferência líquida.
- A implementação ISRS da v2 é uma aproximação de transferência e ASE; **NÃO** é o modelo ISRS-GN completo. Não utilizar essa aproximação como prova de validação GN/ISRS multibanda.
- O `QotLinkCache` da v2, com invalidação baseada em versões do enlace, NÃO foi transplantado, pois as alterações de PICh, subspans, potência longitudinal e saturação da v22 não são representadas integralmente por suas chaves. Foi incorporado somente o cache puro dos coeficientes GN, seguro contra resultados QoT desatualizados.
- Controles de retomada e progresso da v2 NÃO foram transplantados, pois exigem revisão integrada dos formatos de save state e da jornada de simulação do ramo v22.
- Faltam `mvn clean test` com dependências reais e regressão dinâmica completa das arquiteturas MF/MB/MB-SDM antes de concluir equivalência para publicação científica.

## Arquivos alterados

Consulte `MANIFESTO_V23.json` e o diff no pacote de entrega. O código atualizado mantém a organização existente e evita novos caminhos concorrentes de NLI/XT.

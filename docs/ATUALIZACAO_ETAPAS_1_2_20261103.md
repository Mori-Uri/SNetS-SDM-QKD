# Atualizacao incremental: etapas 1 e 2 (2026-11-03)

## Natureza da entrega

Este pacote e uma **implementacao incremental** das etapas 1 e 2 solicitadas, feita diretamente sobre os fontes enviados. NAO representa a conclusao integral da consolidacao fisica; os itens nao implementados estao explicitados abaixo. Os padroes de configuracao sao retrocompativeis.

## Etapa 1: PICh e qualidade de transmissao classica

- `qkd.capacity.PichClassicalCrosstalk` calcula a densidade espectral de potencia de inter-core XT causada por PIChs na vitima DCh, utilizando a topologia real de adjacencia, intersecao dos slots e comprimento longitudinal de cada QKDSpan. O calculo e de primeira ordem; nao pretende substituir um modelo acoplado de propagacao ou FWM intercore.
- `network.PhysicalLayer.computeSNRSegment` e `computeSNRSegment2` incorporam a PSD de PICh no termo `Ixt`, **apenas** com XT ativo e `XT_TOGETHER`. A contribuicao classica previa de DCh nao e alterada.
- `QKDControlPlane` reavalia QoTN do DCh candidato e QoTO dos DChs ativos afetados pelos PIChs provisorios, antes do commit. Em caso de insucesso, libera a sessao provisoria, inclusive seus PIChs, pelo fluxo ja existente. Respeita as flags QoTN/QoTO da camada classica.
- A politica `network.qkd.pichCorePolicy = "fixed"` passa a ser obrigatoria: **nao** executa fallback para outro nucleo. Quando esse nucleo esta cheio, a requisicao de PICh falha.

## Etapa 2: perdas de dispositivos e ASE residual

O bloco opcional `opticalBudget` em `qkdPhysicalLayer.json` permite adicionar perdas passivas a transmissao do QCh e uma fonte de ASE residual medida/calibrada *na entrada optica do receptor*, depois de filtragem optica relevante:

```json
{
  "opticalBudget": {
    "txMuxLossDb": 0.5,
    "rxDemuxLossDb": 0.8,
    "bypassLossDb": 0.2,
    "residualAseReceiverW": 1e-15
  }
}
```

Este trecho deve ser colocado **dentro do objeto existente** `qkdPhysicalLayer`, sem substituir `source`, `detector`, `multiband`, etc. Os valores sao **apenas ilustrativos**; nao sao valores validados de equipamentos. Ausencia de bloco = todos os valores zero.

- Perda efetiva de sinal por QKDSpan = soma das perdas acima, aplicada somente ao `eta` do modelo BB84 na avaliacao multibanda, sem alterar o coeficiente da atenuacao da fibra.
- `residualAseReceiverW` e convertido de W para probabilidade de deteccao por janela, com `eta_d`, `receiverOpticalEfficiency`, energia do foton e largura temporal de gate ja configuradas. O evento ASE e combinado com os demais eventos independentes.
- Nao definir simultaneamente a mesma perda passiva em `receiverOpticalEfficiency` e em `opticalBudget`, para evitar duplicacao.
- A perda `bypassLossDb` e uma perda **agregada por QKDSpan**: nao e uma contagem automatica de bypasses por amplificador.

## Verificacao executada

- Compilacao incremental dos **12** arquivos Java alterados ou adicionados usando `javac -encoding ISO-8859-1` com `target/classes` do projeto original como classpath.
- `QKDStages12Verification`: PASS (compatibilidade de 0 dB; perda reduz SKR; ASE reduz SKR; validacao dos parametros; PICh intercore/sobreposicao/nucleos nao adjacentes/remocao).
- `QKDPhysicalVerification`: ALL CHECKS PASSED.
- `MultiBandPhysicsVerification`: ALL CHECKS PASSED, com 1 pendencia anteriormente apontada pela propria verificacao.

**ATENCAO:** o `target/classes` original e um conjunto de compilados preexistentes. Nao foi executado `mvn clean verify` (Maven e dependencias externas indisponiveis). Essa validacao incremental NAO substitui compilacao limpa, suite de regressao e repeticao de experimentos.

## Itens ainda pendentes das etapas 1 e 2

1. Integracao PICh em NLI/GN de DCh, saturacao de amplificadores e QoT classica com `XT_SEPARATE`.
2. Gerenciamento espectral por intervalo longitudinal para permitir reutilizacao de slots PICh em QKDSpans distintos do mesmo enlace (quando os componentes fisicos permitirem).
3. Posicionamento real dos amplificadores, ganhos/perdas por trecho, potencia de DCh/PICh ao longo da fibra e respectivas integrais de Raman/NLI.
4. Especificacao de filtros e leakage dependente da frequencia; ASE espectral e ganho, sem dupla contagem.
5. Validacao cruzada das contribuicoes de Raman/XT/FWM e normas de potencias com artigos e casos de laboratorio.
6. Tests end-to-end QoTO/rollback com varias sessoes, incluindo recriacao de estado apos excecoes e as configuracoes `XT_SEPARATE`.

## Passos de instalacao e validacao (Windows 11)

1. Copie os caminhos de `src/main/java` do ZIP de modificacoes para o mesmo local do seu projeto. Faca backup ou utilize um branch Git separado.
2. Execute `mvn clean test` em ambiente com Maven, JDK e dependencias configuradas; em seguida rode `java -cp target/classes qkd.verification.QKDStages12Verification` (se necessario, inclua as dependencias do projeto no classpath).
3. Compare simulacoes com `opticalBudget` ausente contra a versao anterior; em seguida habilite os parametros calibrados gradualmente.
4. Teste PICh QoTO com `activeXT=true`, `XT_TOGETHER`, `activeQoTForOther=true` e circuitos DCh ativos em nucleos adjacentes.

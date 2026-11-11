# Continuacao das etapas 1 e 2 - integracao fisica PICh e Raman longitudinal

## Arquivos e funcionalidades

- `network/Crosstalk.java`: adiciona a potencia de XT dos PIChs auxiliares ao calculo de XT linear usado pela politica `XT_SEPARATE`. A conversao para dB ocorre apenas ao final. Usa o espectro do DCh por enlace e a potencia de lancamento da vitima.
- `network/PhysicalLayer.java`: GN XCI produzido por PICh no **mesmo nucleo** do DCh nos modelos Johannisson e Habibi; a politica de potencia de amplificadores saturados recebe uma estimativa de potencia PICh media por enlace. A integracao existente `XT_TOGETHER` continua preservada.
- `qkd/capacity/PichClassicalNonlinearity.java`: calculo de XCI dos PIChs considerando potencia, frequencia, banda e fracao longitudinal de ocupacao do enlace. O termo Habibi nao inventa parametros de modulacao para PICh.
- `qkd/multiband/ClassicalPowerEvolution.java`: perfil unidirecional de potencia de bombas classicas com atenuacao e amplificadores ideais posicionados periodicamente. **O QCh nao recebe ganho.**
- `qkd/physical/QKDPhysicalLayer.java`: substitui a integral analitica de Raman **direto** pela integral longitudinal quando a opcao esta habilitada, usando a origem absoluta do DCh (inicio do enlace fisico) ou do PICh (inicio do QKDSpan). O modo padrao permanece identico.
- `qkd/multiband/MultiBandSettings.java` e `qkd/physical/QKDPhysicalLayerConfig.java`: configuracao opt-in do modelo longitudinal.
- `qkd/verification/QKDStages12ContinuationVerification.java`: verificacoes sem dependencias externas, incluindo limite analitico do Raman passivo, aumento por ganho, coordenadas dos nos confiaveis, GN PICh e remocao de fontes.

## Ativar o Raman longitudinal

Dentro do bloco ja existente `multiband` do arquivo `qkdPhysicalLayer.json`, adicione:

```json
{
  "longitudinalRamanEnabled": true,
  "amplifierSpacingKm": 80.0,
  "amplifierGainDb": 16.0
}
```

Esse objeto e apenas um **trecho** do bloco `multiband`, nao um arquivo JSON completo. Se `longitudinalRamanEnabled` estiver ausente ou `false`, os modelos anteriores de Raman sao preservados. `amplifierSpacingKm` refere-se a posicoes absolutas 80, 160, 240... km a partir da origem do enlace fisico *direcionado*. `amplifierGainDb` corresponde ao ganho aplicado aos DChs no modelo longitudinal de Raman. Nunca aplique esse ganho aos QChs.

Os parametros sao hipoteses experimentais, nao valores validados dos equipamentos. Recomendamos comecar com dados mensurados do sistema alvo e repetir as simulacoes com e sem a opcao.

## Limites fisicos conhecidos / NAO concluido

1. `PichClassicalNonlinearity` usa **fracao de comprimento** do enlace para aproximar XCI e potencia para saturacao do EDFA. Ainda nao calcula a interacao nao linear secao a secao, nem a evolucao de amplificador para cada PICh. O modelo Habibi de PICh ainda nao possui uma correcao de formato de modulacao calibrada.
2. O perfil longitudinal novo afeta **somente Raman copropagante (forward)** quando existe `QKDSpan`; Raman contrapropagante continua com a integral analitica anterior. O GN-NLI quantico nao foi recalibrado para ganhos longitudinais.
3. Os amplificadores no perfil longitudinal sao ideais, de ganho fixo, sem NF, ASE por estagio, saturacao, ISRS, tilt e filtragem. ASE residual continua ajustavel no orcamento optico da etapa anterior; nao some ASE duas vezes.
4. `XT_SEPARATE` inclui a contribuicao PICh na verificacao do XT da vitima, mas a politica deve ainda ser validada em cenarios de roteamento e controle de admissao ponta a ponta.
5. Nao foi implementado reaproveitamento de slots entre `QKDSpans`, nem mapa fisico de banda por trechos de fibra. O estado das reservas espectrais mantem a politica conservadora herdada.
6. Faltam compilacao Maven limpa, execucao de cenarios completos e calibracao cruzada em multiplos pontos experimentais para validar resultados publicaveis.

## Verificacao incremental executada

Compilacao com `javac` (Java 8 source/target) sobre classes compiladas previamente do projeto v1 e classes da etapa anterior, seguida da execucao das verificacoes de continuacao e de parte das verificacoes QKD existentes. Isso **nao** substitui `mvn clean test` sobre dependencias oficiais.

## Recomendacoes de pesquisa

Antes de inferir ganho de SKR ou bloqueio devido ao Raman longitudinal, estabelecer explicitamente a origem das potencias classicas dos enlaces, a posicao e ganho dos EDFAs, os pontos de insercao/retirada dos PIChs e as direcoes de propagacao. Comparar com o modelo passivo anterior e com o limite analitico no caso sem ganho.

# SNetS-SDM-QKD — Relatório de implementação e verificação (08/11/2026)

## Escopo e honestidade da validação

Esta entrega modifica o **código-fonte Java** recebido em `SNetS-SDM-qkd-v1(2).zip`; preserva o restante da árvore original sem incluir binários antigos de `target/` no pacote final. Os testes executados aqui **não constituem um build Maven limpo**: `mvn` não está instalado e dependências externas da distribuição (notadamente Gson) não estão disponíveis neste ambiente. Compilamos as 17 fontes Java modificadas/adicionadas com `javac` Java 8, usando as classes antigas como fallback somente para dependências inalteradas. Esse procedimento detecta erros locais de compilação, mas **não garante** a consistência de todas as fontes na compilação limpa. Executar `scripts/ci_qkd_clean.bat` em Windows ou `.sh` em Linux com Maven e dependências resolvidas é critério obrigatório antes de resultados publicáveis.

A investigação de modelos físicos considera Moghaddam (2021), Dibaj (2024), Mehdizadeh (2024), Xie (2024) e Gao (2026) discutidos anteriormente, mais Lacerda Júnior (2024) e Vale & Almeida Jr. (2019). Os dois últimos dão a base para cotejamento das limitações clássicas; a equivalência experimental QKD/MCF não foi demonstrada nesta entrega.

## Matriz dos requisitos solicitados

| Item | Intervenção | Status e limite |
|---|---|---|
| P0 compilação limpa e regressão | Scripts de `clean package`, classpath resolvido e execução individual de 63 `*Verification` | **Implementação do procedimento feita; execução integral pendente** por Maven/dependências ausentes. Não alegar aprovação total. |
| P0 cenários separados | Três diretórios autocontidos Moghaddam, Dibaj e MCF7 da qualificação; auditor Python | **Arquivos criados e estruturalmente validados**, mas Moghaddam/Dibaj são cenários *inspirados*, não reprodução exata do conjunto de dados/artigo. |
| P1 PICh→QCh crosstalk | `QKDPhysicalLayer.collectInterferers()` inclui `getAuxiliaryClassicalCircuitList()`, deduplica e respeita link | **Implementado**, teste unitário específico passa. |
| P1 QBER independente | `network.qkd.maximumQber` (padrão 0,11); integrado a provisionamento, recursos compartilhados e proteção dos ativos | **Implementado**; valor deve ser justificado pelo protocolo e hipóteses. |
| P1 PICh QoT | `pichSnrCheckEnabled=true` por padrão, modulação/SNR/XT reais do motor clássico, inclui DCh ativo e candidato | **Implementado**. `pichRxPowerCheckEnabled` segue `false`: limiar de sensibilidade óptica precisa de calibração. |
| P1 atomicidade grooming | Pré-reserva QKD antes do *grooming* SimpleTrafficGrooming sem expansão, reversão em erro; verificador de rollback nos demais caminhos | **Parcial**: não é uma transação universal para *grooming* com expansão ou estratégias alternativas. |
| P1 XT longitudinal no QCh | `qkdLongitudinalXtReceiverPower` com potência longitudinal clássica, atenuação quântica até receptor, sobreposição física de spans | **Implementado como opção** `longitudinalXtEnabled=true`; teste de invariantes passou, calibração experimental pendente. |
| P1 reutilização de PICh por QKDSpan | Reutilização opcional do mesmo *lease* espectral em trechos longitudinalmente disjuntos; referência e rollback | **Implementado com ressalva**: ainda reserva a banda no núcleo do enlace inteiro contra novos DChs. A reutilização entre PIChs é suportada, não a segmentação física plena do espectro clássico. |
| P1 otimização de nós confiáveis | Gerador de candidatos QTND global ou contagem por enlace; análise multiobjetivo com fronteira de Pareto usando **métricas medidas** | **Parcial**: busca sobre divisão uniforme por enlace; requer rodar o simulador para preencher CSV, e não resolve locais arbitrários de instalação nem custos geográficos reais. |
| P2 GN/EGN, ASE, amplificadores | Reexecutados verificadores numéricos existentes e documentadas comparações | **Verificação numérica parcial**; não há nova validação física/experimental independente e nem compilação limpa. |

## Arquivos Java alterados

- `network/PhysicalLayer.java` — potência XT longitudinal no receptor; integração com modelo de amplificadores/fontes.
- `simulationControl/parsers/NetworkConfig.java` — QBER máxima, QoT PICh padrão, XT longitudinal e reutilização.
- `simulationControl/parsers/SimulationConfig.java` — perfis de demanda bibliográficos e política de prevalência.
- `qkd/network/{QKDMesh,QKDMeshFactory,QKDNetworkParameters}.java` — propagação da configuração.
- `qkd/key/QKDKeyDemandPolicy.java` — seleção de perfil versus taxa fixa por gerador.
- `qkd/physical/{QKDCrosstalkPowerResult,QKDPhysicalLayer}.java` — XT por PICh, substituição da potência recebida por integração longitudinal, QBER.
- `qkd/capacity/{QKDSharedResourceManager,QKDPichPool}.java` — QBER de circuitos compartilhados, PICh espectro por trecho.
- `qkd/control/{QKDControlPlane,QKDExistingSessionImpactEvaluator,QKDProvisioningService}.java` — admissão conjunta, QoT de PICh ativos, proteção de sessões e rollback.
- Novos: `qkd/verification/{QKDLongitudinalXtVerification,QKDKeyReferenceProfilesVerification,QKDCoexistenceCorrectionsVerification}.java`.

## Configuração e semântica das demandas de chaves

No arquivo `simulation`, em `qkdKeyDemand`, a seleção recomendada para Dibaj é:

```json
"qkdKeyDemand": {
  "profile": "dibaj2024",
  "keySizeBits": 256,
  "mode": "uniform",
  "preferGeneratorFixedRate": false
}
```

Trocar `"profile": "moghaddam2021"` seleciona Moghaddam (2021). Atenção: **Moghaddam é em milissegundos** (64/128/256 ms), gerando 0,004/0,002/0,001 Mbit/s; **Dibaj é em microssegundos** (64/128/256 µs), gerando 4/2/1 Mbit/s, para chaves de 256 bits. `"profile": "custom"` e `"updateIntervalSeconds"` permitem demanda própria (unidade: segundos).

`preferGeneratorFixedRate=true` permanece **padrão compatível**: quando o gerador de `traffic` especifica `keyRateMbps`, ele substitui o sorteio do perfil. Para realmente sortear as classes Moghaddam/Dibaj, usar **`false`** ou remover `keyRateMbps` dos geradores. `mode="byBitRate"` associa as classes às taxas dos DChs em vez de sorteá-las uniformemente.

No arquivo `network`, sob `qkd`, pode-se ajustar:

```json
"maximumQber": 0.11,
"pichSnrCheckEnabled": true,
"pichRxPowerCheckEnabled": false,
"longitudinalXtEnabled": true,
"pichLongitudinalReuse": false
```

`longitudinalXtEnabled=false` e `pichLongitudinalReuse=false` são **padrões conservadores**, para não alterar silenciosamente experimentos anteriores. Ative as opções conforme a hipótese física do experimento. A verificação de QoT do PICh **já é ativada por padrão** em novas cargas de `network`.

### O DCh afeta a QoT de PICh?

**Sim.** `PhysicalLayer.evaluatePich(pich, pendingDch)` incorpora a influência de DCh ativo e candidato nos ruídos/XT, de acordo com os mecanismos habilitados na camada física. `QKDControlPlane.existingPichQualityPreserved(pendingDch)` reavalia PIChs ativos quando chega um DCh, e `isPichSafeForClassicalTraffic` avalia a interferência dos PIChs novos sobre DChs e outros PIChs. A aceitação depende de `pichSnrCheckEnabled` (agora `true` por padrão), modelos físicos ligados e configuração da modulação. Não se deve confundir isso com limiar de potência recebida, ainda opcional e desabilitado por padrão.

### Alcance da reutilização por trecho

`pichLongitudinalReuse=true` permite que PIChs localizados em **trechos físicos disjuntos da mesma direção e enlace** compartilhem uma reserva óptica quando todas as regras de alocação permitirem, com contagem de usuários da reserva. O *core* ainda mantém o *slot* reservado ao longo de todo o enlace para DChs; portanto, **a reutilização plena do espectro por DCh/PICh nas subfibras não está implementada**. Testes de QTND devem comparar `false` × `true` explicitamente.

## Cenários de referência: hipóteses diferentes

- `simulations/QKD_Referencias/Moghaddam2021_MCF7_Inspirado`: arquitetura **SDM** de sete núcleos com perfil de demanda Moghaddam; *não* reproduz fielmente todos os parâmetros de seu artigo.
- `simulations/QKD_Referencias/Dibaj2024_MF_Inspirado`: arquitetura **MF lógica**, com **oito IDs de núcleo simulados**, isolando o canal QKD. **Não chamar de fibra física MCF7**.
- `simulations/QKD_Referencias/MCF7_Qualificacao_SDM`: sete núcleos físicos representados, um **externo** reservado a QKD e seis clássicos; XT longitudinal ativado.

Os três foram aceitos por `python scripts/validar_perfis_qkd.py --strict`. Esse resultado garante a consistência **estrutural dos arquivos**, não que o simulador foi executado de ponta a ponta nem que reproduziu as figuras publicadas.

## Experimentos de otimização de QTND / nós confiáveis

Em Python 3:

```bash
python scripts/otimizar_nos_confiaveis_qkd.py gerar --scenario simulations/QKD_Referencias/MCF7_Qualificacao_SDM --mode global --values 10,15,20,25,30,35,40 --output experimentos_qtnd
```

Para explorar **quantidades diferentes** de nós por enlace:

```bash
python scripts/otimizar_nos_confiaveis_qkd.py gerar --scenario simulations/QKD_Referencias/MCF7_Qualificacao_SDM --mode perLink --values 0,1,2 --max-candidates 100 --output experimentos_tn_por_enlace
```

Execute os experimentos gerados no SNetS, mantendo **mesmas sementes/tráfego** quando comparar candidatos. Agregue resultados medidos por cenário em `metricas.csv` (cabeçalho obrigatório: `scenario,blocking,qtrx,pichSlots,maxQber,minSkrMbps`). Em seguida:

```bash
python scripts/otimizar_nos_confiaveis_qkd.py avaliar --metrics metricas.csv --maximum-qber 0.11 --min-skr-mbps 0 --output classificacao_qtnd.csv
```

O relatório marca cenários factíveis, dominância de Pareto e classificação por arrependimento ponderado. **Os valores de Q-TRx devem vir da simulação**, não de estimativas dos spans no `manifest.csv`.

## Validações numéricas obtidas nesta execução

Compilação incremental de **17** fontes Java alteradas/novas: passou (avisos sobre `-source/-target 8` no JDK atual). Execução de verificadores **sob classpath híbrido, NÃO compilação limpa**:

- `QKDKeyReferenceProfilesVerification`, `QKDCoexistenceCorrectionsVerification`, `QKDLongitudinalXtVerification`, `QKDKeyIntervalSecondsVerification`: **passaram**.
- `QKDMoghaddam2021UpgradeVerification`, `QKDPhysicalVerification`, `QKDPichSharedClassicalQoTVerification`: **passaram**.
- `QKDThesisFiguresVerification`: GN normalizado 30,145822690 dB (1 span) e 47,135522734 dB (50 spans); EGN 28,086034039 dB (1 span), 45,075734083 dB (50 spans).
- `QKDThesisSNetSPhysicalLayerVerification`: EGN do motor 28,086036330 dB (1 span), 45,075736373 dB (50 spans). Diferença EGN aos 50 spans: ~`2,29e-6 dB`, concordância **com referência analítica** sob hipóteses do caso.
- `QKDv26AseFrequencyParityVerification`: 48 comparações de motores, maior erro relativo `5,81e-16` (**concordância entre implementações**).
- `QKDGnScientificCorrectionsVerification`, `QKDAmplifierOperatingPointVerification`, `QKDAmplifierLongitudinalVerification`, `QKDCascadedOsnrVerification`: **passaram**.
- O lote de 63 verificações tentado com binários herdados teve 47 aprovadas, 15 bloqueadas por dependências externas e 1 incompatibilidade de teste binário antigo com o código de demanda corrigido. Essa última incompatibilidade é **binário obsoleto**; não foi verificada após compilação limpa da fonte do teste (depende de Gson). Os binários `target/classes` do ZIP original NÃO são evidência de que representam o fonte atualizado.
- `scripts/ci_qkd_clean.sh` executado aqui retornou código **2**, explicitamente `[ERROR] Required tool unavailable: mvn`.

**Pendências antes de publicação científica:** build limpo em Windows 11 com Maven/JDK, execução dos 63 verificadores recompilados; regressões fim-a-fim do *grooming* com expansão e falhas injetadas; validação independente da integração longitudinal IC-XT→QBER→SKR contra dados científicos para MCF; validação GN/EGN/ASE/ganhos amplificados contra figuras/cenários externos, registrando unidades e hipóteses. Os testes GN/EGN são comparações numéricas de base e não comprovam por si só a validade do modelo físico completo com ruído quântico.

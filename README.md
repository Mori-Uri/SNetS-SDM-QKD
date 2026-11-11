> **Topologia óptica atual:** duas fibras físicas independentes por par de sentidos A→B / B→A; não existe fibra compartilhada contrapropagante. Ver [verificação e cenários salvos](docs/VALIDACAO_FIBRAS_DIRECIONAIS_E_CENARIOS.md).

# SNetS-SDM-QKD

Simulador de **distribuição quântica de chaves (QKD) em redes ópticas elásticas com multiplexação espacial
(SDM-EONs)**. O foco é a **coexistência quântico-clássica** numa mesma fibra multinúcleo (MCF): canais quânticos (QCh)
num núcleo reservado, convivendo com o tráfego clássico e o crosstalk dos núcleos vizinhos.

Projeto da dissertação de mestrado de **Uriel Pinheiro Mori** (PPGCC/UFPI). Ele é a base do trabalho de qualificação
*"Planejamento de Nós Confiáveis para QKD em SDM-EONs: Análise da Coexistência Quântico-Clássica"* (UFPI, 2026).

O simulador estende o **SNetS-SDM**, um simulador de eventos discretos em Java para SDM-EONs (ver
[Origem](#origem-e-créditos)). O código clássico foi preservado. A camada QKD é um addon opcional, e um caso com
`network.qkd.enabled = false` roda como o SNetS original.

---

## O que o simulador modela

**Requisição protegida por QKD.** Cada requisição pode ser protegida por QKD. Nesse caso, ela só é aceita se for
possível estabelecer, ao mesmo tempo:

- **CCh**: o circuito clássico de dados (RMLSA + QoT/crosstalk);
- **QCh**: o(s) canal(is) quântico(s), com taxa de chave secreta (SKR) suficiente para a demanda de chave da
  requisição;
- **PICh**: o canal público da discussão do BB84, quando modelado.

Um circuito clássico novo também é recusado se o crosstalk dele derrubar a SKR de uma sessão QKD ativa (**proteção
das sessões ativas**).

**Camada física QKD.** BB84 com estados decoy (Lo, Ma e Chen 2005), como no artigo-base de Dibaj et al. (2024) e em
Moghaddam et al. (2021). O modelo inclui:

- atenuação dependente do comprimento de onda;
- eficiência e dark counts do detector;
- ruído de crosstalk intercore vindo dos canais clássicos dos núcleos vizinhos.

Os parâmetros ficam no arquivo de caso `qkdPhysicalLayer`.

**Nós confiáveis (QTND).** Cada enlace é dividido em trechos de no máximo QTND km, com uma sessão BB84 e um par
QTx/QRx por trecho. Os nós confiáveis podem ser:

- **globais**: o mesmo QTND na rede toda;
- **por enlace**: um número de nós ou um QTND próprio em cada enlace.

**Núcleo quântico.** Um núcleo da MCF fica reservado aos QChs. Os QChs seguem a regra espectral do ABNE/ACINE para
esse núcleo: medium fit no centro, last fit nos externos pares e first fit nos externos ímpares. Também há opção de
first fit ou do slot de menor atenuação.

**Granularidade dos QChs:**

- **route**: QCh dedicado, com o mesmo slot em todos os trechos da rota QKD (cenário da qualificação);
- **link**: um QCh por enlace, compartilhado pelas sessões;
- **span**: pools de QChs por trecho, com TDM opcional (como no artigo-base).

**Recursos e restrições:**

- **PICh**: slots, modelo (`segment` ou `route`), núcleo, slot e banda de guarda configuráveis;
- **rotas QKD alternativas** (k menores caminhos de Yen);
- **limites de QTx/QRx** por nó;
- **núcleos desativados**;
- **demanda de chave**: AES-256 renovada a intervalos fixos, sorteada ou proporcional à taxa clássica.

**Algoritmos:** `qabne` (ABNE sem o núcleo quântico e os núcleos desativados), `qfirstfit` e `qrmsta` (RMSTA do
artigo-base), além dos algoritmos clássicos do SNetS-SDM (`csbasdm`/ABNE, ACINE etc.).

## Métricas geradas (CSV por caso)

Além das métricas clássicas do SNetS (bloqueio, utilização, fragmentação, modulação, energia…):

| Arquivo | Conteúdo |
|---|---|
| `QKDMetrics.csv` | Bloqueio QKD e causas (SKR, espectro do QCh, PICh, QTx/QRx), sessões ativas, QChs, Q-TRx, nós confiáveis, PIChs |
| `BlockingDetails.csv` | Bloqueio por causa e por classe (clássicas × protegidas), por núcleo, por enlace e diagnóstico do espectro |
| `QKDChannelBlocking.csv` | Canal que impediu a requisição protegida (CCh, QCh ou PICh), por causa e por enlace |
| `SlotUtilization.csv`, `SlotBlocking.csv`, `QKDChannelSlotBlocking.csv` | Ocupação e bloqueio slot a slot, por núcleo e por enlace (mapas de calor do espectro) |
| `QKDStatistics.csv` | Contadores das sessões por replicação: aceitas, recusadas, recusas pela proteção, SKR e QBER na admissão, QChs ativados |

O launcher calcula também o **custo de equipamento QKD** (QTx, QRx, nós confiáveis e PIChs, com pesos ajustáveis).

## Launcher (interface web)

O `launcher/` é uma interface web local para criar e editar casos, executar simulações e analisar resultados, sem
editar JSON à mão. Ele permite:

- **montar a rede e a fibra**:
  - topologias prontas: metropolitanas (Metropolitana, Metropolitana 2, Losango, DT com relays, Linha, Borboleta) e
    backbone (NSFNet, USA, COST239, EON, JPN);
  - fibras de 2, 3, 5, 7, 12 e 19 núcleos;
  - edição dos enlaces nos dois sentidos de uma vez;
- **configurar o QKD** na aba QKD: QTND global ou por enlace, núcleo quântico, granularidade, PICh e banda de guarda;
- **simular**: execução em fila e varreduras de parâmetros (ex.: QTND × fração protegida);
- **analisar**: estúdio de gráficos com dezenas de modelos (bloqueio, causas, canais QKD, núcleos, mapas de calor
  da topologia e do espectro, custo × bloqueio, métrica × QTND), exportação em SVG/PNG/PDF e relatórios;
- **verificar**: plano de verificação contra o artigo-base e outras referências.

Para abrir: `launcher\SNetS-Launcher.bat` (Windows) ou `java -jar launcher/snets-launcher.jar`. O navegador abre em
`http://localhost:8765`. Detalhes em [`launcher/README.md`](launcher/README.md).

## Começo rápido

Requisitos: **Java 17 ou superior** (para o simulador e o launcher). Maven e Python só são necessários para compilar
o código ou usar as ferramentas de análise antigas.

```bat
:: 1. Abrir o launcher (Windows)
launcher\SNetS-Launcher.bat

:: 2. Ou executar um caso diretamente
cd simulations
java -jar SNetS-SDM-QKD.jar QKD_qualificacao\Exemplo_5nos_QTND30
```

Os resultados (CSV) são gravados na pasta do caso. A pasta `simulations/` é local: o repositório guarda só os JARs, os
scripts de execução e os casos de exemplo. Os estudos e os resultados de cada um ficam fora do controle de versão.

### Exemplo de bloco QKD no arquivo `network`

```json
"qkd": {
  "enabled": true,
  "qtndKm": 25.0,
  "quantumCoreId": 1,
  "qchGranularity": "route",
  "qchSlotPolicy": "abne",
  "wavelengthPolicy": "grid",
  "alternativeRoutes": 2,
  "pichSlots": 1,
  "pichMode": "segment",
  "trustedNodeMode": "global"
}
```

Na requisição, a proteção e a demanda de chave são definidas em cada gerador do arquivo `traffic`
(`qkdProtectedProbability`, `keyRateMbps`) ou em `simulation.qkdKeyDemand`. A lista completa de opções está em
[`README_QKD_ADDON.md`](README_QKD_ADDON.md).

## Estrutura do repositório

| Pasta / arquivo | Conteúdo |
|---|---|
| `src/main/java/` | Simulador. `gprmcsa/`, `network/`, `simulator/` = SNetS-SDM clássico; `qkd/` = addon QKD |
| `simulations/` | JARs (`SNetS-SDM-QKD.jar` e o clássico `SNetS-SDM-SBRC26.jar`), scripts de execução e casos de exemplo |
| `launcher/` | Interface web (Java + HTML/JS), `snets-launcher.jar` |
| `verificacao/` | Plano de verificação e reprodução do artigo-base (`QKD_reference/`) |
| `docs/` | Referências usadas no código (`REFERENCIAS_QKD.md`), guias e o README original do SBRC 2026 |
| `README_QKD_ADDON.md` | Opções do addon QKD, por etapa |
| `CHANGELOG.md` | Histórico de todas as mudanças (uma seção por etapa) |

## Verificação

O simulador é verificado em etapas, do artigo-base ao cenário completo. O plano inclui:

- reprodução das curvas de SKR, SKR/Q-TRx e Q-TRx × QTND de Dibaj et al. (2024) para as redes Small Core, Butterfly
  e Metro;
- verificadores do BB84, do crosstalk, da proteção das sessões e da contagem de recursos;
- comparações com Moghaddam et al. (2021) e com o modelo analítico QKD-Analytical (Ahmed, Chatterjee e Oki, 2026).

Os verificadores são executados pelo botão "Verificação QKD" do launcher.

## Referências principais

- M. R. Dibaj et al., "Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in
  Multi-Band and Multi-Fiber Realms", IWCIT 2024 (artigo-base).
- E. E. Moghaddam, H. Beyranvand, J. A. Salehi, "Resource Allocation in Space Division Multiplexed Elastic Optical
  Networks Secured With QKD", IEEE JSAC 39(9), 2021.
- I. Ahmed, B. C. Chatterjee, E. Oki, "QKD-Analytical: Analytical Model for Blocking Probabilities in Priority-Aware
  QKD over SDM-EONs", IEEE INFOCOM 2026.
- J. C. Lacerda Jr., A. V. T. Cartaxo, A. C. B. Soares, "Novel dynamic impairment-aware algorithm for modulation, core,
  and spectrum assignment in SDM-EONs", Opt. Switch. Netw. 51, 2024.
- A. Fontinele et al., "An Efficient IA-RMLSA Algorithm for Transparent Elastic Optical Networks", Comput. Netw. 118,
  2017 (SNetS).

A lista completa, com a classe que usa cada referência, está em [`docs/REFERENCIAS_QKD.md`](docs/REFERENCIAS_QKD.md).

## Origem e créditos

Este simulador parte do **SNetS-SDM** usado no artefato do SBRC 2026, *"IMPA: Novo algoritmo para atribuição de
potência de forma adaptativa em SDM-EONs"* (A. C. Fontinele et al.). O README original desse artefato está em
[`docs/README_SBRC26_original.md`](docs/README_SBRC26_original.md). O SNetS foi desenvolvido no grupo de pesquisa em
redes ópticas da UFPI.

## Licença

MIT. Ver [`LICENSE`](LICENSE).

### Unidade de renovação das chaves QKD

Use `simulation.qkdKeyDemand.updateIntervalSeconds` (lista em segundos); veja `docs/QKD_UPDATE_INTERVAL_SECONDS.md` para os cenários de Moghaddam e Dibaj.

## Integração seletiva da v2 — 2026-11-08

A v1 incorpora agora a política QCh `maxskr` (somente `qchGranularity=route`), a calibração Raman anti-Stokes opcional `qkdPhysicalLayer.multiband.antiStokesCorrection=medida`, um cache transitório de faixas livres em `network.Spectrum` e o Launcher **1.21.0**. Os dois modos de integral Johannisson (SCI/XCI) permanecem independentes; o padrão é logarítmico com fallback numérico para domínios inválidos. Consulte `docs/INTEGRACAO_SELETIVA_V2_MAXSKR_RAMAN_CACHE_LAUNCHER.md` para parâmetros, limites científicos e testes.

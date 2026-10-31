# Histórico de mudanças do SNetS-SDM-QKD

Todas as mudanças do addon QKD e do launcher, da mais nova para a mais antiga, num só arquivo. Cada parte
corresponde a uma etapa de desenvolvimento da dissertação. Mudanças novas entram no topo deste arquivo.

| Parte | Resumo |
|---|---|
| [41](#parte-41) | Parte 41 — repositório SNetS-SDM-QKD: README novo, CHANGELOG único, simulations/ fora do git, enlaces bidirecionais no launcher (1.18.1) |
| [40](#parte-40) | Parte 40 — topologias e fibras de pequena escala, regra ABNE nos QChs, PICh configurável (launcher 1.18.0) |
| [39](#parte-39) | Parte 39 — canal QKD bloqueado, nós confiáveis por enlace, qabne e launcher 1.17.0 |
| [38](#parte-38) | Parte 38 — launcher 1.16.0 (estúdio de gráficos) |
| [37](#parte-37) | Parte 37: bloqueio por núcleo e causa "Outros" |
| [36](#parte-36) | Parte 36: correção do QABNE ("Core not selected.") |
| [35](#parte-35) | Parte 35: estudos Test5/Test6, variações num só lugar, gráficos de custo |
| [34](#parte-34) | Parte 34: demanda de chave pela taxa clássica |
| [33](#parte-33) | Parte 33: QCh no mapa de espectro, núcleos desativados, modulação por enlace |
| [29](#parte-29) | Parte 29: onde no espectro as requisições são bloqueadas |
| [28](#parte-28) | Parte 28: mudanças do coorientador e "Gerar JAR" no launcher |
| [27](#parte-27) | Parte 27: ocupação de cada slot e correções do launcher |
| [26](#parte-26) | Parte 26: pedidos do coorientador |
| [25](#parte-25) | Parte 25: detalhes do bloqueio (BlockingDetails.csv) |
| [24](#parte-24) | Addon QKD — Parte 24: plano de verificação (do artigo-base ao cenário completo) |
| [23](#parte-23) | Addon QKD — Parte 23: cenário de estudo da qualificação |
| [21–22](#parte-21-22) | Entrega QKD - Partes 21 e 22 (+ interface) |
| [18–20](#parte-18-20) | Entrega QKD - Partes 18, 19 e 20 |

---

<a id="parte-41"></a>

## Parte 41 — repositório SNetS-SDM-QKD: README novo, CHANGELOG único, simulations/ fora do git, enlaces bidirecionais no launcher (1.18.1)

- `README.md` reescrito para o SNetS-SDM-QKD (simulador de QKD em SDM-EONs da dissertação). O README do artefato
  SBRC 2026 (IMPA) foi para `docs/README_SBRC26_original.md`.
- Os `CHANGELOG_QKD_PARTE*.md` foram juntados neste arquivo (`CHANGELOG.md`). As próximas mudanças entram no topo dele.
- `.gitignore`: a pasta `simulations/` deixa de ser versionada, exceto os JARs, os scripts `run_*.bat`,
  `QKD_qualificacao/` (exemplo) e `QKD_experiments/` (ferramentas).
- Launcher 1.18.1 (`launcher/web/js/forms.js`, `styles.css`): na tabela de enlaces da aba Rede, a caixa
  "Bidirecional" (marcada por padrão) copia a mudança de um campo de a→b para b→a (ex.: size do 1-2 de 50 para 100
  muda também o 2-1).

---

<a id="parte-40"></a>

## Parte 40 — topologias e fibras de pequena escala, regra ABNE nos QChs, PICh configurável (launcher 1.18.0)


### Simulador

- **QCh pela regra do núcleo (`network.qkd.qchSlotPolicy = "abne"`, novo padrão).** Os QChs seguem a regra espectral
  que o ABNE/ACINE usa no núcleo QKD: núcleo central (0, ou 18 com 19 núcleos) em medium fit, externo par em last fit
  e externo ímpar em first fit. Vale nos modos route, link e span. Antes, os QChs iam sempre em first fit
  (`lowestloss` continua no modo route; `spanLanePlacement = "wavelength"` continua definindo a posição quando usado).
  Com o núcleo QKD 1 (o padrão), abne = firstfit: os resultados não mudam. Casos com `"qchSlotPolicy": "firstfit"`
  escrito no arquivo continuam em first fit.
  Teste (Metropolitana, 100 % protegidas, PICh 1, 3 680 E): núcleo QKD 0 em first fit → bloqueio 0,527; em medium
  fit (QChs nos slots 157–162) → 0,162. Núcleo 1: idêntico à Parte 39.
- **Banda de guarda entre QChs (`qkd.qchGuardBand`, padrão 0 = como antes).**
- **PICh configurável:**
  - `qkd.pichCorePolicy`: `lowest` (padrão, como antes), `fixed` (com `qkd.pichCoreId`) ou `leastUsed`;
  - `qkd.pichSlotPolicy`: `firstfit` (padrão), `lastfit` ou `abne` (regra do núcleo escolhido);
  - `qkd.pichGuardBand`: −1 (padrão) = `guardBand` da rede, ou um valor próprio;
  - correção: o PICh não usa mais núcleos desativados (`network.disabledCores`).
- Arquivos: `parsers/NetworkConfig.java`, `qkd/network/QKDNetworkParameters.java`,
  `qkd/capacity/QKDSharedResourceManager.java`, `qkd/capacity/QKDPichPool.java`,
  `qkd/control/QKDProvisioningService.java`.

### Launcher 1.18.0

- Rede: topologias agrupadas em "Metropolitanas e de pequena escala" e "Backbone". Novas: Losango (5 nós),
  Metropolitana 2 (8 nós, deitada), DT (12 nós, 20 enlaces; os 20 relays da figura podem ser gravados como nós
  confiáveis por enlace ao aplicar) e Linha (2 nós, 50 km). A rede de 28 nós passa a se chamar EON e a de 12, JPN.
- Fibras novas: 5 núcleos, 3 núcleos em triângulo e 3 núcleos em linha (posições da fibra de 7 núcleos; núcleo 0 no
  centro).
- Aba QKD: ordem do slot do QCh em todos os modos (com aviso quando o núcleo QKD não está na regra dele), banda de
  guarda entre QChs e um painel "Canal público (PICh)" com slots, modelo, sessões, núcleo, slot e banda de guarda.
- Arquivos: `launcher/web/js/{presets,netedit,qkdcase,schema,validate}.js`, `LauncherServer.java` (VERSION 1.18.0),
  `launcher/README.md`, `launcher/snets-launcher.jar`, `simulations/SNetS-SDM-QKD.jar`.

---

<a id="parte-39"></a>

## Parte 39 — canal QKD bloqueado, nós confiáveis por enlace, qabne e launcher 1.17.0


### Simulador

- **Canal que não pôde ser estabelecido (só observa, não muda decisões).** Para cada requisição protegida por QKD
  bloqueada, o `BlockingDetailRecorder` registra o canal que falhou:
  - **CCh**: o circuito clássico da requisição (XT, XTO, QoTN/QoTO, fragmentação, falta de espectro, núcleo não
    tentado e recusa pela proteção das sessões ativas);
  - **QCh**: canal quântico (SKR insuficiente, espectro do QCh, falta de QTx/QRx, outros QKD);
  - **PICh**: canal público.
  Arquivos novos (gravados com BlockingProbability; o de slots precisa também de SlotBlocking):
  - `<caso>_QKDChannelBlocking.csv` — `Metrics,LoadPoint,Channel,Cause,Link, ,rep…`: bloqueio por canal, parcela de
    cada canal, canal × causa, bloqueios sem faixa tentada e bloqueio por canal em cada enlace.
  - `<caso>_QKDChannelSlotBlocking.csv` — mesmo formato do SlotBlocking (coluna Cause = canal): onde, no espectro, o
    CCh, os QChs e o PICh de cada requisição protegida foram tentados e bloqueados.
  Arquivos: `qkd/metrics/BlockingDetailRecorder.java`, `qkd/control/QKDControlPlane.java`,
  `qkd/control/QKDProvisioningService.java`, `qkd/capacity/QKDPichPool.java`,
  `simulationControl/SimulationManagement.java`, `SimulationFileManager.java`, `parsers/SimulationRequest.java`.
- **Nós confiáveis por enlace (opcional).** `network.qkd.trustedNodeMode = "global"` (padrão, igual a antes) ou
  `"perLink"`. Com `perLink`, `network.qkd.trustedNodesPerLink = [{"link": "A-B", "trustedNodes": n}` ou
  `{"link": "A-B", "qtndKm": x}]`: n nós dividem o enlace em n + 1 trechos iguais; `qtndKm` usa a regra normal com o
  km do enlace. "A-B" vale para os dois sentidos (uma entrada "B-A" vence para o sentido B→A). Enlaces sem regra
  usam `qtndKm`. Arquivos: `parsers/NetworkConfig.java` (`qtndForLink`), `qkd/network/QKDTopologyPlanner.java`.
- **qabne** não reserva faixa em núcleo desativado (`QABNE.ensureCoreLayout`) e pode ser usado em execução clássica
  (`simulator/Simulation.java` escolhe a fábrica QKD quando o algoritmo é QKD).

Métricas clássicas não mudaram. Teste (Q25_R25_PICh1, 100 % protegidas, 3 680 E): CCh 0,0367 (XTO 0,0186 + proteção
0,0181) + QCh 0,0278 (SKR) = 0,0644, igual ao bloqueio das protegidas do BlockingDetails.

### Launcher 1.17.0

- Caixa de modelos: nomes numa linha só (com "…"; o nome completo aparece ao passar o mouse), a lista abre centrada no
  modelo escolhido e a coluna da esquerda não muda mais de largura.
- Barras com **padrões** (hachuras: diagonais, cruzadas, listras, grade, pontos, tijolos, zigue-zague), com cor,
  tamanho e traço por série (seção 3, "Padrão e valores"). A legenda mostra o padrão.
- **Rótulos dos valores** (seção 5 nova): cor (texto, série, contraste preto/branco ou escolhida), negrito, caixa com
  fundo/contorno/opacidade, posição na barra (acima, dentro no topo/centro/base), empilhadas ao lado da pilha com
  linha guia, rotação, posição nos pontos das linhas e "ocultar valores menores que". Cada série pode mudar cor,
  negrito, caixa, posição ou esconder os seus valores.
- **Mapa da topologia**: rótulos dos enlaces só texto, em caixa, ou em caixa afastada com linha até o enlace; cores,
  tamanho, distância, negrito, "km" no comprimento, afastamento automático de rótulos que batem em nós/rótulos e
  **arrastar** um rótulo no gráfico (style.topoLabelOffsets; botão para desfazer).
- **Mapa de bloqueio do espectro**: cor de cada tipo de bloqueio e da cor "tentado, sem bloqueio"
  (style.slotCauseColors).
- Modelos novos "Canais QKD bloqueados" (QKDChannelBlocking/QKDChannelSlotBlocking) e descrições das métricas.
- **Métrica × parâmetro (ex.: × QTND)**: os casos viram famílias (configuração igual fora o parâmetro; o tráfego entra
  pela fração protegida, chave e carga), uma curva por família; opção de juntar como antes e de esconder famílias
  com um só ponto. O nome da família pode ser editado na seção 3.
- Aba QKD do caso: "Nós confiáveis: QTND global / Por enlace", com a regra de cada enlace na tabela de planejamento;
  pré-visualização da rede e mapas da topologia desenham os nós confiáveis com a regra de cada enlace; validação das
  regras.

Arquivos: `launcher/web/js/{util,figures,figrender,metricinfo,presets,qkdcase,validate}.js`,
`launcher/web/styles.css`, `launcher/src/launcher/LauncherServer.java` (VERSION 1.17.0), `launcher/README.md`,
`launcher/snets-launcher.jar`, `simulations/SNetS-SDM-QKD.jar`.

Figuras salvas antes continuam abrindo (campos novos com valores padrão). Casos antigos não precisam de mudança.

---

<a id="parte-38"></a>

## Parte 38 — launcher 1.16.0 (estúdio de gráficos)


Só o launcher mudou; o simulador e os resultados não mudam.

Arquivos:
- launcher/web/js/figures.js — seção "Como ler este gráfico", ocultar caso (c.hidden), pontos de carga das linhas
  (line.lps), ordem das séries (seriesOrder), painéis de linhas de referência, largura das barras, downloads numerados.
- launcher/web/js/figrender.js — xStep, marcas extras, linhas de referência, valores nos pontos das linhas, valueSize,
  barMaxPx.
- launcher/web/js/metricinfo.js — glossário dos itens da legenda (App.metricInfo.item).
- launcher/web/js/api.js — figureImageName.
- launcher/web/styles.css — estilos novos.
- launcher/src/launcher/Figures.java, LauncherServer.java — /api/figure/image-name (primeiro nome livre: n, n(1)…);
  VERSION 1.16.0.
- launcher/README.md, launcher/snets-launcher.jar.

Figuras salvas antes continuam abrindo (campos novos com valores padrão).

### 1.16.1 e 1.16.2

- 1.16.1: a causa "Free band in a core not tried" aparece na legenda como "Núcleo não tentado"; a explicação
  detalhada fica em "Como ler este gráfico" (metricinfo.js).
- 1.16.2 (figures.js): ajustes guardados por modelo de gráfico (localStorage snets.fig.templates, botão "Padrão do
  modelo"); slots.oneWay (só um sentido de cada enlace) no estúdio e no relatório; sentidos dos mapas da topologia no
  relatório.

---

<a id="parte-37"></a>

## Parte 37: bloqueio por núcleo e causa "Outros"


### O problema

Quando o algoritmo não acha faixa em nenhuma modulação, o `spcsav2` grava no circuito um núcleo SORTEADO (código
clássico, sem semente). Esse núcleo nunca foi tentado, mas o SNetS o usava para duas coisas:

- **Atribuir o bloqueio a um núcleo.** O sorteio podia cair no núcleo quântico ou num núcleo desativado, que então
  apareciam com 100 % de bloqueio sem nunca receber circuito.
- **Diagnosticar a causa.** O diagnóstico rodava nesse núcleo sorteado, e daí saíam "Outros", "Falta de espectro
  livre" e parte da "Fragmentação" sem relação com o que aconteceu.

### A correção no BlockingDetails.csv

Só observação; nenhuma decisão muda.

- Essas requisições não são mais atribuídas a núcleo nenhum.
- A causa delas é diagnosticada em todos os núcleos utilizáveis, sem o quântico e os desativados:
  - **Lack of free spectrum:** nenhum núcleo tem slots livres suficientes em todos os enlaces da rota;
  - **Fragmentation:** há slots suficientes, mas não contíguos ou não alinhados;
  - **Free band in a core not tried (causa nova):** havia uma faixa livre num núcleo que o algoritmo não tentou. O
    spcsav2 + ABNE testa um núcleo por modulação, em rodízio.
- Falta de transmissor ou receptor continua com a causa do SNetS.

Exemplo, na Metropolitana com Q25_R0 e 3400 Erlangs: "Outros" e "Falta de espectro" foram a 0, a fragmentação caiu de
1,31 % para 0,48 %, e 1,61 % (16 % dos bloqueios) passou a "havia faixa em núcleo não tentado".

### O que não mudou

- O BlockingProbability.csv clássico do SNetS continua igual; o caso clássico de teste ficou idêntico byte a byte.
- As 21 verificações passam.
- Os resultados já rodados não mudam. Para ver as causas novas é preciso rodar de novo; o launcher 1.15.0 já esconde os
  núcleos não usados também nos resultados antigos.

---

<a id="parte-36"></a>

## Parte 36: correção do QABNE ("Core not selected.")


- **O problema.** Quando o `qabne` não achava espaço para uma modulação, ele devolvia "falhou" sem limpar a faixa nem
  gravar o núcleo no circuito. O ABNE clássico (`csbasdm`) faz as duas coisas.
- **As consequências.** O `spcsav2`, que testa as modulações da mais eficiente para a menos eficiente, tinha dois efeitos:
  - imprimia "Core not selected." a cada tentativa sem núcleo;
  - se uma modulação anterior tivesse achado faixa, testava a camada física da modulação seguinte com essa faixa antiga,
    que tem outro tamanho.
- **A correção.** O `QABNE` agora grava a faixa (nula na falha) e o núcleo também quando falha, como o `CSBASDM2`.
  A escolha do núcleo e da faixa não mudou.
- **Efeito.**
  - A mensagem sumiu.
  - Nos casos com QKD, o bloqueio muda um pouco nas cargas altas. Exemplo, na Metropolitana com 25 % protegidas:
    10,28 % → 9,22 % e 15,97 % → 16,72 %.
  - Os casos clássicos (`csbasdm`) não mudam.
  - As 21 verificações passam.
- **Atenção.** Simulações QKD rodadas com o jar anterior devem ser rodadas de novo.

---

<a id="parte-35"></a>

## Parte 35: estudos Test5/Test6, variações num só lugar, gráficos de custo


### Simulador

- As mensagens "Core not selected." que vinham do código QKD foram retiradas. O núcleo de um candidato sem núcleo agora é
  lido sem imprimir nada, em `QKDProvisioningService`, `QKDPhysicalLayer` e `QKDExistingSessionImpactEvaluator`.
  - A mesma mensagem ainda aparece quando o próprio algoritmo `spcsav2` não acha núcleo. Os algoritmos não foram mexidos.
  - Os resultados não mudam: idênticos byte a byte, e as 21 verificações passam.

### Casos

- `simulations/QKD_Test5`: topologia Metropolitana, 94 casos. `simulations/QKD_Test6`: NSFNet com QTND de 25, 50, 75 e
  100 km, 121 casos.
- Os dois foram gerados por `launcher/.work/gen/make_tests.py`. Os nomes e as cargas estão em `LEIAME_CASOS.md`, dentro
  de cada pasta.
- A configuração anterior do Test5 ficou em `launcher/.work/backup_test5`.

### Launcher 1.14.0

Ver o README do launcher.

---

<a id="parte-34"></a>

## Parte 34: demanda de chave pela taxa clássica


- **Campo novo `simulation.qkdKeyDemand.mode`.** Pode ser:
  - `"uniform"`, o padrão (também quando o campo não existe): igual a antes. Os resultados são idênticos byte a byte aos da Parte 33.
  - `"byBitRate"`: cada requisição protegida sem `keyRateMbps` pede a taxa de chave que corresponde à sua taxa clássica.
- **Regra do `"byBitRate"`.** As taxas clássicas dos geradores e as taxas de chave (tamanho da chave ÷ intervalo) são
  ordenadas da menor para a maior e pareadas pela posição:
  - com o mesmo número de taxas dos dois lados, é 1ª com 1ª, 2ª com 2ª, e assim por diante;
  - com mais taxas clássicas, taxas vizinhas dividem a mesma chave;
  - com menos, elas se espalham da menor à maior chave.

  Como a requisição protegida é uma requisição clássica sorteada, as demandas ficam nas mesmas proporções das taxas
  clássicas.
- **Tabela opcional `keyRateMbpsByBitRate`.** Mapeia Gb/s para Mbit/s, por exemplo `{"100": 1, "200": 2, "400": 4}`,
  e substitui a regra automática para as taxas que lista.
- **Precedência.** O `keyRateMbps` de um gerador continua vencendo. O sorteio da política ainda é feito em cada
  requisição, o que mantém o gerador aleatório do QKD alinhado com o modo uniforme.
- **Classes alteradas:**
  - `QKDKeyDemandPolicy`, `QKDRequestGenerator`, `QKDMesh`, `QKDMeshFactory`;
  - `SimulationConfig.QKDKeyDemandConfig`.

---

<a id="parte-33"></a>

## Parte 33: QCh no mapa de espectro, núcleos desativados, modulação por enlace


### Simulador

- **QCh no `SlotBlocking.csv`.** Para cada requisição protegida, a faixa de cada QCh tentado ou aceito no núcleo
  quântico conta como uma tentativa. Se a requisição foi bloqueada por uma causa QKD, essa faixa conta também como
  bloqueio daquela causa.
  - Assim, o núcleo 1 aparece nos mapas de calor de bloqueio por slot.
  - Linha nova `Blocked QKD requests without QCh band`: bloqueios QKD em que nenhum QCh chegou a ser tentado.
  - O registro só observa a alocação; as decisões não mudam. Ver `QKDSharedResourceManager.begin/endQchAttemptLog`.
- **Núcleos desativados (`network.disabledCores`, lista de ids, opcional).** Um núcleo desativado não aceita nenhum
  circuito clássico, QCh nem PICh: `Core.useSpectrum` recusa, e a lista de faixas livres vem vazia.
  - Se o núcleo quântico estiver na lista, ele é ignorado ali.
  - A métrica SlotUtilization trata o núcleo desativado como livre.
  - Sem o campo, os resultados não mudam: no cenário clássico, os 14 CSVs são idênticos byte a byte aos da Parte 29.
- **`BlockingDetails.csv`, métrica nova `Share of circuits per modulation on link`.** Para cada enlace (`Link`), a
  fração dos circuitos aceitos que passam por ele com cada modulação (`Cause` = modulação).
- **Classes alteradas:**
  - `QKDSharedResourceManager`, `QKDControlPlane`, `BlockingDetailRecorder`;
  - `Core`, `Link`, `NetworkConfig`, `SpectrumUtilization`.
- **Testes:**
  - as 21 verificações passam;
  - clássico e QKD dão os mesmos resultados da Parte 29, exceto as linhas novas;
  - com `disabledCores [5,6]`, os núcleos 5 e 6 ficam com uso 0 e nenhuma tentativa, sem erros.

### Launcher 1.12.0

Ver o README do launcher.

---

<a id="parte-29"></a>

## Parte 29: onde no espectro as requisições são bloqueadas


### Simulador: novo arquivo `<caso>_SlotBlocking.csv`

- **O que registra.** Para cada requisição, olha o candidato:
  - quando a requisição é aceita, o candidato é o circuito aceito;
  - quando é bloqueada, é o candidato que foi bloqueado.

  Os slots da faixa desse candidato (sem bandas de guarda), no núcleo dele, contam uma tentativa em cada enlace da rota.
  Se a requisição foi bloqueada, esses slots contam também um bloqueio da causa, a mesma do `BlockingDetails.csv`.
- **Formato.** `Metrics,LoadPoint,Link,Core,Replication,Cause,1..S`, só com contagens inteiras. Linhas só com zeros
  ficam de fora. As métricas são:
  - `Slot attempts`, `Slot blocked` e `Slot blocked by cause`, com uma coluna por slot;
  - `Requests on link and core`, `Blocked requests on link and core` e `... by cause`, com um valor só: cada
    requisição conta uma vez por enlace e núcleo, e não uma vez por slot;
  - `Blocked requests without spectrum band`: bloqueios sem candidato com faixa (sem slots livres, sem rota), que não
    têm lugar no espectro.
- **Como ligar e desligar.** A métrica `activeMetrics.SlotBlocking` vem ligada por padrão e só grava o arquivo se
  `BlockingProbability` também estiver ligada.
- **Classes alteradas:**
  - `qkd.metrics.BlockingDetailRecorder`: contadores e `toSlotCsv`;
  - `SimulationManagement.buildSlotBlockingCsv`;
  - `SimulationRequest`;
  - `SimulationFileManager`;
  - `SimulationConfig.Metrics`.
- **O que não mudou.** Nenhuma decisão de alocação, e nenhum dos CSVs que já existiam. As 21 verificações passam.
- **Cuidado ao comparar.** A fração por slot pesa cada requisição pelo número de slots e de enlaces que ela usa.
  Requisições longas e largas são bloqueadas mais vezes, então essa fração fica acima da probabilidade de bloqueio por
  requisição.

### Launcher 1.10.0

- **Modelos novos em Mapas de calor:**
  - bloqueadas ÷ tentativas em cada slot;
  - causa do bloqueio em cada slot (uma cor por causa);
  - só os bloqueios por QoTO.
- **Colunas à direita do mapa.** "Requisições bloqueadas/feitas" e a porcentagem, por núcleo de cada enlace.
- **Opções:**
  - o que cada slot mostra: fração, contagem ou causa;
  - a causa;
  - mostrar só os núcleos com bloqueio;
  - a cor dos slots sem tentativa.
- **Dica.** Passe o mouse sobre um slot para ver as tentativas, os bloqueios e as causas.

---

<a id="parte-28"></a>

## Parte 28: mudanças do coorientador e "Gerar JAR" no launcher


### Commit ec65200 ("Ajustes no XT.", Alexandre Fontinele, 15/10)

- **`Crosstalk.java` e `PhysicalLayer.java`.** No modelo de crosstalk junto com os outros efeitos
  (`crosstalkModel = 1`, `XT_TOGETHER`), o crosstalk passa a ser calculado em cada enlace, com o núcleo e o espectro
  daquele enlace (`calculateCrosstalkPowerInLink`), em vez de um valor da rota inteira. No modelo Johannisson
  (`physicalLayerModel = 1`), ele é dividido pelo número de modos de polarização.
- **`Amplifier.setNoiseFigure`.** Corrigido: o valor linear agora é calculado a partir do valor em dB. Antes era
  calculado a partir do valor linear antigo. O simulador não chama esse método hoje.
- **Arquivos removidos:** `network_qkd_snippet.txt` e `routesByPar.txt`.
- **Conflitos com a Parte 27:** nenhum. Os dois commits não têm arquivos em comum, e o código QKD não usa os métodos
  alterados.
- **Efeito nos resultados.** O mesmo caso de teste (com `crosstalkModel = 0` e `physicalLayerModel = 0`) deu CSVs
  idênticos com o JAR antigo e com o novo. As mudanças só aparecem com `crosstalkModel = 1` ou
  `physicalLayerModel = 1`.
- **Verificação.** O `SNetS-SDM-QKD.jar` foi gerado de novo com essas mudanças. As 21 verificações QKD passaram.

### Launcher 1.9.0: botão "Gerar JAR"

- **Classe nova.** O botão chama a classe `launcher.JarBuilder`, que roda como uma execução comum.
- **O que ela faz:**
  - compila todos os `.java` de `src/main/java`, menos `simulationControl/Main.java`, contra um JAR base;
  - monta o JAR novo a partir do JAR base, trocando as classes do projeto pelas recompiladas;
  - remove as classes antigas do projeto que não têm mais código-fonte;
  - guarda o JAR anterior em `simulations/backup_jar`;
  - opcionalmente, roda as verificações QKD.
- **Por que `Main.java` fica de fora.** Ele usa uma versão do Firebase mais nova que a do JAR atual e só compila com o
  Maven. Por isso a classe `Main` vem do JAR base.
- **Linha de comando:**
  `java -cp launcher/snets-launcher.jar launcher.JarBuilder --src src/main/java --base simulations/SNetS-SDM-QKD.jar --out simulations/SNetS-SDM-QKD.jar [--verify]`.

---

<a id="parte-27"></a>

## Parte 27: ocupação de cada slot e correções do launcher


### Simulador: novo arquivo `<caso>_SlotUtilization.csv`

- **O que é.** Para cada ponto de carga, enlace, núcleo e replicação, a fração das observações em que cada slot
  estava ocupado. É a mesma amostragem do `SpectrumUtilization`: uma observação a cada chegada de requisição.
- **Formato.** Cabeçalho `Metrics,LoadPoint,Link,Core,Replication,1,2,…,S`, com uma coluna por slot. Cada linha
  traz `Slot utilization`, o ponto de carga, o enlace `<a-b>`, o núcleo, a replicação e os S valores.
- **O que conta como ocupado.**
  - As bandas de guarda contam como ocupadas. Por isso a média de uma linha fica acima da `Utilization per Link and
    Core`, que conta só os slots dos circuitos.
  - O núcleo reservado à QKD é lido pela visão QKD do espectro, então aparecem os QChs e PIChs. A visão clássica
    mostraria esse núcleo como cheio.
- **Como ligar e desligar.** A métrica `activeMetrics.SlotUtilization` vem ligada por padrão e só grava o arquivo se
  `SpectrumUtilization` também estiver ligada. Casos antigos, sem a chave, também gravam o arquivo.
- **Tamanho.** Numa rede de 22 enlaces dirigidos × 7 núcleos × 320 slots, com 5 pontos × 5 replicações, o arquivo
  tem cerca de 4–5 MB.
- **Classes alteradas:**
  - `measurement.SpectrumUtilization`: contadores por enlace, núcleo e slot, e `appendSlotUtilizationRows`;
  - `SimulationManagement.buildSlotUtilizationCsv`;
  - `SimulationRequest.Result`: `slotUtilization` e `FILE_SLOT_UTILIZATION`;
  - `SimulationFileManager`;
  - `SimulationConfig.Metrics`: `SlotUtilization`.
- **O que não mudou.** Nenhuma decisão de alocação, e nenhum dos CSVs que já existiam.
- **Observação sobre um CSV antigo.** No `SpectrumUtilization.csv` de casos com QKD, a "Utilization Per Slot" (que é
  do SNetS original, não foi alterada) conta o núcleo QKD como sempre ocupado. Para a ocupação por slot, use o
  arquivo novo.

### Launcher 1.7.0

- **Gráficos › novo modelo "Ocupação dos slots de cada núcleo em cada enlace"** (mapa de calor do espectro).
  - Opções: ponto de carga, enlaces, núcleos, faixa de slots, ordem dos enlaces, escala de cores (contínua ou em 5
    faixas) e faixa da escala.
  - Ao passar o mouse sobre uma célula, aparece a ocupação.
- **Correção:** editar o rótulo de uma série tirava o foco da caixa a cada letra. O painel de séries era redesenhado
  porque a assinatura das séries incluía os nomes.
- **Correção:** "Cannot read properties of undefined (reading 'style')" no bloqueio por núcleo com causas, quando havia
  vários casos com mais causas do que casos. O estilo agora é buscado pela chave da série.
- Os grupos de caixas de seleção dos filtros novos usam `div` em vez de `label`. Assim, clicar no texto não aciona o
  primeiro botão do grupo.

### Parte 27b: verificação em pasta própria, diagnóstico do espectro e gráficos novos

#### Pasta `verificacao`, separada de `simulations`

- **Para onde foram os resultados.** O plano de verificação grava agora em `<projeto>/verificacao/QKD_ladder/<perfil>/<passo>/`,
  e não mais em `simulations/`. Cada passo tem casos SNetS, `checks.csv`, `data.csv`, `charts.csv`, `cases.txt`,
  `report.txt` e `status.txt`.
- **O artigo-base também mudou de lugar.** `QKD_reference` (Dibaj_MF e Dibaj_MF_SNetS) é lido de
  `verificacao/QKD_reference`. Quando ele ainda estiver em `simulations/QKD_reference`, o lugar antigo continua valendo.
- **Linha de comando.** `QKDLadderMain` aceita `--out <pasta>`. Sem a opção, usa a pasta `verificacao` ao lado de
  `simulations`.
- **Caminhos no `cases.txt`.** O `cases.txt` grava os casos como `@verificacao/…`, que é o prefixo que o launcher usa
  para essa pasta.
- **Verificadores.** Os verificadores do artigo-base procuram primeiro `verificacao/QKD_reference/Dibaj_MF`. Os demais
  verificadores só escrevem no console.

#### Diagnóstico do espectro dos bloqueios (BlockingDetails.csv)

- **Métrica nova.** `Blocking probability by spectrum diagnosis`, com a coluna `Cause`.
- **Quando e como é calculada.** Para cada requisição bloqueada, o simulador olha o espectro da rota do candidato
  bloqueado no momento do bloqueio. Usa o menor número de slots que alguma modulação com alcance suficiente exige e
  considera o melhor estado entre os núcleos clássicos:
  - `Spectrum: not enough free slots`: em todos os núcleos, algum enlace não tinha slots livres suficientes;
  - `Spectrum: continuity fragmentation`: cada enlace tinha slots livres suficientes, mas não os mesmos;
  - `Spectrum: contiguity fragmentation`: os slots livres comuns à rota bastavam, mas não eram vizinhos;
  - `Spectrum: free band available`: havia uma faixa contígua e contínua, então o bloqueio veio da camada física
    (QoT/XT) ou da proteção QKD;
  - `Spectrum: QKD side`: o lado QKD recusou um candidato válido;
  - `Spectrum: no route`: não havia rota.
- **Por que é útil.** As parcelas somam o bloqueio total e não dependem da ordem dos testes do algoritmo. Com o
  PRMSCA, a causa "Fragmentation" do SNetS quase nunca aparece, porque um candidato com faixa é sempre escolhido.
- **O que não mudou.** Nenhuma decisão de alocação nem os outros CSVs. A medição só acontece nos bloqueios.

#### Launcher 1.8.0

- **Árvore.** Os casos da pasta `verificacao` aparecem numa seção própria, "Verificação", abaixo de `simulations`.
  O seletor de casos do estúdio só os mostra quando se marca "Incluir os casos da pasta verificacao".
- **Modelos de gráfico agrupados** em Bloqueio, Causas de bloqueio, Núcleos, Espectro e utilização, Fragmentação,
  Mapas de calor, Correlações, QKD e Outros. Os nomes ficaram mais curtos e alguns modelos têm uma descrição. Os ids
  são os mesmos, então as figuras salvas continuam abrindo.
- **Modelos novos:**
  - diagnóstico do espectro (empilhado, em 100 % e × carga);
  - fragmentação externa × carga, sua entropia, fragmentação por enlace e fragmentação relativa por tamanho;
  - mapa de calor da fragmentação na topologia;
  - perfil de ocupação por slot, com uma linha por núcleo;
  - correlações, um tipo novo de dispersão:
    - por enlace: utilização × bloqueio, utilização × fragmentação e fragmentação × bloqueio;
    - por núcleo: utilização × bloqueio;
    - por carga: utilização × bloqueio e fragmentação × bloqueio;
    - em todos, com reta de ajuste e r de Pearson.
- **Nós confiáveis nas topologias.** Na aba Rede e nos mapas de calor, os nós confiáveis aparecem como pontos menores,
  a cada QTND km a partir da origem de cada sentido, como faz o planejador QKD. Dá para ligar e desligar e escolher a
  cor e o raio.
- **Correção.** Os mapas na topologia agora aceitam a dimensão `link` em minúsculas, como a do
  `ExternalFragmentation.csv`.
- **Casos novos.** O modelo de caso do launcher (`templates/simulation`) vem com `ExternalFragmentation` e
  `RelativeFragmentation` ligadas. Nos casos existentes, ligue as duas na aba Métricas.

---

<a id="parte-26"></a>

## Parte 26: pedidos do coorientador


### 1. Camada física QKD separada (arquivo e classes)

- **Novo arquivo de caso `qkdPhysicalLayer`**, só com os parâmetros QKD. O `physicalLayer` fica só com a camada física
  clássica. Os parâmetros estão agrupados:
  - `source`: μ, T_s, potência e largura do QCh;
  - `protocol`: f, e_d;
  - `detector`: η_d, p_dark, T_g, η_f;
  - `fiber`: α(λ);
  - `crosstalk`: crosstalk ligado ou desligado e modelo de transferência.

  Classe: `qkd.physical.QKDPhysicalLayerConfig`.
- **Compatibilidade.** Casos antigos, com os blocos `qkdPhysical`/`qkdReceiver` dentro do `physicalLayer`, continuam
  funcionando com os mesmos resultados. Se o arquivo novo existir, ele vale e os blocos antigos são ignorados (o
  simulador avisa). Depois de uma execução, o SNetS não recoloca mais os blocos QKD no `physicalLayer`.
- **Migração.**
  - No launcher: botão "Mover para a Camada física QKD" na aba Camada física.
  - Em lote: `java -cp SNetS-SDM-QKD.jar qkd.tools.QKDPhysicalLayerMigration <pasta> [--dry-run]`. Guarda uma cópia
    `physicalLayer.before_p26`.
- **Classes.**
  - `BB84DecoyModel` continua sozinho: QBER e SKR do BB84 com estados decoy.
  - O resto da física QKD foi reunido em `qkd.physical.QKDPhysicalLayer`: potência de XT por trecho, referência ao
    receptor, conversão em p_b e avaliação de trecho, enlace e rota.
  - Esta classe substitui `QKDCrosstalkPowerCalculator`, `QKDCrosstalkNoiseModel`, `QKDCrosstalkReceiverTransfer`,
    `QKDRoutePhysicalEvaluator` e `capacity.QKDLinkChannelEvaluator`, que foram removidas.
  - As equações e a ordem das contas não mudaram. O mesmo caso deu CSVs idênticos antes e depois.
  - Continuam separadas as classes de resultado (DTOs) e o enum `QKDCrosstalkReceiverTransferMode`.
- **Novo modo de transferência `attenuated`** (Moghaddam 2021, eq. 1): P_XT · 10^(−αL/10).

### 2. PICh segmentado e compartilhado

- **`network.qkd.pichMode`.**
  - `segment` (novo padrão): o PICh acompanha o QCh. É um PICh por enlace da rota QKD, terminando nos nós, compartilhado
    pelas sessões que usam o enlace, como o pool de QChs. Classe `qkd.capacity.QKDPichPool`.
  - `route`: comportamento das Partes 23–25, com um PICh dedicado por sessão e contínuo na rota. Com `route` os
    resultados são idênticos aos da Parte 25 (conferido).
- **`network.qkd.pichSessionsPerChannel`**: sessões por PICh (0 = ilimitado).
- **Métricas novas no QKDMetrics.csv**: `Mean open PIChs`, `Peak open PIChs`.

### 3. Transmissores e receptores quânticos (QTx/QRx)

- **Uso.** Cada trecho de um QCh ativo usa um QTx no nó de origem e um QRx no nó de destino, seja ele um nó da rede ou
  um nó confiável intermediário (`QKD_TN[...]`). A contagem é feita em `QKDSharedResourceManager`.
- **Limites opcionais** (0 = ilimitado):
  - `network.qkd.qTransmittersPerNode` e `qReceiversPerNode`;
  - `qTransmittersPerTrustedNode` e `qReceiversPerTrustedNode` (−1 = igual aos nós da rede);
  - valor próprio por nó nos campos `qTransmitters` e `qReceivers` da lista `nodes`.
- **Nova causa de bloqueio.** Uma sessão sem QTx/QRx livre é bloqueada com a causa `[QTRX]`:
  - QKDMetrics: `QKD blocking probability by lack of QTx/QRx`;
  - BlockingDetails: `QKD QTx/QRx`.
- **Novas métricas por nó**: `Mean/Peak QTx/QRx in use per node`, mais os totais da rede.
- **Causas reclassificadas** nas granularidades link e span: a falta de espectro para abrir um QCh, antes contada como
  `[SKR]`, agora aparece como `[SPECTRUM]`. As decisões não mudam.

### 4. Compartilhamento dos QChs e TDM

- **`network.qkd.qchSharing`** (granularidades link e span):
  - `shared` (padrão): a sessão usa primeiro a taxa de chave que sobra nos QChs ativos;
  - `dedicated`: cada sessão abre o seu próprio QCh.
- **`tdmSlots = 0`** continua desligando o TDM. A explicação foi incluída no launcher.

### 5. Referências no código

As classes e os métodos de cálculo QKD citam no Javadoc as referências pela chave (DIBAJ24, MOGH21, LACERDA24,
NOTA-RX, MORI26, AHMED26a…). A tabela com chaves, arquivos de `docs/artigos` e onde cada referência é usada está em
`docs/REFERENCIAS_QKD.md`.

### 6. Cenários de verificação de dois artigos

Novos passos no plano de verificação (launcher: Verificação QKD › Plano de verificação):

- **Fase M**, Moghaddam, Beyranvand e Salehi (IEEE JSAC 2021):
  - **M1**: SKR × comprimento da Fig. 5, com contagem escura e XT. Os alcances batem com o artigo: 299 km contra ≈ 300 km
    e 204 km contra ≈ 205 km. Com XT de h = 6,3e-10, deu 129 km contra ≈ 115 km. Com XT de h = 4,6e-11 (p_dark =
    1e-6), deu 201 km contra ≈ 190 km.
  - **M2 e M3**: SKR média na admissão nas redes da Fig. 6. O SNetS coincide com a média independente: 43,56 contra
    43,58 Mbit/s e 4,761 contra 4,754 Mbit/s. Com o fator ½ de peneiramento, fica −12 % e +8 % dos valores do artigo.
- **Fase Q**, QKD-Analytical (Ahmed, Chatterjee e Oki, IEEE INFOCOM 2026):
  - **Q1**: as regras de alocação foram reconstruídas e reproduzem exatamente o exemplo da Fig. 2: 1200 sucessores do
    estado vazio, 34193 estados e as taxas λ/162 e λ/192.
  - **Q2**: a CTMC exata coincide com o Monte Carlo quando as saídas têm taxa n·μ. A eq. (3) lida ao pé da letra
    subestima o bloqueio.
  - **Q3**: Fig. 3(a), com |W| = 6, por Monte Carlo. A ordem de grandeza bate em carga alta. Em carga baixa o bloqueio
    sai até 3–4× menor, e o CS-FF sai abaixo do artigo.

Novos pacotes: `qkd.analytical` (modelo, CTMC e Monte Carlo) e `qkd.tools`.

---

<a id="parte-25"></a>

## Parte 25: detalhes do bloqueio (BlockingDetails.csv)


Toda simulação com a métrica BlockingProbability ativa (clássica ou QKD) grava agora `<caso>_BlockingDetails.csv`.
O novo arquivo só observa as decisões do plano de controle e não muda nenhuma delas. Os demais CSVs ficam
idênticos aos de antes; isso foi conferido com o mesmo caso rodado no jar anterior e no novo.

### Conteúdo

Layout SNetS: `Metrics,LoadPoint,Class,Core,Cause,Link, ,rep0..`.

- **Dimensões**
  - `Class`: `all` (todas as requisições), `classical` (não protegidas) ou `qkd` (protegidas). As duas últimas só
    aparecem quando há QKD.
  - `Cause`: as causas do SNetS (Fragmentation, QoTN, QoTO, Crosstalk, Crosstalk in other, Lack of
    transmitters/receivers), mais:
    - `Lack of free spectrum`: o "other" do SNetS quando algum enlace da rota não tem nenhum slot livre no núcleo do
      candidato (`isBlockingByLackOfFreeSpectrum`);
    - `Other`: o restante do "other" do SNetS;
    - causas QKD, para um candidato clássico válido recusado pelo addon: `QKD session protection` (protegeria mal
      uma sessão ativa), `QKD QCh spectrum`, `QKD SKR`, `QKD PICh` e `QKD other` (falha QKD de uma requisição
      protegida).
- **Métricas**
  - `Blocking probability` e `Requests` por classe.
  - `Blocking probability by cause` por classe. As causas somam o bloqueio da classe.
  - `Blocking probability per core`, `... per core by cause` (bloqueadas no núcleo ÷ requisições do núcleo, mesma
    regra do SNetS) e `Share of all requests blocked per core by cause` (÷ todas as requisições, que soma o bloqueio
    total).
  - `Link blocking probability` e `Link requests` por enlace direcional e classe, e `Link blocking probability by QKD
    causes`. Uma requisição conta em todos os enlaces da rota tentada.
- **Transiente:** é o mesmo dos CSVs clássicos (10 % das requisições).

### Arquivos

- **Novos**
  - `qkd/metrics/BlockingDetailRecorder.java`: contadores e CSV.
  - `qkd/metrics/ObservedClassicalControlPlane.java`: o `ControlPlane` clássico sem mudanças. Só chama o
    registrador depois de `handleRequisition`.
- **Alterados**
  - `QKDControlPlane`: guarda a causa QKD da última recusa (proteção, precommit ou fallback) e registra cada
    requisição.
  - `simulator/Simulation.java`: o caminho clássico transparente usa `ObservedClassicalControlPlane`.
  - `SimulationManagement`, `SimulationRequest` e `SimulationFileManager`: montam, guardam e leem o novo arquivo.
- **Inalterados:** `GPRMCSA.java`, `CSBASDM.java`, `ControlPlane.java` e o jar SBRC26.

### Conferências feitas

As conferências usaram qTest_Maluco, qTest_C e qTest (1500 E), com 4000 e 10000 requisições.

- `Blocking probability` (all) e `per core` são idênticos aos do BlockingProbability.csv.
- O "other" clássico é igual a falta de espectro + outros + causas QKD. Exemplo: 0,733 % = 0,644 % (QKD SKR) +
  0,089 % (proteção).
- `Blocking probability by cause` na classe qkd é igual ao QKDMetrics: a classe qkd somada às causas clássicas é
  igual ao "QKD blocking probability by classical resources".

---

<a id="parte-24"></a>

## Addon QKD — Parte 24: plano de verificação (do artigo-base ao cenário completo)


Objetivo: uma escada de cenários que parte da reprodução do artigo-base (Dibaj et al., 2024, cenário
multifibra) e chega a um cenário SDM/QKD completo numa topologia grande. Cada passo muda um único
fator e tem critérios de aprovação. Há também verificações extras com resposta conhecida.

Componentes clássicos protegidos (CLASSICAL_BASE_SHA256.txt): nenhum foi alterado.

### Como executar

- **Pelo launcher:** botão "Verificação QKD" › painel "Plano de verificação".
  - Cada passo tem o botão "Executar".
  - Também há "Executar fase" e "Executar tudo", ambos em fila.
  - O botão "Resultados" mostra os critérios, os gráficos e os casos do passo.
- **Pela linha de comando**, a partir da pasta `simulations`:

  ```bat
  java -cp SNetS-SDM-QKD.jar qkd.ladder.QKDLadderMain B2 . --profile rapido
  java -cp SNetS-SDM-QKD.jar qkd.ladder.QKDLadderMain list
  ```

  Opções:
  - `--profile rapido|completo`;
  - `--threads N`: total de threads por simulação;
  - `--force`: simula de novo mesmo os casos que já têm resultado.

Perfis:

| | Rápido | Completo |
|---|---|---|
| Valores de QTND | 10, 20, 25, 30, 40 km | 5 a 50 km |
| Replicações | 3 | 10 |
| Chegadas por replicação (passos dinâmicos) | 20 000 | 100 000 |
| Uso | conferir a cadeia (minutos por passo) | resultados finais (pode levar horas) |

Os casos ficam em `simulations/QKD_ladder/<perfil>/<passo>/<caso>`.

- São casos SNetS comuns: podem ser abertos no launcher, com gráficos, mapa de calor dos enlaces e
  diagrama dos núcleos.
- A pasta de cada passo guarda os arquivos de resultado do plano:
  - `checks.csv`: critérios, com status OK / FALHA / AVISO / INFO;
  - `data.csv` e `charts.csv`: dados dos gráficos;
  - `cases.txt`: lista dos casos;
  - `report.txt`: relatório;
  - `status.txt`: status geral do passo.
- Um passo que depende de outro (por exemplo, o B2 compara com o B1) simula os casos que faltam e
  reaproveita os que já existem.
  - O reaproveitamento é identificado por um hash da configuração (`.ladder_key`), porque o SNetS
    regrava os arquivos de configuração ao final.

### Passos

| Passo | Muda | Critérios principais |
|---|---|---|
| A1 | — (reprodução nativa do artigo) | Curva de Q-TRx em U; ótimo a ≤ 5 km do Python; ótimo de cada replicação a ≤ 5 km de 25 km; MAE ≤ 10 Q-TRx; bloqueio nulo |
| A2 | Ativa as métricas de bloqueio e utilização | Resultados idênticos ao A1; bloqueio nulo |
| A3 | Carga × fatores (QTND 25 km) | Bloqueio não decrescente; tráfego em que o bloqueio chega a 1 % comparado ao artigo |
| B1 | MCF de 2 núcleos (0 clássico, 1 QKD), sem XT/OSNR | Clássico idêntico a um gêmeo sem requisições protegidas; PXT = 0 |
| B2 | + XT | O XT age (recusas para proteger QChs); bloqueio QKD ≥ B1 |
| B2L | XT com acoplamento 1e-12 | Idêntico ao B1 |
| B3 | + OSNR/QoT | Bloqueio por QoT presente |
| C1 / C2 | MCF de 7 núcleos, QKD na borda (1) / no centro (0) | Vizinhança; XT age; bloqueio no centro ≥ na borda |
| D1 | Tráfego dinâmico (Poisson, várias cargas) | Bloqueio não decrescente; lei de Little; bloqueio QKD = clássico com 100 % protegidas; conservação |
| D2 | Dobro de chegadas e 20 % de aquecimento | Compatível com o D1 |
| E1–E4 | Modo route; + rotas alternativas; + PICh; + demanda por gerador | SKR ≥ demanda; alternativas não pioram; PICh; fração protegida ≈ 0,2 |
| F1 / F2 | Cenário completo (modelo clássico da qualificação) em 5 nós / USA | Todos os anteriores numa rede grande |
| X1 | Erlang-B (enlace único) | Bloqueio = fórmula |
| X2 | Nós confiáveis | = Σ ⌈L/QTND⌉ − 1; monotonia; QTND ≥ maior enlace ⇒ 0 |
| X3 | SKR independente (sem simulação) | Diferença ≤ 1e-9; Tabela 5; ótimo da Fig. 5(b) |
| X4 | Limites | Fração 0 ⇒ nenhum QKD e núcleo QKD intocado; demanda → 0 ⇒ sem bloqueio por SKR |
| X5 | Monotonia | Mais demanda ⇒ mais bloqueio por SKR; mais núcleos ⇒ menos bloqueio clássico |
| X6 | Reprodutibilidade | Mesmos resultados com a mesma semente, inclusive com outro número de threads |
| X7 | Conservação em todos os casos do plano | Aceitas + bloqueadas = tentativas; causas somam o total |
| X8 | Simetria dos sentidos (D1) | Diferença média entre sentidos ≤ 25 % |

### Escolhas de modelagem dos passos SDM

- **Carga dos passos B e C**
  - A carga é a do artigo × (núcleos clássicos ÷ 10) × 2.
  - O fator 2 é o ponto de 1 % de bloqueio encontrado no A3.
  - Com essa carga, o espectro clássico chega à região dos QChs e o crosstalk passa a ter efeito.
- **Nova opção `network.qkd.spanLanePlacement`** (modos span e link)
  - `"first"` é o padrão e mantém o artigo-base inalterado.
  - `"wavelength"` coloca cada QCh no slot do seu comprimento de onda da grade, no meio da banda.
    Assim, a sobreposição espectral com os núcleos vizinhos é coerente com o λ usado na SKR.
  - Com `"first"`, os QChs ocupam os slots 1, 2, …, que ficam sob o canal reservado ao PICh e nunca
    recebem crosstalk.
- **Limiares de XT clássicos nos passos B a E**
  - Seguem o modelo do artigo, com limiares de XT 0 dB.
  - O crosstalk clássico → clássico entra na fase F, com o modelo da qualificação (qabne, que evita XT).
  - O qrmsta do artigo coloca os circuitos no mesmo canal de todos os núcleos. Com limiares reais,
    ele bloquearia quase tudo por XT.
- **Passos dinâmicos**
  - Usam 240 slots por enlace (40 canais de 75 GHz).
  - Assim cada replicação cobre várias durações médias, com algumas centenas de conexões
    simultâneas, e não milhares.

### Observações dos primeiros resultados (perfil rápido)

- **A3:** o bloqueio de 1 % aparece com cerca de 2× o tráfego do artigo (≈ 1250–1340 Tbit/s
  contra 612–654 Tbit/s).
  - O modelo de capacidade clássica da reconstrução (10 fibras × 159 canais, K = 2) tem mais folga
    que a do artigo.
  - É uma diferença de suposição, não de implementação.
- **B2:** um canal clássico de 0 dBm no núcleo vizinho, na mesma frequência do QCh, leva p_b → 1 e a
  SKR → 0.
  - Só são admitidas sessões sem sobreposição espectral.
  - Os candidatos clássicos que cairiam sobre um QCh ativo são recusados, o que dá até ~35 % de
    bloqueio.
  - Esse é o custo da coexistência sem coordenação espectral entre núcleos.
- **D1:** com 100 % das requisições protegidas e o mesmo aquecimento, o bloqueio QKD é idêntico ao
  bloqueio clássico.
  - A lei de Little vale com erro de até ~2,5 %.
  - Com poucas chegadas por replicação, o transitório inicial puxa a ocupação para baixo; o D2 mostra
    isso.

### Desempenho

`QKDControlPlane` reavaliava o estado físico de todas as sessões QKD ativas a cada circuito clássico
alocado ou liberado. Agora:

- só reavalia as sessões com QCh num enlace do circuito que mudou, porque o crosstalk de um enlace
  depende apenas dos circuitos daquele enlace;
- cada QCh compartilhado é avaliado uma vez por atualização.

Os resultados são idênticos, inclusive a coluna ActiveRefreshes. O caso C1 (7 núcleos, XT) caiu de
160 s para 33 s.

---

<a id="parte-23"></a>

## Addon QKD — Parte 23: cenário de estudo da qualificação


Objetivo: permitir simular no SNetS o cenário dos slides 26 a 37 da apresentação de qualificação
("Requisição Protegida por QKD"). A Parte 23 também acrescenta, no launcher, o modo de execução
Clássica/QKD.

Componentes clássicos protegidos (CLASSICAL_BASE_SHA256.txt): nenhum foi alterado.

### Cobertura dos slides

| Slide | Elemento | Situação antes | Parte 23 |
|---|---|---|---|
| 24–25, 28 | QTND, nós confiáveis, Q-TRx | já existia (QKDTopologyPlanner) | planejamento exibido no launcher; nº de nós confiáveis no QKDMetrics |
| 26, 36 | Admissão conjunta e proteção das sessões ativas | já existia | sem mudança |
| 27, 33–35 | Demanda de chave por requisição (ex.: 2 Mbit/s) | só fração global e classes de demanda | `keyRateMbps` e `qkdProtectedProbability` em cada gerador do `traffic` |
| 29 | Rota QKD alternativa | QKD sempre seguia a rota clássica | `network.qkd.alternativeRoutes` = k rotas alternativas (Yen) |
| 30 | QCh no núcleo 1 e PICh num núcleo clássico | PICh não modelado no SDM | `network.qkd.pichSlots` = slots do PICh por sessão |
| 31–35 | Mesmo slot em todos os trechos e SKR por trecho ≥ demanda | QCh independente por enlace/trecho | `network.qkd.qchGranularity = "route"` |

### Novas opções no arquivo `network` (bloco `qkd`)

```json
"qkd": {
  "enabled": true, "qtndKm": 30.0, "quantumCoreId": 1,
  "qchGranularity": "route",
  "qchSlotPolicy": "firstfit",
  "wavelengthPolicy": "grid",
  "alternativeRoutes": 2,
  "pichSlots": 1
}
```

- **`qchGranularity`**: o valor `"route"` dá um QCh dedicado por sessão, com os mesmos slots contíguos do
  núcleo QKD em todos os enlaces (e trechos) da rota QKD.
  - As faixas livres em todos os enlaces são testadas em ordem. Para cada faixa, a SKR de todos os trechos
    é calculada no comprimento de onda daquela faixa, com a interferência clássica atual mais a do candidato
    pendente. É alocada a primeira faixa cuja SKR mínima atende à demanda de chave.
  - Com `wavelengthPolicy = "grid"`, o comprimento de onda do slot vem da grade do núcleo, centrada no mínimo
    de atenuação. Com `"fixed"`, todos os slots usam λ de mínima atenuação; aí só o crosstalk diferencia os
    slots.
  - Os modos `link` e `span` continuam existindo e são os usados na reprodução do artigo-base.
- **`qchSlotPolicy`**:
  - `firstfit`: menor índice de slot primeiro, como no slide 31.
  - `lowestloss`: começa pelo slot mais próximo de 1550 nm.
- **`alternativeRoutes`** (padrão 0): se o QCh não puder ser provisionado na rota do circuito clássico, o
  simulador tenta as k menores rotas alternativas por comprimento (algoritmo de Yen, determinístico). O
  circuito clássico continua na rota escolhida pelo RMSCA.
- **`pichSlots`** (padrão 0): o PICh de cada sessão ocupa os mesmos slots de um núcleo clássico em todos os
  enlaces da rota QKD.
  - A escolha é first-fit: o núcleo clássico de menor índice e, nele, o slot de menor índice.
  - O espectro do candidato clássico pendente é tratado como ocupado.
  - O PICh ocupa espectro, mas a potência dele não entra no cálculo do crosstalk.

### Novas opções no arquivo `traffic` (por gerador, opcionais)

```json
{ "source": "2", "destination": "3", "bitRate": 1.0E11, "arrivalRate": 2.0, "holdRate": 1.0,
  "arrivalRateIncrease": 1.0, "qkdProtectedProbability": 0.2, "keyRateMbps": 2.0 }
```

- `qkdProtectedProbability`: fração das requisições do gerador protegidas por QKD. Quando ausente, vale
  `simulation.qkdProtectedRequestProbability`.
- `keyRateMbps`: demanda de chave fixa. Quando ausente, a demanda é sorteada de `simulation.qkdKeyDemand`.
  O sorteio da política continua sendo consumido, para manter as sequências aleatórias alinhadas.

### Nova métrica: `QKDMetrics.csv`

- Usa o formato padrão do SNetS (`Metrics,LoadPoint,src,dest, ,rep0..`). Por isso pode ser lida pela aba
  Resultados e pelo Estúdio de gráficos.
- É ativada por `activeMetrics.QKDMetrics` (padrão `true`) e só é gravada com QKD habilitado.
- Linhas:
  - bloqueio QKD (total e por causa: recursos clássicos, espectro do QCh, SKR, PICh, outros);
  - aceitação e fração de requisições protegidas;
  - candidatos clássicos rejeitados para proteger sessões QKD;
  - fração de sessões em rota alternativa;
  - SKR e QBER na admissão, demanda média de chave;
  - QChs, pares Q-TRx e sessões ativos (média PASTA e pico);
  - Q-TRx, saltos e km médios por sessão;
  - nós confiáveis planejados e QTND;
  - bloqueio QKD por par.
- O `QKDStatistics.csv` (formato antigo, uma linha por replicação) continua sendo gravado.

### Verificação

- `qkd.verification.QKDQualificationScenarioVerification` refaz o cenário dos slides e confere:
  - topologia de 5 nós, MCF de 7 núcleos, núcleo 1 reservado e QTND de 30 km;
  - os trechos e os 13 nós confiáveis;
  - o QCh no mesmo slot em todos os trechos, com SKR ≥ 2 Mbit/s;
  - o PICh no núcleo 0;
  - a rota alternativa quando 1→3 não tem slot QKD livre;
  - as causas de bloqueio `[SPECTRUM]` e `[SKR]` e a liberação completa;
  - os campos QKD do tráfego;
  - uma simulação completa do SNetS que grava `QKDMetrics.csv`.
- Os 21 verificadores passam, inclusive a reprodução nativa do artigo-base, que manteve os mesmos resultados.

### Caso-exemplo

`simulations/QKD_qualificacao/Exemplo_5nos_QTND30`:

- 20 % de requisições protegidas e 2 Mbit/s de chave;
- `qabne`, QoT/XT ligados;
- 5000 requisições, 3 pontos de carga e 3 replicações.

Na nuvem (2 núcleos) leva cerca de 2 min 40 s. O bloqueio clássico ficou entre 1 % e 14 % e o bloqueio QKD
entre 1 % e 14 %, todo por recursos clássicos nesta carga.

### Observações para a análise

- Com a SKR testada slot a slot, as sessões QKD tendem a evitar os slots sobrepostos por circuitos
  clássicos dos núcleos vizinhos. Por isso a QBER de admissão fica próxima de `e_d`. O bloqueio por SKR só
  aparece quando não há faixa livre sem interferência, ou quando a demanda de chave é alta.
- Com `wavelengthPolicy = "grid"` e `firstfit`, o slot 1 fica na ponta da grade (≈1566 nm), onde a SKR é
  menor. `qchSlotPolicy = "lowestloss"` prioriza os slots perto de 1550 nm.
- O custo por requisição cresce com o número de sessões QKD ativas: cada candidato clássico é avaliado contra
  todas elas. Cargas com centenas de sessões simultâneas levam minutos por ponto de carga.

---

<a id="parte-21-22"></a>

## Entrega QKD - Partes 21 e 22 (+ interface)


Continuacao das Partes 18-20. Objetivo: fechar a camada QKD do simulador segundo a
qualificacao, permitir verificar o artigo-base (Dibaj et al., 2024, cenario multifibra)
dentro do proprio SNetS e concluir a interface (`launcher/`).

Os componentes classicos protegidos em `CLASSICAL_BASE_SHA256.txt` (GPRMCSA.java,
CSBASDM.java e SNetS-SDM-SBRC26.jar) nao foram alterados.

### 1. Parte 21 - QChs por trecho QTND (modelo do artigo-base)

Ate a Parte 20 cada QCh cobria um enlace inteiro (capacidade = trecho gargalo, Q-TRx = numero
de trechos do enlace). A qualificacao (Secao 4.3) e o artigo-base usam um conjunto de QChs por
trecho QTND. Os dois modelos agora existem e sao escolhidos em `network.qkd`:

| Campo | Valores | Padrao | Efeito |
|---|---|---|---|
| `qchGranularity` | `link`, `span` | `link` | `span`: um pool de QChs por trecho; Q-TRx = QChs ativos somados sobre os trechos |
| `undirectedChannels` | `true`/`false` | `false` | os dois sentidos de um par de fibras compartilham os pools (chave simetrica) |
| `tdmSlots` | inteiro >= 0 | `0` | `0` = compartilhamento continuo pela taxa; `N` = cada demanda reserva ceil(d/C*N) slots de um unico QCh (sem dividir a demanda entre QChs) |
| `wavelengthPolicy` | `fixed`, `grid` | `fixed` | `grid`: o k-esimo QCh de um trecho usa o k-esimo melhor comprimento de onda da grade de 12,5 GHz |

Com os padroes o comportamento das Partes 1-20 (C2/C3) e identico; todos os verificadores
antigos continuam passando. Verificador novo: `QKDSpanGranularityVerification`.

### 2. Parte 22 - artigo-base executado pelo motor do SNetS

`qkd.reference.QKDNativeReferenceMain` gera 30 casos SNetS comuns
(`simulations/QKD_reference/Dibaj_MF_SNetS`), executa-os com `simulationControl.Main` e
compara com a reconstrucao em Python e com a Fig. 7 digitalizada. Requisicoes, rotas,
alocacao classica, segmentacao QTND, TDM e contagem de Q-TRx sao todos produzidos pelo
simulador (nao ha mais dependencia dos traces roteados nesse modo).

| Topologia | QTND otimo SNetS | Python | Artigo | MAE vs Python | MAE vs artigo |
|---|---|---|---|---|---|
| Small Core | 25 km (7/10 replicacoes; 30 km em 3/10) | 25 km | 25 km | 5,1 Q-TRx | 16,2 Q-TRx |
| Butterfly | 30 km (10/10) | 30 km | 25 km | 3,8 Q-TRx | 11,5 Q-TRx |
| Metro | 25 km (10/10) | 25 km | 25 km | 2,4 Q-TRx | 13,9 Q-TRx |

Sem bloqueio classico ou QKD (como na reconstrucao). Verificador: `QKDNativeArticleBaseVerification`.
A verificacao fisica agora grava a Tabela 5 da qualificacao e SKR x mu com status por valor.

Componentes novos usados pelo cenario de referencia (todos no namespace do addon):

- `qrmsta` (`qkd.algorithms.integrated.QKDReferenceRMSTA`): RMSTA classico do artigo -
  K caminhos, modulacao por distancia, first fit na grade de canais de 75 GHz, canal do PICh
  reservado e escolha do caminho com menor indice de canal final.
- `qfirstfit` (`qkd.algorithms.coreSpectrumAssignment.QKDFirstFit`): first fit sobre os
  nucleos classicos (o nucleo QKD nunca e candidato).
- `physicalLayer.qkdPhysical`: parametros BB84 (mu, Ts, eta_ec, e_d, eta_d, p_dark, largura
  do QCh, alpha(lambda)) configuraveis por arquivo; ausente = valores do artigo.
- `simulation.qkdExactRequestCount`: processa exatamente `requests` chegadas (ver item 4).

### 3. Metricas e metodologia

- Warm-up QKD: os contadores do `QKDStatistics.csv` agora descartam o transiente com a mesma
  regra das metricas classicas do SNetS (10% das chegadas). Configuravel em
  `simulation.qkdWarmupFraction` (0 para estudos semiestaticos).
- Novas colunas no fim do `QKDStatistics.csv` (as antigas foram mantidas na mesma ordem):
  `QKDAcceptanceRatio`, `QKDBlockingProbability`, `MeanActiveQChs`, `MeanActiveQTRxPairs`,
  `MeanActiveQKDSessions` (media nas chegadas apos o warm-up; por PASTA estima a media
  temporal), `QTND_km`, `WarmupArrivals`.
- Desempenho: quando `qkdReceiver.intercoreCrosstalkEnabled = false` (MF ou C2), p_b = p_dark
  nao depende do estado classico; a protecao/atualizacao das sessoes ativas a cada mudanca
  classica e pulada (antes era O(sessoes) por alocacao). O cenario de referencia passou de
  ~90 s para ~2 s por caso.

### 4. Pontos de atencao encontrados na auditoria

1. Cauda de chegadas: o SNetS para de gerar quando `requests` chegadas foram geradas, mas cada
   gerador ja tem uma chegada agendada. Com 910 geradores (NSFNet, 5 taxas) e `requests = 100`,
   cada replicacao processa ~1010 chegadas - ~90% vem dessa cauda. Isso vale para C1, C2 e C3
   atuais. Antes da campanha final: aumentar `requests` (>> numero de geradores) ou, em
   cenarios QKD, ativar `qkdExactRequestCount`. A interface mostra um aviso.
2. `SimulationManagement`: o contador de replicacoes concluidas era incrementado sem
   sincronizacao (podia travar a espera final) e uma replicacao com excecao travava o programa.
   Agora o contador e sincronizado e uma falha encerra a execucao com mensagem e codigo != 0.
3. No C3 (XT ligado) com os parametros atuais o bloqueio QKD e muito alto (83-96% nas
   replicacoes do teste reduzido): e um resultado do modelo, mas merece revisao das hipoteses
   do receptor (eta_f = 1, Tg = 100 ps, receiver_referenced) antes do artigo. A aba
   "Varredura" da interface gera os casos de sensibilidade.

### 5. Interface (`launcher/`)

- Pagina "Verificacao QKD": executa os 20 verificadores em fila e as tres verificacoes do
  artigo-base (fisica, traces Python e SNetS nativo) com tabelas e graficos (SNetS x Python x
  artigo, absoluto e normalizado).
- Aba "Varredura": cria casos variando um ou dois campos (QTND, eta_f, modelo de transferencia,
  Tg, fracao protegida, algoritmo...), com execucao em fila.
- Fila de execucao, geracao de `.sh`, leitura do `QKDStatistics.csv` nos graficos e eixo x
  "Parametro dos casos" para comparar varreduras.
- Formularios com os novos campos QKD e botoes para adicionar campos conhecidos ausentes.

### Arquivos

Modificados: `simulator/Simulation.java`, `simulationControl/SimulationManagement.java`,
`simulationControl/parsers/{NetworkConfig,PhysicalLayerConfig,SimulationConfig}.java`,
`qkd/algorithms/QKDAlgorithmFactory.java`, `qkd/capacity/{QKDChannelShare,QKDSharedChannel,
QKDSharedResourceManager}.java`, `qkd/control/{QKDControlPlane,QKDExistingSessionImpactEvaluator}.java`,
`qkd/metrics/QKDMetricsSnapshot.java`, `qkd/network/{QKDMesh,QKDMeshFactory,QKDNetworkParameters}.java`,
`qkd/reference/QKDReferencePhysicalMain.java`, `qkd/traffic/QKDRequestGenerator.java`,
`qkd/verification/QKDExistingSessionProtectionVerification.java` (fixture com warm-up 0),
`simulations/SNetS-SDM-QKD.jar` (copia anterior em `simulations/backup_jar/`),
`simulations/QKD_reference/README.md`.

Adicionados: `qkd/algorithms/integrated/QKDReferenceRMSTA.java`,
`qkd/algorithms/coreSpectrumAssignment/QKDFirstFit.java`, `qkd/traffic/QKDArrivalBudget.java`,
`qkd/reference/QKDNativeReferenceMain.java`, `qkd/verification/QKDSpanGranularityVerification.java`,
`qkd/verification/QKDNativeArticleBaseVerification.java`,
`simulations/QKD_reference/Dibaj_MF/run_native_reference.bat`,
`simulations/QKD_reference/Dibaj_MF_SNetS/` (30 casos + resultados),
`docs/VALIDACAO_QKD_PARTES1_A_22.txt`, este arquivo.

O JAR foi compilado com `javac --release 8` a partir de `src/` usando as dependencias contidas
no JAR anterior (o `Main.class` original foi mantido, pois `Main.java` nao mudou).

---

<a id="parte-18-20"></a>

## Entrega QKD - Partes 18, 19 e 20


Esta entrega foi aplicada diretamente sobre o arquivo `SNetS-SDM-qkd-v1.rar` enviado pelo usuario. A estrutura original `simulations/` foi preservada; nao foi criada uma pasta paralela chamada `scenario`.

### Arquivos modificados

1. `src/main/java/qkd/verification/QKDArticleBaseReferenceVerification.java`
   - caminho padrao da verificacao do artigo-base atualizado para `simulations/QKD_reference/Dibaj_MF`.

2. `simulations/NSFNet_C2_QKD_NoXT/simulation`
   - `coreAndSpectrumAssignment` alterado de `csbasdm` para `qabne`.

3. `simulations/NSFNet_C3_QKD_XT/simulation`
   - `coreAndSpectrumAssignment` alterado de `csbasdm` para `qabne`.

4. `simulations/NSFNet_QKD_test/simulation`
   - `coreAndSpectrumAssignment` alterado de `csbasdm` para `qabne`.

5. `target/classes/`
   - recompilado para refletir o codigo-fonte atual.

### Arquivos/pastas adicionados

- `simulations/SNetS-SDM-QKD.jar`
  - executavel separado para o addon QKD.
  - o JAR classico original `SNetS-SDM-SBRC26.jar` foi mantido.

- `simulations/run_SNetS-SDM-QKD_jar.bat`
  - executa um cenario QKD pelo nome.

- `simulations/QKD_reference/`
  - dados, traces, scripts e resultados da verificacao do artigo-base Dibaj MF.

- `simulations/QKD_experiments/`
  - Parte 20: preparacao automatica de sweep QTND, smoke test, execucao C1/C2/C3, analise estatistica e geracao de graficos.

- `README_QKD_ADDON.md`
  - guia rapido da arquitetura addon e execucao.

- `CLASSICAL_BASE_SHA256.txt`
  - hashes dos principais componentes classicos preservados.

- `docs/VALIDACAO_QKD_PARTES1_A_20.txt`
  - saida dos verificadores executados nesta entrega.

- `docs/Estado_Atual_Implementacao_QKD_SNetS.docx`
  - documento tecnico com tudo que foi implementado, objetivo final e pendencias.

### Arquivos removidos

**Nenhum arquivo-fonte classico foi removido.**

O algoritmo classico `CSBASDM/ABNE` e o factory classico `GPRMCSA` nao foram alterados nesta entrega. A adaptacao QKD usa `qkd.algorithms.coreSpectrumAssignment.QABNE` e `QKDAlgorithmFactory`.

### Validacoes executadas

- Compilacao Java 8 da arvore atual: OK.
- Todos os verificadores QKD disponiveis: OK.
- Parte 18, isolamento ABNE/qABNE: OK.
- Parte 19, artigo-base: 300/300 observacoes Q-TRx iguais a reconstrucao Python.
- Smoke C1/C2/C3 com JAR QKD: os tres cenarios chegaram a `Finish!`.
- Gerador de sweep QTND: 20 cenarios gerados corretamente (C2/C3 x 10 QTNDs).

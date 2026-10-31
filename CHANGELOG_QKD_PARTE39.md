# Parte 39 — canal QKD bloqueado, nós confiáveis por enlace, qabne e launcher 1.17.0

## Simulador

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

## Launcher 1.17.0

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

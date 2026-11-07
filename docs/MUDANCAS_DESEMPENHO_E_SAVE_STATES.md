# Mudanças de desempenho e save states no SNetS-SDM-QKD

Este documento registra o que foi alterado no código do simulador para:

- deixar as simulações mais rápidas;
- medir o custo computacional dos algoritmos;
- gravar o estado das simulações (save states) e continuar execuções interrompidas.

O objetivo é que outras pessoas do laboratório possam revisar cada mudança: o que era, o que ficou, por que o resultado não muda e como isso foi conferido.

## 1. Versões envolvidas

| Versão | Onde está | Papel neste documento |
|---|---|---|
| SNetS-SDM-SBRC26 | `Simulador/SNetS-SDM-SBRC26 (Alexandre)` | código **original**, base do SNetS-SDM-QKD |
| SNetS-SDM-QKD, branch `main` | `Simulador/SNetS-SDM-QKD` | SBRC26 + addon QKD. Tem a otimização da Parte 24 (seção 4). |
| SNetS-SDM-QKD, branch `experimental` | `Simulador/SNetS-SDM-QKD_ex` | `main` + Partes 45 e 52 (seções 3, 5, 6 e 7) |

No caminho clássico, a `main` difere do SBRC26 em três pontos, todos sem efeito quando o QKD está desligado:

- semente de tráfego opcional (`simulation.trafficRandomSeed`);
- núcleos reservados ao QKD (`qkdReserved`), que o clássico não usa;
- núcleos desativados (`disabled`).

Nos arquivos de QoT, crosstalk e RMLSA conferidos linha a linha (`PhysicalLayer`, `Crosstalk`, `ShortestPathsCoreAndSpectrumAssignment_v2` e `IntegratedPRMCSAAlgorithm_v3`), o resto são mudanças de formatação, comentários e nomes de variáveis. Outros arquivos, como `RequestGenerator`, `Link`, `Simulation` e os *parsers*, foram reorganizados para o addon QKD. Em vez de revisar cada um, a seção 8.3 testa o conjunto: com a mesma semente, o SBRC26 e a `main` dão resultados idênticos.

## 2. Resumo

| Parte | Branch | Mudança | Efeito nos resultados |
|---|---|---|---|
| 24 | main e experimental | Reavaliação das sessões QKD só nos enlaces do circuito que mudou | nenhum (bit a bit) |
| 21–22 | main e experimental | Sem reavaliação das sessões quando o XT intercore do receptor está desligado | nenhum |
| 45 | experimental | Faixas livres lidas uma vez por observação nas métricas de fragmentação e utilização | nenhum (bit a bit) |
| 45 | experimental | Busca binária na inserção de eventos | nenhum (bit a bit) |
| 45 | experimental | Métrica `AlgorithmCost` (custo de cada algoritmo) | nenhum; desligada por padrão |
| 45 | experimental | Save states periódicos e retomada | nenhum (retomada = execução contínua) |
| 52 | experimental | Save states ligados por padrão, cancelar sem perder, continuar | nenhum |
| 54 | experimental | Caches da QoT, do espectro e do QKD, cálculo do consumo só na leitura, ordem das simulações (seção 11) | nenhum (bit a bit; conferido por um modo de verificação) |

## 3. Desempenho do caminho clássico (Parte 45, experimental)

### 3.1 Como os pontos quentes foram encontrados

O tempo de execução foi medido com o Java Flight Recorder (JFR), que amostra a pilha de chamadas das threads durante a simulação. No caso Q01, com todas as métricas ligadas, os maiores consumidores eram:

- `Spectrum.getFreeSpectrumBands`, com ~30 % do tempo;
- as métricas de fragmentação e de utilização, com ~35 %.

`getFreeSpectrumBands` monta, a cada chamada, uma lista nova com as faixas livres de um núcleo. Para isso percorre o `TreeSet` de faixas e consulta dois `HashMap` de bandas de guarda. As métricas chamavam esse método várias vezes para o mesmo núcleo, no mesmo instante e com o mesmo argumento. Todas essas chamadas devolvem a mesma lista.

As mudanças abaixo eliminam as chamadas repetidas. Os valores somados e a ordem das somas não mudam, então o resultado é idêntico bit a bit.

### 3.2 `measurement/RelativeFragmentation.java`

A fragmentação relativa é calculada para vários valores de c (o tamanho da requisição de referência). O código original percorria todos os enlaces e núcleos **uma vez para cada c** e pedia as faixas livres em cada passada:

```java
// Antes (main)
private void observationLinks(Mesh mesh) {
    for (Integer c : relativeFrag.keySet()) {
        this.observationAllLinks(c, mesh);
    }
}
private void observationAllLinks(Integer c, Mesh mesh) {
    ...
    for (Link link : mesh.getLinkList()) {
        for (Core core : link.getCores()) {
            fragAllCores += cf.relativeFragmentation(core.getFreeSpectrumBands(0), c);
        }
        ...
```

Agora as faixas livres de cada núcleo são lidas uma vez por observação, guardadas numa lista (enlace → núcleo → faixas) e reutilizadas para todos os valores de c:

```java
// Depois (experimental)
private void observationLinks(Mesh mesh) {
    final List<List<List<int[]>>> bands = new ArrayList<>();
    for (Link link : mesh.getLinkList()) {
        final List<List<int[]>> perCore = new ArrayList<>();
        for (Core core : link.getCores()) {
            perCore.add(core.getFreeSpectrumBands(0));
        }
        bands.add(perCore);
    }
    for (Integer c : relativeFrag.keySet()) {
        this.observationAllLinks(c, mesh, bands);
    }
}
```

**Por que não muda o resultado:**

- a observação acontece num instante fixo da simulação, então as faixas livres não mudam entre um c e outro;
- a ordem dos enlaces, dos núcleos e das somas é a mesma;
- `relativeFragmentation` não altera a lista que recebe.

**Ganho:** proporcional ao número de valores de c. Com os 5–10 valores usuais, essa métrica deixa de ser a mais cara do simulador.

### 3.3 `measurement/ExternalFragmentation.java`

Cada núcleo pedia as faixas livres duas vezes seguidas: uma para a fragmentação externa e outra para a entropia. Agora pede uma vez:

```java
// Antes
externalFragAllCores += cf.externalFragmentation(core.getFreeSpectrumBands(0));
entropyExternalFragAllCores += cf.entropyExternalFragmentation(core.getFreeSpectrumBands(0), core.getNumOfSlots());

// Depois
final List<int[]> free = core.getFreeSpectrumBands(0);
externalFragAllCores += cf.externalFragmentation(free);
entropyExternalFragAllCores += cf.entropyExternalFragmentation(free, core.getNumOfSlots());
```

### 3.4 `measurement/SpectrumUtilization.java`

Mesma ideia. A lista de faixas livres clássicas de cada núcleo é lida uma vez e reaproveitada mais adiante no mesmo método. Isso vale quando o núcleo não está desativado nem reservado ao QKD, porque nesses dois casos o código já usava outro método.

```java
// Depois
final List<int[]> classicalFree = core.getFreeSpectrumBands(0);
for (int[] faixa : classicalFree) { ... }
...
: core.isDisabled() ? core.getFreeSpectrumBandsIgnoringReservation(0) : classicalFree;
```

### 3.5 `simulator/EventMachine.java`: inserção de eventos por busca binária

A lista de eventos (`Vector<Event>`) fica ordenada pelo instante do evento. Para inserir um evento novo, o código original procurava a posição percorrendo a lista desde o começo:

```java
// Antes
int i = 0;
while (i < eventList.size() && (eventList.elementAt(i)).getTimeHours() < e.getTimeHours()) {
    i++;
}
eventList.insertElementAt(e, i);
```

Agora a posição é encontrada por busca binária:

```java
// Depois
final double t = e.getTimeHours();
int lo = 0;
int hi = eventList.size();
while (lo < hi) {
    final int mid = (lo + hi) >>> 1;
    if (eventList.elementAt(mid).getTimeHours() < t) lo = mid + 1;
    else hi = mid;
}
eventList.insertElementAt(e, lo);
```

**Por que não muda o resultado:**

- as duas versões devolvem a primeira posição cujo evento tem instante maior ou igual ao do evento novo;
- portanto, entre eventos com o mesmo instante, o novo continua indo antes dos que já estavam lá, e a ordem de processamento não muda.

**Ganho:** a busca cai de O(n) para O(log n) comparações, em que n é o número de eventos pendentes (uma chegada por gerador e uma saída por circuito ativo). O deslocamento dos elementos no `Vector` continua O(n), mas é uma cópia de memória rápida (`System.arraycopy`). O ganho aparece com carga alta e muitos circuitos ativos.

### 3.6 Ganho medido

| Caso | main | experimental | Redução |
|---|---|---|---|
| Q01 (QKD, metro de 8 nós, todas as métricas, 1 thread) | 25,8 s | 19,9 s | −23 % |
| Teste_EX (clássico, 100 000 req. × 8 cargas × 10 replicações, 5 threads, PC do laboratório) | 2 h 35 min | 1 h 38 min | −37 % |
| Teste_EX reduzido (20 000 req. × 8 cargas × 1 replicação, 1 thread) | 397 s | 292 s | −26 % |
| Teste_Ex_QKD reduzido (10 000 req. × 8 cargas × 1 replicação, 1 thread, com JFR) | 255 s | 225 s | −12 % |

**De onde vem o ganho.** Quase todo o ganho vem das métricas:

- no perfil do Teste_EX reduzido, `Measurements.addNewObservation` caiu de 45 % do tempo na `main` para 30 % na experimental, e a fragmentação relativa sozinha caiu de 20 % para menos de 1 %;
- no caso QKD, as métricas caíram de 30 % para 21 %;
- a inserção de eventos pesava só 1–1,5 % na `main`, então a busca binária contribui pouco nestes casos. Ela pesa mais em redes grandes, com muitos circuitos ativos.

No caso QKD o ganho relativo é menor porque a avaliação física das sessões QKD, que não foi alterada, ocupa ~30 % do tempo.

**Cuidado ao comparar tempos.** No laboratório, o Teste_Ex_QKD levou 3 h 57 min na experimental e 3 h 59 min na `main`, mas as duas execuções não rodaram nas mesmas condições:

- as duas versões rodaram juntas no mesmo PC, em horários parcialmente sobrepostos;
- a da `main` terminou ~1 h sozinha, com a máquina livre, e a experimental começou disputando a CPU com o Teste_EX da `main`.

Por isso esses dois tempos não medem a diferença entre as versões. Para isso, rode as duas ao mesmo tempo do início ao fim, ou cada uma sozinha.

## 4. Desempenho do caminho QKD (Partes 21–24, main e experimental)

Essas mudanças estão nas duas branches. Entram aqui porque também alteraram o código que existia antes.

### 4.1 Reavaliação só das sessões afetadas (Parte 24)

Depois de cada circuito clássico alocado ou liberado, o `QKDControlPlane` recalcula o estado físico (SpRS, XT, QBER e SKR) das sessões QKD ativas, porque o ruído que os canais clássicos causam nos QChs mudou.

- **Antes:** todas as sessões ativas eram reavaliadas.
- **Agora:** `QKDExistingSessionImpactEvaluator.refreshActiveSessions(registry, changed)` só reavalia as sessões com um QCh num enlace do circuito que mudou. O motivo é que o ruído num enlace depende apenas dos circuitos daquele enlace.
- Um QCh compartilhado por várias sessões é avaliado uma vez por atualização (mapa `evaluated`, indexado pelo id do canal).

Os resultados ficaram idênticos, inclusive a contagem `ActiveRefreshes`, que continua contando todas as sessões. O caso C1 (7 núcleos, XT) caiu de 160 s para 33 s.

### 4.2 Sem reavaliação quando o ruído clássico não chega ao QCh (Partes 21–22)

Com `qkdReceiver.intercoreCrosstalkEnabled = false` (multifibra ou cenário "sem XT"), p_b = p_dark não depende do estado clássico. Nesse caso o campo `classicalNoiseCoupled` fica falso, e a proteção das sessões ativas e a reavaliação depois de cada mudança clássica são puladas. O cenário de referência passou de ~90 s para ~2 s.

## 5. Custo computacional dos algoritmos: métrica `AlgorithmCost` (Parte 45, experimental)

Arquivos: `simulator/AlgorithmCostRecorder.java` (novo), `Simulation.java`, `SimulationManagement.java`, `SimulationFileManager.java`, `SimulationRequest.java`, `parsers/SimulationConfig.java` e `qkd/control/QKDControlPlane.java`.

Quando `activeMetrics.AlgorithmCost` é `true`, cada objeto de algoritmo da simulação é envolvido num `java.lang.reflect.Proxy` da sua interface. Os algoritmos envolvidos são grooming, RMLSA integrado, roteamento, k-roteamento, espectro, núcleo e espectro, modulação, potência e realocação.

```java
// Simulation.java
if (sc.getActiveMetrics() != null && sc.getActiveMetrics().AlgorithmCost) {
    costRecorder = new AlgorithmCostRecorder();
    grooming   = costRecorder.wrap(grooming, TrafficGroomingAlgorithmInterface.class, "Grooming");
    integrated = costRecorder.wrap(integrated, IntegratedRMLSAAlgorithmInterface.class, "IntegratedRMLSA");
    ...
}
```

O proxy só repassa a chamada e soma o tempo (`System.nanoTime`) e uma chamada ao componente. Ele não muda argumentos, resultados, exceções nem a ordem das chamadas. A admissão QKD, a proteção e a atualização das sessões são medidas direto no `QKDControlPlane`.

- O PABS não é envolvido, porque a métrica de bloqueio lê os contadores dele com `instanceof`.
- Com a métrica desligada (o padrão), nenhum proxy é criado.

A saída é `<caso>_AlgorithmCost.csv`, com chamadas, chamadas por requisição, tempo total, médio e máximo, e fração do tempo da simulação. Os tempos são inclusivos: o tempo do espectro chamado de dentro do RMLSA integrado entra nos dois.

## 6. Save states (Parte 45, experimental)

### 6.1 Ideia

Cada simulação, isto é, cada par carga × replicação, grava periodicamente o seu estado completo em `<caso>/savestate/lp<L>_rep<R>.state`. Quando o mesmo caso é executado de novo:

- simulações com estado final não são refeitas, e os resultados delas são reaproveitados;
- simulações com estado parcial continuam do ponto gravado;
- simulações sem estado começam do zero.

Como o estado inclui os geradores aleatórios e a lista de eventos pendentes, a continuação percorre exatamente a mesma sequência de eventos de uma execução sem interrupção.

### 6.2 O que é gravado

A classe `SaveStateStore.State` (serialização Java com gzip) contém:

| Campo | Conteúdo |
|---|---|
| `simulation` | o objeto `Simulation` inteiro: malha, enlaces, núcleos, espectro, circuitos ativos, plano de controle, sessões QKD, geradores de requisições com o `Random` de cada um, e medidas acumuladas |
| `eventMachine` | a lista de eventos pendentes (só em estado parcial) |
| `arriveListener` | o *listener* de chegadas (só em estado parcial) |
| `circuitCounter` | o contador global de ids de circuito (`Circuit.quantity`) |
| `finished` | se a simulação terminou |
| `inputHash` | SHA-256 das entradas (seção 6.4) |

### 6.3 Quando é gravado

`EventMachine.executeEvents()` chama um gancho (`checkpointHook`) **entre dois eventos**, nunca no meio do processamento de um evento:

```java
if (checkpointHook != null && System.nanoTime() - lastCheckpointNanos >= checkpointIntervalNanos) {
    checkpointHook.run();
    lastCheckpointNanos = System.nanoTime();
}
```

O intervalo é `saveState.intervalMinutes` (10 min por padrão). Ao terminar, a simulação grava um último estado com `finished = true`.

A gravação é atômica: o estado vai para um arquivo `.tmp`, que depois é renomeado. Uma queda de energia durante a gravação deixa o estado anterior intacto.

### 6.4 Quando um estado é ignorado

O `inputHash` é o SHA-256 de:

- `network`, `traffic`, `physicalLayer` (com a camada física QKD) e `others`;
- `simulation`, sem `threads` e sem `saveState`, porque esses dois campos não mudam os resultados;
- desde a Parte 52, o SHA-256 do JAR do simulador.

Um estado com hash diferente é ignorado, com aviso no console, e aquela simulação recomeça do zero. Assim nunca se mistura um estado de outra configuração ou de outra versão do simulador.

### 6.5 Mudanças necessárias para serializar (sem efeito na simulação)

- `implements Serializable` em `FastSwitching`, `SortListCircuits`, `HoldRequestListener`, `ArriveRequestForConexionListener`, `QKDExistingSessionImpactEvaluator`, `QKDPhysicalLayer`, `BB84DecoyModel`, `QKDProvisioningService` e `QKDRoutePlanner`.
- `network/Circuit.java` ganhou `writeObject`/`readObject`, que gravam e leem o `id` antes dos outros campos. Durante a leitura, um circuito pode ser alcançado de novo pelas suas próprias referências (rota, núcleos, conjuntos de circuitos ativos). Os `HashSet` que o contêm chamam `hashCode()`, que usa o `id`, então ler o `id` primeiro mantém esses conjuntos válidos.
- O contador global `Circuit.quantity` é restaurado acima do maior id salvo, para que os ids continuem únicos.
- O objeto `Util` é compartilhado por todas as simulações do caso. Ele não é gravado: entra no arquivo como uma marca (`SharedUtil`) e, na leitura, volta como o `Util` da execução atual. Sem isso, cada estado levaria uma cópia de outra simulação, e com várias threads a gravação leria conjuntos que outra simulação estava alterando (`ConcurrentModificationException`, visto no caso C05).
- A leitura e a gravação rodam numa thread com pilha de 1 GB. A serialização Java é recursiva, e o grafo de objetos de redes grandes estoura a pilha padrão (visto no caso C05, rede USA).

### 6.6 Arquivos

- `simulator/SaveStateStore.java` (novo);
- `simulator/Simulator.java`: `start()` e `resume(state)` instalam o gancho e gravam o estado final;
- `simulator/EventMachine.java`: gancho entre eventos;
- `simulationControl/SimulationManagement.java`: carrega os estados antes de cada simulação e decide se pula, continua ou começa;
- `simulationControl/parsers/SimulationConfig.java`: grupo `saveState`;
- as classes da seção 6.5.

## 7. Cancelar sem perder e continuar (Parte 52, experimental)

### 7.1 Padrão

Sem o grupo `saveState` no arquivo `simulation`, os save states ficam **ligados**, com gravação a cada 10 min. Para desligar: `"saveState": { "enabled": false }`.

### 7.2 Parada com gravação

O pedido de parada é uma flag estática `volatile` (`SaveStateStore.stopRequested`), conferida pela `EventMachine` depois de cada evento:

```java
if (SaveStateStore.isStopRequested()) {
    if (checkpointHook != null) checkpointHook.run();   // grava o estado agora
    throw new SaveStateStore.Stopped();
}
```

A flag é ligada por três caminhos:

1. **Arquivo `savestate/STOP`:** uma thread (`startWatcher`) confere a cada 500 ms se o arquivo existe. O launcher cria esse arquivo quando o usuário aperta **Cancelar**. Um `STOP` que tenha sobrado de outra execução é apagado no início.
2. **Ctrl+C, fechamento da janela de console ou término normal do processo:** um *shutdown hook* (`installHook`) liga a flag e espera até 25 s que as simulações gravem.
3. **Simulações que ainda não começaram:** quando a parada chega, elas não são iniciadas e ficam para a continuação.

Quando há simulações paradas, o `SimulationManagement`:

- grava `progresso.json` com o status `stopped`;
- não grava os CSVs de resultado;
- termina o processo com o **código 3**.

Uma **queda de energia** não pode ser avisada ao processo. Nesse caso vale o último estado periódico, no máximo um intervalo antes da queda.

### 7.3 `savestate/progresso.json`

É um resumo para o launcher com:

- o status (`running`, `stopping`, `stopped` ou `finished`);
- o total e a fração de requisições processadas;
- cada simulação concluída, em andamento (com as requisições já processadas) ou sem início;
- a data da última gravação;
- os arquivos do caso e o JAR usado.

### 7.4 Fim do caso

Quando todas as simulações terminam e os CSVs estão gravados, um *shutdown hook* registrado em `caseFinished()` apaga a pasta `savestate`. Antes de apagar, ele confere que `*_BlockingProbability.csv` foi escrito nesta execução. Com `"keepAfterFinish": true` a pasta é mantida.

### 7.5 Launcher 1.20.6

O launcher passou a usar essas informações:

- **Cancelar** cria o `STOP` e espera até 120 s; só depois encerra o processo à força.
- Um processo que termina com código 3 aparece como "interrompida · progresso salvo".
- Os casos interrompidos mostram `⏸ 42 %` na lista de casos e uma faixa no caso aberto com **Continuar execução**, **Detalhes** e **Descartar progresso**.
- A aba **Execuções** tem a seção **Interrompidas**, onde é possível continuar vários casos em fila ou em paralelo.
- A aba **Executar** mostra o estado de cada simulação e avisa quando os arquivos ou o JAR mudaram, porque nesse caso o progresso seria ignorado.

### 7.6 Custo

- Conferir a flag de parada e o relógio a cada evento custa uma leitura de memória e uma chamada a `System.nanoTime()`, o que é desprezível.
- Cada gravação leva de frações de segundo a poucos segundos (redes grandes). Com intervalo de 10 min, isso fica abaixo de 1 % do tempo.
- Cada arquivo de estado tem de dezenas de kB a poucos MB.

## 8. Como a fidelidade dos resultados foi conferida

### 8.1 Regra de comparação

Os CSVs de duas execuções são comparados célula a célula. As linhas são casadas pela chave (métrica, carga, taxa, origem, destino), e não pela posição, porque a ordem das linhas "por par" pode mudar entre execuções. Um valor numérico conta como diferente se a diferença relativa passa de 1e-9.

### 8.2 Experimental × main (casos completos do laboratório, 5 threads)

| Caso | Configuração | Resultado |
|---|---|---|
| Teste_Ex_QKD (QKD, `qabne`) | 100 000 req. × 8 cargas × 10 replicações | **18 CSVs idênticos**, 12,6 milhões de células |
| Teste_EX (clássico, `spcsav2`) | idem | idênticos, exceto o efeito do sorteio da seção 8.5 |
| Classico_CANCEL × Teste_EX experimental | mesmo caso, cancelado no meio e continuado | idênticos, exceto o efeito do sorteio da seção 8.5 |

### 8.3 Versão original (SBRC26) × main × experimental, com a mesma semente

O SBRC26 sorteia a semente do tráfego pelo relógio (`new RandGenerator()`), então duas execuções dele nunca são iguais. Para comparar bit a bit, o código-fonte do SBRC26 foi compilado com uma única mudança: a semente de cada simulação passa a ser `13000 + 100000 × carga + replicação`, a mesma regra que a `main` usa com `trafficRandomSeed = 13000`. Fora isso, o código é o original.

| Caso | Algoritmos | Tamanho | SBRC26 × main | SBRC26 × experimental |
|---|---|---|---|---|
| Teste_EX (5 nós, 7 núcleos) | `spcsav2`, `csbasdm`, `cpsd` | 10 000 req. × 8 cargas × 2 rep. | idênticos (11 CSVs) | idênticos |
| NSFNet CPSD p = −5 | `prmscav3`, `cpsd` | 3 000 req. × 5 cargas × 1 rep. | idênticos | idênticos |
| NSFNet EPA | `prmscav3`, `epa` | idem | idênticos | idênticos |
| NSFNet IMPA mo = mx = 0,5 | `prmscav3`, `impa` | idem | idênticos | — |
| NSFNet CPA p = −3 | `prmscav3`, `cpa` | idem | idênticos | idênticos |

Os casos NSFNet são do estudo do Alexandre (`simulations/NSFNet_sims`).

**Limitação:** a comparação usou o SBRC26 compilado a partir do código-fonte da pasta, não o `SNetS-SDM-SBRC26.jar` distribuído. Esse JAR foi gerado por outro compilador, então o bytecode não pode ser comparado diretamente. Como a semente dele vem do relógio, também não dá para comparar resultados com ele bit a bit.

**SNetS-SMD_XT (Rayline).** É outro ramo do SNetS, com o mesmo `Mesh` sem semente. O código-fonte dele foi compilado com a mesma injeção de semente. Ele difere do SBRC26 principalmente no cálculo do crosstalk dentro da QoT: usa `calculateCrosstalk2` no lugar de `calculateCrosstalkPowerInLink`.

| Caso | SBRC26 × Rayline |
|---|---|
| Teste_EX (10 000 req. × 8 × 2) | idênticos em todos os CSVs que os dois geram |
| NSFNet CPSD p = −5 (3 000 req. × 5 × 1) | idênticos |

A única diferença é de rótulo: o Rayline escreve "Numm Attempts Counter (PABS)" onde o SBRC26 escreve "Num Attempts Counter (PABS)". Nesses dois casos a diferença no crosstalk não mudou nenhuma decisão. Em cenários em que o XT limita a admissão, os dois ramos podem divergir.

### 8.4 Save states

- Parte 45: Q01, C01, C05 e M02 interrompidos com `kill` e executados de novo deram CSVs iguais aos da execução sem interrupção.
- Parte 52: Q01 e Q07 foram cancelados pelo arquivo STOP, por Ctrl+C e com o processo morto à força (simulando uma queda de energia), e depois continuados. Os CSVs saíram iguais.
- Caso real: o `Classico_CANCEL` (seção 8.2) foi cancelado pelo launcher e continuado.

### 8.5 Diferença conhecida entre execuções: sorteio sem semente no `spcsav2`

Em `gprmcsa/integrated/ShortestPathsCoreAndSpectrumAssignment_v2.java` (o algoritmo `spcsav2`, igual no SBRC26, na `main` e na experimental), quando nenhuma modulação serve, o núcleo do circuito **bloqueado** é sorteado com um `Random` sem semente:

```java
checkCore = new Random().nextInt(cp.getMesh().getLinkList().get(0).getNumberOfCores());
```

Esse sorteio não muda a sequência de eventos, a alocação nem o bloqueio total. Ele só muda três coisas, e só nas cargas em que esse bloqueio acontece:

- o núcleo atribuído ao circuito bloqueado ("Blocking probability per core");
- a classificação da causa ("by fragmentation" × "by other"), porque o teste de fragmentação olha o espectro desse núcleo;
- a fragmentação externa horizontal, que é medida nesse núcleo.

Por isso duas execuções do mesmo caso com `spcsav2`, mesmo na mesma versão, diferem nesses valores. Exemplos: main × experimental e a execução contínua × a cancelada. No Teste_EX, foram 63 a 86 células de 18 320 no `BlockingProbability.csv` e 11 a 12 de 3 038 no `ExternalFragmentation.csv`, todas nas cargas 6 e 7. O bloqueio total, o bloqueio por par, a utilização, os slots e a camada física saíram idênticos.

Corrigir isso exige dar uma semente a esse `Random`, o que muda os valores dessas três métricas em relação às execuções antigas. É uma decisão pendente do usuário.

## 9. Onde ainda dá para ganhar tempo (análise de novembro de 2026, antes da Parte 54)

Esta seção é a análise que motivou a Parte 54. Todas as propostas das seções 9.2 e 9.3 foram implementadas; a seção 11 descreve como ficaram e o ganho medido.

### 9.1 Perfil atual

Perfil com JFR, 1 thread, versão experimental, casos Teste_EX (clássico) e Teste_Ex_QKD reduzidos (8 cargas × 1 replicação). Os valores são a fração inclusiva das amostras: um método conta junto com tudo o que ele chama.

| Ponto quente | Clássico | QKD | O que faz |
|---|---|---|---|
| `PhysicalLayer.computeSNRSegment` | 44 % | 27 % | QoT (ASE + NLI + XT) do circuito novo e dos que já estão na rede |
| ↳ `ControlPlane.checkOSNRForOther` | 26 % | 17 % | recalcula a QoT de todos os circuitos dos enlaces do circuito novo |
| ↳ `PhysicalLayer.getGnli2` | 31 % | 19 % | NLI (GN de Habibi), com dois `arcsinh` por par de circuitos |
| ↳ `Math.log1p` (dentro do `arcsinh`) | 22 % | 14 % | o `log1p` do Java é implementado em Java puro (`StrictMath`), sem instrução nativa |
| ↳ `PhysicalLayer.getCircuitList` | 13 % | 7 % | monta um `TreeSet` novo com os circuitos do núcleo a cada enlace avaliado |
| Métricas (`Measurements.addNewObservation`) | 30 % | 21 % | todas as métricas ligadas |
| `Spectrum.getFreeSpectrumBands` | 14 % | 9 % | faixas livres de um núcleo, recalculadas a cada chamada |
| `Mesh.computesPowerConsmption` | 10 % | 7 % | consumo de energia da rede inteira, recalculado a cada alocação e liberação |
| `GroomingStatistics.computeReqByCirc` | 7 % | 5 % | percorre todos os circuitos a cada observação |
| `QKDExistingSessionImpactEvaluator.refreshActiveSessions` | — | 25 % | atualização das sessões QKD depois de cada mudança clássica |
| ↳ `QKDPhysicalLayer.collectInterferers` | — | 15 % | lista os circuitos dos núcleos vizinhos ao QCh, uma vez por *span* |
| ↳ `touches` | — | 6 % | procura em todas as sessões quais usam o enlace que mudou |
| `QKDExistingSessionImpactEvaluator.evaluateCandidate` | — | 7 % | proteção das sessões ativas antes de admitir um circuito clássico |
| `EventMachine.insert` | < 1 % | 1,5 % (main) | inserção de eventos, já otimizada na Parte 45 |
| Coleta de lixo (GC) | ~1 % | — | não é gargalo |

### 9.2 Testado em protótipo (fora do repositório), com resultado idêntico

Duas mudanças foram implementadas numa cópia do código experimental para medir o efeito:

1. **Memória do `arcsinh` no NLI** (`PhysicalLayer.getGnli2`). O argumento do `arcsinh` só depende da largura dos dois circuitos e da distância entre eles. Como a grade de slots é fixa, os mesmos valores se repetem milhões de vezes. O resultado de cada argumento passa a ficar guardado numa tabela por thread, indexada pelos bits exatos do `double`. Como é a mesma função com a mesma entrada, o resultado é idêntico bit a bit.
2. **Cache das faixas livres** (`Spectrum.getFreeSpectrumBands`). Cada `Spectrum` ganha um contador de versão, incrementado em `useSpectrum` e `freeSpectrum`. Enquanto a versão e a banda de guarda não mudam, o método devolve uma cópia da última lista calculada.

As duas versões rodaram ao mesmo tempo na mesma máquina, uma por núcleo, com 1 thread cada:

| Caso | Experimental atual | Protótipo | Redução | CSVs |
|---|---|---|---|---|
| Teste_EX reduzido (10 000 req. × 8 × 2) | 255,5 s | 202,9 s | −21 % | 14 idênticos |
| Teste_Ex_QKD reduzido (10 000 req. × 8 × 1) | 232,8 s | 200,6 s | −14 % | 18 idênticos |

### 9.3 Propostas sem mudança nos resultados (implementadas na Parte 54)

| # | Proposta | Alvo no perfil | Ganho esperado | Cuidado |
|---|---|---|---|---|
| C1 | As duas mudanças da seção 9.2 | `log1p` 14–22 %, faixas livres 9–14 % | medido na seção 9.2 | as duas são locais e simples |
| C2 | QoT incremental dos outros circuitos: guardar, por circuito e enlace, os termos de ruído (ASE, NLI, XT) e recalcular só os enlaces cujo conjunto de circuitos mudou, somando na mesma ordem | `checkOSNRForOther` 17–26 % | alto (a maior parte desse custo) | invalidação correta do cache; regressão bit a bit |
| C3 | Manter os circuitos de cada núcleo num `TreeSet` permanente, em vez de montá-lo a cada avaliação, e copiar só quando houver circuito de teste | `getCircuitList` 7–13 % | médio | mesma ordem de iteração (`Circuit.compareTo`) |
| C4 | Calcular o consumo de energia só quando a métrica `ConsumedEnergy` for ler o valor (ela é a única que lê os totais do `Mesh`), em vez de a cada alocação e liberação | 7–10 % | médio; total sem a métrica | o valor lido tem de ser o do estado atual, como hoje |
| C5 | `GroomingStatistics` incremental (contadores atualizados na alocação e na liberação) | 5–7 % | pequeno a médio | — |
| C6 | Distribuir as simulações da mais pesada para a mais leve (maior carga primeiro) | fim da execução com threads ociosas | 0–9 %, conforme threads × simulações | nenhum efeito nos resultados |
| Q1 | Na atualização das sessões QKD, pular a sessão quando o circuito que mudou não está num núcleo vizinho ao QCh ou não sobrepõe o espectro do QCh. É o mesmo filtro (`candidateCanAffect`) que a proteção já usa. Vale só sem `multiband`, porque o SpRS soma todos os circuitos. | `refreshActiveSessions` 25 % | alto no QKD | algoritmos de potência que mudam a potência de circuitos já alocados; validar por regressão |
| Q2 | Listar os interferentes uma vez por enlace e reaproveitar a lista em todos os *spans* do enlace | `collectInterferers` 15 % | médio | — |
| Q3 | Índice enlace → sessões, para não varrer todas as sessões em `touches` e `candidateCanAffect` | 6–9 % | pequeno a médio | — |

### 9.4 Ganhos sem mexer no código

- **Métricas:** cada métrica ligada custa tempo a cada requisição, e juntas chegam a 20–30 % do total. Desligue as que o estudo não usa, principalmente `RelativeFragmentation`, `ExternalFragmentation`, `SpectrumUtilization`, `SlotUtilization`, `SlotBlocking` e `GroomingStatistics`.
- **Threads:** as simulações (carga × replicação) são independentes. Use `threads` perto do número de núcleos físicos da máquina.
- **Execuções em paralelo:** com hyper-threading, dois casos rodando juntos dividem os mesmos núcleos físicos. Para comparar tempos de duas versões, rode as duas sozinhas ou ao mesmo tempo do início ao fim.
- **O que não vale a pena:** trocar `Math.log1p` por uma versão aproximada mudaria a última casa decimal da QoT e quebraria a comparação bit a bit com as execuções antigas. A memória da seção 9.2 dá quase o mesmo ganho sem isso.

## 10. Como repetir a comparação

1. Rode o mesmo caso nas duas versões com `trafficRandomSeed` definido, sem mudar nada além do JAR.
2. Compare os CSVs casando as linhas pela chave (métrica, carga, taxa, origem, destino) e as colunas de replicação, com tolerância relativa de 1e-9.
3. Com `spcsav2`, ignore as três métricas da seção 8.5.
4. Para medir tempo, use `threads = 1` e a métrica `AlgorithmCost`, ou rode as duas versões ao mesmo tempo na mesma máquina.

## 11. Parte 54: as otimizações implementadas

Todas as propostas da seção 9 foram implementadas na branch experimental. O princípio é o mesmo da Parte 45: nenhuma conta muda. As mudanças:

- evitam refazer contas cujo resultado já se conhece (caches);
- evitam montar de novo estruturas que não mudaram;
- deixam de lado avaliações que não podem mudar nada.

Cada valor reaproveitado é exatamente o que o código original calcularia, com as mesmas entradas e as somas na mesma ordem.

### 11.1 Interruptores (propriedades do Java)

| Opção | Efeito |
|---|---|
| (nenhuma) | caches ligados (padrão) |
| `-Dsnets.verifyPerf=true` | modo de verificação: todo valor servido por um cache também é calculado do jeito original e comparado bit a bit; a primeira diferença para a simulação com `IllegalStateException`. É lento, para testes. |
| `-Dsnets.noPerfCache=true` | desliga os caches da Parte 54 (caminho original) |
| `-Dsnets.perfStats=true` | no fim, mostra quantas vezes cada cache foi aproveitado |

Exemplo, no console: `java -Dsnets.verifyPerf=true -jar SNetS-SDM-QKD-exp.jar MeuCaso`.

A classe `simulator/PerfChecks.java` concentra esses interruptores.

### 11.2 Mudanças no caminho clássico

**a) Memória do `arcsinh`** (`network/PhysicalLayer.java`)

O NLI do modelo GN (`getGnli2`) calcula dois `arcsinh` para cada par de circuitos no enlace, e o `arcsinh` usa `Math.log1p`, que no Java é feito em software (`StrictMath`). O argumento só depende das larguras dos dois circuitos e da distância entre eles, e como a grade de slots é fixa os mesmos valores se repetem: no Teste_EX reduzido, 1,08 bilhão de chamadas para 39 mil argumentos diferentes.

O `arcsinh` original virou `arcsinhCompute`, e `arcsinh` agora procura o resultado numa tabela por thread (65 536 posições, endereçamento aberto), indexada pelos bits exatos do argumento. Se não encontrar, calcula e guarda. Mesma função com o mesmo argumento dá o mesmo resultado.

**b) Cache das faixas livres** (`network/Spectrum.java`)

`getFreeSpectrumBands(guardBand)` percorria o `TreeSet` de faixas livres e consultava dois `HashMap` de bandas de guarda a cada chamada. Agora:

- o `Spectrum` tem um contador de versão, incrementado em `useSpectrum` e `freeSpectrum`, os únicos métodos que mudam as faixas;
- o último resultado fica guardado com a versão e a banda de guarda;
- enquanto nada muda, o método devolve uma cópia nova, com arrays novos como antes, então quem recebe a lista ainda pode alterá-la.

O cálculo original virou `computeFreeSpectrumBands`. Aproveitamento medido: 96–98 %.

**c) Lista ordenada dos circuitos de cada núcleo** (`Core`, `PhysicalLayer.getCircuitList` e `Crosstalk`)

A QoT e o crosstalk percorrem os circuitos de um núcleo em ordem de id e, para isso, montavam um `TreeSet` novo a partir do `HashSet` do núcleo a cada avaliação.

- O `Core` passou a manter esse conjunto ordenado (`getSortedCircuitList`), atualizado em `addCircuit` e `removeCircuit`.
- Quando o circuito avaliado e o circuito de teste não mudam o conjunto, o próprio conjunto do núcleo é usado, só para leitura.
- Quando mudam, uma cópia é alterada exatamente como antes.

O auxiliar `Crosstalk.circuitsWithTest` faz o mesmo para os núcleos adjacentes.

**d) Rastreamento de mudanças no estado físico** (`Link`, `Core`, `Circuit`, `TrackedRequestList` e `PhysicalStateChanges`)

É a base dos caches (e) e (j):

- `Link` ganhou uma versão (`getPhysicalVersion`), incrementada por `touch()`.
- Um núcleo chama `touch()` do seu enlace quando entra ou sai um circuito e quando o espectro muda.
- Um circuito que está na rede (contador `coreLists`, mantido pelo `Core`) chama `touch()` em todos os enlaces da rota quando muda: espectro, núcleo, modulação, potência, rota, banda de guarda ou requisições (`physicalChange`).
- A lista de requisições do circuito passou a ser uma `TrackedRequestList`, um `ArrayList` que avisa os circuitos que a usam quando muda. Alguns algoritmos de grooming alteram a lista direto (`getRequests().add`), e isso também é rastreado.
- `PhysicalStateChanges` conta, por thread (uma simulação roda numa só thread), as mudanças de circuitos já na rede e as entradas e saídas das listas dos núcleos.
- **Proteção:** circuitos translúcidos alteram mapas por enlace sem passar por métodos rastreáveis, e uma lista de requisições de outro tipo também não é rastreável. Se algum desses aparecer, os caches (e) e (j-1) se desligam no processo inteiro (`PhysicalStateChanges.isUntracked`).

**e) QoT por enlace** (`PhysicalLayer.computeSNRSegment`, `linkNoiseTerms` e `QotLinkCache`)

O laço de `computeSNRSegment` soma, enlace por enlace, os termos de NLI, ASE e XT. Esse corpo do laço virou `linkNoiseTerms`, com as mesmas contas. Os três termos de cada enlace ficam guardados no circuito (`QotLinkCache`). Eles são reaproveitados enquanto forem iguais:

- a versão do enlace;
- o núcleo, o espectro, a modulação, a potência, a largura de banda, a densidade espectral de potência e a frequência central do circuito (comparados bit a bit);
- a camada física usada.

Termos calculados com o circuito de teste naquele enlace nunca são guardados. Os termos são somados na mesma ordem dos enlaces, então o SNR sai idêntico.

Um efeito colateral do original é mantido: o pré-amplificador fica com o ganho do último enlace avaliado.

Aproveitamento medido: 7–12 % nas redes pequenas dos testes. É maior em rotas longas, porque só os enlaces compartilhados com o circuito novo mudam.

**f) Consumo de energia calculado na leitura** (`network/Mesh.java`)

`computesPowerConsmption` recalculava o consumo da rede inteira a cada alocação e liberação, mas só a métrica `ConsumedEnergy` lê esses totais. Agora a chamada só marca os totais como desatualizados, e eles são calculados quando lidos, a partir do mesmo estado da rede: toda mudança de estado chama esse método antes da próxima leitura. No modo de verificação, os totais também são calculados na hora da mudança e comparados na leitura. Sem a métrica `ConsumedEnergy`, o custo cai a zero.

**g) `GroomingStatistics`**

Para contar as requisições ativas distintas, a métrica montava um `HashSet` com as requisições de todos os circuitos a cada observação. Agora cada requisição recebe uma marca (`markCounted`) com o número da observação, e a contagem é a mesma. O modo de verificação compara com o `HashSet`.

**h) Ordem das simulações** (`SimulationManagement`)

Com mais de uma thread, as simulações das cargas mais altas, que são as mais lentas, começam primeiro. Assim o fim da execução não fica com poucas simulações longas e threads paradas. Com uma thread, a ordem original é mantida.

Cada simulação é independente e tem a própria semente, então os resultados não mudam. Os ids dos circuitos são globais e se intercalam entre threads, o que já acontecia com várias threads. Isso só muda a ordem das linhas "por par" e a última casa decimal do `ConsumedEnergy.csv`, como entre duas execuções da versão anterior com várias threads.

**i) `Link.getCore`**

Os núcleos ficam guardados na posição igual ao id. Agora a busca vai direto a essa posição e só percorre a lista se o id não bater. Devolve o mesmo núcleo.

### 11.3 Mudanças no caminho QKD

**j-1) Atualização só das sessões que o circuito pode afetar** (`QKDExistingSessionImpactEvaluator.refreshActiveSessions`)

Sem multibanda, o único ruído clássico num QCh é o XT intercore (`QKDPhysicalLayer.evaluateInternal`). Um circuito clássico só soma XT a um QCh num enlace em que:

- usa um núcleo adjacente ao núcleo QKD;
- ocupa slots que se sobrepõem aos do QCh.

Quando o circuito alocado ou liberado não cumpre essas condições para nenhum canal de uma sessão, uma nova avaliação daria os mesmos números, e a sessão mantém o resultado guardado.

Isso só é usado quando o circuito alocado ou liberado é a única mudança desde a última atualização, o que é conferido com os contadores de (d):

- nenhum circuito na rede foi alterado (realocação, expansão de espectro, potência);
- as listas dos núcleos mudaram exatamente uma vez por enlace do circuito.

Fora disso, a atualização é a da Parte 24. Com `multiband`, o SpRS soma todos os circuitos do núcleo hospedeiro e dos adjacentes, então o filtro não é usado. No modo de verificação, as sessões que seriam puladas são avaliadas e comparadas bit a bit.

**j-2) Interferentes uma vez por enlace** (`QKDPhysicalLayer.collectInterferersCached`)

Um QCh é avaliado *span* a *span*, e um enlace tem vários *spans*, então a mesma lista de interferentes (circuitos dos núcleos adjacentes ao núcleo QKD) era montada de novo para cada um. Sem circuito candidato, a lista agora é guardada por (enlace, núcleo QKD) e reaproveitada enquanto a versão do enlace for a mesma. A lista só guarda os circuitos: espectro e potência continuam lidos na hora do cálculo do XT.

**j-3) Enlaces de cada sessão** (`QKDActiveConnection.getChannelLinks`)

Para saber se uma sessão usa um enlace que mudou, o código percorria as alocações e os canais compartilhados de cada sessão e consultava um conjunto com *hash*. Uma sessão e seus canais não mudam depois de criados, então a lista dos enlaces é montada uma vez e comparada por identidade. O modo de verificação compara com o teste original.

**j-4) Ordem dos testes na proteção das sessões** (`candidateCanAffect`)

Os três testes (sobreposição de slots, enlace na rota e núcleo adjacente) são os mesmos, mas o mais barato, a sobreposição, agora vem primeiro.

### 11.4 Verificação

1. **Modo de verificação nos 17 casos do teste de regressão** (`tools/regressao`, casos C, Q e M, 4 threads), com `-Dsnets.verifyPerf=true`:
   - todo valor servido por um cache foi recalculado e comparado bit a bit, sem nenhuma diferença;
   - as sessões QKD puladas também foram reavaliadas, sem nenhuma diferença;
   - os CSVs saíram iguais aos da versão anterior.
2. **Teste de regressão normal** (sem o modo de verificação), em todos os casos idênticos ou equivalentes:
   - contra a referência `main` (casos C e Q);
   - contra a referência `experimental` (casos M);
   - contra uma referência nova gerada com o JAR da Parte 52 (`p52base`, os 17 casos).

   "Equivalente" é a regra de sempre: as mesmas linhas em outra ordem, ou o `ConsumedEnergy.csv` com diferença abaixo de 1e-12 entre threads. O M01 continua com os três CSVs ignorados por causa do sorteio sem semente do `spcsav2` (seção 8.5), com o bloqueio total igual.
3. **Teste_EX e Teste_Ex_QKD reduzidos** (10 000 requisições × 8 cargas, 1 thread): todos os CSVs com os mesmos valores da versão anterior. Só a ordem das linhas "por par" muda, como entre duas execuções da versão anterior.
4. **Save states:** Q01 e C01 foram parados pelo arquivo STOP no meio (código 3) e continuados. Os CSVs saíram iguais aos da referência.
5. **Bytecode:** o JAR final foi compilado de novo depois da última troca de comentários, e as classes são idênticas às do JAR testado.

Aproveitamento dos caches (`-Dsnets.perfStats=true`, casos reduzidos):

| Cache | Teste_EX | Teste_Ex_QKD |
|---|---|---|
| `arcsinh` | 1,08 bilhão aproveitados / 39 mil calculados | 609 milhões / 39 mil |
| faixas livres | 96,8 % | 96,4 % |
| QoT por enlace | 12,4 % | 11,2 % |
| sessões QKD puladas | — | 26,3 milhões puladas / 8 reavaliadas |


### 11.5 Ganho medido

Cada versão foi rodada nas mesmas condições: a versão anterior e a nova ao mesmo tempo, ou o teste de regressão com 2 casos em paralelo.

| Caso | Parte 52 | Parte 54 | Redução |
|---|---|---|---|
| Teste_EX reduzido (10 000 × 8 × 2, 1 thread) | 258 s | 148 s | −43 % |
| Teste_Ex_QKD reduzido (10 000 × 8 × 1, 1 thread) | 202 s | 111–119 s | −41 a −45 % |
| C01 metro clássico | 30,5 s | 23,7 s | −22 % |
| C03 USA, IMPA | 222,6 s | 192,8 s | −13 % |
| C05 NSFNet, `prmscav3` | 374,3 s | 264,8 s | −29 % |
| Q01 | 34,6 s | 21,4 s | −38 % |
| Q04 rotas alternativas | 34,5 s | 14,0 s | −59 % |
| Q07 `prmscav3` + APAmem | 128,0 s | 83,5 s | −35 % |
| Q08 granularidade `route` | 71,1 s | 42,5 s | −40 % |
| M01 multibanda | 63,2 s | 41,6 s | −34 % |
| M02 MB-SDM | 96,6 s | 64,7 s | −33 % |

Somando com a Parte 45, o Teste_EX reduzido passou de 397 s na `main` para 148 s na experimental, cerca de 2,7 vezes mais rápido.

No perfil do caso QKD depois da Parte 54, os maiores consumidores restantes são:

- a iteração dos `TreeSet` de circuitos dentro do NLI e do XT, que poderia virar um array ordenado;
- o consumo de energia lido pela métrica `ConsumedEnergy` a cada requisição;
- a proteção das sessões ativas (`evaluateCandidate`), que ainda percorre todas as sessões.


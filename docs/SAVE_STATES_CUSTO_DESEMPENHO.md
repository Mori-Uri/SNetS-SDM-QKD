# Save states, custo computacional e desempenho (Parte 45)

Os três recursos desta parte não mudam nenhum resultado, e o teste de regressão confere isso. Só existem na branch
`experimental`.

## Save states

No arquivo `simulation` (ou na aba **Simulação** do launcher, grupo **Save states**):

```json
"saveState": { "enabled": true, "intervalMinutes": 10 }
```

**Gravação.** Cada simulação (carga × replicação) grava `<caso>/savestate/lp<L>_rep<R>.state`:
- a cada `intervalMinutes` de execução;
- e uma última vez quando termina.

O estado guarda a simulação inteira:
- rede e espectro;
- circuitos e sessões QKD ativos;
- geradores aleatórios;
- eventos pendentes;
- medidas.

A gravação acontece entre dois eventos, com serialização Java (gzip).

**Ao rodar o mesmo caso de novo:**
- simulações concluídas não são refeitas (os resultados delas são reaproveitados);
- simulações interrompidas continuam do último estado salvo;
- um estado gravado com arquivos de entrada diferentes é ignorado, com aviso no console. Entram na comparação o
  `network`, o `traffic`, os dois `physicalLayer`, o `others` e o `simulation`, exceto `threads` e `saveState`.

**Teste.** Interrompi Q01 (QKD), C01, C05 (clássicos) e M02 (multibanda) com `kill` no meio e rodei de novo. Os CSVs
saíram iguais aos da execução sem interrupção (ou equivalentes, pela regra do teste de regressão).

**Observações:**
- A pasta `savestate` não é apagada sozinha. Apague-a quando o estudo terminar. Cada estado tem de dezenas de kB a
  poucos MB.
- A gravação custa de frações de segundo a poucos segundos em redes grandes; com intervalo de 10 min é desprezível.
- Mudanças só para gravar o estado, sem efeito na simulação:
  - `Serializable` em `FastSwitching`, `SortListCircuits`, `QKDExistingSessionImpactEvaluator`, `QKDPhysicalLayer`,
    `BB84DecoyModel`, `QKDProvisioningService`, `QKDRoutePlanner` e nos dois *listeners* de eventos;
  - `Circuit` grava o `id` antes dos outros campos, porque os `HashSet` de circuitos precisam do `id` ao serem lidos.
- O contador global de ids de circuito (`Circuit.quantity`) é restaurado acima do maior id salvo.
- O objeto `Util`, comum a todas as simulações do caso, guarda os pares da malha da simulação (0, 0). Ele não é
  gravado: entra no arquivo como uma marca e, na leitura, volta como o `Util` da execução atual. Sem isso, cada
  estado levaria uma cópia de outra simulação e, com várias threads, a gravação lia conjuntos que a outra
  simulação estava mudando (`ConcurrentModificationException`, visto no C05).
- A leitura e a gravação rodam numa thread com pilha de 1 GB, porque a serialização Java é recursiva e o grafo de
  objetos de redes grandes estoura a pilha padrão (visto no C05, rede USA).

### Parte 52: ligados por padrão, cancelar sem perder e continuar

- **Padrão:** sem o grupo `saveState` no `simulation`, os save states ficam **ligados** (a cada 10 min). `"enabled": false` desliga.
- **Cancelar sem perder:** o botão Cancelar do launcher cria `savestate/STOP`. O simulador grava o estado de cada simulação entre dois eventos e termina com o código 3, sem gravar resultados. Ctrl+C e o fechamento da janela de console fazem o mesmo.
- **Queda de energia:** não pode ser avisada; vale o último estado gravado (no máximo um intervalo antes).
- **Continuar:** "Continuar execução" no caso, ou em Execuções → Interrompidas (uma, várias em fila ou em paralelo), roda o caso de novo. As simulações concluídas não são refeitas e as outras continuam do ponto gravado, com os mesmos resultados de uma execução sem interrupção (Q01 e Q07 conferidos, inclusive com o processo morto à força).
- **`savestate/progresso.json`:** o que está gravado (concluídas, em andamento com as requisições feitas, sem início), a data, os arquivos do caso e o JAR. O launcher usa esse arquivo para mostrar o caso como interrompido.
- **Quando o estado é ignorado:** o hash do estado inclui o JAR do simulador, então um estado de outro JAR é ignorado, como já acontecia com arquivos do caso alterados.
- **Fim do caso:** a pasta `savestate` é apagada quando o caso termina e os resultados são gravados (`"keepAfterFinish": true` a mantém).

## Custo computacional dos algoritmos

Ative a métrica **AlgorithmCost** (aba **Métricas**). Ela gera `<caso>_AlgorithmCost.csv`:

| Coluna | Significado |
|---|---|
| Component | Grooming, IntegratedRMLSA, Routing, KRouting, SpectrumAssignment, CoreAndSpectrumAssignment, ModulationSelection, PowerAssignment, Reallocation, QKDAdmission, QKDSessionProtection, QKDSessionRefresh, Simulation |
| Algorithm | classe usada (ex.: QABNE, DJK, ShortestPathsCoreAndSpectrumAssignment_v2) |
| Calls, CallsPerRequest | chamadas no total e por requisição (não dependem da máquina) |
| TotalMs, MeanUs, MaxUs | tempo total, médio e máximo por chamada |
| ShareOfSimulationPct | fração do tempo da simulação |

**Como medir.** Cada algoritmo é envolvido por um *proxy* da sua interface, que só conta a chamada e o tempo
(`System.nanoTime`). A admissão QKD e a proteção das sessões são medidas direto no plano de controle QKD.

**Como ler:**
- Os tempos são inclusivos: o tempo do espectro chamado de dentro do RMLSA integrado entra nos dois.
- Para comparar algoritmos, use `threads = 1` ou compare dentro da mesma execução.
- O número de chamadas é a medida independente da máquina.
- O PABS não é envolvido, porque a métrica de bloqueio lê os contadores dele pela classe.

## Desempenho

Encontrei os pontos quentes com o Java Flight Recorder:
- `getFreeSpectrumBands`: 30 % do tempo;
- métricas de fragmentação e utilização: 35 %.

Mudanças, todas com o mesmo resultado bit a bit:

| Onde | Mudança |
|---|---|
| `RelativeFragmentation` | as faixas livres de cada núcleo são lidas uma vez por observação, não uma vez por valor de c |
| `ExternalFragmentation` | faixas livres lidas uma vez por núcleo (antes, duas) |
| `SpectrumUtilization` | idem |
| `EventMachine.insert` | busca binária da posição do evento (antes, busca linear); mesma posição, inclusive nos empates |

**Ganho.** No caso Q01 (QKD, metro de 8 nós, todas as métricas ligadas, 1 thread), o tempo caiu de 25,8 s para
19,9 s (−23 %). O ganho é maior com mais valores de c na fragmentação relativa e com mais eventos pendentes.

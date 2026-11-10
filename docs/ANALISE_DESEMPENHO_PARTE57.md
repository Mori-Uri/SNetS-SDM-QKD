# Análise de desempenho e plano de refatoração (Parte 57, experimental)

Esta é a análise. Nada foi alterado no simulador. Ela mede onde o tempo das simulações vai hoje, depois das
otimizações da Parte 54. Também propõe as próximas otimizações e um plano de padronização e comentários do código. Cada
proposta só entra depois de aprovada.

## 1. Regras que valem para todas as propostas

- **Resultados idênticos, bit a bit.** Cada valor reaproveitado tem de ser exatamente o que o código original
  calcularia. Também a ordem das somas em ponto flutuante fica a mesma, porque mudar a ordem muda os últimos dígitos.
  Propostas que só dão resultados "equivalentes" estão separadas na seção 5 e não são recomendadas.
- **Custo dos algoritmos.** O comportamento de cada algoritmo e o número de chamadas da métrica `AlgorithmCost` não
  mudam. O que muda é o tempo medido, que cai porque cada chamada fica mais rápida.
- **`GPRMCSA.java` e `CSBASDM.java` (e os demais algoritmos) ficam intocados.** Quando um algoritmo precisar mudar, a
  mudança vai para uma cópia com prefixo `ex_` (seção 4).
- **Verificação de cada etapa:**
  - teste de regressão (`tools/regressao`, 17 casos C, Q e M);
  - modo `-Dsnets.verifyPerf=true`, que compara cada valor reaproveitado com o cálculo original;
  - comparação no mesmo JAR com `-Dsnets.noPerfCache=true` (caminho original) contra o caminho otimizado;
  - Teste_EX e Teste_Ex_QKD reduzidos, para medir o tempo.

## 2. Como foi medido

- **Código:** `experimental` atual (igual à `main` até a Parte 55), compilado com Java 21.
- **Casos:** os mesmos da Parte 54, Teste_EX e Teste_Ex_QKD reduzidos (10 mil requisições, 8 cargas, 1 thread):
  - Teste_EX: clássico, `spcsav2` + `csbasdm`;
  - Teste_Ex_QKD: QKD, `spcsav2` + `qabne`.
- **Tempos agora:** 143 s (Teste_EX) e 103 s (Teste_Ex_QKD). Antes da Parte 54 eram 258 s e 202 s.
- **Ferramenta:** Java Flight Recorder (amostragem do processador), com 11.927 e 8.539 amostras. As porcentagens são a
  parte do tempo total gasta no método e em tudo o que ele chama.

## 3. Onde o tempo vai

O resultado mais importante: **o código dos próprios algoritmos quase não pesa.**

| Onde | Teste_EX | Teste_Ex_QKD |
|---|---|---|
| Código dos algoritmos (CSBASDM, QABNE, linhas próprias do `spcsav2`, rotas) | ~1 % | ~1 % |
| Camada física e espectro (`network/`: modelo GN, crosstalk, energia) | 82 % | 61 % |
| Métricas (`measurement/`) | 14 % | 10 % |
| Controle QKD (`qkd/`: sessões ativas, admissão) | 0,3 % | 26 % |

Os algoritmos passam quase todo o tempo chamando a avaliação de QoT, de crosstalk e de energia, e as métricas são
recalculadas a cada requisição. É nessa infraestrutura comum que está o ganho.

### Pontos quentes

| # | Ponto | Teste_EX | Teste_Ex_QKD | O que acontece hoje |
|---|---|---|---|---|
| 1 | `Crosstalk.isIntersection` | 14,8 % | 6,3 % | Para saber se duas faixas de slots se cruzam, monta uma lista com os slots de uma delas (com conversão para `Integer`) e procura cada slot da outra nessa lista. São milhões de chamadas. |
| 2 | Modelo GN (`PhysicalLayer.getGnli2`) | 26,9 % | 21,5 % | Para cada circuito, soma a interferência de todos os outros do núcleo. Cada par chama `arcsinh` duas vezes. A memória do `arcsinh` (Parte 54) acerta 99,9 % das vezes, mas a consulta (50 milhões no caso C01) ainda custa 12,7 % do tempo. |
| 3 | `Circuit.getRequiredBitRate` | 6,2 % | 8,6 % | Soma as taxas das requisições do circuito a cada chamada, inclusive dentro do laço do modelo GN e da métrica de dados transmitidos. |
| 4 | Energia (`Mesh.computePowerConsumptionNow`) | 17,3 % | 12,8 % | A métrica `ConsumedEnergy` recalcula, a cada requisição, o consumo de todos os transponders, um circuito por vez. |
| 5 | `PhysicalLayer.getCircuitList` | 7,3 % | 5,5 % | Quando há um circuito em teste, copia o conjunto ordenado de circuitos do núcleo (`TreeSet`) para incluí-lo. |
| 6 | `SpectrumUtilization` | 8,8 % | 5,8 % | A cada requisição percorre enlaces, núcleos e faixas livres, guardando os valores em `HashMap<Integer, Double>` (com conversões para objeto). |
| 7 | Sessões QKD (`QKDExistingSessionImpactEvaluator`) | — | 10 % | Reavaliação e teste das sessões ativas que um circuito novo pode afetar. |
| 8 | Outras métricas (`PhysicalLayerStatistics`, `RelativeFragmentation`, `GroomingStatistics`) | ~6 % | ~4 % | Recalculadas a cada observação. |

## 4. Propostas com resultado idêntico (recomendadas)

Ganho estimado é a parte do tempo que deixa de existir no Teste_EX / Teste_Ex_QKD. As estimativas não se somam
exatamente; a seção 6 traz o total esperado.

| # | Proposta | Ganho estimado | Risco | Por que o resultado não muda |
|---|---|---|---|---|
| A | `isIntersection` por comparação direta dos extremos: `max(início) ≤ min(fim)` | −13 % / −6 % | muito baixo | É a mesma pergunta respondida sem montar lista. Faixas invertidas (início > fim) continuam dando "não cruza", como hoje. |
| B | Taxa do circuito guardada no circuito e recalculada só quando a lista de requisições muda (a lista já é rastreada desde a Parte 54) | −5 % / −7 % | baixo | É a mesma soma, na mesma ordem, feita uma vez por mudança. |
| C | Consumo de cada transponder guardado no circuito enquanto rota, modulação e slots não mudam; o total continua sendo somado circuito a circuito, na mesma ordem | −8 % / −6 % | baixo | Mesmos valores por circuito, mesma soma. |
| D | Termo de interferência de cada par de circuitos (i, j) num enlace guardado enquanto nenhum dos dois muda; a soma do modelo GN continua na mesma ordem | −12 % / −10 % | médio | Cada termo é o mesmo número; só deixa de ser recalculado. A conferência de cada termo fica com `verifyPerf`. |
| E | Circuito em teste incluído na leitura, sem copiar o conjunto ordenado do núcleo: a leitura percorre o conjunto e encaixa o circuito em teste na posição que ele teria | −4 % / −3 % | médio | Mesma sequência de circuitos, na mesma ordem. |
| F | `SpectrumUtilization` com vetores de números (sem `HashMap` de objetos) durante a simulação, convertidos para os mapas só ao gravar os resultados | −5 % / −3 % | baixo | Mesmas somas, na mesma ordem. |
| G | Sessões QKD: perfil mais detalhado do `QKDExistingSessionImpactEvaluator` antes de propor algo específico | a medir / até −5 % | — | — |
| H | Pequenos ajustes no laço do modelo GN: ler a memória por thread uma vez por laço e não a cada `arcsinh`; constantes da modulação lidas uma vez por circuito | −2 % / −2 % | muito baixo | Mesmas operações, menos consultas. |

Todas as propostas ficam atrás do interruptor que já existe: com `-Dsnets.noPerfCache=true` o simulador usa o
caminho original. Assim, o mesmo JAR roda as duas versões para comparar.

### Cópias `ex_` dos algoritmos

Como os algoritmos pesam cerca de 1 % do tempo, copiá-los não acelera as simulações. Os ganhos acima ficam na camada
física, no crosstalk, na energia e nas métricas, que todos os algoritmos usam. A comparação "original × otimizado" se
faz pelo interruptor acima, com os mesmos algoritmos.

As cópias `ex_` continuam úteis para a parte de refatoração e comentários dos algoritmos:

- **Nomes:** `ex_csbasdm`, `ex_spcsav2`, `ex_qabne`, `ex_prmscav3`, `ex_apamem`, `ex_cpsd`, `ex_modulationbyqotv2` (os
  usados nos casos).
- **Onde ficam:** pacote próprio (ex.: `gprmcsa/ex/`), registradas numa fábrica nova consultada em `Simulation.java`
  antes do `GPRMCSA`. O `GPRMCSA.java` não muda.
- **Uso:** num caso, trocar `csbasdm` por `ex_csbasdm` roda a cópia; o teste de regressão confere que a cópia dá
  exatamente os resultados do original.

## 5. Propostas com resultado só equivalente (não recomendadas)

Estas dariam mais velocidade, mas mudam a ordem de somas em ponto flutuante, ou seja, os últimos dígitos dos resultados
(diferenças da ordem de 10⁻¹⁵ relativo). Ficam registradas só para uma decisão sua:

- **Soma incremental do ruído não linear:** ao entrar um circuito, somar só o termo dele à soma já guardada de cada
  vizinho, sem refazer a soma inteira.
- **Totais incrementais de energia e de dados transmitidos:** somar e subtrair a cada circuito criado ou liberado.

## 6. Ganho total esperado

Com A, B, C, D, E, F e H, a estimativa é de **35 % a 50 % menos tempo** nos dois casos, ou seja, **1,5 a 2 vezes mais
rápido**. Somando as Partes 54 e 57, o Teste_EX iria de 258 s para algo entre 75 e 95 s. O valor real só é conhecido
medindo; cada etapa será medida separadamente.

## 7. Padronização e comentários

Feitos depois das otimizações e separados delas, em commits próprios, para que uma mudança de formatação nunca
esconda uma mudança de lógica. Uma refatoração não pode mudar nenhum resultado, e o teste de regressão roda a cada
pacote.

- **Ordem:** primeiro as classes do caminho quente (`network/PhysicalLayer`, `Crosstalk`, `Circuit`, `Core`,
  `Spectrum`, `ControlPlane`, `Mesh`, `EnergyConsumption`). Depois `measurement/`, `qkd/control`, `simulator/` e, por
  último, as cópias `ex_` dos algoritmos usados.
- **Padrão:**
  - indentação única (hoje há tabulação e espaços misturados);
  - cabeçalho em cada classe dizendo o que ela faz e quem a chama;
  - comentário em cada método público com o que entra, o que sai e as unidades (W, Hz, dB, km);
  - comentários de passo nos trechos de física (modelo GN, crosstalk, ASE), com a referência ao artigo ou à equação.
- **Idioma:** comentários em inglês, como a maior parte do código, com termos em português só nos nomes já usados nos
  arquivos de configuração. Os fontes continuam só com caracteres ASCII, porque o "Gerar JAR" compila em ISO-8859-1.
- **Fica de fora:** apagar algoritmos antigos ou sem uso, renomear classes, métodos ou chaves dos arquivos JSON, e
  mudar `GPRMCSA.java` e `CSBASDM.java`.

## 8. Ordem sugerida

1. A, B e H: as mais simples e seguras, com cerca de −20 % / −15 %.
2. C e F.
3. D e E: as maiores mudanças, com `verifyPerf` cobrindo cada termo.
4. Perfil detalhado do controle QKD (G).
5. Cópias `ex_` e padronização e comentários, pacote por pacote.

Cada etapa termina com a regressão completa, o `verifyPerf`, a medição de tempo e uma entrada no CHANGELOG.

## 9. Etapa 1 implementada (Parte 57)

Feitas as propostas A, B, C, F e H (seção 4), sem tocar nos algoritmos. Detalhes, verificação e arquivos estão no
`CHANGELOG.md`, Parte 57.

| Caso | Antes | Depois | Redução |
|---|---|---|---|
| Teste_EX reduzido | 143 s | 100 s | −30 % |
| Teste_Ex_QKD reduzido | 103 s | 83 s | −19 % |

Ficam para uma próxima etapa:

- **D:** termo de cada par de circuitos no modelo GN.
- **E:** circuito em teste sem cópia do conjunto ordenado do núcleo.
- **G:** perfil detalhado do controle QKD, que pesava 26 % do Teste_Ex_QKD na análise.

Todas seguem as mesmas regras: resultado idêntico, conferido por `verifyPerf` e pela regressão.

## 10. Etapa 2 implementada (Parte 59)

A análise da etapa 2 mediu também os casos multibanda. Neles o tempo estava em outro lugar:

- **Raman do QCh** (`expm1` por circuito e por trecho): 32 a 70 % do tempo nos casos MB e MB-SDM.
- **Modelo GN com a QoT por banda (C+L):** 56 %, porque os argumentos de `arcsinh` mudam com a banda de cada circuito e a memória da Parte 54 quase não acertava.

O que foi feito, sem tocar nos algoritmos, está no `CHANGELOG.md` (Parte 59):

- cache do fator de Raman de cada circuito;
- soma do Raman sem `TreeMap`;
- termos do GN por par de circuitos (só com a QoT por banda);
- bordas do ISRS lidas uma vez por enlace;
- cópia da lista do núcleo com o circuito em teste reaproveitada;
- correção da invalidação da taxa do circuito.

| Caso | Parte 57 | Parte 59 |
|---|---|---|
| Teste_EX reduzido | 92 s | 82 s (−11 %) |
| Teste_Ex_QKD reduzido | 79 s | 72 s (−9 %) |
| MB, fibra comum (D2, curto) | 19,9 s | 14,2 s (−29 %) |
| MB-SDM, MCF-7 (E2, curto) | 82 s | 41 s (−50 %) |
| C+L com QoT por banda (B4, curto) | 61 s | 29 s (−53 %) |

Na banda C os termos do GN por par não ajudam: a memória de `arcsinh` já atende quase todas as chamadas. Por isso esses termos só são usados com a QoT por banda.

O que ainda pesa na banda C:

- o laço do GN, que soma todos os circuitos do núcleo para cada circuito ativo a cada nova alocação;
- as métricas por requisição (energia e utilização).

Reduzir mais o GN exigiria somas incrementais, que mudam os últimos dígitos (seção 5).

# SNetS-SDM-QKD — Etapa 4: estoque temporal de chaves secretas

## Objetivo e compatibilidade

A etapa adiciona o **modo opcional de estoque temporal de chaves**, preservando o modo histórico (`rate`) como padrão. Não altera o modelo quântico BB84-Decoy, os cálculos de SKR/QBER, os algoritmos de modulação ou o modelo de amplificadores da Etapa 3.

No objeto **`simulation`** do JSON de execução, usar, por exemplo:

```json
{
  "qkdKeyInventory": {
    "mode": "temporal-inventory",
    "initialBits": 256,
    "capacityBits": 1048576
  }
}
```

Valores válidos de `mode`:
- `rate` (padrão), sinônimo `rate-reservation`: preserva a reserva de taxa existente, sem acumulação de chaves.
- `stock`, sinônimo `temporal-inventory`: utiliza um saldo de bits secretos **por QKDSpan**.

`initialBits` representa chaves pré-carregadas em **cada trecho QKD**, a partir de sua primeira utilização. É um parâmetro de cenário hipotético, não uma geração instantânea pelo protocolo. `capacityBits` é a capacidade máxima de armazenamento por trecho. Ambos são expressos em **bits** e devem ser finitos, com `0 <= initialBits <= capacityBits` e `capacityBits > 0`.

`initialBits=0` pode impedir a admissão da primeira sessão, porque a admissão exige uma chave completa em todos os trechos e QCh não são estabelecidos de maneira autônoma sem uma sessão. Para estudar partida com estoque vazio, será necessário prever uma fase explícita de pré-geração independente da admissão. Não apresente esse cenário como suportado nesta etapa.

## O que é consumido e produzido

- Cada QCh ativo gera bits secretos à **SKR física do canal**. As taxas são atualizadas na chegada/saída de requisições e durante reavaliações físicas; entre eventos de rede permanecem constantes no relógio de simulação.
- A primeira chave completa é debitada na admissão, **em cada QKDSpan**. A renovação ocorre a cada `qkdKeyDemand.updateIntervalSeconds` da demanda.
- A chave não atravessa um nó confiável como o mesmo objeto: cada salto consome a sua própria chave. Um evento de renovação **só é atendido se todos os saltos tiverem a chave completa**. Caso contrário, nenhum saldo é debitado; o evento é contabilizado como não atendido.
- O saldo nunca se torna negativo. Produção acima de `capacityBits` é contada como chave descartada. A liberação da sessão remove seus futuros eventos de consumo; o desaparecimento do último QCh de um trecho interrompe a geração, mas não apaga o saldo.
- O modo `stock` reserva uma **âncora física QCh** de 1 bit/s em vez de reservar a demanda média de cada consumidor. O consumo médio pode exceder temporariamente a SKR graças ao estoque. Isso **não** é uma reserva de 1 bit de chave por sessão; o débito real é de chaves completas.

## Exatidão temporal e custo computacional

A simulação de eventos do SNetS usa **horas**; o estoque transforma o relógio em segundos. Intervalos de renovação de microssegundos gerariam milhões ou bilhões de eventos por conexão, tornando inviável enfileirar cada renovação no SNetS.

O ledger usa três estratégias para preservar a viabilidade computacional:

1. **Cronologia exata** para até 100 mil eventos de renovação entre eventos sucessivos da rede. A geração e o limite de capacidade são aplicados no instante de cada renovação.
2. **Atalho analítico exato para um consumidor com geração suficiente em cada trecho a cada renovação**, independentemente do número de renovações; respeita capacidade e descarte de bits.
3. **Agregação aproximada em até 256 subintervalos**, quando há mais de 100 mil renovações e as condições da estratégia 2 não se aplicam. A contagem de renovações e a indivisibilidade das chaves são preservadas, mas o instante preciso de esgotamento e a ordem entre clientes no mesmo subintervalo não são exatos. O relatório registra `QKD key stock batched windows (approximate)` e `QKD key stock renewals processed approximately`.

**Limitação científica**: não interpretar a taxa de eventos não atendidos obtida no modo de agregação como estimativa exata de duração de indisponibilidade. Para esse objetivo, isolar cenários menores, garantir que a métrica de eventos aproximados seja zero ou implementar uma simulação temporal de maior resolução.

## Métricas incluídas em `QKDMetrics.csv`

- Saldo restante por replicação (agregado por trechos) e bits gerados.
- Bits efetivamente consumidos por salto (inclui chaves da admissão).
- Bits descartados por capacidade e bits correspondentes a renovações não atendidas.
- Renovações atendidas e não atendidas, rejeições por insuficiência na admissão.
- Número de janelas e renovações processadas por agregação aproximada.

No modo legado, os mesmos campos são exportados como zero e a linha `Temporal key stock enabled` retorna 0. O relatório registra os bits por salto, não a contagem de chaves idênticas compartilhadas ao longo de uma rota.

## Exemplos de demanda

Para `keySizeBits=256`, as taxas de consumo médias são:

| Renovação | Consumo médio (por trecho) |
| --- | ---: |
| 64 microssegundos (0.000064 s) | 4.000.000 bit/s |
| 128 microssegundos (0.000128 s) | 2.000.000 bit/s |
| 256 microssegundos (0.000256 s) | 1.000.000 bit/s |
| 64 milissegundos (0.064 s) | 4.000 bit/s |

**A unidade é segundos no arquivo JSON.** O valor do artigo e o exemplo de cada experimento devem ser conferidos antes de comparar curvas de SKR e bloqueio: `64` microssegundos e `64` milissegundos produzem taxas mil vezes diferentes.

## Verificação no Windows

Depois de instalar a etapa 4 sobre a etapa 3, na raiz do projeto:

```bat
scripts\verificar_estoque_chaves.bat
```

Ou executar individualmente:

```bat
java -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDTemporalKeyInventoryVerification
java -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDStage4InventoryDynamicsVerification
java -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDStage4EventLoopVerification
```

Os testes incluem continuidade em dois trechos, renovação atômica sem consumo parcial, geração entre eventos, mudança de taxa, capacidade, esgotamento, liberação, rollback, 1 milhão de renovações por microssegundo e execução pelo relógio real de eventos do SNetS em ambos os modos.

## Limitações que NÃO foram resolvidas nesta etapa

- Banco de chaves criptográficas real, autenticação, ataques, comunicação de chaves entre nós, vazamento de material e verificação experimental de hardware QKD.
- Partida com estoque inicial nulo e pré-geração em QCh sem sessões.
- Simulação contínua de outages e arbitragem exata sob sobrecarga de renovações agregadas.
- Validação quantitativa ampliada GN/EGN, EDFA e comparações estatísticas com os trabalhos científicos: previstas para a Etapa 5.

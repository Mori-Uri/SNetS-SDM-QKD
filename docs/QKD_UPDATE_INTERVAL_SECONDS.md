# Demanda de chaves QKD: intervalos em segundos

A propriedade de configuração única é `simulation.qkdKeyDemand.updateIntervalSeconds` (lista de números positivos, em **segundos**).
O tamanho da chave permanece em `keySizeBits`. A demanda de uma sessão é `keySizeBits / updateIntervalSeconds` em **bits/s**. A API Java interna utiliza os mesmos segundos.

## Moghaddam (2021) — 64, 128 e 256 MILISSEGUNDOS

```json
"qkdKeyDemand": {
  "keySizeBits": 256,
  "updateIntervalSeconds": [0.064, 0.128, 0.256],
  "mode": "uniform",
  "randomSeed": 2024
}
```

Demanda equivalente a 4.000, 2.000 e 1.000 bit/s, ou 0,004, 0,002 e 0,001 Mbit/s.
Este é o padrão para um `QKDKeyDemandConfig` novo quando não há valores no JSON.

## Dibaj (2024) — 64, 128 e 256 MICROSSEGUNDOS

```json
"qkdKeyDemand": {
  "keySizeBits": 256,
  "updateIntervalSeconds": [0.000064, 0.000128, 0.000256],
  "mode": "uniform",
  "randomSeed": 2024
}
```

Demanda equivalente a 4.000.000, 2.000.000 e 1.000.000 bit/s, ou 4, 2 e 1 Mbit/s.

As simulações existentes foram convertidas **numericamente**, preservando a taxa de cada caso. `traffic.keyRateMbps` continua expresso em Mbit/s e tem precedência quando definido para um gerador.

**Migração:** os campos antigos de intervalos em milissegundos ou microssegundos deixaram de ser os campos de configuração suportados: converta os valores para segundos antes de executar novos experimentos. Use ponto decimal em JSON (`0.000064`), não vírgula. Save states produzidos com arquivos/código anteriores devem ser reiniciados, já que o formato serializado de `QKDKeyDemand` mudou.

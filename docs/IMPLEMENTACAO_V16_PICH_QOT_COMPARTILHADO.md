# SNetS-SDM-QKD v16 — PICh integrado à camada física clássica

## Alterações

- O PICh seleciona um objeto `Modulation` que já existe em `Mesh.getAvaliableModulations()`. `network.qkd.pichModulation` é opcional; quando omitido, usa a primeira modulação configurada em `network.modulations`. Nome inexistente causa falha explícita.
- `QKDPichCircuit` armazena a mesma modulação selecionada e calcula uma taxa equivalente a partir dos slots alocados, sem configurar outra largura espectral ou outra taxa de FEC.
- `network.PhysicalLayer.evaluatePich(...)` é a entrada de QoT dos PIChs. Usa os **mesmos parâmetros físicos do SNetS** (atenuação, dispersão, não linearidade, crosstalk via `Crosstalk.getH`, ASE, figura de ruído, ganho/saturação, potência e banda) e os **mesmos limiares de `Modulation`** com `PhysicalLayer.isAdmissible` para SNR/BER. Ao contrário do cálculo DCh de enlace completo, a integração respeita as coordenadas do QKDSpan dentro do enlace.
- Os cálculos GN usam o núcleo `LongitudinalClassicalGN.sectionPsd`, compartilhado com a avaliação longitudinal dos DChs; a versão agora aceita a origem longitudinal de cada PICh. Não foi introduzido GGN/EGR/ISRS.
- A ASE é calculada para o booster (se o PICh iniciar no nó SNetS), os amplificadores de linha atravessados e o pré-amplificador do nó SNetS de destino (se aplicável). **Nenhum amplificador é inventado em trusted nodes virtuais.** A ASE reduz a SNR do PICh quando `physicalLayer.activeASE=true`.
- A avaliação também considera a carga dos DChs/PIChs ativos para ganho saturado e GN, bem como o DCh candidato durante a admissão e o XT de núcleos adjacentes. Um DCh/PICh novo não deve comprometer a QoT de PIChs já admitidos.
- A sensibilidade óptica opcional (`pichRxPowerCheckEnabled`) usa a potência efetivamente recebida calculada com o modelo de propagação; não aplica uma segunda atenuação parametrizada.
- Continuam obrigatórios: PICh por QKDSpan, somente canais copropagantes, QKDSpan contido em um enlace do SNetS, GN original e guarda de mesma banda da v15.

## Exemplo (somente os campos relevantes de `network.qkd`)

```json
{
  "pichSlots": 1,
  "pichModulation": "QPSK",
  "pichSnrCheckEnabled": true,
  "pichRxPowerCheckEnabled": false
}
```

O nome `QPSK` precisa estar na lista `network.modulations` do cenário. A seleção de SNR/BER, ASE, GN, XT, NF, saturação, ganho e parâmetros da fibra continua **exclusivamente** no arquivo `physicalLayer` existente. O valor de potência de lançamento do PICh permanece configurável como parâmetro de transmissor, pois pode diferir do DCh. Se a checagem adicional de sensibilidade estiver habilitada, mantenha apenas `pichReceiverSensitivityDbm` como característica específica do receptor PICh.

## Campos antigos a remover dos cenários

Quando `pichSnrCheckEnabled=true`, remova `pichMinimumSnrDb`, `pichReceiverNoiseW`, `pichXtCouplingPerKm`, `pichGnCoefficientPerW2Km`. Quando `pichRxPowerCheckEnabled=true`, remova `pichFiberLossDbPerKm` e `pichInsertionLossDb`. Esses campos legados não alimentam mais o motor PICh e valores antigos explícitos provocam erro de migração para impedir divergências silenciosas. Os acessores legados do parser foram retidos apenas para diagnóstico.

## Limitações

- Quando o longitudinal EDFA do SNetS não está habilitado, o PICh constrói a evolução longitudinal com os parâmetros do `PhysicalLayer`. Para intervalos menores que um enlace não é correto invocar diretamente `computeSNRSegment`, que representa todo o enlace; `evaluatePich` reutiliza os núcleos GN, ASE e ganho em uma avaliação por subtrecho. Há diferenças entre essa avaliação por subtrecho e os caminhos legados de GN Johannisson/Habibi quando esses estão selecionados.
- Não existe calibração experimental de OSNR/BER de PICh nesta entrega. A modulação fornece o limiar conforme o cenário.
- Regressões determinísticas unitárias passaram, mas não houve compilação Maven limpa nem simulações completas MF/MB/MB-SDM.

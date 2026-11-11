# V14 — PICh optical receiver power precheck

## Objetivo
O PICh continua sendo uma fonte óptica auxiliar por QKDSpan. Um orçamento
óptico opcional pode rejeitar spans cuja potência prevista no receptor seja
inferior à sensibilidade declarada pelo pesquisador. O teste ocorre ANTES da
ocupação de slots e não cria estados residuais ao rejeitar.

## Configuração (campos dentro de `network.qkd`)

```json
{
  "pichRxPowerCheckEnabled": true,
  "pichFiberLossDbPerKm": 0.2,
  "pichInsertionLossDb": 2.0,
  "pichReceiverSensitivityDbm": -18.0
}
```

**Apenas exemplo, não calibração científica.** Os parâmetros são fornecidos
explicitamente pelo usuário. Padrão: verificação DESABILITADA, mantendo v13.

`P_rx[dBm] = 10*log10(P_tx[W]/0.001) - L[km]*alpha[dB/km] - loss[dB]`.

## Limitação física
Não é QoT completa: não calcula BER/SNR/OSNR do PICh, nem ASE, dispersão,
XT e GN-NLI na recepção PICh. Essas verificações continuam pendentes.
O GN do SNetS permanece inalterado. Não há contrapropagação e cada QKDSpan
permanece limitado a um enlace SNetS.

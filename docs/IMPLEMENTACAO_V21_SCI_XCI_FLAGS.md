# SNetS-SDM-QKD v21 — Seleção independente de SCI e XCI de Johannisson

A v21 adiciona ao arquivo de configuração **`physicalLayer`** duas opções independentes:

```json
{
  "physicalLayerModel": 1,
  "johannissonSciUseIntegral": false,
  "johannissonXciUseIntegral": false
}
```

Esses campos devem ser incorporados ao arquivo de configuração já existente; não substitua o restante do objeto.

| Flag | `false` — padrão | `true` |
|---|---|---|
| `johannissonSciUseIntegral` | SCI pela aproximação logarítmica (Johannisson, Eq. 16) | SCI pela integral retangular (Eqs. 11/15) |
| `johannissonXciUseIntegral` | XCI pela aproximação logarítmica (Eq. 16) | XCI pela integral retangular (Eqs. 11/15) |

A configuração permite as quatro combinações SCI/XCI. Não implementa seleção automática por tolerância de erro nem introduz GGN/ISRS/EGN adicionais. As opções somente afetam `physicalLayerModel=1` (Johannisson); o modelo Habibi não é alterado.

## Domínio de validade da expressão logarítmica

* Na SCI, quando `log(rho)` **não for positivo e finito**, a implementação calcula a SCI pela integral positiva para não eliminar artificialmente um ruído existente. Isso inclui casos como 12,5 GHz nos parâmetros de referência, em que `rho < 1`.
* Para SCI com log positivo, o simulador respeita a seleção logarítmica, **mesmo que ela possa ser imprecisa** para bandas estreitas. Uma expressão positiva não implica precisão.
* Na XCI, quando a separação espectral não permite definir o logaritmo da Eq. 16, existe fallback para a integral positiva.
* A proteção `GnModelMath.nonnegative()` é mantida: a PSD NLI total nunca recebe valores negativos ou não finitos.

## Caminhos atualizados

* `PhysicalLayer.getGnli()` (DCh legado): SCI e XCI independentes.
* `PhysicalLayer.computeSNRSegmentLongitudinal()` (DCh longitudinal): ambas as flags.
* `PhysicalLayer.evaluatePich()` (PICh como vítima longitudinal): ambas as flags.
* `PichClassicalNonlinearity` (PICh como fonte XCI sobre DCh): usa a flag **XCI**, porque essa contribuição não é SCI da vítima.
* `LongitudinalClassicalGN.sectionPsd`: novo overload com dois parâmetros booleanos explícitos. Overloads antigos permanecem para compatibilidade binária/de fonte: o overload legado com booleano único continua interpretando esse booleano **exclusivamente como XCI** e SCI permanece integral nesse overload legado.

## Compatibilidade v20

A antiga propriedade `johannissonUseIntegral` ainda é lida como alias da **XCI**, quando `johannissonXciUseIntegral` não está presente. Ela **não muda a SCI**. Uma configuração com ambos os campos de XCI usa `johannissonXciUseIntegral` como fonte de verdade. Em novas configurações, use somente as duas flags nomeadas, como no template. Os getters/setters antigos estão marcados como obsoletos, mas foram mantidos.

## Testes e limitações

A classe `QKDJohannissonSciModeVerification` testa padrão de configuração, independência das duas flags, precedência do alias v20, domínio da SCI, propagação ao `PhysicalLayer`, quatro combinações do GN longitudinal e a proteção de não negatividade. Os testes preexistentes de Johannisson, GN científico e camada física foram executados incrementalmente.

Esta mudança afeta a estimativa de NLI e pode afetar SNR e admissão. A compilação incremental **não equivale** a uma compilação Maven limpa nem à regressão dinâmica completa das arquiteturas MF/MB/MB-SDM.

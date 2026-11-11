# v20 — Seleção de XCI de Johannisson: logarítmica (padrão) ou integral

O campo opcional `johannissonUseIntegral` foi adicionado ao arquivo existente
`physicalLayer` da simulação (não ao `qkdPhysicalLayer`).

```json
{
  "physicalLayerModel": 1,
  "johannissonUseIntegral": false
}
```

- `false` (padrão inclusive quando o campo estiver ausente): usa a XCI da
  **Equação 16** de Johannisson/Agrell (2014), logarítmica e mais rápida.
- `true`: usa a XCI das **Equações 11/15**, integral de retângulos,
  mais precisa para larguras estreitas e geralmente mais custosa.
- `physicalLayerModel` continua selecionando **Johannisson** ou **Habibi**;
  a nova flag somente altera o caminho Johannisson. Verifique o valor de
  `physicalLayerModel` no cenário — `1` é Johannisson no SNetS.

O parâmetro é propagado ao QoT dos DChs legados, à avaliação longitudinal
DCh/PICh e à XCI de PIChs sobre DChs.

## Segurança numérica e escopo

A **SCI continua usando a integral positiva das Equações 11/15**, nos dois
modos, para preservar a correção anterior de bandas estreitas e evitar
valores negativos artificiais de SCI. A flag seleciona apenas a **XCI**.

Quando a frequência central do interferente está dentro de sua própria
meia largura em relação à vítima, a Equação 16 diverge; nesse caso ocorre
fallback controlado para a integral positiva. As contribuições de NLI
continuam passando pelo `GnModelMath.nonnegative`.

O cálculo Habibi mantém `asinh` e os termos de correção EGN por modulação,
sem alteração de coeficientes GN, potência, polarização ou convenções de fibra.

## Verificação

`qkd.verification.QKDJohannissonModeVerification` comprova que o modo
padrão corresponde ao logaritmo, que a flag permite a forma integral,
que os casos com sobreposição não retornam ruído inválido e que os
caminhos longitudinais utilizam a mesma seleção.

A alteração é uma escolha matemática de desempenho. Não representa
validação experimental da precisão do GN nem garante que ambas as
aproximações produzam a mesma probabilidade de bloqueio.

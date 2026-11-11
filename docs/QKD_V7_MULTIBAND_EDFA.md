# SNetS-SDM-QKD v7 — estado de ganhos, espectro C+L e verificações

## Escopo efetivamente implementado

- `network.AmplifierGainLaw`: **uma única equação** de compressão de ganho e densidade espectral ASE usada por `network.Amplifier` e pelo modelo longitudinal QKD. A API clássica permanece compatível.
- `qkd.multiband.MultiBandEdFA`: perfis de ganho e figura de ruído por banda com carga óptica agregada de todas as bandas e compressão de ganho comum; resultados imutáveis por amplificador (`State`).
- `ClassicalPowerEvolution`: snapshots dos amplificadores por índice longitudinal; agora cada fonte possui frequência, origem e término. A propagação Raman e GN longitudinal utiliza o ganho correspondente ao comprimento de onda do DCh/PICh.
- Configuração opcional `multiband.edfaBands`: faixas C/L configuráveis (Hz convertidos de THz), com verificação de faixas sobrepostas e frequências fora da faixa. Quando ausente, preserva-se o modo legado da v6.
- Verificação numérica `QKDUnifiedMultibandVerification`: compressão compartilhada C/L, ganho ideal, ASE, limites espectrais, integridade do estado e PICh em posições longitudinais.

## Exemplo de configuração (MESCLAR em `multiband`, não substituir o JSON)

```json
{
  "longitudinalRamanEnabled": true,
  "longitudinalNliEnabled": true,
  "nliModel": "gn",
  "amplifierModel": "saturated",
  "amplifierSpacingKm": 80,
  "amplifierGainDb": 16,
  "amplifierSaturationPowerDbm": 17,
  "amplifierNoiseFigureDb": 5,
  "amplifierAseToQchEnabled": false,
  "edfaBands": [
    {"name":"L", "lowerThz":184.4, "upperThz":191.3, "gainDb":14, "noiseFigureDb":6},
    {"name":"C", "lowerThz":191.3, "upperThz":196.1, "gainDb":18, "noiseFigureDb":5}
  ]
}
```

**Parâmetros ilustrativos, não calibrados!** É preciso selecionar o `classicalBandStartThz` e os slots do SNetS de modo que suas frequências pertençam às bandas definidas. Para uma fibra que transporta C+L em um mesmo núcleo, a carga de ambas as bandas participa da mesma saturação do modelo reduzido.

## Limites científicos / implementações pendentes

1. **Estado físico único para QoT e QKD: PARCIAL.** A lei matemática de ganho foi unificada, e Raman/GN/ASE da avaliação QKD usam os mesmos snapshots imutáveis. O QoT clássico ainda NÃO consulta automaticamente esses mesmos snapshots por enlace/amplificador; `network.PhysicalLayer` mantém suas aproximações de booster, linha e pré-amplificador. Não relatar unificação completa.
2. **Modelo EDFA multibanda completo: PARCIAL.** O modelo espectral de faixas e saturação compartilhada é um substituto fenomenológico calibrável, NÃO resolve equações de população de Er³⁺, bombeamento, ASE bidirecional interna, hole burning espectral, tilt variável no comprimento da fibra, ISRS, nem componentes ópticos específicos de amplificação L. Não chamar de EDFA físico completo de laboratório.
3. **Validação científica integrada: PARCIAL.** Existem testes numéricos e verificações de regressão incrementais. Não foram reproduzidas curvas medidas em laboratório ou cenários completos das referências para esta atualização. Recomenda-se comparar potência de saída/ganho/NF por canal em C/L vs medições; e depois QBER, SKR e bloqueio para uma topologia e semente fixa.
4. **ASE na banda quântica:** amplificadores clássicos não amplificam o QCh. O vazamento espectral de ASE só deve ser habilitado após obter filtro e isolamento medidos; permanece desligado por padrão.
5. **Topologia:** uma fibra por direção; contrapropagação não participa da avaliação física da rede.
6. **Granularidade:** snapshot montado por núcleo no QKD, considerando DCh, PICh e circuito candidato; equipamentos compartilhados entre vários núcleos exigirão uma carga global explícita.

## Executar verificação incremental

Compilar `AmplifierGainLaw`, `MultiBandEdFA`, `ClassicalPowerEvolution` e `QKDUnifiedMultibandVerification` com `-encoding ISO-8859-1 -source 8 -target 8`, usando as dependências originais de SNetS no classpath. Depois executar:

`java -cp <classpath> qkd.verification.QKDUnifiedMultibandVerification`

Para validação completa, executar `mvn clean test` com todas as dependências do projeto disponíveis, além da suíte de simulações e reprodução científica.

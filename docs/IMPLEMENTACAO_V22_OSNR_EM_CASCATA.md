# SNetS-SDM-QKD v22 — Cascata física ASE/NLI e potência máxima de OSNR

## Fonte científica

V. A. C. Vale e R. C. Almeida Jr., *Power, routing, Modulation Level and Spectrum Assignment in all-optical and elastic networks*, Optical Switching and Networking 32 (2019), 14–24. DOI: 10.1016/j.osn.2018.11.001.

- Seção 3, p. 15 (PDF p. 2): ganhos e perdas de cada span aplicados em cascata.
- Seção 4.1, pp. 16–17 (PDF pp. 3–4): NLI (Equações 2 e 6), ASE (Equação 7), OSNR (Equação 9).
- Seção 5.3, p. 18 (PDF p. 5): OSNR = dP/(aP³+bP+c), Eqs. (18)–(22); ótimo = (c/2a)^(1/3), Eqs. (23)–(24).

## Implementação

1. `computePowerThatMaximizesOSNR()` e `computePowerThatMaximizesOSNR2()` chamam o mesmo cálculo de coeficientes `computeCascadedMaximumPower()`. A igualdade é exata por construção, não uma validação independente do modelo.
2. Coeficientes `a` (SCI cúbica de referência) e `c` (ASE) são avaliados após a mesma cascata de ganhos e perdas. A XCI/XT não entra no termo cúbico de referência, pois sob ganhos fixos corresponde ao termo linear `b` na Eq. 18.
3. `computeSNRSegment()` (caminho clássico sem o opt-in multibanda) agora usa `propagateLegacyCascade()` e `OpticalNoiseCascade` para transportar o sinal e as PSDs ASE/NLI/XT por enlaces e spans. A perda de MUX/switch, booster, atenuação por span, amplificador de linha, pré-amplificador e perda de DEMUX são processadas na sequência física.
4. A geração de NLI é calculada no início de cada span, inserida após seu amplificador, com fator real ganho/atenuação. O último span usa seu comprimento real. A modelagem NLI permanece incoerente/perturbativa, segundo hipóteses do GN SNetS.
5. Para circuitos interferentes, a PSD é atualizada pelo fator de ganho/perda do estágio. PIChs continuam avaliados pelas integrações de GN e XT v21, com suas posições físicas conservadas.
6. `johannissonSciUseIntegral`/`johannissonXciUseIntegral` (padrão `false`) e `MODEL_HABIBI` permanecem intactos; são usados também na SCI analítica de referência.
7. `GnModelMath.nonnegative()` continua a truncar NLI negativa para zero com diagnóstico. Não há inversão de sinal. A fórmula do ótimo não usa `1E-12` artificial no denominador.
8. Sem ASE ou sem SCI positiva, não existe máximo cúbico interior; retornam-se sementes limitadas a `[1E-9, 0.1]` W para o refinamento numérico.
9. Ganho saturado: a Equação 23 pressupõe ganhos constantes; a potência obtida é uma semente, refinada por `checkMaximumPower()` usando a QoT com saturação. Para amplificação longitudinal multibanda opt-in, o método de QoT baseado em snapshots existente continua sendo a referência.
10. `computeSNRSegment2()` continua como rotina legada não invocada, sem alterar seu contrato nesta atualização.

## Arquivos novos/alterados

- `src/main/java/network/PhysicalLayer.java` (alterado)
- `src/main/java/network/OpticalNoiseCascade.java` (novo)
- `src/main/java/qkd/verification/QKDCascadedOsnrVerification.java` (novo)

## Verificações executadas

- Compilação Java 8 incremental: aprovada, com classes substitutas *apenas para compilação* de Gson/OpenCSV, ausentes deste ambiente. **Essas classes substitutas não foram incluídas nos ZIPs.** Não equivale a compilação Maven limpa com dependências reais.
- `QKDCascadedOsnrVerification`: 17 combinações validadas. Os dois métodos de potência foram exatamente iguais; máximos analíticos melhoraram a SNR em comparação com potências 20% abaixo/acima (ganho fixo), e XCI diminuíu SNR sem alterar o ótimo cúbico em ganho fixo. O refinamento `computeMaximumPower()` retornou potência válida.
- Regressão: nove verificadores Java existentes aprovados, incluindo validação Johannisson, tese de Jurandir, crosstalk Lobato, multibanda/QKD e QoT longitudinal.
- Preservado limite de NLI em zero, inclusive nos casos Habibi com correção EGN superior ao GN.

## Restrições ainda pendentes

- Não foi executado `mvn clean test`, pois Maven e dependências Gson/OpenCSV não estão disponíveis no ambiente.
- Não foi executada a regressão dinâmica integral das arquiteturas MF, MB, MB-SDM com carga e bloqueio; comparações de PBC, SNR, SKR e QBER seguem necessárias.
- As mudanças de cascata **podem alterar as SNRs e decisões de admissão** em relação à v21; isso é esperado e requer revalidação para uso científico.
- A aproximação GN de cada seção e a carga média de PIChs não substituem integração longitudinal estritamente exata para todos os emissores ou amplificadores multibanda.

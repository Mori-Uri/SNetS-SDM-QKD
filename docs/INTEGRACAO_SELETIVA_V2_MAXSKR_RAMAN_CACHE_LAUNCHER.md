# Integração seletiva de recursos v2 no SNetS-SDM-QKD v1

Atualização: 2026-11-08. Base: código-fonte fornecido em `SNetS-SDM-qkd-v1.zip`, preservando a camada física e as correções da v1. Versão do Launcher: 1.21.0.

## 1. Modelos GN/EGN: seleção da integral

O arquivo **physicalLayer** controla separadamente a escolha de SCI e XCI no modelo **Johannisson**:

```json
{
  "physicalLayerModel": 1,
  "johannissonSciUseIntegral": false,
  "johannissonXciUseIntegral": false
}
```

Os dois `false` são o **padrão**: usa-se a aproximação logarítmica de Johannisson/Agrell (Eq. 16). Com `true` usa-se a quadratura positiva (Eqs. 11/15). Na SCI, o código faz fallback para integral quando `log(rho) <= 0` ou não finito; na XCI, quando há sobreposição e o log não está definido. Uma aproximação logarítmica positiva não garante precisão em todos os canais estreitos (12,5/25 GHz), portanto a integral explícita é importante na comparação científica. O controle SCI é independente de XCI; o campo legado `johannissonUseIntegral` só é fallback de **XCI**. O modelo **Habibi/EGN** não é convertido em Johannisson integral por essas flags; ele possui outra formulação. A discretização longitudinal (`LongitudinalClassicalGN`) também não equivale à seleção da integral espectral.

Os flags são lidos em `simulationControl.parsers.PhysicalLayerConfig`, aplicados em `network.PhysicalLayer` e executados pelos kernels em `network.GnModelMath` (inclusive pelo cálculo longitudinal).

Para comparações quantitativas com a tese de Lacerda Júnior, registre explicitamente os dois campos no JSON e execute pares de casos em modo logarítmico e integral.

## 2. `maxskr`: escolha de slot QCh

Exemplo em **network**:

```json
"qkd": {
  "enabled": true,
  "qchGranularity": "route",
  "qchSlotPolicy": "maxskr",
  "wavelengthPolicy": "grid"
}
```

Só é permitida com `qchGranularity=route`. A implementação avalia cada faixa disponível e calcula a SKR do pior trecho/enlace da rota, incluindo os recursos QKD e os interferentes clássicos já visíveis no estado de admissão. Seleciona a faixa de maior SKR mínima, com desempate pelo menor slot, e **ainda** exige SKR suficiente e a QBER independente, se ativada. A lista de resultados físicos só é memoizada **dentro da tentativa de admissão**; nenhum resultado físico é armazenado entre chegadas ou saídas. A escolha pode diferir do `firstfit` quando `wavelengthPolicy=grid` ou quando a interferência depende da faixa.

Os padrões `abne`, `firstfit` e `lowestloss` permanecem disponíveis; os cenários existentes não foram alterados. A política `maxskr` custa mais avaliações do que `firstfit`, pois examina todas as faixas livres.

## 3. Raman anti-Stokes empírico

Exemplo no arquivo **qkdPhysicalLayer**:

```json
"multiband": {
  "enabled": true,
  "ramanModel": "fisico",
  "antiStokesCorrection": "medida"
}
```

`nenhuma` é o padrão e conserva o comportamento anterior; `medida` ativa uma correção **empírica** derivada da v2, para o lado anti-Stokes da seção de choque Raman. O fator interpolado é: 1,0 em 10 THz ou menos; 2,3 em 17 THz; 2,2 em 24,7 THz; 1,1 em 27,6 THz; 1,0 em 30 THz ou mais. O lado Stokes é inalterado. Esses fatores foram relacionados na v2 a medições em fibra SMF-28 (Chapuran/Nweke) e não são uma calibração universal de MCF; devem ser comparados com os artigos e com a fibra específica antes de publicação.

A opção agora percorre `QKDPhysicalLayerConfig.toMultiBandSettings()` até `SpontaneousRamanModel` (a interface v1 exibia o campo, mas o backend ainda o ignorava).

## 4. Cache de desempenho

Preservados os caches seguros **já existentes na v1**: `GnModelMath` (kernels numéricos) e `QotLinkCache` (estado físico de enlaces com fontes clássicas e PICh). Acrescentado à `network.Spectrum` um cache transitório de **faixas livres com banda de guarda**, com invalidação nas operações de uso/liberação e cópias defensivas de resultados. O ganho depende da repetição de consultas à mesma combinação estado/guarda. Não foi importado indiscriminadamente o cache de `QotLinkCache` da v2, baseado em carimbos de versão, porque a v1 tem um conjunto mais amplo de fontes e condições de invalidação (inclusive PICh).

Parâmetros JVM de diagnóstico:

- `-Dsnets.noPerfCache=true` — evita cache de desempenho para comparação A/B.
- `-Dsnets.verifyPerf=true` — em cada acerto do cache de faixas, reexecuta a consulta sem cache e compara os resultados; usado em testes, com custo extra.

Não existe garantia de ganho em qualquer tráfego. Não altere as fontes interferentes sem invalidar os caches físicos existentes.

## 5. Launcher v1.21.0

Atualizada interface de caso QKD com: `maxskr` (somente em route), correção Raman `medida`, opções independentes SCI/XCI integral, seleção de perfis de chaves Moghaddam/Dibaj, limiar opcional de QBER, XT longitudinal, QoT dos PIChs, reutilização espectral de PICh, controles de demanda e informações sobre cache. Validação do launcher agora reconhece a granularidade `route` (uma validação redundante antiga a rejeitava) e verifica os novos valores. O JAR independente está em `launcher/snets-launcher.jar`, compilado a partir dos fontes atualizados.

Para construir no Windows 11: `launcher\\build-launcher.bat`; para executar: `java -jar launcher\\snets-launcher.jar --project <pasta-do-projeto>`. A versão também aparece no cabeçalho/status do servidor.

## 6. Testes adicionados

- `qkd.verification.QKDMaxSkrRouteVerification`: teste real de alocação: `firstfit` escolhe slot 1, `maxskr` escolhe slot 9 no cenário determinístico de 40 km/16 slots/grade de λ; capacidade e λ relatados no console. Executado também com cache desligado e verificação de cache ativada.
- `qkd.verification.QKDV2SelectiveIntegrationVerification`: valida configuração `maxskr`, rejeição fora de `route`, fatores Raman, preservação do Stokes, ponte de configuração Raman, política antiga e cache de espectro incluindo alterações e cópias defensivas.

O script existente `scripts/ci_qkd_clean.bat` detecta automaticamente os testes `*Verification.java`. Execute em máquina com Maven e dependências resolvidas. Aqui, os arquivos Java alterados passaram por compilação dirigida com `javac` usando classes fornecidas pela distribuição original. Não foi possível comprovar compilação limpa integral devido à ausência de Maven e de dependências locais. Não se deve confundir este resultado com uma aprovação end-to-end das simulações.

## 7. Compatibilidade

Nenhum cenário do usuário foi sobrescrito. `maxskr` e `medida` são **opt-in**, e o cache de espectro deve preservar resultados com ou sem a opção JVM de desligamento. Os padrões QBER opcional desligado e verificação de QoT do PICh ligada foram mantidos da v1.

# Refatoração dos componentes da camada física — 08/11/2026

## Objetivo

Reunir implementações que utilizam as mesmas leis físicas, sem alterar equações,
parâmetros, políticas de fallback, resultados numéricos ou protocolos QKD.

| Antes | Depois |
|---|---|
| `network.AmplifierGainLaw` | `network.Amplifier`: métodos estáticos `gain`, `asePsd`, `sharedCompression`, `gainFromCompression`, `noiseFactor`. |
| `network.HabibiEgnHybrid` | `network.PhysicalLayer`: integra cache, `evaluate`, `legacySci`, `legacyXci`, `supportedChannel`, `cachedPoggioliniReference`, `HabibiEgnDecision`. |
| `network.HabibiEgnComparisonVerification` | `verification.HabibiEgnComparisonVerification` (mesmo nome, novo pacote). |
| `network.LobatoSpectralOverlap` | `network.Crosstalk`: métodos estáticos `overlappingSlots`, `interfererFraction` e validador interno. |
| `network.PoggioliniGnIntegral` | `network.Poggiolini2017EgnReference.evaluateGnIntegral`, `GnResult`, `Grid`, `mu2`. |
| `network.Poggiolini2017HybridVerification` | `network.Poggiolini2017EgnReference.main`: programa de comparação reproduzível. |
| `network.Poggiolini2017EgnReference` | Preservada, agora única classe de GN integral + referência EGN + verificação de Poggiolini. |

## Regressão aplicada

O projeto foi compilado em Java com `-source 8 -target 8` no conjunto
modificado, usando as classes de apoio originais para tipos não modificados.
Não equivale a um Maven `clean package` de toda a aplicação.

- `verification.HabibiEgnComparisonVerification`: 25 casos aprovados;
  arquivo CSV **byte a byte idêntico** ao da implementação anterior.
- `network.Poggiolini2017EgnReference` (método `main`): 15 casos aprovados;
  arquivo CSV **byte a byte idêntico** ao da implementação anterior.
- `qkd.verification.QKDUnifiedMultibandVerification`: aprovado.
- `qkd.verification.QKDLobatoPerLinkPichVerification`: aprovado.
- `qkd.verification.QKDv25SharedNoiseFactorVerification`: aprovado.
- `verification.PhysicalModelRefactorVerification`: verifica ganho, ASE, saturação,
  intervalo de slots Lobato e cálculo EGN; aprovado.

## Como aplicar apenas as alterações

1. Extraia `SNetS-refatoracao-fisica-somente-alteracoes.zip` na raiz da sua
   instalação SNetS (pasta que contém `src`, `scripts` e `pom.xml`).
2. Execute `scripts\remover_classes_refatoradas.bat` no Windows ou
   `scripts/remover_classes_refatoradas.sh` no Linux.
3. Faça compilação limpa: `mvn clean package` (é necessário Maven configurado).
4. Execute `scripts\verify_habibi_egn.bat` e demais verificadores de regressão.

As referências a nomes antigos em relatórios históricos sob `docs/` são
registros de versões anteriores e não significam que essas classes continuem
existindo no código atual.

# Teste de regressão (golden master)

Prova que uma versão nova do simulador dá **os mesmos resultados** de uma versão de referência, caso a caso e CSV a
CSV. Use antes de juntar qualquer mudança na `main` (otimização, save states, multibanda, refatoração).

Requisitos: Python 3.8+ (só a biblioteca padrão) e Java 17+.

## Uso

Na pasta `tools/regressao`:

```bat
:: 1. comparar o JAR experimental com a referência da main
python regressao.py comparar --jar ..\..\simulations\SNetS-SDM-QKD-exp.jar --referencia main

:: 2. conferir se um JAR é determinístico (roda cada caso duas vezes)
python regressao.py determinismo --jar ..\..\simulations\SNetS-SDM-QKD.jar

:: 3. (re)gerar uma referência, ex.: depois de uma mudança aprovada que altera resultados de propósito
python regressao.py gerar --jar ..\..\simulations\SNetS-SDM-QKD.jar --referencia main
```

Opções: `--casos "C0*,Q01*"` (filtro), `--paralelo 3` (casos ao mesmo tempo), `--java <caminho>`,
`--tolerancia 0` (exige bytes idênticos). O resultado termina com `RESULTADO: todos os casos idênticos à referência`
ou com a lista dos CSVs que mudaram, a primeira linha diferente e o maior desvio numérico.

## Casos (`casos/`)

Casos pequenos (≤ 3000 requisições, 2 cargas, 2 replicações) com semente de tráfego fixa (`trafficRandomSeed`):

| Caso | O que cobre |
|---|---|
| C01_classico_metro | clássico, metro de 8 nós, csbasdm + spcsav2 + cpsd |
| C02_classico_nucleo_desativado | clássico com núcleo desativado (recurso do SNetS-SDM-QKD) |
| C03_classico_usa_impa | clássico, USA, prmscav3 + apamem (IMPA, SBRC 2026) |
| C05_classico_prmscav3 | clássico, prmscav3 + apamem |
| Q01–Q10 | QKD: QTND/rotas, PICh, núcleo central, rotas alternativas, limite de Q-TRx, chave pela taxa, qrmsta/apamem, granularidade route/span, nós por enlace |
| M01_mb_linha_ssmf_fisico | multibanda (Parte 43): MB, fibra monomodo, Linha, Raman `fisico` |
| M02_mbsdm_losango_artigo | multibanda: MB-SDM, MCF de 7 núcleos, Losango, Raman `artigo` |
| M03_mf_linha_mcf7 | multibanda: MF (fibra escura), MCF de 7 núcleos, Linha |

Os casos M só fazem sentido com o JAR experimental (o da main ignora o grupo `multiband`). Compare-os com a
referência `experimental`:

```bat
python regressao.py comparar --jar ..\..\simulations\SNetS-SDM-QKD-exp.jar --referencia main --casos "C*,Q*"
python regressao.py comparar --jar ..\..\simulations\SNetS-SDM-QKD-exp.jar --referencia experimental --casos "M*"
```

## Referências (`referencias/<nome>/`)

- `manifesto.json`: SHA-256 de cada CSV, JAR usado e data. **Vai para o git.**
- `csv/<caso>/`: os CSVs, para mostrar onde dois arquivos diferem. **Fica local** (`.gitignore`); sem eles a comparação
  ainda funciona pelo hash, mas não mostra a linha que mudou.

Referências atuais:

- `main`: `SNetS-SDM-QKD.jar` da main (Parte 41), casos C e Q.
- `experimental`: `SNetS-SDM-QKD-exp.jar` da Parte 43, casos M.
- `sbrc26_semente`: o SNetS-SDM-SBRC26 original (artefato do SBRC 2026) recompilado com uma única mudança de teste:
  a semente do tráfego lida do caso, igual à do SNetS-SDM-QKD. O JAR original usa semente pelo relógio e não repete
  os próprios resultados, então só assim dá para compará-lo byte a byte (ver CHANGELOG, Parte 42).

## Regras de comparação

- CSV da referência que falta na versão nova: falha. CSV só na versão nova (métrica nova): `NOVO`, não é falha.
- Hash diferente mas conteúdo equivalente: `EQUIVALENTE`, não é falha. Equivalente quer dizer:
  - números iguais até 1e-12 relativo (última casa de somas em ponto flutuante em outra ordem entre threads, ex.:
    `ConsumedEnergy.csv`);
  - as mesmas linhas em outra ordem (tabelas por par impressas a partir de um `HashMap`);
  - rótulo com a grafia corrigida (lista `RENOMEADOS` no script; hoje só `Numm Attempts` → `Num Attempts`).
- Qualquer outra diferença é falha.
- Um caso pode listar CSVs a ignorar em `casos/<caso>/regressao.json` (`ignorar`, `motivo`, `detalhe`). Eles aparecem
  como `IGNORADO`. Hoje só o M01 usa isso: o `spcsav2` sorteia sem semente o núcleo usado para classificar a causa de
  um bloqueio clássico (`ShortestPathsCoreAndSpectrumAssignment_v2`, linha 117). Com um só núcleo clássico, a divisão
  fragmentação × outros e a fragmentação externa mudam entre execuções, também no JAR da main.
- `--somente-comuns` (padrão) compara só os CSVs gerados pelas duas versões (o JAR clássico não gera os CSVs do QKD).

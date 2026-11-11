# SNetS Launcher 1.22.0 — revisão dos campos físicos e interface

**Alvo:** árvore do simulador **SNetS-SDM-QKD v1 integrada (maxskr/Raman/cache)**.
**Escopo:** arquivos do Launcher, sem substituição dos modelos de camada física clássica ou QKD em `src/main/java`.

## Instalação Windows 11

1. Faça backup de `launcher/` da sua versão v1 atual.
2. Extraia o pacote **Launcher-1.22.0-Para-SNetS-v1.zip** na raiz do projeto, mesclando a pasta `launcher/`.
3. Execute `java -jar launcher/snets-launcher.jar --project C:\\caminho\\do\\SNetS` (ou use seu atalho existente).
4. Atualize a aba do navegador com **Ctrl+F5**, para descartar a versão anterior de JS/CSS.
5. Caso queira recompilar o Launcher, execute `launcher\\build-launcher.bat` com um JDK instalado.

> O Launcher inclui HTML, JavaScript e templates dentro do próprio JAR. Não altere os experimentos existentes sem salvar uma cópia.

## Corrigido

- Recuperados o botão **Selecionar** e a funcionalidade **edição em lote** da v2: `bulk.js`, cabeçalho sticky, seleção, fechamento de lote, estilos e banner de progresso salvo. Remove o erro de acesso a elementos inexistentes em `app.js` no Launcher 1.21.0.
- Reintegrado o layout de abas compatível com `caseSticky`, corrigindo as dimensões usadas pelo estúdio de resultados.
- Expostos e editáveis os parâmetros `muxInsertionLoss`, `demuxInsertionLoss` e o fallback legado `switchInsertionLoss`, todos em dB. O **template clássico** agora explicita 5 dB para cada um, o mesmo valor que o fallback anterior (sem mudar a física quando ambos faltavam).
- `qkdPhysicalLayer.opticalBudget`: `txMuxLossDb`, `rxDemuxLossDb`, `bypassLossDb` (dB) e `residualAseReceiverW` (W), com padrões zero, conforme implementação existente no simulador.
- `qkdPhysicalLayer.pich.launchPowerDbm` (dBm) exposto. Um grupo novo pode ser acrescentado a arquivos antigos pelo botão `+` sem alteração automática dos arquivos.
- Em novos `qkdPhysicalLayer`, os dois grupos opcionais acima são preenchidos com padrão físico zero.
- `simulation.qkdKeyDemand.profile` explícito: `custom`, `moghaddam2021` (ms) ou `dibaj2024` (µs). `preferGeneratorFixedRate` é mostrado para controlar prioridade de `traffic.keyRateMbps`.
- Modelo NLI explicado no seletor: `0 = Habibi / EGN aproximado`, `1 = Johannisson GN`; flags de integral SCI/XCI atuam em Johannisson, não sobre a correção EGN de Habibi. Incluída advertência sobre canal físico estreito e sobre **slots não serem automaticamente larguras de sinal**.
- Validação de perdas com números finitos não negativos e aviso de fallback de campos clássicos ausentes. `residualAseReceiverW` exige watts e não dBm.
- Preservadas as opções integradas previamente: `maxskr`, correção Raman anti-Stokes (opt-in), QBER independente opcional, QoT PICh por padrão, XT longitudinal, `pichLongitudinalReuse` e outras.

## Verificações executadas

- `javac --release 8` e empacotamento JAR: **PASS**.
- `node --check` em todos os 20 JS do Launcher: **PASS**.
- `python launcher/tests/test_schema_contract.py`: **PASS**.
- Inicialização de servidor Launcher 1.22.0 e HTTP 200 para `/` e `/js/bulk.js`: **PASS**.
- Smoke test do DOM via Chromium headless com API simulada a partir de respostas reais do Launcher: **PASS**; nenhuma exceção JavaScript; seleção de casos, abertura de configuração, campo de MUX e botão para orçamento óptico QKD verificados. Nenhuma configuração foi salva nesse teste.

**Não foi executada compilação Maven integral do SNetS nem simulação de rede.** Alterações deste pacote afetam somente Launcher; os cenários existentes não são sobrescritos.

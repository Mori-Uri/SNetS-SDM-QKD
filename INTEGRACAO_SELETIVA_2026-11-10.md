# SNetS-SDM-QKD v1 — integração seletiva (10/11/2026)

## Base e escopo

Base de produção preservada: projeto v1 de 09/11/2026 (com continuidade espectral QCh/PICh), sobrepondo as duas últimas correções dos verificadores. A branch experimental de 10/11/2026 foi usada somente como **fonte seletiva de implementação**, não como substituta dos modelos físicos da v1.

### Melhorias incorporadas

1. **Desempenho, experimental:** `network.GnPairCache` foi adaptado para memoizar coeficientes GN/EGN geométricos entre pares de circuitos clássicos na rotina Habibi de `PhysicalLayer.getGnli2`. O cache é **opt-in**, `-Dsnets.gnPairCache=true`; padrão = false. Guarda apenas fatores dependentes de frequência, largura, fibra e modulação, nunca potência óptica, ASE, ganho longitudinal, PICh ou NLI total. Cada circuito tem tabela `ThreadLocal` transitória (evita concorrência e Save State); tamanho limitado. `-Dsnets.verifyPerf=true` verifica coeficientes recalculados quando há acerto.
2. **Observabilidade/rastreabilidade, experimental:** `simulator.PerfChecks` e `network.PhysicalStateChanges`, adaptados à v1, imprimem contadores de cache de faixa espectral, QoT e GN e alguns eventos de mudança de circuito/membros de núcleo com `-Dsnets.perfStats=true`. A telemetria é **parcial**: alterações feitas diretamente em coleções mutáveis e todas as representações translúcidas não são instrumentadas. Por segurança, não autoriza pular reavaliação QKD ou inferir estabilidade da interferência óptica.
3. **Launcher 1.24.0:** incorporado `Picker.java` da experimental para escolha de pastas locais a partir do formulário de Configurações, mantendo digitação manual para ambiente sem interface gráfica. Integração restrita ao endpoint HTTP POST `/api/pick`, acessível no servidor vinculado ao loopback. Mantidos os campos da v1: MUX/DEMUX, Habibi híbrido, SCI/XCI Johannisson, PICh, QKD, continuidade e MaxSKR. Adicionadas dicas de A/B e unidades legadas no formulário e validações do editor.
4. **Legado de intervalos QKD:** `simulation.qkdKeyDemand` agora aceita **um único** campo entre: `updateIntervalSeconds`, `updateIntervalsMicroseconds`, `updateIntervalsMilliseconds` e `updateIntervalsMiliseconds` (grafia solicitada). Conversão para segundos é explícita, entrada vazia/não finita/não positiva é rejeitada; campos concorrentes causam erro com descrição. Perfil `custom` usa valores do campo informado; quando nenhum campo é informado, preserva 64/128/256 ms. Perfis `moghaddam2021` e `dibaj2024` mantêm seus valores bibliográficos. O campo `traffic.keyRateMbps` continua tendo precedência quando `preferGeneratorFixedRate=true`.

### Exemplos de entradas alternativas

```json
{"qkdKeyDemand":{"profile":"custom","keySizeBits":256,"updateIntervalsMicroseconds":[64,128,256]}}
```

64 μs = 0,000064 segundos; 256 bits / 0,000064 s = **4.000.000 bit/s = 4 Mbit/s**.

```json
{"qkdKeyDemand":{"profile":"custom","keySizeBits":256,"updateIntervalsMiliseconds":[64,128,256]}}
```

64 ms = 0,064 segundos; 256 bits / 0,064 s = **4.000 bit/s = 0,004 Mbit/s**. Também aceito: `updateIntervalsMilliseconds` (grafia inglesa correta). Não colocar simultaneamente com `updateIntervalSeconds`.

## A/B executado neste ambiente

- **A**: `-Dsnets.noPerfCache=true -Dsnets.gnPairCache=false`.
- **B**: `-Dsnets.gnPairCache=true -Dsnets.verifyPerf=true -Dsnets.perfStats=true`.
- Mesmo código Java do núcleo atualizado compilado contra classes de suporte pré-compiladas, mesma malha controlada de dois nós, 15 canais de 32 GHz, QPSK, fibra de 100 km, modelo Habibi, mesma potência e mesmo espectro. 120 avaliações repetidas; reavaliações após mudar potência, frequência e largura de um interferente, e restauração da entrada original.
- **Sete métricas `METRIC` coincidiram bit a bit**, inclusive a potência de NLI e as perturbações. A restauração reproduziu a PSD original.
- Cache Habibi (B): **1.719 acertos / 17 faltas** de coeficientes GN/EGN. Estes números medem reutilização, **não aceleração**. Tempo registrado em B com `verifyPerf` ativado não é comparável diretamente ao tempo de A. Em execuções adicionais sem verificação, não houve redução de tempo consistentemente demonstrada. A otimização permanece **desativada por padrão** até existir benchmark representativo.
- Verificação de conversão de intervalos com setters Java (`QKDLegacyIntervalsABVerification`): **PASS**; incluiu conversões, demanda de chaves e rejeição de conflito/zero.
- Verificação física da tese (`QKDThesisSNetSPhysicalLayerVerification`, com componentes recompilados): **PASS**, 28,086036330 dB no cenário de um span; manteve 45,075736373 dB para 50 spans no teste analítico.
- Launcher: compilação `javac --release 8` (**bytecode versão 52**), `node --check` para arquivos JS, HTTP `GET /api/info` (versão 1.24.0), HTTP estático e rejeição de modo inválido em `POST /api/pick`: **PASS**. Teste de validação de conflitos e grafia `Miliseconds` no schema/validador JS: **PASS**.

**Limitações dos testes:** não foi possível executar o build integral do simulador usando o JAR base completo do Windows (não presente no ZIP de fonte desta etapa); componentes modificados foram compilados seletivamente e ligados a classes existentes do ZIP v1 original. Não se executou benchmark de tráfego dinâmico, testes de concorrência do simulador completo, teste do diálogo de pastas na área de trabalho Windows ou campanhas A/B com QCh+PICh, ASE, amplificadores saturados e múltiplas bandas. Nenhuma equivalência global ou ganho de tempo está comprovado. A verificação baseada em Gson real permanece para seu JAR base; a sintaxe Java foi compilada e a validação de unidades via Java isolado foi executada.

## Uso no Windows 11

1. Faça backup do projeto v1 e aplique o ZIP **Somente Alterações** na raiz `SNetS-SDM-qkd-v1`.
2. Recompile o simulador com o Launcher/JarBuilder ou com o script atual `scripts\recompilar_e_retestar_qkd.bat full`.
3. Execute, a partir da pasta `scripts`, ` .\verificar_integracao_seletiva_ab.bat ` (com JAR `SNetS-SDM-QKD-exp.jar` já reconstruído). A comparação grava A/B e `comparacao.txt` em `simulations\verification-reports\AB_seletivo\`.
4. Para benchmarks posteriores com tráfego real, use o mesmo JAR/arquivos/seed/carga em duas execuções: A: `-Dsnets.noPerfCache=true`; B: `-Dsnets.gnPairCache=true` (sem `verifyPerf` ao medir tempo). Use a terceira rodada `-Dsnets.verifyPerf=true` apenas para conferência de acertos.
5. Use `-Dsnets.perfStats=true` para obter indicadores de reutilização e contagem parcial de mutações.

## Funcionalidades da experimental NÃO copiadas deliberadamente

`TrackedRequestList` e o mecanismo de saltar reavaliação QKD com base apenas em contadores (dependeriam de instrumentação integral das fontes PICh, estados de saturação, direções e bypass); troca de `QotLinkCache` pelo cache baseado só em versão de enlace (poderia perder fontes longitudinais); AppShell/tray, atualização remota e exportadores de animação de vários formatos (alterações grandes, não necessárias para este incremento). A v1 mantém seu cache QoT de snapshot completo, seu rastreamento físico e suas regras de admissão.

## Arquivos e evidências

O CSV `SNetS-v1-AB-Resultados-2026-11-10.csv` contém as sete métricas A/B comparadas. O ZIP `SNetS-v1-AB-Evidencias-2026-11-10.zip` traz logs, métricas e testes básicos. O manifesto `SNetS-v1-Integracao-Seletiva-Manifesto-2026-11-10.csv` relaciona somente os arquivos realmente modificados/adicionados nesta integração, além do novo JAR do Launcher. Nenhum arquivo científico anexado foi editado.

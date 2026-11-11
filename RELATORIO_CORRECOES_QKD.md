# SNetS — análise e correções para a regressão de 09/11/2026

## Evidência recebida

Depois de reconstruir `SNetS-SDM-QKD-exp.jar` a partir de 426 fontes e gerar 615 classes, um dos quatro verificadores passou: `QKDSpanGranularityVerification`. Os outros três terminaram com `exit=1`. O retorno `exit=2` do Launcher é a sinalização correta de regressão não aprovada.

## Ajustes implementados (somente código-fonte)

1. `QKDExistingSessionProtectionVerification.java`: a verificação do impacto de um DCh sobre a sessão QKD ativa utiliza `pichTransport=ip` exclusivamente em seu fixture. Assim, o PICh óptico não invalida a sessão-base antes de medir o efeito XT do DCh. O padrão de produção, PICh óptico dedicado, **não é alterado**. Continua sendo obrigatório que o DCh interferente seja rejeitado, que um não interferente seja admitido e que recursos sejam liberados.
2. `QKDQualificationScenarioVerification.java`: os testes antigos pressupunham slot 1 sempre viável, exatamente um PICh por enlace e PICh compartilhado por padrão. Na implementação atual, a admissão calcula a SKR *depois* da reserva dos PIChs ópticos e cada QKDSpan tem PICh próprio. A verificação passou a aferir banda contínua realmente alocada (sem assumir posição fixa), seis PIChs físicos independentes para seis spans, três reservas de espectro por enlace, ausência de vazamento e reutilização TDM explícita por span (`pichAllocation=tdm`, `pichSessionsPerChannel=256`). Somente o fixture usa TDM: o padrão global continua dedicado. A faixa restante no enlace bloqueado é preenchida dinamicamente em vez de assumir slots 3..N.
3. `SaveStateStore.java`: `inputHash()` não percorre mais por reflexão via `Gson.toJson` os objetos de configuração em memória. A identificação SHA-256 é calculada a partir dos JSONs de entrada efetivos, com prioridade do arquivo `.json`, incluindo `qkdPhysicalLayer`. A implementação segue incluindo `simulatorBuild()` e desconsiderando as opções `threads` e `saveState`, que não alteram os resultados da simulação. Isso elimina o caminho de serialização reflexiva apontado pela pilha de exceção do teste `QKDNativeArticleBaseVerification`, mas a causa original da exceção não pode ser identificada sem o **início completo do log**.
4. `SaveStateInputHashVerification.java`: novo teste do hash de entradas físicas, QKD e simulação, inclusive exclusão de opções de execução e preferência por `.json`.
5. `scripts/recompilar_e_retestar_qkd.bat`: a verificação rápida passa a incluir o novo teste do hash. `full` também executa `QKDNativeArticleBaseVerification`.

## Observações de compatibilidade

A assinatura do hash mudou. Estados gerados por versões anteriores podem não ser retomáveis (eles deverão ser rejeitados pelo hash), comportamento seguro diante de uma atualização do simulador. Fazer backup dos diretórios `savestate` antes da aplicação. O código **não** desativa Save State. O objeto Java de estado e sua serialização gzip não foram alterados.

## Verificações realizadas neste ambiente

- Conferido que os três arquivos existentes são idênticos, antes da edição, aos do pacote `SNetS-SDM-QKD-Verificacoes-Corrigidas-Projeto-Completo.zip` entregue anteriormente.
- Arquivos Java alterados compilados com `javac --release 8` contra as classes binárias da distribuição original **e stubs limitados da API Gson** (teste de sintaxe e compatibilidade de métodos, sem prova de execução funcional).
- **Não executadas** as quatro regressões reais em Windows / JDK 23 / JAR base do usuário, indisponível aqui.

## Como aplicar

Extraia `SNetS-QKD-ajustes-regressoes-2026-11-09.zip` diretamente na raiz `SNetS-SDM-qkd-v1`, preservando os caminhos. Recomenda-se backup dos arquivos existentes.

Em PowerShell aberto na pasta `scripts`:

```powershell
.\recompilar_e_retestar_qkd.bat
.\recompilar_e_retestar_qkd.bat full
```

Se `QKDNativeArticleBaseVerification` ainda falhar, capture o início e as ocorrências de `Caused by` no log da nova execução:

```powershell
$log = Get-ChildItem '..\simulations\verification-reports' -Recurse -Filter '*QKDNativeArticleBaseVerification.log' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Get-Content $log.FullName -TotalCount 90
Select-String -Path $log.FullName -Pattern 'Caused by:','Exception','Error' | Select-Object -First 25
```

Não é correto afirmar que todas as verificações passaram até executar o BAT no ambiente real.

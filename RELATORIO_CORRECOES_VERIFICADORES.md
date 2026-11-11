# Diagnóstico e correções — verificações QKD (09/11/2026)

## Fontes inspecionadas
- ZIP anexado `verification-reports.zip`, contendo registros das execuções 05:09, 05:09:22, 05:09:57, 05:18:51 e um único log da execução 06:17:20 (`QKDExistingSessionProtectionVerification`).
- Base de código: último projeto completo `SNetS-QKD-Continuidade-QCh-PICh-Projeto-Completo-2026-11-09.zip` (contém os patches prévios de regressão).
- `QKDControlPlane.java`, método de pré-admissão de DCh chama `existingSessionImpactEvaluator.evaluateCandidate(circuit, sessionRegistry, true)`.
- `QKDExistingSessionImpactEvaluator.java`, parâmetro `forceAll=true` marca `affected=true` para toda sessão ativa e a reavalia. Isto é proposital: Raman, ASE e GN/NLI podem afetar QCh sem sobreposição espectral.
- `QKDPichPool.java`, `SpectrumLease` reutiliza a reserva espectral em QKDSpans longitudinalmente disjuntos no mesmo enlace físico, sem unificar suas fontes ópticas independentes.

## Diagnóstico confirmado — 06:17:20
```
[OK] baseline active QKD session established on core 0 / slot [1,1]
[OK] overlapping classical candidate rejected before resource commitment
[OK] rejected candidate leaves classical and QKD resources unchanged
AssertionError: non-overlapping candidate must not be marked as affecting QKD
```

A falha é **exclusivamente na asserção do verificador**: `harmlessImpact.getAffectedSessions()==0` é incompatível com a chamada de produção `forceAll=true`. Não se deve enfraquecer a proteção QKD para fazer essa asserção passar.

**Correção:** continuar exigindo admissibilidade da requisição sem sobreposição e SKR preservada; exigir exatamente uma sessão reavaliada, uma sessão marcada `affected` pela reavaliação integral e **zero violações**. Nenhuma classe de produção foi modificada.

## Inconsistência identificada por inspeção de código — QualificationScenario
O novo mecanismo de continuidade espectral (`QKDPichPool`) mantém **um PICh físico independente por QKDSpan** enquanto permite que três PIChs longitudinalmente disjuntos de um mesmo enlace compartilhem uma única *SpectrumLease* e, portanto, uma única faixa espectral reservada.

No código da verificação da qualificação ainda havia duas asserções exigindo três slots físicos ocupados no núcleo 0 para os três PIChs daquele enlace. Após a migração para `SpectrumLease`, essa expectativa está obsoleta.

**Correção:** configurar explicitamente as duas flags de continuidade no cenário, verificar 3 fontes PICh independentes por enlace, continuidade da mesma faixa em toda a rota e número de slots reservados igual à largura da faixa (1 neste cenário). Na segunda admissão TDM, exigir que não se criem reservas adicionais. A verificação de liberação continua exigindo zero vazamentos.

**Atenção:** não existe, no ZIP enviado, o log `QKDQualificationScenarioVerification` da execução 06:17:20. Esta é uma **correção preventiva baseada no código**, não a confirmação de que a execução mais recente falhou nesse ponto.

## NativeArticleBase e SaveState
Os logs anteriores apontam `IllegalArgumentException: NaN is not a valid double value` durante serialização reflexiva `Gson.toJson(...)`, a partir de `SaveStateStore.inputHash`.

A versão atual do projeto **já substitui esse caminho** pelo hash SHA-256 da árvore JSON dos arquivos de entrada, sem reflexionar o grafo de objetos de configuração e sem alterar resultados físicos. Ainda falta executar a verificação completa na versão atual. O ZIP enviado não contém log desta verificação da execução 06:17:20.

## Execução e validação
- **Compilação sintática Java:** as duas classes de verificação modificadas compiladas com `javac --release 8 -encoding ISO-8859-1` usando classes pré-compiladas da versão original e as interfaces auxiliares locais. Verificado (sem erro; JDK 21 emitiu apenas avisos de opções obsoletas).
- **Regressão real via `SNetS-SDM-QKD-exp.jar` do Windows:** não executada aqui. É necessário aplicar os arquivos e executar novamente `scripts\recompilar_e_retestar_qkd.bat full`.
- A correção não muda os modelos de interferência, SKR, QBER, PICh, continuidade ou admissão, somente a validade das expectativas dos dois verificadores.

## Aplicação
Extraia o pacote de alterações na raiz do projeto, substituindo os dois arquivos `.java`. A partir da pasta `scripts` no PowerShell, execute ` .\recompilar_e_retestar_qkd.bat full `, que recompila antes de testar. Se persistirem outras falhas, envie o `resumo.csv` e **todos** os `.log` do diretório mais recente em `simulations\verification-reports`.

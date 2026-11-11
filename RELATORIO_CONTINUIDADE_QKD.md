# SNetS-SDM-QKD — controle de continuidade espectral de QCh e PICh

**Data:** 09/11/2026  
**Base:** distribuição v1 com refatoração de camada física, integração seletiva v2 e correções de regressão entregues anteriormente.

## Requisito

Aplicar a contiguidade intralink e a continuidade interlink a QChs e PIChs, com controles independentes que autorizam conversão de banda **somente nas fronteiras dos enlaces originais da rede SNetS**. Os nós confiáveis artificiais QTND dentro do mesmo enlace físico **não realizam conversão**, mesmo com o controle interlink desativado.

## Parâmetros

Objeto `qkd` do arquivo `network`:

- `qchSlotContinuityAtNetworkNodes`: boolean, padrão `true`.
- `pichSlotContinuityAtNetworkNodes`: boolean, padrão `true`.

Ambos padrão `true` tanto no parser Java como no template do Launcher, para não alterar o comportamento de cenários existentes por ausência do campo. `false` autoriza outro bloco de slots **contíguos** após atravessar um nó SNetS.

**Limitação deliberada:** contiguidade **dentro do enlace** não é opcional nesta versão; intervalos fragmentados não são compatíveis com a representação espectral atual das classes `Circuit`/`QKDChannelAllocation` e não foram introduzidos.

## Modificações

| Arquivo | Alteração |
| --- | --- |
| `simulationControl/parsers/NetworkConfig.java` | Leitura/configuração dos dois parâmetros. |
| `qkd/network/QKDNetworkParameters.java` | Disponibilização das políticas ao motor QKD. |
| `qkd/capacity/QKDPichPool.java` | Pin de núcleo e bloco no mesmo enlace; continuidade dos slots ao longo da rota por padrão, sem impor continuidade de núcleo nos nós SNetS; conversão autorizada apenas no limite SNetS; reserva compartilhada dentro da requisição para spans longitudinais disjuntos; rollback. |
| `qkd/capacity/QKDSharedResourceManager.java` | QChs contínuos para `route`, `link` e `span`; possibilidade de conversão somente ao atravessar nó físico, sem trocar bloco nos nós QTND. |
| `qkd/verification/QKDSlotContinuityVerification.java` | Novo teste de admissão, rejeição, conversão em nó SNetS, continuidade QTND, liberação e rollback. |
| `qkd/verification/QKDPichAdmissionSafetyVerification.java` | Alinhado à reserva longitudinal intrarrequisicao sem remover a independência óptica dos PIChs. |
| `launcher/web/js/schema.js` e `launcher/templates/network` | Campos no formulário e padrões. |
| `launcher/src/launcher/LauncherServer.java` | Versão 1.23.3. |
| `launcher/snets-launcher.jar` | Interface recompilada. |
| `scripts/verificar_continuidade_espectral_qkd.bat` | Recompila JAR do simulador e executa as verificações. |
| `docs/QKD_CONTINUIDADE_CONTIGUIDADE_ESPECTRAL.md` | Regras, limites e instruções. |

## Testes efetivamente executados

Compilação seletiva Java com alvo Java 8 e dependências binárias do SNetS. Os testes executaram com código de saída zero:

1. `QKDSlotContinuityVerification` — **PASS**.
2. `QKDPichAdmissionSafetyVerification` — **PASS**.
3. `QKDPichIntegratedAdmissionV15Verification` — **PASS**.
4. `QKDPichReceiverPowerVerification` — **PASS**.
5. `QKDCoexistenceCorrectionsVerification` — **PASS**.
6. `QKDSpectrumAllocationVerification` — **PASS**.
7. `QKDRoutePhysicalEvaluationVerification` — **PASS**.
8. `QKDLobatoPerLinkPichVerification` — **PASS**.
9. `QKDSameBandGuardVerification` — **PASS**.
10. `QKDAbneCompatibilityVerification` — **PASS**.
11. `QKDMaxSkrRouteVerification` — **PASS**.

O Launcher 1.23.3 foi recompilado com sucesso, o `schema.js` passou por `node --check` e o JAR contém os parâmetros novos.

**Não realizados:** execução do BAT no Windows do usuário; compilação integral do projeto com `SNetS-SDM-QKD.jar` do usuário; campanha completa das 67 verificações; simulações de tráfego dinâmico comparando probabilidade de bloqueio. Para estas últimas, o novo comportamento pode elevar bloqueio em relação à alocação anterior por ser mais restritivo.

## Observação de modelagem

Um PICh é uma fonte óptica por QKDSpan. Reusar uma reserva espectral em **trechos longitudinais disjuntos do mesmo enlace** não significa compartilhar a mesma fonte PICh nem permitir sobreposição com DCh no mesmo núcleo/slots.

O motor de recursos QKD (`QKDSharedResourceManager`) é o caminho ativo de provisionamento; o alocador de compatibilidade legado (`QKDSpectrumAllocator`) não foi modificado. Essa distinção deve ser mantida em relatórios de experimentos.

## Aplicação

Extrair o ZIP de alterações **na raiz** do projeto SNetS. Substituir o JAR do Launcher e executar, no PowerShell a partir de `scripts`:

```powershell
.\verificar_continuidade_espectral_qkd.bat
```

A execução deve recompilar o simulador e produzir relatórios sob `simulations/verification-reports`. A aprovação de uma compilação seletiva em outro ambiente não substitui essas verificações na instalação de destino.

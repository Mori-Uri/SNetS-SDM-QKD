# Correção de compilação: referências EGN antigas (10/11/2026)

## Diagnóstico reproduzido

Ao extrair arquivos de um ZIP incremental sobre uma árvore anterior, os fontes antigos
`network/Poggiolini2017EgnReference.java`, `verification/HabibiEgnComparisonVerification.java`
e `network/EgnFullReference.java` podem permanecer no diretório `src/main/java`.
O compilador `javac` compila TODOS os `.java` que permanecem nessa árvore, não apenas
os que vieram do último ZIP.

Esses arquivos EGN antigos utilizam `PhysicalLayer.HabibiEgnDecision`, ausente depois
da remoção das integrais EGN; chamam também `PhysicalLayer.legacySci(...)` com
`EgnFullReference.Fiber`, que já não é o tipo aceito pelo método atual.

A implementação **ativa** é:

- `network/Poggiolini2017GnIntegral` — integral GN, sem correção integral EGN de Carena;
- `network/PhysicalLayer.HabibiGnDecision` — resultado do fallback GN;
- `network/PhysicalLayer.legacySci(Poggiolini2017GnIntegral.Fiber, Poggiolini2017GnIntegral.Channel, double)` — aproximação Habibi legada, **válida** e compilada com a nova API GN;
- `verification/HabibiGnComparisonVerification` — teste de comparação Habibi × GN integral.

**Não faça uma substituição textual isolada** `HabibiEgnDecision` -> `HabibiGnDecision`
nos arquivos EGN antigos. As estruturas `EgnFullReference.Fiber`, `.Channel` e `.Components`
também foram eliminadas e sua reinserção contrariaria a remoção de EGN solicitada.

## Correções entregues

1. `scripts/remover_egn_full_reference.bat` e `.sh` retiram os arquivos fonte antigos,
   guardando cópias fora de `src`, em `simulations/backup_fontes_egn_removidas`, e removem
   `.class` obsoletos de `target/classes`, `bin` e `build/classes`.
2. `launcher.JarBuilder` rejeita fontes antigas ANTES de compilar e remove entradas
   `.class` EGN obsoletas e antigas `PhysicalLayer$HabibiEgnDecision` durante o empacotamento.
   A implementação ativa `PhysicalLayer$HabibiGnDecision` é preservada.
3. `scripts/auditar_simbolos_obsoletos.py` detecta referências executáveis a tipos
   removidos, verifica assinaturas GN e aceita `--jar` para auditar o JAR gerado.
4. `scripts/recompilar_e_retestar_qkd.bat` executa a limpeza e a auditoria antes
   de recompilar.
5. `scripts/corrigir_compilacao_egn.bat` oferece uma verificação curta sem executar
   toda a campanha QKD: limpa, recompila, executa Habibi/GN e os três modos NLI.
6. `launcher/tests/JarBuilderRemovedClassesTest.java` garante a limpeza de classes
   antigas, inclusive classes internas, e a preservação das novas.

## Aplicação no PowerShell (a partir da pasta scripts)

```powershell
.\corrigir_compilacao_egn.bat
```

Ou, para executar todas as verificações previamente configuradas:

```powershell
.\recompilar_e_retestar_qkd.bat full
```

A correção usa `launcher/snets-launcher.jar` atualizado; a versão do Launcher é **1.24.8**.
Se estiver usando a compilação automática do Eclipse, execute **Project > Clean** depois
que o script remover os `.class` antigos.

## Verificações locais

- Auditada a árvore atual de fontes Java + JS: nenhum uso executável de tipos EGN removidos.
- Teste de regressão do JarBuilder: entradas `.class` antigas eliminadas, GN preservado.
- Reprodução controlada com os três `.java` antigos: a auditoria os detecta; a
  limpeza os retira, preserva backup, e a auditoria passa.
- `verification.HabibiGnComparisonVerification`: PASS em 15 linhas A/B.
- `qkd.verification.QKDNliIntegrationModesVerification`: PASS (3 modos).
- Launcher compilado com `javac --release 8`, JAR executável validado.

A compilação limpa de **todo o simulador** contra o JAR base da instalação Windows
continua dependente da execução no ambiente do usuário; não a consideramos realizada.

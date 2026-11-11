# SNetS v1 — configuração NLI atual e correção do build

Data: 10/11/2026. Base: projeto v1 GN/EGN corrigido, Launcher 1.24.8. Nova versão do Launcher: **1.24.9**.

## 1. Configurações removidas

Foram excluídas as quatro propriedades JSON anteriores `johannissonSciUseIntegral`, `johannissonXciUseIntegral`, `habibiEgnFallbackEnabled` e `habibiEgnNarrowbandGHz`, junto do alias antigo `johannissonUseIntegral`. Foram retiradas as propriedades em `PhysicalLayerConfig`, no uso da `PhysicalLayer`, no schema e no template do Launcher. Os verificadores que antes usavam as flags individuais foram migrados para o controle conjunto dos modos. Configurações antigas que ainda contiverem tais nomes são **rejeitadas explicitamente** pelo leitor `SimulationFileManager` e sinalizadas pelo Launcher; não há migração silenciosa de experimentos anteriores.

O conjunto atual para `physicalLayer` é:

```json
{
  "physicalLayerModel": 1,
  "johannissonNliMode": "adaptive",
  "habibiNliMode": "adaptive",
  "nliNarrowbandGHz": 25.0
}
```

`physicalLayerModel=0` seleciona Habibi; `=1` Johannisson. Para cada modelo, o modo aceita `approximate` (analítico, sem integrais), `integral` (integrais no domínio da implementação) e `adaptive` (integral quando largura física <= limite ou aproximação não física). A ausência de um dos dois campos de modo usa `adaptive` por padrão; não existe modo `legacy` configurável. `nliNarrowbandGHz` é a largura **física do canal**, não a largura total de slots reservados. Os controles de ordem da quadratura GN existentes permanecem para compatibilidade do integrador.

**Atenção científica:** um arquivo antigo que antes dependia de flags com valores booleanos precisa ser convertido conscientemente antes de uma nova campanha. `approximate` e `integral` não devem ser considerados equivalentes nos canais estreitos. Johannisson SCI/XCI possui integrais específicas; o modo integral de Habibi usa GN de Poggiolini como substituição parcial de SCI/XCI elegíveis, não EGN integral completa. O motor clássico, a avaliação longitudinal, a BER e o QKD mantêm os modelos da v1.

## 2. Falha de compilação `TrackedRequestList`

A fonte `network/TrackedRequestList.java` citada no log é da versão experimental, **não consta do projeto v1 atual**. Ela invoca `Circuit.physicalChange()` e `RequestForConnection.markInTrackedList()`, que não estão presentes nas implementações v1. Essa é uma integração incompleta de otimizações do projeto experimental; adicionar métodos artificiais sem invalidar corretamente os caches seria perigoso.

A correção instala `scripts/limpar_tracked_experimental.py`, integrado em `corrigir_compilacao_egn.bat` e `recompilar_e_retestar_qkd.bat`. O script **só remove** a fonte experimental quando nenhum outro arquivo Java depende do tipo, gravando backup em `simulations/backup_fontes_experimentais_incompletas/network/TrackedRequestList.java`. Se detectar consumidores, interrompe e informa os arquivos para uma integração segura. O JarBuilder deixa de copiar o bytecode residual da classe experimental a partir de um JAR base.

## 3. Validação realizada neste ambiente

- Compilação dos **438 arquivos Java selecionados**, excluindo `simulationControl.Main` como no JarBuilder, com `javac --release 8`, encoding ISO-8859-1, usando classes/stubs pré-compilados de dependências externas: **PASS**. Não constitui build limpo com todas as dependências reais do Windows.
- `QKDNliIntegrationModesVerification`, `QKDNarrowbandHabibiVerification`, `QKDJohannissonSciModeVerification`, `QKDJohannissonModeVerification`, `QKDGnScientificCorrectionsVerification`, `QKDLongitudinalGNVerification`, `QKDThesisSNetSPhysicalLayerVerification`, `QKDv26AseFrequencyParityVerification`, `QKDClassicalLongitudinalQoTVerification`: **9/9 PASS**.
- Referência da tese, um span: **28,086036330 dB**; paridade ASE: **48 casos, maxRel=5.49e-16** neste teste.
- JavaScript `node --check` e contratos de esquema: **PASS**. Compilação do Launcher 1.24.9 com `--release 8`: **PASS**. `JarBuilderRemovedClassesTest` com `TrackedRequestList.class`: **PASS**.
- Teste de limpeza experimental: **PASS** quando sem consumidores; recusa segura quando a classe é referenciada: **PASS**.
- Compilação integral e regressão QKD com JAR base real da instalação Windows: **pendente**.

## 4. Aplicação no Windows 11

Extraia o ZIP incremental na raiz da v1, substituindo os arquivos; feche o Launcher antes de substituir o `.jar`. Abra o PowerShell em `scripts`:

```powershell
python .\verificar_modos_nli_atuais.py
.\corrigir_compilacao_egn.bat
.\recompilar_e_retestar_qkd.bat full
```

O primeiro comando audita os arquivos de configuração e sinaliza legados não migrados. O segundo retira a classe experimental incompleta, se não tiver consumidores, recompila o JAR e executa o verificador GN. O terceiro recompila e executa os testes críticos QKD. Se persistir erro, envie o primeiro bloco de mensagens Java e os logs de `simulations/verification-reports`.

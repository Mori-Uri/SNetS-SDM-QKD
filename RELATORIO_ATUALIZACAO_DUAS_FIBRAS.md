# SNetS-SDM-QKD v1 — consolidação de duas fibras direcionais e validação reproduzível
**Atualização do Launcher:** 1.25.0  
**Base:** última entrega v1 com modos NLI atuais e correção de `TrackedRequestList` (Launcher 1.24.9)  
**Escopo:** eliminar mecanismos incompletos de propagação contrária na *mesma fibra*, sem interferir em enlaces A→B e B→A com fibras separadas.

## 1. Arquitetura mantida

Cada enlace orientado possui sua **fibra física independente**, com núcleos, grade, fontes, reserva espectral e potência próprias. O par A→B e B→A não compartilha o mesmo meio óptico. Todos os DCh/PICh/QCh de um enlace são **copropagantes**. Não há Raman/IC-XT de contrafluxo entre fibras distintas.

**Atenção:** a propriedade histórica `network.qkd.undirectedChannels` diz respeito à eventual *partilha lógica de recursos/chaves QKD*, e **não** à propagação contrapropagante nem à presença de uma única fibra bidirecional. Seu comportamento foi preservado para compatibilidade científica. Cenários que exigem QCh e pools completamente separados por direção devem usar `undirectedChannels:false`.

## 2. Código alterado

| Área | Alteração |
|---|---|
| `qkd/multiband/MultiBandSettings` | Excluído estado `bidirectionalFiber`, construtor associado e acesso à propriedade. |
| `qkd/multiband/ClassicalChannel` | Excluídas enumeração `Direction`, opção `COUNTER_PROPAGATING` e assinaturas correspondentes. Canais são sempre copropagantes na sua fibra dirigida. |
| `qkd/multiband/SpontaneousRamanModel` | Excluídas funções não utilizadas de propagação Raman reversa (`backwardLengthKm`, `intercoreBackwardLengthKm`). Preservadas equações forward e IC-SpRS forward. |
| `qkd/multiband/MultiBandQkdLinkModel` | Cálculo limitado a fontes da própria fibra, sem seleção de sentidos. |
| `qkd/multiband/LinkCalculator` | Removida opção `sentido`; uso antigo é rejeitado explicitamente. |
| `qkd/physical/QKDPhysicalLayerConfig` | Removida configuração `multiband.bidirectionalFiber`. |
| `simulationControl/SimulationFileManager` | Rejeita chave retirada `multiband.bidirectionalFiber`, mesmo se `false`, com instrução de migração. |
| Launcher 1.25.0 | Removido controle e default da opção retirada; validação do JSON com erro explícito; sem alteração em outros campos. |
| `launcher.JarBuilder` | Filtragem de bytecode interno `ClassicalChannel$Direction.class` eventualmente existente no JAR base. |
| Verificações | Teste de independência A→B/B→A (`QKDDirectedFibresVerification`), atualização de verificadores multibanda e teste do empacotamento. |
| Scripts | Auditoria estática, limpeza de bytecode, verificação de duas fibras e comparação de cenários salvos. |

Apenas a implementação incompleta de **fibra única com contrafluxo** foi retirada. Não se eliminou a possibilidade de **dois enlaces dirigidos entre o mesmo par de nós**, nem a possibilidade de canais clássicos e quânticos copropagarem em uma fibra MCF.

## 3. Migração de JSON

Remover inteiramente a chave `bidirectionalFiber` de `qkdPhysicalLayer.multiband`:

```diff
 {
   "multiband": {
     "enabled": true,
     "architecture": "MB-SDM",
-    "bidirectionalFiber": false,
     "ramanModel": "fisico"
   }
 }
```

Não há nova chave substituta. Os dois sentidos são representados pelos **dois enlaces direcionais** presentes na topologia. Scripts de comparação A/B removem `bidirectionalFiber:false` somente das **cópias de trabalho**; o arquivo original é intocado.

## 4. Testes executados neste ambiente

| Teste | Resultado | Interpretação |
|---|---|---|
| Compilação seletiva Java `--release 8` | PASS | Fontes de física, parser e verificação. |
| Compilação ampliada de **438 fontes Java** (sem `simulationControl.Main`/`KBP_Tuning`) | PASS | Usa dependências auxiliares; não é substituta de compilação integral real. |
| `QKDDirectedFibresVerification` | PASS (MB e MBSDM) | Adicionar cargas em B→A não altera Raman, SKR ou QBER em A→B; ocupação independente. |
| `MultiBandPhysicsVerification` | PASS | Regressoes do modelo de propagação forward. |
| `MultiBandIwcitVerification` | PASS | Checagens numéricas multibanda da literatura. |
| Paridade numérica dos diagnósticos `MultiBandPhysicsVerification` com bytecode anterior | **33/33 linhas idênticas** | Na amostra de referência testada, nenhuma mudança numérica introduzida. |
| Testes JS de esquema, QoT, OSNR e fibras do Launcher | PASS | UI sem `bidirectionalFiber` e com rejeição explícita da chave antiga. |
| Launcher compilado para Java 8 e JAR inicializado com `--help` | PASS | Versão 1.25.0. |
| `JarBuilderRemovedClassesTest` | PASS | Bytecode obsoleto removido do novo JAR do simulador. |
| Auditoria estática de fibras e de cenário salvo | PASS | Nenhuma API de contrapropagação ativa encontrada. |
| Comparação CSV entre cópias idênticas de um resultado salvo | PASS | Mesmo JSON e 2 CSVs sem divergência. |
| Teste de detecção de divergência de 1 célula do CSV | PASS | Divergência detectada com exit code 1. |

**Ainda não executado aqui:** `MultiBandNetworkVerification` que depende do Gson real, regressões extensivas do simulador Windows e A/B com duas versões reais do JAR e todos os cenários. Os testes de física utilizam classes auxiliares e compilações seletivas na plataforma da execução; a reconstrução real do Windows é a aceitação final.

## 5. Roteiro de verificação no Windows

1. Faça backup de `simulations/SNetS-SDM-QKD.jar`, do último `simulations/SNetS-SDM-QKD-exp.jar` validado e dos cenários/resultados. Dê ao JAR experimental anterior o nome `SNetS-SDM-QKD-antes.jar`.
2. Aplique as alterações em sua raiz de projeto; substitua o Launcher `launcher/snets-launcher.jar` pela versão 1.25.0.
3. Remova `multiband.bidirectionalFiber` de cenários legados e mantenha a mesma topologia A→B/B→A.
4. Execute no PowerShell:

```powershell
cd C:\Users\alexa\eclipse-workspace\SNetS-SDM-qkd-v1\scripts
.\recompilar_e_retestar_qkd.bat full
.\verificar_duas_fibras.bat
python .\auditar_fibras_direcionais.py .. "..\simulations\QKD_Referencias\Dibaj2024_MF_Inspirado"
```

**Observação:** `full` chama os verificadores do script de recompilação — não corresponde à execução de **todos** os verificadores científicos disponíveis. Os relatórios são mantidos em `simulations/verification-reports`.

### 5.1 Comparação direta de dois cenários já salvos

```powershell
python .\comparar_cenarios_salvos.py comparar `
  --referencia "C:\Resultados\v1_antes\MEU_CENARIO" `
  --atual "C:\Resultados\v1_depois\MEU_CENARIO" `
  --relatorio "C:\Resultados\comparacao.txt"
```

Compara entradas JSON equivalentes e todos os CSVs encontrados, por célula, com `--rtol 1e-10` e **`--atol 0`** por padrão. `atol 0` evita mascarar diferenças de ruído óptico na faixa 10⁻¹⁴ W. Exit code `0`: paridade. `1`: diferenças. `2`: erro de execução ou entradas inválidas.

### 5.2 Rodar cenários A/B com o mesmo conjunto de configurações

```powershell
python .\comparar_cenarios_salvos.py executar-ab `
  --cenario "..\simulations\QKD_Referencias\Dibaj2024_MF_Inspirado" `
  --jar-antigo "..\simulations\SNetS-SDM-QKD-antes.jar" `
  --jar-novo "..\simulations\SNetS-SDM-QKD-exp.jar" `
  --saida "..\simulations\comparacao_duas_fibras\Dibaj2024_MF"
```

- Exige **dois JARs distintos** e diretório de saída inexistente.
- Cria duas cópias independentes de entradas: `antigo/` e `novo/`. O cenário original não é alterado.
- Executa `java -jar <versão> <pasta do cenário>` para cada cópia, coleta logs e compara CSVs/resultados.
- Use o último **JAR QKD válido anterior** como baseline, não um JAR clássico sem as extensões QKD.
- Mantenha constantes `threads=1`, seeds, número de replicações e requisições, modo NLI, amplificação, topologia, métricas e critérios QoT.
- Cenários relevantes no pacote: `Dibaj2024_MF_Inspirado`, `Moghaddam2021_MCF7_Inspirado`, `MCF7_Qualificacao_SDM`.
- Esta comparação supõe equivalência determinística das saídas; caso a serialização de configuração mude entre JARs, o script sinaliza diferença de configuração. Inspecione esse caso antes de concluir regressão científica.

### 5.3 O que comparar

- `*_QKDStatistics.csv`: quantidade de solicitações, aceitação/rejeição, sessões e métricas QKD.
- `*_BlockingProbability.csv` e detalhes: bloqueio por ponto de carga e replicação.
- Métricas de QoT: OSNR e BER; de QKD: SKR, QBER e potenciais interferências, quando presentes nas saídas.
- Estado de circuitos QCh/PICh, potência e ocupação espectral por **fibra orientada**.
- Tempo de CPU/RAM só como informação de desempenho; não deve ser usado como equivalência bitwise.

**Critério esperado:** com a mesma topologia, mesmo modelo e sementes, o pacote novo não deve mudar as saídas de simulações que nunca usaram contrapropagação. A comparação A/B efetiva ainda precisa ser feita no Windows; a paridade local 33/33 não deve ser interpretada como validação global.

## 6. Riscos e limites deliberados

- A opção de **compartilhamento lógico** `undirectedChannels` foi preservada, embora **não** represente uma fibra bidirecional. Se `true`, duas direções podem disputar recursos de QKD **lógicos**, sem compartilhar o meio óptico.
- `MF`, `MB` e `MBSDM` preservam seus próprios modelos de fontes de ruído por enlace; remover a contrafluxo não corrige automaticamente outras hipóteses da camada física (amplificadores, NLI EGN, etc.).
- Não foi implementada uma nova física bidirecional, nem mecanismos de Raman contrário.
- O JAR base não foi sobrescrito; o Launcher e o script de recompilação constroem o JAR experimental novo.

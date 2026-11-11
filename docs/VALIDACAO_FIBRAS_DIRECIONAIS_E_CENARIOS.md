# SNetS-SDM-QKD: duas fibras independentes e validação com cenários salvos

## Modelo físico fixado

- **Enlace A → B**: fibra física e conjunto de núcleos próprios. DCh/PICh/QCh copropagam; ruídos Raman e NLI são computados a partir de fontes dessa fibra.
- **Enlace B → A**: outra fibra física, com ocupação e fontes próprias. Nenhum canal da fibra B → A é um bombeio Raman reverso da fibra A → B.
- `network.links` tem uma entrada para **cada sentido**. Um par bidirecional de nós tem duas entradas, mas **não** uma única fibra bidirecional.
- `network.qkd.undirectedChannels` é **compartilhamento lógico de pools QKD**, não habilitação de contrapropagação. Para comparar recursos físicos QKD estritamente separados por sentido, configure `undirectedChannels:false`. Preservou-se esse mecanismo legado separadamente para não modificar silenciosamente cenários científicos existentes.
- Para o esquema `MF` (dark fiber), a fibra quântica de cada sentido não recebe ruído clássico; a fibra dark QKD pode ser adicional às fibras clássicas de cada direção. `MB` e `MBSDM` mantêm suas semânticas de núcleo/banda **em cada fibra direcional**.
- A chave `multiband.bidirectionalFiber` foi **retirada**, inclusive quando `false`. O leitor de casos e o Launcher rejeitam sua presença, em vez de ignorá-la.
- Foram removidas as fórmulas públicas `SpontaneousRamanModel.backwardLengthKm` / `intercoreBackwardLengthKm`, a enumeração `ClassicalChannel.Direction`, os parâmetros `sentido` da calculadora e o estado `MultiBandSettings.bidirectionalFiber`. Não há mais seleção contrapropagante.

## 1. Recompilação e verificadores Java no Windows 11

No PowerShell, na pasta-raiz:

```powershell
cd .\scripts
.\recompilar_e_retestar_qkd.bat full
.\verificar_duas_fibras.bat
```

A recompilação usa o JAR base preservado em `simulations/SNetS-SDM-QKD.jar`, compila as fontes atuais e grava o novo JAR em `simulations/SNetS-SDM-QKD-exp.jar`, sem sobrescrever o base. `full` ativa as verificações adicionais previstas no script; **não** significa executar automaticamente todos os verificadores da árvore. Os relatórios do JarBuilder ficam em `simulations/verification-reports`.

`verificar_duas_fibras.bat` executa:

1. `auditar_fibras_direcionais.py` — fonte sem tipos/rotinas de contrapropagação.
2. `QKDDirectedFibresVerification` — carregar B→A não altera o Raman de A→B (MB e MBSDM).
3. `MultiBandPhysicsVerification` — expressões copropagantes e SKR.
4. `MultiBandIwcitVerification` — checagem numérica de referência para Raman copropagante e cenários científicos.
5. `MultiBandNetworkVerification` — equivalência do motor e modelos multibanda.
6. `QKDSpanGranularityVerification` — **atenção:** esse verificador existente cobre a opção legada de **compartilhamento lógico** `undirectedChannels=true`, que não é fibra compartilhada. Para independência estrita de QCh por direção, usar `false` em cenários de produção.

Para auditar um cenário específico:

```powershell
python .\auditar_fibras_direcionais.py .. "..\simulations\MEU_CENARIO"
```

Se o cenário contiver `qkdPhysicalLayer.multiband.bidirectionalFiber`, mesmo `false`, remova a propriedade; os outros parâmetros ficam intactos.

## 2. Verificação utilizando cenários salvos

Guarde **duas versões de JARs**: (a) a última que rodava corretamente **antes desta mudança**, por exemplo `SNetS-SDM-QKD-antes.jar`, e (b) o `SNetS-SDM-QKD-exp.jar` recém-compilado. O primeiro **não pode** ser o JAR clássico sem o módulo QKD se o cenário tiver QKD habilitado.

Reutilize um caso já validado contendo `network`, `simulation`, `physicalLayer`, `traffic`, `others` e `qkdPhysicalLayer` (com ou sem extensão `.json`). Exemplos do projeto: `simulations/QKD_Referencias/Dibaj2024_MF_Inspirado`, `Moghaddam2021_MCF7_Inspirado` e `MCF7_Qualificacao_SDM`.

Antes do A/B, congele os seguintes parâmetros: topologia e enlaces direcionais, número de núcleos, slots, `physicalLayer`, modelo físico (Habibi/Johannisson e modo NLI), demanda QKD, `trafficRandomSeed`, `qkdProtectionRandomSeed`, `qkdKeyDemand.randomSeed`, `threads`, `replications`, `requests` e `loadPoints`. Para comparação determinística, use `threads=1` e inicie com poucas requisições; aumente depois para o cenário científico original. Não altere as variáveis entre as duas execuções.

Exemplo de **A/B integral**, que duplica os arquivos de configuração para **duas pastas independentes**, executa as duas versões com o mesmo caso e compara todos os CSVs (o caso original não é modificado):

```powershell
python .\comparar_cenarios_salvos.py executar-ab `
  --cenario "..\simulations\QKD_Referencias\Dibaj2024_MF_Inspirado" `
  --jar-antigo "..\simulations\SNetS-SDM-QKD-antes.jar" `
  --jar-novo "..\simulations\SNetS-SDM-QKD-exp.jar" `
  --saida "..\simulations\comparacao_duas_fibras\Dibaj2024_MF"
```

**Observação:** a pasta de saída **não pode existir**; é uma proteção contra perda de resultados. O script cria os subdiretórios `antigo/<nome-do-caso>/` e `novo/<nome-do-caso>/`, produz `antigo.log`, `novo.log` e `comparacao.txt` e termina com código `0` somente se as entradas e os CSVs coincidirem dentro das tolerâncias (`--rtol` padrão 1e-10, `--atol` padrão 0; evita mascarar potência Raman da ordem de 10⁻¹⁴ W). A opção descontinuada `bidirectionalFiber:false` é removida apenas das cópias de trabalho, por ser inócua no motor anterior; `true` resulta em erro.

Para comparar **dois conjuntos já salvos** (sem executar Java):

```powershell
python .\comparar_cenarios_salvos.py comparar `
  --referencia "C:\Resultados\v1_antes\MEU_CENARIO" `
  --atual "C:\Resultados\v1_depois\MEU_CENARIO" `
  --relatorio "C:\Resultados\comparacao.txt"
```

Esta comparação exige os arquivos JSON de entrada e as métricas CSV na pasta de cada caso. Não é correto comparar apenas CSVs sem garantir igualdade de parâmetros, sementes e número de requisições.

## 3. Indicadores e critérios de aceitação

Priorize `*_QKDStatistics.csv`, `*_BlockingProbability.csv`, além das saídas físicas e de utilização efetivamente presentes no caso. Comparar por ponto de carga, replicação e núcleo/enlace, não somente pela média final.

| Indicador | Critério para esta remoção, se entradas e execução forem determinísticas |
|---|---|
| Contagem de conexões/proteção QKD, QChs e PIChs ativos | **Igual** ao último baseline válido |
| SKR, QBER, ruído Raman, XT e NLI no cenário co-propagante | **Igual** dentro da tolerância numérica |
| OSNR e BER dos DChs/PIChs | **Igual** dentro da tolerância numérica |
| Bloqueios por QoT, QKD e espectro | **Igual** por ponto de carga e replicação |
| Consumo de recursos A→B | Não deve mudar ao adicionar tráfego na fibra independente B→A, salvo concorrência **lógica** explicitamente configurada entre sessões QKD |
| Uso de RAM e tempo total | Apenas diagnóstico: podem variar por JVM/Windows e não são critério bitwise |

Se o A/B divergir: primeiro confirme que o JAR antigo realmente corresponde à versão imediatamente anterior, que os casos não foram editados pelo Launcher, que `threads=1`, que as sementes e `undirectedChannels` são iguais. Depois compare os logs e CSVs; divergência de SKR/Raman num par de fibras independentes constitui regressão a investigar, não mudança esperada.

## 4. Testes científicos complementares

Para o cenário com os modelos NLI atualizados, realize a matriz **Johannisson × Habibi** e modos **`approximate` × `integral` × `adaptive`**, repetindo para MB/MBSDM/MF e `undirectedChannels=false` (comparações que exigem recursos QKD físicos separados). Depois compare bloqueios, recursos, OSNR/BER, SKR/QBER e duração. Esses modos podem produzir **resultados diferentes entre si**, pois mudam a aproximação matemática; o requisito de igualdade A/B vale **dentro de cada configuração idêntica** antes/depois da remoção da contrapropagação.

**Limitação desta entrega:** os testes realizados aqui são locais e parciais, com bibliotecas auxiliares de compilação. Não foi possível executar o JAR QKD completo nem o A/B das campanhas no Windows do usuário, portanto não há alegação de equivalência global já demonstrada.

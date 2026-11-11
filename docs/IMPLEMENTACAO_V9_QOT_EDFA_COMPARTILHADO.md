# SNetS-SDM-QKD v9 — ponte de estado EDFA para QoT clássico

## Escopo efetivamente implementado

1. `DirectedAmplifierSnapshotFactory`: fonte única de montagem da carga óptica longitudinal por enlace dirigido/núcleo. Inclui DChs e PIChs físicos, sem incluir enlaces reversos. Considera os limites longitudinais de cada PICh e deduplica a mesma instância de circuito. Não altera o estado dos recursos da rede.
2. `QKDPhysicalLayer`: substitui a montagem duplicada da carga QKD pela fábrica compartilhada. Usa o mesmo `ClassicalPowerEvolution` e os mesmos objetos de estado `AmplifierOperatingPoint`.
3. `PhysicalLayer`: habilita, **somente mediante configuração**, a computação da ASE dos amplificadores de linha a partir dos pontos operacionais por amplificador, incluindo atenuação entre equipamentos e ganhos posteriores. O booster e o pré-amplificador permanecem no modelo tradicional nesta versão. O cálculo clássico GN-NLI também permanece tradicional.
4. `QKDProvisioningService`: ativa a ponte no QoT clássico em cenários multibanda com configuração longitudinal (Raman ou NLI), de acordo com os parâmetros QKD já carregados. Uma execução puramente clássica mantém o comportamento legado.
5. `QKDSharedAmplifierGainVerification`: testes numéricos de compressão de ganho, operação ideal, ASE, determinismo e inserção tardia de fontes.

## Restrições e hipóteses

- A mesma lei de ganho, fontes e localização física agora podem ser empregadas pelo QoT clássico e pela QKD. **Não se trata ainda de um cache único persistente de snapshots entre todos os chamados**: cada avaliação reconstrói seu snapshot para incluir os candidatos locais sem mutar o grafo.
- O QoT clássico agora só usa os novos estados no termo de ASE dos **amplificadores de linha**. A potência do sinal, a NLI e os estados do booster/pré-amplificador ainda não compartilham integralmente o ponto operacional; não interpretar como unificação física completa.
- A atenuação clássica é representada por um coeficiente efetivo por enlace. As diferenças entre canais C/L já são consideradas no perfil espectral do EDFA quando configurado, mas a atenuação dependente da frequência em cada canal precisa ser integrada depois.
- A geração de ASE pode ser propagada em frequência clássica e não corresponde automaticamente a ASE no QCh. Um modelo de leakage espectral calibrado é necessário para converter essa ASE em contagens quânticas.
- Em nenhum momento se usa o enlace reverso como fonte interferente. O modelo continua exclusivamente copropagante por fibra dirigida.
- A política de QoTO deve continuar sendo acionada normalmente pelo controle de admissão ao modificar a ocupação dos PIChs.

## Verificações executadas neste ambiente

- Compilação Java 8 alvo das classes puras `AmplifierGainLaw`, `AmplifierOperatingPoint`, `MultiBandEdFA`, `ClassicalPowerEvolution` — aprovada.
- `QKDSharedAmplifierGainVerification` — PASS.
- Compilação da nova fábrica contra classes mínimas de apoio com assinaturas correspondentes — aprovada; `FactorySmoke` — PASS (deduplicação).
- Tentativa de compilação integral de todas as fontes — **não concluída**: falta de bibliotecas externas (Gson, entre outras) no classpath do ambiente. Não foi possível executar Maven nem regressão global.

## Próximos passos

1. Integrar o ganho real por amplificador também à propagação do sinal DCh e ao GN-NLI clássico; isso exige atualizar o cálculo completo do SNR, não apenas a ASE.
2. Unificar explicitamente o posicionamento físico do booster, do amplificador de linha e do pré-amplificador, em todas as rotinas de QoT.
3. Calibrar EDFA multibanda com curvas medidas e comparar as grandezas ganho, NF, ASE, OSNR, QBER, SKR, Raman e NLI.
4. Executar `mvn clean test` em ambiente com as dependências do `pom.xml` e ensaios MF/MB/MB-SDM, incluindo QoTN/QoTO.

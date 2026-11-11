# Auditoria do atendimento de requisições QKD e reorganização de código

**Base analisada:** `SNetS-SDM-qkd-v1(1).zip`, fornecido pelo usuário.  
**Literatura confrontada:** 19 PDFs do arquivo `artigos(2).zip` previamente fornecido.  
**Escopo desta entrega:** avaliação técnica e formatação **exclusivamente de whitespace** dos arquivos Java. **Não foram realizadas mudanças funcionais.**

## 1. Parecer executivo

**O fluxo principal de atendimento de requisições protegidas por QKD é coerente com a arquitetura QCh + canal clássico acompanhante (PICh/CCh) + DCh, com nós confiáveis, SKR mínima e proteção de sessões em serviço descrita em Moghaddam et al. (2021), Dibaj et al. (2024) e no tutorial de coexistência.** O modelo não é, entretanto, uma reprodução integral ou numericamente validada de todos os 19 trabalhos: reúne recursos de diversos artigos e mantém hipóteses próprias. Em especial, a QKD de produção é **BB84 com estados decoy e trusted nodes**, não TF-QKD nem repetidores quânticos.

### Fluxo auditado no código

1. `qkd.traffic.QKDRequestGenerator.scheduleNextRequest`: eventos Poisson/exponenciais, seleção probabilística da classe protegida, `QKDKeyDemandPolicy` e fluxos pseudoaleatórios separados para tráfego/proteção quando usado o construtor de produção.
2. `qkd.control.QKDControlPlane.handleRequisitionQkd` e `isAdmissibleGeneralCrosstalk`: inicia o procedimento SNetS clássico, protege sessões QKD e PIChs existentes, calcula admissão QKD **antes** de confirmar o novo DCh.
3. `qkd.control.QKDProvisioningService.provision`: recebe a rota clássica e testa rotas QKD alternativas; atualmente exige **exatamente um circuito clássico** em cada requisição QKD protegida.
4. `QKDProvisioningService.provisionOnRoute`: `QKDRoutePlanner` divide enlaces físicos em QKDSpans; `QKDPichPool.acquire` registra PIChs físicos por QKDSpan; `QKDSharedResourceManager.allocate` escolhe/compartilha QChs, espectro e Q-TRx; `QKDPhysicalLayer` avalia interferência e SKR.
5. `qkd.physical.BB84DecoyModel.evaluateWithOpticalLoss`: calcula QBER e limite inferior de SKR com atenuação, probabilidade de fundo e parâmetros do detector. Em `QKDSharedResourceManager`, a capacidade efetiva é limitada pela **menor SKR** dos spans pertinentes.
6. `QKDControlPlane.allocateCircuit` confirma o DCh e registra a sessão QKD; se falhar, desfaz reservas. Na saída, `finalizeConnection` libera os recursos e `refreshActiveQkdPhysicalState` atualiza condições físicas.

### Elementos implementados e respaldados

| Capacidade | Implementação | Correspondência/ressalva |
|---|---|---|
| Tráfego dinâmico e demanda de chaves | `QKDRequestGenerator`, `QKDKeyDemandPolicy` | Coerente com estudos de alocação dinâmica; demanda protegida vinculada a DCh. |
| Trusted nodes e QTND | `QKDTopologyPlanner`, `QKDRoutePlanner`, `QKDSpan` | Coerente com Dibaj (2024); QKDSpan não atravessa um nó óptico SNetS. |
| BB84 com estados decoy | `BB84DecoyModel` | Modelo de taxa assintótica consistente com referências; não cobre outras famílias de protocolo. |
| QCh e Q-TRx limitados | `QKDSharedResourceManager` | Recursos discretos, compartilhamento de capacidade, TDM opcional, modos route/link/span. |
| PICh físico | `QKDPichPool`, `QKDPichCircuit` | Um PICh dedicado por QKDSpan quando `pichSlots > 0`; contribui como fonte óptica. |
| Proteção QKD de conexões existentes | `QKDExistingSessionImpactEvaluator` e `QKDControlPlane` | Avaliação de interferência/viabilidade antes do commit e atualização após mudança real. |
| Raman, NLI, XT e vazamento de ASE | `QKDPhysicalLayer`, `qkd.multiband.*` | Modelos opcionais dependentes da arquitetura/flags e da potência longitudinal. |
| Compatibilidade MF / MB / MB-SDM | `MultiBandSettings`, `QKDPhysicalLayer` | Existe no código; validação numérica artigo a artigo ainda é necessária. |
| Modulação/QoT PICh como canal clássico | `QKDPichPool`, `PhysicalLayer.evaluatePich` | Emprega critérios e estado físico de sinais clássicos. |
| Métricas de bloqueio e ocupação | `BlockingDetailRecorder`, `QKDControlPlane` | Há causas e contadores por par e por período após warm-up. |

## 2. Matriz de aderência aos 19 trabalhos do ZIP

Legenda: **Aderente** = mecanismo principal reconhecível no código; **Parcial** = apenas parte dos efeitos/algoritmos; **Não reproduzido** = artigo aborda outra tecnologia/método; **Conceitual** = serve como fundamentação, não reprodução dos experimentos.

| # | Artigo (tema) | Avaliação | Fundamentação técnica |
|---:|---|---|---|
| 01 | 2011, saturação de EDFAs | **Parcial** | Há ganho dependente da carga, mas `AmplifierGainLaw` usa lei racional, não a equação implícita do artigo. |
| 02 | 2020, routing/wavelength SDM-QKD | **Parcial** | Alocação por rota, núcleo e frequência existe; heurística e topologia específicas do artigo não são reproduzidas literalmente. |
| 03 | Moghaddam et al. (2021), QCh/CCh/DCh | **Aderente parcialmente** | Coexistência, XT, proteção e demanda SKR; FWM de tons discretos e regras do artigo não são integralmente usadas na admissão. |
| 04 | Alia et al. (2022), DV-QKD em hollow-core | **Conceitual** | QKD discreta e ruído podem ser comparados, mas o cenário experimental com fibra de núcleo oco não é um modo de reprodução demonstrado. |
| 05 | Otimização C+L (2023) | **Parcial** | Há multibanda e parâmetros ópticos dependentes da frequência; não há evidência de implementação integral do algoritmo de otimização de potência do artigo. |
| 06 | Dibaj et al. (2024), trusted nodes MB/MF | **Aderente parcialmente** | QTND, Q-TRx, SKR, compartilhamento, TDM e MB/MF; o código **não compartilha PICh por TDM**: reserva PICh dedicado por QKDSpan. |
| 07 | Mehdizadeh et al. (2024), coexistência MB | **Parcial** | Modelos Raman, NLI e espectro; aproximação CW-FWM existe como utilitário, mas não está ligada diretamente à admissão QKD. |
| 08 | TF-QKD em MCF (2025) | **Não reproduzido** | O protocolo de produção é BB84 decoy-state, sem detectores/protocolo twin-field e sinais servo equivalentes. |
| 09 | FWM intercore/backward em MCF (2026) | **Não reproduzido integralmente** | Não há acoplamento do modelo FWM CW aos ruídos usados em `QKDProvisioningService`; os efeitos retropropagantes exigem arquitetura específica. |
| 10 | Repetidores quânticos/hollow-core (2026) | **Não reproduzido** | Há trusted nodes, não swapping/entanglement/quantum repeaters. |
| 11 | Gestão robusta de recursos QKD | **Parcial** | Compartilhamento por capacidade disponível; sem mecanismo comprovado de estoque/buffer persistente de chaves. |
| 12 | AnaQKD | **Parcial (separado)** | Pacote `qkd.analytical` permite estudos analíticos, mas não substitui o provisionamento dinâmico de produção. |
| 13 | Priority-based IC-XT / IM-XT SS-EON | **Parcial** | Seleções por núcleo/XT; não foi constatado modelo completo multimodo IM-XT na admissão QKD padrão. |
| 14 | ML-Optimized QKD Frequency Assignment | **Não reproduzido** | A seleção de QCh usa políticas de espectro, sem inferência ML integrada à admissão. |
| 15 | Launch power control for QKD | **Parcial** | Poder de lançamento e testes de QoT/QKD presentes; não é reprodução da política de otimização conjunta do artigo. |
| 16 | QKD-Analytical | **Parcial (separado)** | Implementação analítica de apoio, não verificação automática de todos os cenários da CTMC do artigo. |
| 17 | Xie et al., geração/consumo de chaves | **Parcial** | `QKDKeyDemand` e reserva da SKR disponível; não há buffer temporal de chaves confirmado na via principal. |
| 18 | Quantum-native communications (survey) | **Conceitual** | Revisão ampla, sem especificação de um algoritmo QKD completo a reproduzir. |
| 19 | Tutorial coexistência quântico-clássica | **Aderente conceitualmente** | QCh, PICh, DCh, interferências e reserva conjunta estão representados. |

**Importante:** classificar uma função como presente **não implica** que os resultados numéricos coincidam com os experimentos publicados. Tais alegações exigem os mesmos parâmetros, topologia, cargas, sementes e métricas dos artigos.

## 3. Lacunas e verificações técnicas recomendadas

### P0 — Alta prioridade científica

**P0.1 — QBER como limiar de admissão:** em `QKDPhysicalLayer.evaluateLinkChannelInternal` os resultados por span recebem `maximumQber = 1.0` no fluxo usual, enquanto o gerenciador de recursos decide principalmente pela SKR mínima. O BB84 já penaliza erros por meio do cálculo da SKR, portanto isso **não equivale a ausência de segurança**, mas **não reproduz um requisito adicional e configurável de QBER máximo**. Recomenda-se diferenciar o QBER observado de um limiar físico explícito, caso o artigo ou o experimento o imponha.

**P0.2 — FWM do artigo de 2024/2026:** `qkd.multiband.FourWaveMixingModel.productPowerW` é invocado apenas nos verificadores de literatura (`MultiBandLiteratureVerification`); a via de produção usa GN-NLI, Raman, XT e leakage, mas não injeta explicitamente os produtos discretos CW-FWM no fundo do detector. O modelo GN representa parte da não linearidade agregada; não substitui uma reprodução de FWM coerente/intercore ou backward.

### P1 — Modelagem e política de alocação

**P1.1 — PICh dedicado versus TDM do artigo-base:** `QKDPichPool` conserva `sessionsPerChannel` por compatibilidade, mas atualmente abre um **PICh dedicado por QKDSpan**. É escolha conservadora e fisicamente explícita; pode aumentar consumo espectral e de equipamentos relativamente à estratégia de compartilhamento/TDM de Dibaj (2024).

**P1.2 — Proteção atrelada ao DCh:** `QKDProvisioningService.provision()` exige `request.getCircuits().size() == 1`; não suporta diretamente pedidos QKD puros, múltiplos DChs no mesmo pedido nem todos os fluxos de grooming estudados em parte da literatura. Existe fallback para grooming, que merece teste de regressão ponta a ponta.

**P1.3 — PICh opcional:** `pichSlots = 0` é aceito na configuração, e nesse caso `pichPool == null`: um pedido QKD pode não reservar PICh físico. Para cenários de coexistência que **exigem** CCh/PICh, deve-se validar `pichSlots > 0`. As configurações MB examinadas usam `pichSlots = 1`.

**P1.4 — Grade espectral absoluta:** alguns cenários MB apresentam `classicalBandStartThz = 191.3` em `qkdPhysicalLayer.json`, enquanto a camada clássica possui `centerFrequency` e `amplificationFrequency` de 193,85 THz. **Esses campos não têm a mesma função.** É necessário auditar a conversão *slot → frequência absoluta* de DCh/PICh/QCh dentro do mesmo espectro físico antes de afirmar consistência. Não alterar valores por inferência.

**P1.5 — Estado de chaves:** a administração de SKR reservada por canal e TDM não simula explicitamente armazenamento e saldo dinâmico de chaves. A comparação quantitativa com estudos de *key generation and consumption matching* exige decidir se a modelagem é por taxa instantânea ou buffer de estoque.

### P2 — Modelos fora do escopo atual

TF-QKD, servos e protocolo twin-field, repetidores quânticos, interferência modal IM-XT detalhada, políticas de frequência por ML e mecanismos de FWM retropropagante demandam novas implementações, caso sejam alvos dos experimentos. Não ativá-los implicitamente em uma reprodução de BB84/SDM-QKD.

## 4. Política de espaçamento adotada: `KSPXT_v3`

**Referência:** `src/main/java/gprmcsa/integrated/KSPXT_v3.java` **permaneceu intacta**.

- Indentação visual equivalente ao padrão de tabulações (tab-stop de quatro colunas).
- Uma linha em branco entre grupos lógicos (campos/documentação/métodos, blocos de controle e retorno onde aplicável).
- Preservação da ordem de classes, declarações, comentários, imports, métodos e conteúdo dos comandos.
- Preservação dos finais de linha (CRLF/LF) e da codificação de origem usada pelos Java do projeto (compatível com `ISO-8859-1` do `pom.xml`).
- Limite intencional: **nenhum método foi extraído, renomeado ou reordenado**. A solicitação de espaçamento foi interpretada como edição estilística, não alteração de comportamento.

**Arquivo de automação reproduzível:** `tools/format_java_kspxt_v3.py`.

No Windows, a partir da raiz do projeto:

```powershell
python tools/format_java_kspxt_v3.py
# Apenas conferência (retorno 0 quando está formatado)

python tools/format_java_kspxt_v3.py --write
# Aplicar de novo em arquivos alterados futuramente
```

## 5. Evidência de validação

- **413 Java** passaram pelo parser do compilador Java configurado para `--release 8` (0 erros sintáticos).
- **390 Java modificados somente em whitespace**, demonstrado por comparação de todos os caracteres não-brancos com o ZIP original, um a um; **23 arquivos idênticos**.
- **12 classes centrais** (controle QKD, admissão, PICh, gerenciador compartilhado, camada física, BB84, conversor de rota, `PhysicalLayer`, leis EDFA) recompiladas com alvo Java 8 sobre as classes pré-compiladas fornecidas no ZIP. Três avisos de obsolescência do alvo Java 8 no JDK 21; sem erros.
- **20/26 verificadores de regressão executados com sucesso** usando as classes centrais recompiladas e `target/classes` da versão fornecida. **6/26 não puderam iniciar** por ausência da biblioteca Gson de runtime (`NoClassDefFoundError`/`ClassNotFoundException`). Isso **não é evidência de defeito do código do simulador**.
- A verificação sintática e a equivalência lexical indicam segurança da mudança de apresentação; **não foram executadas** compilação Maven limpa, regressão integral com dependências reais nem reprodução numérica de todos os artigos.

## 6. Referências principais (no ZIP do usuário)

- E. E. Moghaddam, H. Beyranvand, J. A. Salehi, *Resource Allocation in Space Division Multiplexed Elastic Optical Networks Secured With Quantum Key Distribution*, 2021.
- M. R. Dibaj et al., *Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in Multi-Band and Multi-Fiber Realms*, 2024.
- P. Mehdizadeh et al., *Quantum-Classical Coexistence in Multi-Band Optical Networks: A Noise Analysis of QKD*, 2024.
- C. Xie et al., *Synergistic Resource Allocation with Key Generation and Consumption Matching in Quantum Secured Multicore Fiber Optical Networks*, 2024.
- *Analysis of Four-Wave Mixing Type Noises in the Quantum and Classical Coexistence Systems Over Multicore Fiber*, 2026.
- *QKD-Analytical* e *AnaQKD* (artigos de análise de bloqueio e QKD-aware resource allocation).

**Decisão editorial:** o relatório identifica divergências funcionais, mas a entrega aplica **somente a reorganização visual** pedida para evitar mudanças silenciosas nos resultados de simulação.

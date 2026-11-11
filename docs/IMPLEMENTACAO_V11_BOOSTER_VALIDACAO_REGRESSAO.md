# SNetS-SDM-QKD v11 — booster, cadeia de EDFAs e validação

## Escopo

**GN do SNetS mantido**: as classes `network.LongitudinalClassicalGN` e as formulações GN legadas não foram alteradas. Nenhum EGN, GGN ou ISRS-GN foi introduzido.

### Mudança física

`ClassicalPowerEvolution.withBooster(G0, Psat, saturated)` configura a etapa de amplificação imediatamente após o lançamento no início do enlace dirigido (z=0). O snapshot imutável calcula o ganho efetivo do booster a partir da potência total dos DCh/PICh que **começam em z=0**.

A potência de cada fonte lançada em z=0 atravessa o booster antes de percorrer a primeira seção de fibra e integra a carga dos EDFAs de linha subsequentes. Fontes PICh lançadas em nós confiáveis internos não atravessam retroativamente o booster nem EDFAs anteriores. Os EDFAs da linha preservam os perfis multibanda de ganho/ASE já disponíveis.

`LongitudinalClassicalQoT` utiliza o ganho do booster registrado nesse mesmo snapshot para propagação do sinal, carga do pré-amplificador e propagação do ruído; o modelo GN longitudinal e o Raman QKD consultam a mesma curva de potência da fonte. Como as fontes iniciam em posições diferentes, não deve ser aplicado um segundo fator de booster ao termo GN gerado.

`QKDProvisioningService` injeta a configuração do booster da camada clássica no avaliador QKD; `QKDPhysicalLayer` aplica essa configuração somente ao construir snapshots longitudinais do enlace, sem amplificar o QCh. A antiga opção sem booster é preservada para usos isolados do modelo e simulações legadas.

**Limitações:** ganho comprimido é fenomenológico, não um modelo por equações de taxa de EDFA. O booster é tratado por um ganho escalar único por núcleo; não há modelo de bombeamento multibanda específico para o booster, nem ISRS-GN. Não se presume contrapropagação na fibra.

### Verificação científica

Executadas com `javac --release 8`, Java 21:

- `QKDBoosterIntegratedVerification`: ganhou PASS; verifica aumento de carga nos amplificadores de linha causado pelo booster, saturação maior, origem longitudinal correta do PICh, equivalência entre a transferência de sinal do QoT clássico e a curva de potência utilizada pela QKD/GN, limite de fibra passiva, e ASE positiva.
- `QKDClassicalLongitudinalQoTVerification`: PASS.
- `QKDLongitudinalGNVerification`: PASS (o modelo GN original não foi alterado).
- `QKDAmplifierLongitudinalVerification`: PASS.
- `QKDDynamicAmplifierVerification`: PASS.
- `QKDSharedAmplifierGainVerification`: PASS.
- `QKDUnifiedMultibandVerification`: PASS.
- Compilação incremental dos pontos de integração `PhysicalLayer`, `QKDPhysicalLayer`, `QKDProvisioningService` com classes legadas como dependências: PASS.

Esses testes são de consistência física, invariantes e casos limite; **não substituem calibração experimental** de ganho/ASE, GN, Raman, QBER/SKR.

### Regressão completa: preparo e bloqueio de execução

`tools/regressao/executar_regressao_integrada.py` utiliza o executor existente para rodar:

- `C*`: quatro casos clássicos, referência `main`;
- `Q*`: dez casos QKD, referência `main`;
- `M*`: três casos MF, MB e MB-SDM, referência `experimental`.

A verificação de pré-condições encontrou os 17 diretórios de casos e ambos os manifestos de golden master.

**Os 17 casos NÃO foram executados neste ambiente**, porque o ZIP de fontes v10 não contém um JAR executável e o ambiente não dispõe do conjunto completo de dependências Maven para reconstruí-lo. Um JAR antigo NÃO serve como prova de regressão da v11. O executor escreve status `NOT_RUN` em vez de anunciar aprovação. Compare as saídas alteradas intencionalmente com novas referências científicas antes de atualizar golden masters.

Para executar após compilar o JAR atualizado:

```bash
python tools/regressao/executar_regressao_integrada.py --jar /caminho/SNetS-SDM-QKD-v11.jar
```

Para conferir somente presença de cenários e referências:

```bash
python tools/regressao/executar_regressao_integrada.py --verificar-casos
```

**Atenção:** diferenças intencionais de ganho saturado podem alterar QoT, QBER e SKR nas simulações com modelo longitudinal ativo. O modo GN legado e as configurações com amplificação longitudinal desabilitada não foram modificados diretamente.

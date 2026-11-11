# SNetS Launcher 1.24.2 — Seletores físicos e verificação BER

## Interface

- `physicalLayer.crosstalkModel`: 0 = **separado**, 1 = **junto**; arquivo JSON permanece numérico.
- `physicalLayer.typeOfTestQoT`: 0 = **limiar SNR (OSNR)**; 1 = **BER**.
- `physicalLayer.typeOfAmplifierGain`: 0 = **ganho fixo**, 1 = **ganho saturado**. O modelo saturado tem limitações conhecidas e **não foi modificado** aqui.
- Removido apenas o parágrafo de ajuda longo do campo `physicalLayerModel`. As descrições específicas dos demais campos permanecem.
- `simulation.qkdKeyDemand.updateIntervalsMiliseconds` não é exibido no formulário, nem como opção para adicionar. **O valor permanece no modelo e no JSON ao salvar** e pode ser editado na aba JSON. O parser Java e o validador continuam aceitando o campo. Não confundir esconder campo com excluir suporte legado.

## Melhorias no teste QoT por BER

O algoritmo original aplicava `getBER(OSNRlinear, M)` diretamente em `isAdmissible2`, embora o ramo SNR usasse a conversão `SNR = OSNR * 2*12.5e9 / (bitRate*(1+rateOfFEC))`. Agora ambos os ramos avaliam a mesma SNR calculada a partir da OSNR quando `isAdmissible2` é chamado. `isAdmissible` sempre interpreta seu argumento como SNR linear.

A aproximação AWGN pré-FEC usa M-QAM com rotulagem Gray e geometria retangular. Para 8/32-QAM, pressupõe constelação retangular 2×4 / 4×8; **não representa qualquer geometria arbitrária** e não inclui ruído dependente da constelação, implementações de FEC, desbalanceamento do receptor ou efeitos estatísticos não gaussianos.

A função complementar `erfc(x)` foi substituída por aproximação estável de *Numerical Recipes* para reduzir cancelamento numérico em BER pequena. Entradas inválidas são rejeitadas.

Parâmetro **opcional** para o modo BER no arquivo `physicalLayer`:

```json
{
  "typeOfTestQoT": 1,
  "berThreshold": 0.001
}
```

- `berThreshold`: BER **pré-FEC** máxima (estritamente maior que zero e menor que 0,5). A interface oferece 0,001 somente como valor ilustrativo ao adicionar explicitamente esse campo.
- Se `berThreshold` estiver ausente, cada modulação continua utilizando um limite BER derivado de seu limiar SNR. Nesse caso, a comparação BER é essencialmente uma transformação monotônica do limiar SNR, e **não** um limiar BER experimental independente.
- Não alterar automaticamente os limites FEC ou de modulação existentes; uma BER alvo própria requer calibração bibliográfica ou experimental para constelação/receptor/FEC.
- Nenhum resultado de desempenho, bloqueio ou QoT end-to-end da campanha inteira foi assumido.

## Verificações

- `qkd.verification.ClassicalBerQotVerification`: fórmula QPSK, monotonicidade M=4/8/16/32/64, rejeição de entradas inválidas, limite BER explícito, conversão OSNR→SNR, compatibilidade do limiar SNR histórico.
- `launcher/tests/test_launcher_qot_ui.js`: enumerações, ocultação do campo legado e preservação dos valores JSON.
- `launcher/tests/test_schema_contract.py`: regras HTML/JS e demais contratos do Launcher.
- `qkd.verification.QKDThesisSNetSPhysicalLayerVerification`: regressão do cálculo de NLI de referência.

Executar no Windows, após reconstruir o JAR: `scripts\verificar_qot_ber.bat`.

**Limite dos testes realizados aqui:** compilação seletiva Java 8 contra classes pré-compiladas, verificadores controlados e compilação do Launcher. Ainda é necessária compilação integral e regressão do simulador contra o JAR base da instalação Windows, além de validação científica de BER para constelações não quadradas.

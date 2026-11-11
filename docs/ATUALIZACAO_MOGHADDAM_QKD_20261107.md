> **Nota de atualização de configuração:** os campos antigos de intervalos foram substituídos por `simulation.qkdKeyDemand.updateIntervalSeconds`. Consulte `docs/QKD_UPDATE_INTERVAL_SECONDS.md`.

# Atualização das requisições QKD — Moghaddam (2021) e coexistência multibanda

**Base:** `SNetS-SDM-qkd-v1(1).zip` reorganizada segundo `KSPXT_v3`. **Data:** 2026-11-07.

## 1. Resultados da análise científica

Artigo principal: E. E. Moghaddam, H. Beyranvand e J. A. Salehi, *Resource Allocation in Space Division Multiplexed Elastic Optical Networks Secured With Quantum Key Distribution*, IEEE JSAC 39(9), 2021, DOI 10.1109/JSAC.2021.3064641.

- **QBER não é um limiar independente de CAC:** equações (9)–(13) calculam a SKR usando QBER `E_mu`, probabilidades de detecção, erros e eficiência de correção. A admissão deve reservar capacidade de **SKR suficiente** para a demanda de chaves da requisição. Por isso NÃO foi criado um campo `maximumQber` para reproduzir esse artigo. O argumento de compatibilidade `maximumQber=1.0` existente na avaliação não é um filtro de admissão independente; QBER alta já derruba a SKR.
- **Demanda de chaves do trabalho:** AES de 256 bits, com atualização a cada **256, 128 ou 64 milissegundos** (Seção VI-B). Correspondem a **1000, 2000 e 4000 bits/s**. A implementação anterior interpretava por padrão os números 64, 128 e 256 como **microssegundos** (1–4 Mbit/s), errando por fator 1000. O novo campo JSON `updateIntervalsMilliseconds` corrige os defaults. `updateIntervalsMicroseconds` continua disponível explicitamente para cenários legados.
- **Estado de chaves:** a publicação considera **SKR disponível/reservada frente à taxa de renovação de chaves**; não apresenta inventário temporal de chaves (saldo, consumo de blocos, filas, expiração, reabastecimento de buffer). Nesta atualização, a política permanece de **reserva de capacidade de SKR** em cada trecho e atualização enquanto a sessão está ativa, evitando atribuir ao trabalho um modelo de buffer que não está nele.
- **FWM:** a geração de produtos espectrais clássicos dentro do filtro de um QCh cria detecções de fundo aleatórias; eleva QBER e pode reduzir a SKR. A publicação também analisa o efeito de separação de frequência (incluindo gap de 200 GHz) e outras fontes de ruído. A nova implementação usa produtos FWM **discretos CW** e o modelo de potência já disponível em `FourWaveMixingModel`. NÃO equivale a uma integração FWM de canais modulados ou a todos os casos intercore apresentados em artigos posteriores.

## 2. Mudanças implementadas

| ID | Resultado | Componentes |
|---|---|---|
| P0.1 | Admissão baseada na SKR obtida de BB84 decoy/QBER, **sem limiar adicional inventado**; intervalos corrigidos para ms | `QKDKeyDemand`, `QKDKeyDemandPolicy`, `SimulationConfig`, `QKDProvisioningService` |
| P0.2 | Flag `fwmEnabled` (default `false`); impacto em background, QBER e SKR dos QChs MB/MB-SDM; proteção de sessões existentes; incompatibilidade configuracional com GN no QCh | `QKDPhysicalLayerConfig`, `MultiBandSettings`, `QKDPhysicalLayer`, `QKDSpanPhysicalResult`, `QKDExistingSessionImpactEvaluator` |
| P1.1 | PICh óptico **dedicado por QKDSpan** por padrão; `pichAllocation=tdm` permite até N sessões simultâneas no mesmo PICh do mesmo trecho; uma fonte óptica contada uma única vez | `NetworkConfig`, `QKDNetworkParameters`, `QKDPichPool` |
| P1.2 | Requisições QKD independentes (`QKD_ONLY`) sem DCh; suporte a requisições protegidas com múltiplos DChs usando uma sessão QKD | `RequestProtectionType`, `RequestForConnection`, `QKDRequestGenerator`, `QKDControlPlane`, `QKDProvisioningService` |
| P1.3 | `pichTransport=ip` remove **somente** a reserva/fonte óptica PICh, preservando a sessão QKD; `pichSlots=0` com `optical` é configuração inválida | `NetworkConfig`, `QKDNetworkParameters`, `QKDProvisioningService` |
| P1.4 | Origem de grade calculada: `f_low = centerFrequency − N_slots * larguraSlot / 2`; DCh e PICh usam exatamente a mesma referência clássica da malha física | `AbsoluteFrequencyGrid`, `PhysicalLayer`, `QKDMesh` |
| P1.5 | Estado de chaves conforme equações de SKR, taxa de renovação e reserva de capacidade do trabalho, **sem buffer temporal artificial** | `QKDKeyDemandPolicy`, `QKDSharedResourceManager`, controles de admissão |
| P2 | TF-QKD, repetidores e outros modelos fora do escopo **não implementados** | — |

### Importante: origem clássica versus frequência de QCh

Uma grade de **DCh/PICh** de `N` slots de largura `Δf` é centrada em `physicalLayer.centerFrequency`: `lowerEdgeHz = center − (N Δf)/2`; o centro de uma alocação `[s0,s1]` (slots numerados a partir de 1) é `lowerEdgeHz + ((s0+s1−1)/2) Δf`. O arquivo QKD não desloca a grade clássica com um segundo `classicalBandStartThz`. Os QChs em banda O/E, porém, **possuem seus próprios comprimentos de onda** e não devem ser artificialmente colocados na mesma banda C/L.

### Regras do PICh por IP

`pichTransport=ip` representa sinalização pública por uma rede IP **fora do modelo de camada óptica**. Não reserva espectro nem emite ruído óptico PICh. Não simula latência, pacotes ou falhas de transporte IP.

### Limitações do TDM de PICh

`pichAllocation=tdm` efetua compartilhamento de **um único portador óptico** por até `pichSessionsPerChannel` sessões ativas do **mesmo QKDSpan**. O portador aparece apenas uma vez no espectro e na carga óptica. Essa é uma **abstração de capacidade/conexões simultâneas**; não modela intervalos de tempo, rajadas de protocolo, atrasos ou uma taxa explícita de bits de sinalização.

## 3. Exemplos de configuração

### `network`: PICh óptico dedicado, padrão

```json
"qkd": {
  "enabled": true,
  "pichSlots": 1,
  "pichTransport": "optical",
  "pichAllocation": "dedicated"
}
```

### `network`: PICh óptico TDM com capacidade limitada

```json
"qkd": {
  "enabled": true,
  "pichSlots": 1,
  "pichTransport": "optical",
  "pichAllocation": "tdm",
  "pichSessionsPerChannel": 2
}
```

### `network`: PICh implementado por IP

```json
"qkd": {
  "enabled": true,
  "pichTransport": "ip",
  "pichSlots": 0,
  "pichAllocation": "dedicated"
}
```

### `traffic`: requisição QKD independente

O item em `requestGenerators` pode conter, além dos campos clássicos já existentes:

```json
"qkdOnlyProbability": 0.2,
"qkdProtectedProbability": 0.5
```

As duas probabilidades somadas devem ser <= 1. A parte restante gera requisições clássicas sem QKD. A requisição QKD independente usa o par origem/destino e a mesma distribuição temporal dos geradores, mas **não ocupa DCh**.

### `simulation`: demanda de chaves

```json
"qkdKeyDemand": {
  "keySizeBits": 256,
  "updateIntervalsMilliseconds": [64, 128, 256],
  "mode": "uniform"
}
```

Para reproduzir explicitamente a interpretação legada, substituir apenas o campo de intervalo por `"updateIntervalsMicroseconds": [64, 128, 256]`. **Esses valores não correspondem à Seção VI-B de Moghaddam (2021).**

### `qkdPhysicalLayer`: FWM

```json
"multiband": {
  "enabled": true,
  "architecture": "MB",
  "nliModel": "nenhum",
  "fwmEnabled": true,
  "fwmGammaPerWKm": 1.3,
  "fwmDispersionPsNmKm": 16.0,
  "fwmSlopePsNm2Km": 0.06,
  "fwmPeakEnvelope": false
}
```

O FWM é **desativado por padrão**. O modo de avaliação de QCh rejeita `fwmEnabled=true` junto de `nliModel="gn"` para evitar dupla contagem dos mesmos termos não lineares. `fwmPeakEnvelope=true` usa uma estimativa superior do termo oscilante da eficiência FWM. Em `MF` não há acoplamento FWM via fibra clássica compartilhada; QCh em outra banda/mesmo núcleo (`MB`) e no mesmo núcleo de fibra multinúcleo (`MBSDM`) são os modos atualmente tratados. A versão implementada não inclui produtos FWM entre núcleos ou retropropagantes.

## 4. Correção de regressão descoberta durante o teste ponta a ponta

`QKDSharedResourceManager.singleSpanPlan()` tentava construir um `QKDLinkPlan` completo a partir de um único QKDSpan. Para enlaces com mais de um trecho, o construtor rejeitava o índice e a extensão física e lançava exceção **depois** da ocupação de QCh. A falha deixava um slot quântico reservado.

Introduzida `QKDLinkPlan.forPhysicalSubspan(...)` que mantém **offset e índice reais** do trecho, enquanto identifica uma avaliação física parcial, e limpeza transacional de slots no caso de falha da avaliação. O teste ponta a ponta passou para solicitações QKD independentes com múltiplos spans, PICh dedicado, PICh TDM e PICh IP, inclusive liberação e contagem de fontes físicas.

## 5. Verificações e limites

- Compilação incremental Java 8 dos fontes modificados, usando classes do projeto previamente compiladas.
- 21 verificadores disponíveis previamente aprovados (incluindo novo `QKDMoghaddam2021UpgradeVerification`).
- Verificador ponta a ponta `QKDIndependentAdmissionVerification`: dedicado, TDM e IP, duas ou três sessões e liberação de PICh/QCh, aprovado.
- **Não** foi realizado `mvn clean test`: Maven/dependências externas não disponíveis no ambiente.
- **Não** foram reproduzidas as curvas de QBER/SKR ou os bloqueios de todos os 19 trabalhos, nem medida a performance de simulações extensas com FWM ativado.

**Não alterados:** lei de ganho EDFA, GN de Johannisson/Habibi, tratamento de NLI não negativa, modelo de trusted nodes, arquiteturas de fibra dirigida sem canais contrapropagantes na mesma fibra, e protocolos/repetidores P2.

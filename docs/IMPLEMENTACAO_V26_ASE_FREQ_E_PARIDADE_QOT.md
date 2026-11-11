# SNetS-SDM-QKD v26 — Frequência óptica da ASE e paridade de QoT

## Objetivo e regra física

A PSD de ASE do EDFA continua sendo `S_ASE = h * nu * F(P_in) * (G(P_in) - 1)` (W/Hz). Não houve troca da lei racional de saturação, nem da lei de figura de ruído centralizada na v25.

A frequência `nu` é escolhida de forma **explícita e uniforme** para booster, EDFAs de linha e pré-amplificador:

- **Cenários clássicos de banda única:** `physicalLayer.amplificationFrequency` (Hz), independentemente da frequência central do DCh. Isso permite repetir configurações originais do SNetS e os artigos que usam uma portadora de referência fixa.
- **`physicalLayer.multiBand.enabled=true`:** frequência central real do DCh, calculada a partir dos slots e da grade; a figura de ruído segue o perfil de banda existente.
- **QKD MB / MB-SDM:** frequência central real do DCh ou PICh, inclusive quando o modelo longitudinal ou um perfil espectral calibrado de EDFA não estão habilitados. O provisionamento QKD sinaliza explicitamente a arquitetura à `PhysicalLayer`.
- **EDFA longitudinal com perfil espectral multibanda:** frequência central real. As leis de ganho e NF espectrais existentes permanecem ativas.
- **QKD MF (fibra exclusiva dos QChs):** não habilita automaticamente a regra MB na fibra clássica.

A frequência real do canal continua sendo utilizada para seleção de espectro, XCI, SCI e crosstalk, **mesmo quando ASE utiliza `amplificationFrequency`**.

## Unificação da cascata e do longitudinal

O método `computeSNRSegment()` mantém dois motores. A cascata clássica e o longitudinal agora escolhem a **mesma portadora de ASE**. O modelo de ganho e figura de ruído permanece centralizado em `AmplifierGainLaw` para ambos.

Para cenários DCh homogêneos equivalentes (mesma fibra, spans, perfil de ganho e saturação, mesma NF, sem PIChs e sem EDFA espectral), o longitudinal reutiliza exatamente os **kernels SCI/XCI Johannisson ou Habibi por span** da cascata clássica (`calculateSectionNli()`). A transferência óptica de **cada span** continua sendo obtida no snapshot longitudinal real (`state.spanStartRatio`, `state.signalTransfer`); a NLI gerada é transportada aos estágios subsequentes por esse fator, sem inverter NLI negativa.

O caminho de integração de NLI longitudinal **continua intacto** quando o simulador necessita de física distinta: PIChs internos ou completos, perfis espectrais de ganho, parâmetros de EDFA/fibra distintos ou posições de amplificadores diferentes. Não se força uma igualdade artificial nesses casos. Para QCh, QBER e SKR, os modelos quânticos permanecem específicos e não são substituídos pelo modelo DCh.

Nenhuma perda do OXC/switch foi reintroduzida. Booster compensa MUX, e pré-amplificador compensa último segmento de fibra + DEMUX em cenários de ganho ideal, como na v24.

## Compatibilidade de API

- `enableLongitudinalAmplifierQoT(template, bandStartHz, alphaNeperKm)` permanece disponível (modo não-MB por padrão, exceto perfil EDFA espectral explícito).
- Novo overload `enableLongitudinalAmplifierQoT(template, bandStartHz, alphaNeperKm, multiband)`.
- Novo `setMultibandAseCarrierMode(boolean)` sinaliza uma arquitetura MB/MB-SDM **sem** exigir o modelo longitudinal.
- O provisionamento QKD seleciona automaticamente a política de portadora da arquitetura, sem criar novas flags JSON.

## Evidências e limitações

`QKDv26AseFrequencyParityVerification` executa 48 comparações clássico × longitudinal, cobrindo Johannisson, Habibi, SCI/XCI integral/log, 1 e 2 enlaces (segundo enlace parcial), ganho ideal/saturado, ASE e NLI, e XCI com interferente. Em **configurações homogêneas equivalentes**, a maior diferença relativa observada foi `5,81e-16` (erro de arredondamento). Os testes também verificam a dependência da SNR em `amplificationFrequency` apenas em banda única e sua independência em MB/MB-SDM (com e sem EDFAs espectrais). Executados com e sem cache QoT.

Executados ainda 19 verificadores anteriores de ganho, ruído ASE, Johannisson, Habibi, PICh, XT, cache, save-state e aspectos científicos. Todos terminaram sem exceção neste ambiente.

**Atenção à grade espectral de cenários anteriores:** o arquivo `simulations/QKD_Multibanda/Linha/SSMF_MB_Raman-fisico/qkdPhysicalLayer.json` informa `classicalBandStartThz=191.3`, enquanto a grade da `PhysicalLayer` para a rede correspondente (`centerFrequency=193.85 THz`, 320 slots de 12,5 GHz) começa em **191,85 THz**. As duas grades diferem em 550 GHz. A v26 não muda esses parâmetros científicos automaticamente: a escolha de frequência real no cálculo de ASE do DCh usa a grade de `PhysicalLayer`, mas as fontes do snapshot QKD continuam usando a grade configurada em `qkdPhysicalLayer`. É necessário reconciliar essas configurações antes de exigir igualdade plena de QoT e consistência espectral QKD em tais cenários.

**Limitações:** não houve `mvn clean test` com dependências genuínas; Java 8 foi verificado com substitutos **somente locais, não distribuídos** para Gson/OpenCSV e testes incrementais. Os testes de igualdade representam somente **mesmos parâmetros/mesma física**; simulações MB com Raman/ISRS, PIChs inseridos internamente, EDFAs espectrais diferentes, diferentes ganhos/perdas de enlaces ou aproximações numéricas GN distintas não devem produzir obrigatoriamente a mesma SNR. Requerem uma campanha de regressão científica própria antes de publicação.

## Arquivos alterados

- `src/main/java/network/PhysicalLayer.java`
- `src/main/java/qkd/control/QKDProvisioningService.java`
- `src/main/java/qkd/verification/QKDv26AseFrequencyParityVerification.java` (novo)
- `docs/IMPLEMENTACAO_V26_ASE_FREQ_E_PARIDADE_QOT.md` (novo)
- `CHANGELOG.md`

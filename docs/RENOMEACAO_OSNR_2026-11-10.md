# Padronização de nomenclatura: OSNR óptica

A razão sinal/ruído usada no caminho de QoT clássica de `PhysicalLayer` é uma razão óptica de PSDs, e os limiares por modulação, antes chamados `SNR`, são limites de **OSNR** em dB.

## Campos de rede

- **Novo e canônico:** `network.modulations[].OSNR` (dB).
- **Legado aceito apenas na leitura:** `network.modulations[].SNR` (dB), via `@SerializedName(alternate={"SNR"})`.
- O PICh usa `network.qkd.pichOsnrCheckEnabled`; a antiga chave `pichSnrCheckEnabled` ainda pode ser lida.
- Não informe ambos os nomes na mesma entrada, pois o Gson não detecta essa ambiguidade automaticamente.
- Parâmetros verdadeiros de SNR por bit, como `snrBit` em `getBERFromSnrPerBit`, não foram renomeados.

## Métodos Java

`Circuit.getOSNR()` (dB), `getOSNRlinear()` (linear), `Modulation.getOSNRthreshold()`, `PhysicalLayer.computeOSNRSegment()` e `OpticalNoiseCascade.osnrLinear()` são as interfaces canônicas. Assinaturas `getSNR` e `computeSNRSegment` residuais são wrappers **@Deprecated** sem consumidores novos, reservados a integrações antigas.

`PhysicalLayer.isAdmissible2()` foi removido por estar sem consumidores no simulador, e os testes passaram a usar `isAdmissible`.

A alteração de chaves JSON/nomes de campos serializados pode afetar checkpoints legados e aplicativos externos que realizem inspeção por reflexão. Faça cópia dos arquivos de Save State antes de migrar.

## Outros pontos importantes

- `network.qkd.pichOsnrCheckEnabled` substitui `pichSnrCheckEnabled` na interface. O parser Gson mantém o nome anterior como alias de **leitura**; o Launcher converte o modelo para o nome novo ao abrir casos antigos e impede que sejam gravados ambos os nomes no mesmo objeto.
- O auxiliar **obsoleto** `qkd.capacity.PichSnrAdmission` permanece com SNR, pois calcula uma razão entre potências de sinal e ruído no receptor (W/W), não a mesma definição de OSNR por densidade espectral do motor principal.
- `SNR_b` e `snrBit` nas equações 3.4–3.10 de Jurandir representam a SNR por bit real e permanecem inalterados.
- Os métodos `Circuit.getSNR`, `Modulation.getSNRthreshold`, `PhysicalLayer.computeSNRSegment`, `ControlPlane.computesImpactOnSNROther` ainda existem como wrappers `@Deprecated`, mas não devem ser usados em implementações novas.
- O JAR de validação deve ser recompilado antes do teste no Windows. Execute `scripts\verificar_renomeacao_osnr.bat` depois de gerar `SNetS-SDM-QKD-exp.jar`.

**Limite da verificação:** a compilação Java foi seletiva, utilizando classes de suporte precompiladas e stubs de compilação para Gson. Isso não substitui o build integral com a biblioteca Gson real nem a regressão de 67+ verificadores no Windows.

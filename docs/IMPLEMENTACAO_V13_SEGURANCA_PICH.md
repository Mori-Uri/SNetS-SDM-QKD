# v13 — continuação incremental das correções prioritárias

## Escopo implementado

1. `QKDPichPool.acquire` (rota e API legada) agora tenta reverter **todas** as reservas anteriores se ocorrer indisponibilidade posterior ou exceção durante o processamento dos QKDSpans. Erros de liberação não são ignorados.
2. `openDedicated` restaura registro de fonte auxiliar e espectro quando uma falha ocorre após a ocupação do slot; erro de rollback é reportado explicitamente.
3. `QKDPichCircuit` valida bandas espectrais e expõe `getOpticalBandwidthHz(slotBandwidthHz)` e `getCenterFrequencyHz(lowerFrequencyHz, slotBandwidthHz)`; estes são valores derivados da grade física e não uma banda arbitrariamente presumida.
4. `PichClassicalNonlinearity` utiliza os acessores comuns para evitar duas conversões espectrais divergentes.
5. Configuração opcional `network.qkd.pichFirstSlot` e `network.qkd.pichLastSlot`: limites **inclusivos de slots 1-based**. Ambos ausentes/-1 mantêm a alocação anterior em toda a grade; os dois definidos restringem a faixa utilizada para abrir novos PIChs. São verificados comprimento mínimo e consistência dos limites. Ex.: `"pichFirstSlot": 1, "pichLastSlot": 80`. Isso não cria uma segunda grade independente, mas restringe uma faixa espectral física da grade clássica existente.
6. Teste `QKDPichAdmissionSafetyVerification`: falha por falta de espectro no segundo QKDSpan, falha por plano de rota incompatível após reservar PICh no primeiro enlace, restauração de slots e fontes, metadados de frequência e restrição de slots.

## Invariantes preservados

- Nenhum QKDSpan atravessa um nó óptico do SNetS. A divisão ocorre dentro de cada enlace físico dirigido.
- Sem contrapropagação na mesma fibra.
- O GN original do SNetS não foi alterado.
- O comportamento padrão permanece sem restrição da janela PICh.

## Testes efetivamente executados

- Compilação incremental Java 8 das classes modificadas (`NetworkConfig`, `QKDNetworkParameters`, `Core` como dependência fonte atual, `QKDPichPool`, `QKDPichCircuit`, `PichClassicalNonlinearity`) com classes antigas precompiladas em classpath: OK.
- `QKDPichAdmissionSafetyVerification`: PASS.
- `QKDPriorityCorrectionsVerification`: PASS.
- Não foi concluída compilação limpa do Maven nem regressão do sistema completo. `QKDStages12ContinuationVerification` não foi compilado neste ambiente pois faltava sua classe de dependência multibanda atual no classpath incremental.

## Pendências

- QoT próprio do PICh (modulação, SNR/BER, recepção): não implementado nesta entrega.
- Guarda física efetiva QCh–clássico em same-band, se a arquitetura permitir: não implementada.
- Persistência do estado de rollback com injeção de exceções em todas as etapas de provisionamento clássico e quântico: não comprovada.
- Parametrização multibanda completa de parâmetros do canal clássico: pendente.
- Compilação limpa e validação científica / regressão de cenários MF, MB, MB-SDM: pendentes.

Nenhuma dessas pendências deve ser considerada concluída por causa da compilação incremental ou dos testes isolados.

# SNetS-SDM-QKD v12 — Correções prioritárias (entrega incremental)

## Escopo implementado

1. Proteção do QoT clássico contra PICh em ambos os modos XT_TOGETHER e XT_SEPARATE. A avaliação de SNR continua verificando NLI/ASE mesmo com XT desativado (quando QoT está ativo). No modo XT_SEPARATE, o crosstalk é verificado com seu próprio limiar.
2. Reavaliação completa das sessões QKD ativas antes de admitir um circuito clássico candidato ou um conjunto provisório de PIChs. Não depende apenas da opção IC-XT; permanece condicionada aos modelos físicos efetivamente habilitados.
3. Rollback explícito com propagação de erros e exceções suprimidas na alocação clássica/QKD. Após commit clássico, falha no registro ou refresh tenta liberar circuito clássico e recursos QKD. O provisionamento limpa PIChs quando a alocação QCh lança exceção.
4. Liberação de PIChs deixa de engolir falhas de liberação espectral; canais que falharam são retidos nas estruturas para permitir diagnóstico e nova tentativa.
5. Invariante de fronteiras: QKDLinkPlan exige que cada QKDSpan pertença ao mesmo enlace, seja contíguo e não ultrapasse o comprimento do enlace; QKDPichPool valida a relação antes de reservar. Nenhum QKDSpan pode atravessar nó óptico SNetS.
6. Teste isolado de contiguidade, pertencimento, limites longitudinais e rejeição de spans inválidos.

## Preservado
- Modelo GN existente do SNetS; não implementa EGN/GGN/ISRS.
- Links ópticos dirigidos sem contrapropagação na mesma fibra.
- PICh independente por QKDSpan e ocupação conservadora dos slots por enlace.

## Pendências NÃO apresentadas como concluídas
- Prova formal de commit atômico em todas as condições de falha, principalmente dentro do código legado SNetS e estados parciais de liberação.
- QoT autônomo de PICh, com modulação e limiar de SNR/BER próprios.
- Banda/frequência do PICh totalmente configuráveis de forma independente.
- Guarda espectral física QCh–clássico para cenários same-band.
- Modelo completo por dispositivo de MUX/DEMUX/filtros, ASE/leakage calibrados e camada clássica multibanda integral.
- Métricas adicionais, roteamento por custo/SKR e limites independentes de PICh-Tx/PICh-Rx.
- Compilação Maven limpa, execução científica das Figuras 1/5/7 e regressão de MF/MB/MB-SDM com JAR recém-construído.

## Validação executada
- Compilação incremental Java --release 8 com dependências compiladas de uma versão anterior: APROVADA para QKDControlPlane, QKDProvisioningService, QKDPichPool e QKDLinkPlan.
- QKDPriorityCorrectionsVerification: APROVADO (1 caso válido e 3 rejeições de spans inválidos).
- Testes end-to-end de XT_SEPARATE/rollback injetado: NÃO EXECUTADOS nesta entrega.
- Maven limpo e regressão completa: NÃO EXECUTADOS (dependências externas incompletas no ambiente).

**Nota científica:** esta entrega reforça invariantes e admissões; não afirma reproduzir quantitativamente resultados do artigo Dibaj et al. (2024).

# Launcher 1.23.0 — EGN de referencia e Habibi hibrido

Mantem os recursos do Launcher 1.22.0: edicao em lote, configuracao separada
de perdas MUX/DEMUX e do orcamento optico QKD, QBER opcional, Raman anti-Stokes,
maxSKR, parametros PICh e integracao GN do modelo Johannisson.

Em **Camada Fisica**, o Launcher agora apresenta:

- `habibiEgnFallbackEnabled` (padrao `true`): modo hibrido em canais estreitos
  ou quando Habibi produz PSD total negativa.
- `habibiEgnNarrowbandGHz` (padrao `25.0`): largura efetiva real do CUT.
- `habibiEgnQuadratureOrder` (padrao `16`): ordem quadratura por banda.
- `habibiEgnReceiverOrder` (padrao `6`): quadratura media na banda do CUT.

O modo referencial EGN de Carena nao pode ser habilitado indiscriminadamente
na simulacao de rede. E uma rotina dedicada de validacao limitada a ate tres
canais retangulares e mesma largura; veja `docs/EGN_CARENA_HABIBI_VALIDACAO.md`.

**Para reproduzir resultados historicos de Habibi** desative o fallback.
O limite de 25 GHz e criterio de execucao do novo modo, nao limiar teorico
universal de validade da aproximacao.

# SNetS-SDM-QKD v17 — Relatório técnico de implementação

## Entregue

Todos os sete itens solicitados receberam alterações de código e testes direcionados.
O truncamento de NLI negativa em zero foi mantido e passou a registrar diagnósticos.
O Johannisson SCI substitui o piso artificial do logaritmo por integral positiva
(Eq. 11/15 do artigo de 2014). O Habibi longitudinal incorpora os termos
SCI/XCI e as correções dependentes de Phi das Eqs. 3.23–3.31 da tese.

## Compilação e regressão

Compilação incremental Java 8: APROVADA.
Nove verificações Java direcionadas: APROVADAS.
Comparação DCh/PICh: 288 avaliações, 144 por modelo. Testes controlados
nas distâncias de 20/40/60/80 km e diferentes combinações de ruído.

- johannisson: 48 pares longitudinais; maior diferença SNR PICh-DCh = 0.00000000 dB.

- habibi: 48 pares longitudinais; maior diferença SNR PICh-DCh = 0.00000000 dB.

## Exemplo de teste unitário

- Johannisson SCI em 12,5 GHz: fator positivo 0,4321754912 (em lugar do piso logarítmico próximo de zero).
- Span parcial 20 km após 80 km: fator aproximado Johannisson = 0,3811843006 da contribuição equivalente de 80 km.
- Habibi (teste de 80 km, 32 GHz, duas fontes): EGN modulado com QPSK no interferente = 4,04119e-18 W/Hz, com 64-QAM = 4,30110e-18 W/Hz. São valores de um teste de consistência interna e não de calibração experimental.

## Ressalvas essenciais

A PSD física NLI nunca é devolvida negativa. Quando a correção EGN de Habibi supera o GN, o valor é truncado em zero e registrado; isso **não valida** uma NLI física nula. Em vários testes de banda estreita isso acontece.
A via Johannisson SCI agora usa a integral do retângulo para todas as larguras, por continuidade e segurança numérica; valores publicados pelo SNetS legado podem mudar mesmo acima de 28 GHz.
O GN longitudinal Habibi usa potência local na entrada de cada seção e acumulação incoerente, não um EGN completo de propagação distribuída.
Não houve compilação Maven limpa nem execução completa dos cenários de tráfego MF, MB ou MB-SDM; esses resultados permanecem pendentes.

## Arquivos

`SNetS-SDM-QKD-v17-fontes-atualizados.zip`: projeto atualizado.
`SNetS-SDM-QKD-v17-somente-alterados.zip`: arquivos criados/alterados.
`SNetS-SDM-QKD-v17-alteracoes.diff`: diff em relação à v16.
`SNetS-SDM-QKD-v17-validacao.json`: resultados e status verificável.
`SNetS-SDM-QKD-v17-comparacoes.zip`: CSVs de comparação + logs.

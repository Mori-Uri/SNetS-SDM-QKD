# Launcher 1.24.0 — integração seletiva da experimental (2026-11-10)

A versão 1.24.0 mantém o Launcher da **v1**, incluindo os parâmetros QKD, MUX/DEMUX, GN/EGN e continuidade de QCh/PICh. Adiciona o seletor de pastas da experimental (`Picker.java`) à tela Configurações; em ambiente sem interface gráfica, ainda é possível digitar manualmente o caminho. Não introduz atualizações automáticas pela internet nem substitui o fluxo do Launcher.

## Intervalos de renovação de chaves (perfil `custom`)

O backend aceita **um** dos campos abaixo, mas nunca mais de um no mesmo `qkdKeyDemand`:

- `updateIntervalSeconds`: `[0.064, 0.128, 0.256]` (segundos)
- `updateIntervalsMicroseconds`: `[64, 128, 256]` (microssegundos; 4/2/1 Mbit/s com chave de 256 bits)
- `updateIntervalsMilliseconds`: `[64, 128, 256]` (milissegundos; 4/2/1 kbit/s)
- `updateIntervalsMiliseconds`: `[64, 128, 256]` (grafia histórica solicitada, **milissegundos**, não microssegundos)

O código converte os valores internamente para segundos. Os perfis `moghaddam2021` e `dibaj2024` permanecem disponíveis e não aceitam campos legados explícitos simultaneamente.

**No formulário**: para usar um campo legado, elimine a chave `updateIntervalSeconds` do JSON do caso e então adicione a chave nova na seção `qkdKeyDemand` (ou use o modo JSON). Nunca mantenha ambos: o backend rejeita essa ambiguidade.

## Flags para comparação A/B

- `-Dsnets.gnPairCache=true`: ativa exclusivamente a memoização dos coeficientes GN/EGN entre pares de DCh no modelo Habibi da v1; padrão desligado.
- `-Dsnets.noPerfCache=true`: desativa os caches em geral (referência funcional).
- `-Dsnets.verifyPerf=true`: verifica recálculos em acertos dos caches instrumentados.
- `-Dsnets.perfStats=true`: coleta contadores de caches e de alterações físicas na v1.

A comparação A/B deve manter os mesmos arquivos de entrada, sementes, versão compilada, carga e política de QKD. Contagem de acertos de cache **não** é ganho de tempo, e verificadores não demonstram por si só equivalência de campanhas de rede.

## Revisão 1.24.1 — nomenclatura dos intervalos (10/11/2026)

- Campo em milissegundos solicitado: `updateIntervalsMiliseconds` (64 ms = 0,064 s).
- `updateIntervalsMicroseconds` permanece disponível para microssegundos;
  `updateIntervalsMilliseconds` permanece como alias de grafia inglesa correta.
- A grafia anterior `updateIntervalsMileseconds` foi descontinuada e produz erro explícito
  (não pode voltar silenciosamente aos intervalos padrões).
- Informar **somente um** campo entre segundos/microssegundos/milissegundos.

### Ajuste do formulário 1.24.3

O campo `updateIntervalsMiliseconds` **continua visível e editável** na aba Simulação.
O alias equivalente `updateIntervalsMilliseconds` permanece aceito nos JSONs legados, sem um segundo campo na interface.
A grafia incorreta `updateIntervalsMileseconds` permanece rejeitada; ela não é um campo selecionável.
O backend de demanda QKD não sofreu alterações nesta atualização.

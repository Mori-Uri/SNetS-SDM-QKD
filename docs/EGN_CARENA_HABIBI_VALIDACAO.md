> **HISTORICO, NAO IMPLEMENTACAO ATUAL:** O codigo de referencia EGN de
> Carena, suas correcoes integrais e os testes que dependiam delas foram
> retirados. O modelo Habibi analitico (EGN aproximado) permanece; seu
> fallback numerico e apenas GN (Poggiolini 2017).

# EGN numerico de referencia e ajuste de Habibi (2026-11-08)

## Escopo fisico e cientifico

Esta atualizacao **nao substitui** o modelo Habibi aproximado do SNetS nem o
modelo Johannisson. Introduz um **modelo EGN de referencia por quadratura**
(`network.EgnFullReference`) inspirado nas equacoes integradas de Carena et al.
(2014) e um ajuste opcional de Habibi (`network.HabibiEgnHybrid`).

**Restricao decisiva:** a quadratura EGN de referencia resolve de modo explicito
SCI, XCI (incluindo contribuicoes de ordem estatistica 4 e 6) e MCI para
**1, 2 ou 3 canais de mesma largura efetiva, sem sobreposicao espectral**, de
polarizacao dupla e pulsos Nyquist retangulares. No caso de 3 canais, exige
simetria em torno do canal sob teste (CUT). Usa spans identicos, perfil de
perda exponencial, ganho compensatorio lumped e acumulacao de fase coerente.
Nao e uma implementacao EGN geral validada para misturas arbitrarias de bandas,
modulacoes, roll-off, topologias, ISRS ou saturacao; nao chamar o resultado de
"EGN rigoroso universal".

Nos termos desta referencia, "rigoroso" significa **integracao numerica das
formulas EGN completas para o dominio restrito**, em contraste com a correcao
assintotica Habibi. A quadratura nao e solucao exata da equacao de Manakov
(split-step) e exige avaliacao de convergencia. Resultados de um/alguns spans
nao devem ser comparados diretamente com a tese de 50 spans.

## Equacoes e componentes

- Carena et al. (2014), *EGN model of non-linear fiber propagation*,
  Optics Express **22**(13), 16335-16362, DOI 10.1364/OE.22.016335:
  referencia para GN + termos completos SCI, XCI X1-X4 e MCI M0/M1;
  momentos 4 e 6 da modulacao QPSK/16QAM/64QAM e integral na banda do CUT.
- Poggiolini e Jiang (2017), *Recent Advances in the Modeling...*,
  Journal of Lightwave Technology **35**(3): importancia do EGN em taxas
  estreitas e do uso de GN confiavel ao subtrair correcoes aproximadas.
- Lacerda Junior (2024), tese, Sec. 3.1.2, Eqs. 3.21-3.31:
  formula Habibi EGN aproximada e parametros Phi positivos.

O referencial produz **componentes individuais**
`sciGn`, `sciCorr4`, `sciCorr6`, `xciGn`, `xciCorr4`, `xciCorr6`,
`mciGn`, `mciCorr4`. Os termos EGN de ordem 4/6 podem ser negativos
como correcoes; a PSD total precisa ser fisicamente nao negativa.

Unidades: potencia total optica dos dois estados de polarizacao em W,
frequencia em Hz (interna THz), largura fisica em Hz, gamma bruto em
(W km)^-1, beta2 em ps^2/km, alfa de potencia em dB/km, saida PSD W/Hz.

## Modos e parametros de simulacao

A partir de `physicalLayer` (e atraves do Launcher >=1.23.0):

```json
{
  "physicalLayerModel": 0,
  "habibiEgnFallbackEnabled": true,
  "habibiEgnNarrowbandGHz": 25.0,
  "habibiEgnQuadratureOrder": 16,
  "habibiEgnReceiverOrder": 6
}
```

`physicalLayerModel=0` permanece Habibi; `physicalLayerModel=1` permanece
Johannisson, sem mudancas de formula. Com `habibiEgnFallbackEnabled=false`
obtém-se o Habibi anterior exatamente (inclusive o truncamento legado da NLI
negativa). Com `true`, o simulador tenta substituir **SCI** por Carena e **XCI
por Carena individualmente para cada interferente suportado** quando a largura
fisica real do canal (nao tamanho de slot) e <= limite ou o **total Habibi
bruto** e negativo. A formula de PICh que nao cabe no dominio da referencia
continua no tratamento Habibi existente para evitar dupla contagem.

**Caveat metodologico:** o modo hibrido **NAO** soma MCI que envolve dois ou
mais interferentes simultaneos. Para 3 canais da configuracao controlada, o
arquivo de comparacao quantifica esta diferenca. Para redes grandes, o modo
hibrido constitui melhoria pragmatica ainda sem demonstracao de equivalencia
ao EGN completo. Modos QPSK, 16QAM, 64QAM suportados; 8QAM e 32QAM nao
tem aqui os momentos de sexta ordem publicados para a geometria de sua
constelacao: sao preservados no legado, jamais atribuir momentos inventados.

`habibiEgnQuadratureOrder` maior aumenta o custo. As chaves do cache incluem
frequencias, larguras, potencias, parametros fisicos e ordem numerica;
reutilizacao de valores com estado diferente da rede e evitada por chave.

**Reproducao historica**: executar a tese de Jurandir com fallback desativado,
ou manter a faixa de 32 GHz do CUT (>25 GHz) em que o ajuste nao entra
sem NLI negativa; resultados historicos permanecem comparaveis.

## Executar os verificadores

Linux:

```sh
bash scripts/verify_habibi_egn.sh saida.csv
```

Windows 11, JDK instalado:

```bat
scripts\verify_habibi_egn.bat saida.csv
```

O verificador roda isolado, sem Maven e sem classpath legado. Confere:
- 5 larguras: 12,5 / 25 / 32 / 37,5 / 50 GHz;
- 1/2/3 canais QPSK, 1/2 canais QAM16 e QAM64;
- contribuicoes SCI/XCI/MCI e termos de correcao;
- ganho na SCI de 12,5 GHz em relacao ao Habibi truncado;
- diferenca de quadratura n=20 x n=24 (<5% no fixture);
- limite com momentos gaussianos; e casos 2/5 spans (referencia somente).

Para as verificacoes integradas da compilacao existente executar
`qkd.verification.QKDNarrowbandHabibiVerification` apos compilar fontes.

## Pendencias antes de publicacao

1. Implementar a versao geral de Carena para N canais e larguras diferentes
   com classificacao de todo termo MCI, sem usar somas XCI independentes.
2. Comparar com resultados publicados do artigo Carena e com split-step
   Fourier/Manakov, em mais de uma configuracao de fibra/modulacao.
3. Quantificar erros e custo numerico, inclusive 1, 2, 5 e 50 spans.
4. Garantir as convencoes de potencia total/por polarizacao e gamma Manakov.
5. Realizar compilacao Maven limpa e regressao integral sem classes antigas.

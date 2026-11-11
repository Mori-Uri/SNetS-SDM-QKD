# Modelos de Johannisson e Habibi apos remocao de EgnFullReference

## O que foi removido

- `network.EgnFullReference` e seus termos integrais EGN de Carena
  (correcoes dependentes de momentos de 4a e 6a ordens de SCI, XCI e MCI).
- `network.Poggiolini2017EgnReference`, que combinava GN integral com
  as correcoes de Carena; o calculo assintotico EGN Eq. (14) foi removido.
- `verification.HabibiEgnComparisonVerification`, cujo verificador dependia
  do EGN numerico removido.

## O que foi preservado

- O modelo **Johannisson** (`physicalLayerModel=1`): GN com soma SCI/XCI
  e dois flags independentes `johannissonSciUseIntegral` e
  `johannissonXciUseIntegral`. Ambos usam a aproximacao logaritmica por
  padrao; com `true` usam o kernel integral de Johannisson/Agrell.
  Se a expressao logaritmica nao e definida/positiva, o kernel integral
  e utilizado como fallback seguro.
- O modelo **Habibi** (`physicalLayerModel=0`): aproximacao EGN analitica
  baseada na tese de Jurandir, com termos SCI/XCI (asinh) menos correcoes
  por Phi da modulacao. **Nao usa integral completa de Carena**.
- O fallback numerico opcional de Habibi para largura fisica <=
  `habibiEgnNarrowbandGHz` (nome legado) ou NLI negativa substitui SCI
  e XCI individuais elegiveis com o integral **GN** Eq. (2)-(4) de
  Poggiolini & Jiang (2017) implementado por
  `network.Poggiolini2017GnIntegral`. O fallback **nao** e um modelo
  EGN integral e nao agrega automaticamente MCI do conjunto total.
- O caminho longitudinal preserva a evolucao de potencia por trecho;
  os kernels analiticos sao aplicados em secoes fisicas. Nao e uma
  integracao longitudinal continua da EGN completa.

## Polarizacoes e BER

- Para a tese de Jurandir, `OSNR = p * log2(M)/2 * SNR_bit`.
- Em `PhysicalLayer`, `getBER(osnrLinear, M)` e **um metodo de instancia**
  que utiliza `polarizationModes` da configuracao (sem p=2 fixo).
- `PhysicalLayer.getBER(osnrLinear, M, polarizations)` e um metodo
  estatico para calculos explicitos com p=1 ou p=2.
- Os caminhos de QoT de producao usam o parametro configurado; os valores
  de `Modulation.OSNRthreshold` permanecem limiares opticos em dB.
- O kernel GN numerico da referencia assume o fator 16/27 de
  **duas polarizacoes**, independentemente do parametro de BER. Isso
  nao equivale a uma verificacao cientifica completa do NLI para p=1.

## Efeitos sobre os resultados

Remover os termos EGN integrais altera os valores de NLI do fallback:
para 12,5 GHz QPSK em um unico span de 100 km, nos parametros
`alpha=0,2 dB/km`, `gamma=1,3 (W km)^-1`, `beta2=-21,3 ps^2/km`,
`P=1 mW`, a antiga integral EGN fornecia cerca de
`9,469612e-18 W/Hz`, enquanto o fallback GN atual fornece
`2,405588e-17 W/Hz`. Essas duas grandezas **nao sao equivalentes**.

## Limites

- Johannisson: integral bidimensional normalizada dos kernels SCI/XCI,
  nao a quadrupla/sextupla integral completa EGN.
- Habibi: aproximacao EGN com integracao somente opcional GN nos
  casos estreitos/negativos.
- Canal PICh e amplificadores: contribuem pelos modelos existentes da
  v1, nao por novo modelo GN integral generalizado da rede.
- Preservacao de perfis JSON: os nomes legados `habibiEgn*` continuam
  aceitos e conservam seus valores. A semantica cientifica do fallback,
  porem, agora e GN e deve ser identificada em cada campanha.

## Validacao

`verification.HabibiGnComparisonVerification` (15 casos) e
`network.Poggiolini2017GnIntegral` (15 casos) geram comparacoes
numericas A/B. `qkd.verification.ClassicalBerQotVerification` compara
1 e 2 polarizacoes, `qkd.verification.QKDNarrowbandHabibiVerification`
verifica o fallback no fluxo de producao. Regressao integral no Windows
com o JAR base e bibliotecas originais continua necessaria.

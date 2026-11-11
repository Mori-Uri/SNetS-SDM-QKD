# SNetS-SDM-QKD v25 — Leis compartilhadas de ganho, NF e ASE dos EDFAs

## Objetivo
Centralizar a lei de ganho saturado, a lei de figura de ruído dependente da potência de entrada e a ASE em `network.AmplifierGainLaw`, mantendo a mesma lei racional de ganho da v24. A fórmula implícita de saturação da imagem NÃO foi adotada nesta atualização.

## Convenções

- `G0`: ganho não saturado, escala linear.
- `Pin`: potência óptica total incidente em cada EDFA, em **W**; inclui DCh/PICh conforme sua presença física no trecho.
- `Psat`: potência de referência de saturação, em **W**, da lei racional existente.
- `F0`: fator de ruído nominal, **linear** (`10^(NF_dB/10)`).
- `A1`: coeficiente empírico adimensional.
- `A2`: parâmetro empírico de potência, em **W** (o código legado já usava `Pin/A2`). `A2 <= 0` desabilita dependência de potência.
- `f`: frequência óptica em Hz; `h`: constante de Planck em J·s.
- `G`: ganho efetivo em escala linear.

Com amplificação saturada:

```
G(Pin) = max(1, G0 / (1 + G0 Pin / Psat))
F(Pin) = F0 * (1 + A1 - A1 / (1 + Pin/A2)), quando A2 > 0
S_ASE(Pin) = h * f * F(Pin) * (G(Pin) - 1)  [W/Hz]
```

Com amplificação ideal, `G=G0`, `F=F0` e a ASE é calculada pela mesma expressão, com os valores fixos. A convenção de ASE segue a aproximação de **duas polarizações** utilizada anteriormente.

## Arquivos alterados

- `network/AmplifierGainLaw.java`: contém a lei racional de ganho, a função pura `noiseFactor`, o cálculo da ASE nominal/saturada e a compressão compartilhada entre bandas.
- `network/Amplifier.java`: métodos históricos de ganho/figura de ruído/ASE delegam à mesma lei; assinaturas públicas preservadas.
- `network/PhysicalLayer.java`: cálculo de ASE para booster, amplificadores de linha, pré-amplificador e PIChs passa a usar `AmplifierGainLaw` também quando a NF varia com a potência; o template longitudinal incorpora A1/A2 do cenário.
- `network/LongitudinalClassicalQoT.java`: ganho e NF do booster, linha e pré-amplificador usam a mesma política, incluindo o estado dinâmico compartilhado da linha; antiga assinatura de `evaluate` continua disponível.
- `qkd/multiband/ClassicalPowerEvolution.java`: snapshots carregam A1/A2 e utilizam a potência incidente medida para cada EDFA; cópias de configuração preservam os parâmetros; propagação para QCh usa o mesmo modelo de emissão clássica quando habilitado.
- `qkd/multiband/MultiBandEdFA.java`: ganho espectral preserva a competição interbanda, mas delega a compressão e a ASE com NF(Pin) ao núcleo compartilhado.
- `qkd/verification/QKDv25SharedNoiseFactorVerification.java`: novo verificador de equivalência e integração clássica/longitudinal/multibanda.

## Compatibilidade e comportamento

- Não altera o ganho racional nem as configurações `physicalLayer` existentes.
- `A1/A2` da configuração clássica são aplicados aos snapshots longitudinais e à QKD que compartilha o mesmo estado físico configurado pelo provisionamento.
- O modelo QCh não ganha amplificação; apenas a emissão ASE clássica calculada poderá participar de seu termo de vazamento já calibrado.
- As fontes PICh em QKDSpans internos permanecem localizadas; não se alteram crosstalk, Raman, GN, seleção Johannisson/Habibi ou os filtros SCI/XCI.
- A proteção de valores negativos de NLI fica intacta.
- O ganho multibanda utiliza a saturação de reserva compartilhada, não o ganho monobanda independente por frequência. A regra de compressão já existente foi preservada.
- Para QKD sem ativação do modelo longitudinal compartilhado, permanece o modelo de NF explicitamente configurado no contexto multibanda, sem inferir parâmetros clássicos externos.

## Validação

- Compilação incremental Java 8 com classes previamente compiladas da v24: aprovada.
- `QKDv25SharedNoiseFactorVerification`: aprovado (leis unitárias, equivalência do `Amplifier`, NF saturada em snapshots, ASE em C+L e SNR real de `computeSNRSegment` nos modos clássico e longitudinal).
- 18 verificadores da v24: aprovados novamente com as classes modificadas.
- SNR linear, cenário do novo verificador (aproximações próprias do cenário): cascata clássica `3940.4699113` para `3459.0391683` quando A1 muda de `0` para `4`; longitudinal `3945.3148558` para `3463.2921770`.
- Ganho fixo: mudar A1 não altera SNR no verificador.

**Limitações**: não houve compilação Maven limpa com dependências reais, nem regressão de tráfego integral MF/MB/MB-SDM. A NF dinâmica segue o modelo empírico legado de Pereira, não as equações de população do érbio. O efeito quantitativo deve ser validado com uma curva EDFA calibrada antes da publicação.

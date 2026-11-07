# QoT clássica por banda e ISRS (Parte 49, experimental)

Até a Parte 48, a QoT dos circuitos clássicos usava os mesmos α, NF, γ e β2 para todo o espectro, os da banda C. No C+L (ou S+C+L) isso deixa de valer, por três motivos:

- a perda e a figura de ruído mudam de banda para banda;
- a dispersão cresce com o comprimento de onda;
- o espalhamento Raman estimulado entre os canais (ISRS) passa potência das frequências altas para as baixas.

O bloco opcional `multiBand` do arquivo `physicalLayer` liga esse modelo. Ele não existe nos casos antigos, e quando está ausente ou com `enabled: false` o simulador dá exatamente os mesmos resultados de antes. A regressão C/Q/M continua idêntica.

```json
"multiBand": {
  "enabled": true,
  "attenuationModel": "g652d",
  "waterPeakDbPerKm": 0.0,
  "noiseFigureDb": { "S": 7.0, "C": 5.0, "L": 6.0 },
  "dispersionSlopePsNm2Km": 0.057,
  "gammaScalesWithFrequency": true,
  "isrs": true,
  "ramanGainSlopePerWKmTHz": 0.028
}
```

## O que muda para cada circuito (frequência central f)

| Grandeza | Sem o bloco | Com o bloco |
|---|---|---|
| Perda α | `fiberLoss` | Curva G.652.D de Ferrari et al. (2020), a mesma do QCh: 0,195 dB/km a 1500 nm, 0,185 a 1550 nm, 0,199 a 1600 nm. Com `constante`, usa `fiberLoss`. |
| Ganho dos amplificadores de linha e pré | α·L | α(f)·L + perda de ISRS |
| Figura de ruído | `noiseFigureOfOpticalAmplifier` | a da banda de f (`noiseFigureDb`); banda ausente usa a global |
| Energia do fóton no ASE | h·`amplificationFrequency` | h·f |
| γ | `fiberNonlinearity` | γ·f/f_ref |
| β2 | de D em f_ref | de D(λ) = D + S·(λ − λ_ref), em f |

O f_ref (λ_ref) é o `centerFrequency`. O NLI continua no modelo GN fechado escolhido (`physicalLayerModel`), agora com α, γ e β2 do canal em teste.

## ISRS (aproximação triangular)

Num span com potência total P e canais de f_min a f_max (B = f_max − f_min), o canal em f chega ao fim do span com a fração ρ(f) da potência que teria sem ISRS. Fórmula de Zirngibl (1998) e Semrau et al. (2018):

```
ρ(f) = P·C_r·L_eff·B · exp(−P·C_r·L_eff·(f − f_min)) / (1 − exp(−P·C_r·L_eff·B))
```

- C_r = 0,028 /(W·km·THz) para SSMF.
- A média de ρ na banda é 1: o ISRS só move energia entre os canais.
- A inclinação entre as bordas é 4,34·P·C_r·L_eff·B dB.
- O amplificador do span repõe a potência de cada canal (ganho equalizado), então o ISRS entra como uma perda extra de −10·log10 ρ(f) no ASE daquele span.
- P e a banda são os circuitos que estão no enlace no momento da avaliação, então a inclinação acompanha a carga.
- Limitação: a mudança do NLI causada pelo ISRS (o modelo GGN de Semrau et al.) não é modelada.

## Verificação (VMB-13, `MultiBandClassicalQoTVerification`)

| Item | Verifica |
|---|---|
| 13a | Sem o bloco ou com `enabled: false`, não há modelo e os resultados não mudam. |
| 13b | Em f_ref, com atenuação `constante`, os parâmetros são os do arquivo. |
| 13c | Perda G.652.D, NF por banda, γ ∝ f e \|β2\| crescendo com λ. |
| 13d | A média de ρ é 1 e a inclinação é 4,34·P·C_r·L_eff·B. |
| 13e | A inclinação confere com as equações de Raman acopladas usando o ganho Raman medido do GNPy (`verificacao/QKD_reference/ISRS_GNPy`). |

Resultado do VMB-13e (80 km, 0,2 dB/km):

| Cenário | P total | GNPy (eqs. acopladas) | Triangular | Diferença |
|---|---|---|---|---|
| C, 4,8 THz | 19,8 dBm | 1,35 dB | 1,17 dB | 13 % |
| C+L, 12 THz | 23,8 dBm | 7,81 dB | 7,38 dB | 5 % |
| C+L, 12 THz, +3 dB | 26,8 dBm | 15,70 dB | 14,73 dB | 6 % |

## Como montar um caso C+L

- `physicalLayer`: `centerFrequency` = 1,906E14 e `amplificationFrequency` = 1,906E14.
- `network`: `slots` = 992 em todos os enlaces (184,4–196,8 THz).
- `qkdPhysicalLayer.json` → `multiband.classicalBandStartThz` = 184,4. A regra geral é centerFrequency − slots × largura do slot / 2.
- Aumente a carga na proporção dos slots (×3,1) para comparar com a banda C.

Exemplos em `simulations/QKD_Multibanda_Parte49` (grupos B e C).

## Referências

- M. Zirngibl, "Analytical model of Raman gain effects in massive wavelength division multiplexed transmission systems", Electron. Lett. 34(8), 1998.
- D. Semrau, R. I. Killey, P. Bayvel, "A closed-form approximation of the Gaussian noise model in the presence of inter-channel stimulated Raman scattering", JLT 36(14), 2018.
- A. Ferrari et al., "Assessment on the achievable throughput of multi-band ITU-T G.652.D fiber transmission systems", JLT 38(16), 2020.
- A. Ferrari et al., "GNPy: an open source application for physical layer aware open optical networks", JOCN 12(6), 2020.

# QKD_Multibanda — primeiros casos multibanda (Parte 43)

Casos de partida para comparar as arquiteturas e os modelos de espalhamento Raman (SpRS). Eles foram feitos a partir do
`QKD_Test10/Q100_R100` (mesmos algoritmos, tráfego por par, demanda de chave e PICh) e mudam só a topologia, a fibra e
o grupo `multiband` do `qkdPhysicalLayer.json`.

## Topologias

| Pasta | Topologia | Enlaces | Taxa de chegada por gerador |
|---|---|---|---|
| `Linha` | Linha (2 nós, 1 enlace) | 1-2: 50 km | 25 (como no Test10) |
| `Losango` | Losango (5 nós, 6 enlaces) | 70–100 km | 10 (mais pares por enlace) |

QTND = 25 km em todos. A carga é só um ponto de partida: ajuste na aba Tráfego.

## Casos (iguais nas duas topologias)

| Caso | Fibra clássica | QCh | Ruído no QCh |
|---|---|---|---|
| `SSMF_MF` | monomodo (núcleo 0) | fibra escura (núcleo virtual 1) | só p_dark |
| `SSMF_MB_Raman-nenhum` | monomodo (núcleo 0) | banda E, 1381 nm, na mesma fibra (núcleo virtual 1) | SpRS desprezado |
| `SSMF_MB_Raman-artigo` | idem | idem | SpRS, convenção de Mehdizadeh/Dibaj |
| `SSMF_MB_Raman-fisico` | idem | idem | SpRS, forma física |
| `MCF7_MF` | MCF de 7 núcleos (0–6) | fibra escura (núcleo virtual 7) | só p_dark |
| `MCF7_MBSDM_Raman-nenhum` | MCF de 7 núcleos | banda E do núcleo 1 (núcleo virtual 7) | SpRS desprezado |
| `MCF7_MBSDM_Raman-artigo` | idem | idem | SpRS do núcleo 1 + dos vizinhos 0, 2 e 6 |
| `MCF7_MBSDM_Raman-fisico` | idem | idem | idem, forma física |
| `MCF7_SDM_nucleoQKD_bandaC` | MCF de 7 núcleos, núcleo 1 reservado ao QKD | banda C, 1550 nm (modelo atual) | crosstalk intercore |

O último caso é o modelo de hoje (qualificação), para comparação. Nele a parte clássica tem só 6 núcleos; nos casos
MF e MB-SDM ela tem os 7.

## Como o multibanda entra no caso

No `network`, o núcleo QKD (`quantumCoreId`) é um núcleo **virtual**: representa a fibra escura (MF) ou a banda do QCh
(MB, MB-SDM). Ele aparece na lista de núcleos com `"adjacentCores": []` e `"qkdReserved": true`; os algoritmos
clássicos (qabne) não o usam.

No `qkdPhysicalLayer.json`:

```json
"multiband": {
  "enabled": true,
  "architecture": "MB",          // MF, MB ou MBSDM
  "ramanModel": "fisico",        // nenhum, artigo ou fisico
  "qchCenterNm": 1381.0,         // λ do QCh (centro da grade)
  "hostCoreId": 0,               // núcleo clássico que divide a fibra/núcleo com o QCh
  "intercoreRaman": true,        // MB-SDM: SpRS dos núcleos vizinhos ao hostCoreId
  "classicalBandStartThz": 191.3,// frequência do slot 1 da grade clássica (início da banda C)
  "attenuationModel": "g652d",   // perda G.652.D de 1260 a 1675 nm
  "waterPeakDbPerKm": 0.0,
  "temperatureK": 300.0
}
```

Tudo isso também pode ser editado no launcher (aba **Camada física QKD**, grupo **Multibanda (experimental)**).
Detalhes do modelo em `docs/multibanda/SIMULACAO_MULTIBANDA.md`.

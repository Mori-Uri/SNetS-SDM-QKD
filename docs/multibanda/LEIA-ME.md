# Multibanda — notas e calculadora de enlace

Notas de leitura dos artigos (Parte 42), usadas para montar o modelo do pacote `qkd.multiband` e o verificador
`MultiBandPhysicsVerification`:

| Arquivo | Conteúdo |
|---|---|
| `G1_qkd_multibanda.md` | QKD multibanda (Dibaj IWCIT 2024, JLT 2025; Mehdizadeh 2024): equações, parâmetros e alvos |
| `G2_raman_qkd.md` | SpRS em QKD (Eraerds 2010, Kawahara 2011, Cai 2020 intercore) |
| `G3_camada_fisica_mb.md` | Camada física clássica multibanda (Ferrari 2020, ISRS-GN), tabelas por banda |
| `G4_mb_eon_algoritmos.md` | RMLSA/RBMLSA em EON e SDM multibanda |
| `G5_secao_choque_raman.md`, `G5_rho_eraerds_1550_resampled.csv` | Seção de choque do SpRS extraída da Fig. 1 de Eraerds 2010 e o ajuste usado no código |

Os artigos ficam em `docs/artigos/` (fora do git). Onde baixar: `docs/artigos/LEITURAS_MULTIBANDA.txt`.

## Simulação de rede multibanda

Como ativar MF, MB e MB-SDM no simulador, os modelos de Raman e as hipóteses: `SIMULACAO_MULTIBANDA.md` (Parte 43).
Casos de exemplo: `simulations/QKD_Multibanda/`.

## Calculadora de enlace

Física de um trecho QKD fora do simulador de rede: MF (fibra escura), MB (comb C+L no mesmo núcleo) ou MB-SDM
(comb nos núcleos vizinhos, SpRS intercore de Cai 2020).

```bat
cd simulations
:: ponto único
java -cp SNetS-SDM-QKD-exp.jar qkd.multiband.LinkCalculator arq=mb lambda=1381 L=70
:: varredura de comprimento de onda e distância, gravando CSV
java -cp SNetS-SDM-QKD-exp.jar qkd.multiband.LinkCalculator arq=mb lambda=1260:1460:5 L=5,15,30 saida=varredura.csv
:: MB-SDM: 3 núcleos vizinhos com o comb, h = 1e-6 /km
java -cp SNetS-SDM-QKD-exp.jar qkd.multiband.LinkCalculator arq=mbsdm vizinhos=3 h=1e-6 lambda=1550 L=10:80:10
```

Opções: `preset=artigo|fisico`, `pot` (dBm por canal), `sentido=co|contra`, `mesmo=1` (MB-SDM com comb também no
núcleo do QCh), `mu`, `pdark`, `etad`, `ed`, `f`, `ts`, `gate`, `bqch`. A saída tem a perda, a potência de SpRS de
cada origem, p_b por origem, QBER e SKR.

Limites atuais:
- sem NLI/FWM (QCh na banda S perto do comb fica otimista);
- o crosstalk linear entre núcleos (já modelado no simulador) não entra na calculadora, só o SpRS intercore;
- um trecho por vez (sem amplificadores: o QCh passa por fora deles).

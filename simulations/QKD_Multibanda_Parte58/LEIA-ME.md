# QKD_Multibanda_Parte58: continuação dos casos multibanda

Casos para os "próximos testes" da análise dos casos multibanda (apresentação `Analise_QKD_Multibanda.pptx`). Partem
dos casos `QKD_Multibanda_Parte49`, com os mesmos algoritmos, demanda de chave, PICh e camada física dos testes de
multinúcleo. Mudam só os campos indicados em cada grupo. Foram gerados por `tools/casos/gerar_casos_parte58.py`.

Rode com o `SNetS-SDM-QKD.jar` da `main` (gerado pelo "Gerar JAR" do launcher).

| Grupo | Pergunta | Casos | Tamanho |
|---|---|---|---|
| D | Com bloqueio clássico baixo (~0,1 a 5 %), a ordem das arquiteturas e o custo na chave continuam os mesmos? | 4 por topologia | 20 000 × 5 × 5 |
| E | E a MB-SDM (MCF-7), com a mesma carga por núcleo da fibra comum e na faixa de bloqueio baixo? | 3 por topologia | 20 000 × 5 × 5 |
| F | No cenário do `QKD_Test9` (200 km, MCF-7), quanto a MB-SDM fica atrás ou à frente do núcleo QKD dedicado, de 25 a 150 km de QTND? | 8 | 100 000 × 8 × 10, igual ao `QKD_Test9` |
| G | Com trechos longos e C+L, a partir de que comprimento de trecho o Raman a 1381 nm custa mais que a perda a 1310 nm? | 8 | 20 000 × 5 × 5 |
| H | A fibra bidirecional e o ruído não linear (NLI) mudam a chave? | 3 | 20 000 × 5 × 5 |

O tamanho é requisições × pontos de carga × replicações. Para mudar, use a edição em lote do launcher (selecione a
pasta e mude a aba Simulação).

## D e E: cargas menores (`D_E_CargasMenores/{Linha,Losango}`)

As cargas dos casos da Parte 49 estavam acima da faixa usual de comparação: a Linha já começava com 18 % de bloqueio.
Aqui as cargas foram calibradas com simulações curtas para ficar entre ~0,1 % e 5 % de bloqueio clássico.

| Caso | Fibra | QCh | Cargas na Linha | Cargas no Losango |
|---|---|---|---|---|
| `D0_SSMF_MF` | comum | fibra escura | 165 a 225 E | 240 a 540 E |
| `D1_SSMF_MB_E1381_antiStokes-nenhuma` | comum | 1381 nm, Raman pelo modelo puro | 165 a 225 E | 240 a 540 E |
| `D2_SSMF_MB_E1381_antiStokes-medida` | comum | 1381 nm, correção anti-Stokes medida | 165 a 225 E | 240 a 540 E |
| `D3_SSMF_MB_O1310_antiStokes-medida` | comum | 1310 nm | 165 a 225 E | 240 a 540 E |
| `E1_MCF7_MF_carga7x` | 7 núcleos | fibra escura | 1155 a 1575 E | 1680 a 3780 E |
| `E2_MCF7_MBSDM_E1381_carga7x` | 7 núcleos | 1381 nm no núcleo 1 | 1155 a 1575 E | 1680 a 3780 E |
| `E3_MCF7_MBSDM_E1381_bloqueio-baixo` | 7 núcleos | 1381 nm no núcleo 1 | 700 a 1100 E | 1500 a 2500 E |

- **D0 a D3** repetem A0 a A3 da Parte 49 com cargas menores. Bloqueio medido na calibração (fibra escura, 1
  replicação):
  - Linha: 0,5 % em 180 E e 2,8 % em 210 E;
  - Losango: 0,3 % em 240 E e 2,7 % em 480 E.
- **E1 e E2** têm 7 vezes a carga do D, ou seja, a mesma carga por núcleo. O crosstalk entre núcleos limita a MCF-7
  antes do espectro, então o bloqueio deles fica acima de 5 % nas cargas maiores.
- **E3** fica na faixa de 0,1 a 5 % da própria MCF-7, estimada pelos resultados do A4 da Parte 49 (Linha: 0 em 600 E,
  1,6 % em 900 E, 7 % em 1200 E; Losango: 0 em 1200 E, 4 % em 2400 E).

## F: o cenário dos testes de multinúcleo (`F_CenarioMultinucleo_200km`)

O `QKD_Test9` usa a mesma camada física, o mesmo tráfego (300 a 2400 E), os mesmos algoritmos e o mesmo tamanho
destes casos. A diferença é onde fica o QCh:

- **`QKD_Test9/Q*_R100`:** núcleo 1 reservado ao QKD, na banda C (modelo dos estudos da qualificação);
- **`F_MF_QTND*`:** os 7 núcleos para o tráfego clássico e o QCh numa fibra escura (melhor caso para a chave);
- **`F_MBSDM_E1381_QTND*`:** os 7 núcleos para o tráfego clássico e o QCh a 1381 nm no núcleo 1, com o Raman do
  próprio núcleo e dos vizinhos.

QTND de 25, 50, 100 e 150 km, para comparar com `QKD_Test9/Q25_R100`, `Q50_R100`, `Q100_R100` e `Q150_R100`.

SKR de admissão na primeira carga, para ter uma ideia antes de rodar:

| QTND | Test9: núcleo dedicado, banda C | F_MF: fibra escura a 1381 nm | F_MBSDM: 1381 nm com Raman |
|---|---|---|---|
| 25 km | 93,9 | 58,4 | 57,0 |
| 50 km | 33,0 | 12,9 | 12,1 |
| 100 km | 4,1 | 0,63 | 0,55 |
| 150 km | 0,52 | 0,031 | 0,030 |

Valores em Mbit/s. Os do Test9 são resultados; os dos casos F vêm de um teste curto (800 requisições).

A perda maior da banda E (0,262 contra 0,185 dB/km) pesa cada vez mais com o trecho. Com 100 km ou mais, a SKR fica
abaixo da demanda de cada sessão (2,33 Mbit/s): espere bloqueio QKD alto ou muitos QChs por sessão. No Test9 isso só
acontece com 150 km (57 % de bloqueio QKD).

O que olhar:

- bloqueio clássico, que deve ser menor sem o núcleo reservado;
- SKR por trecho: banda C sem ruído no Test9; banda E com perda maior e Raman na MB-SDM;
- pares de Q-TRx e custo.

## G: trechos longos com C+L (`G_TrechosLongos_CL_200km`)

Enlace único de 200 km, fibra comum, grade C+L (992 slots) com QoT por banda e ISRS (como o B4 da Parte 49) e QCh por
enlace. QTND de 25, 50, 75 e 100 km, a 1381 nm (`G_E1381_*`) e a 1310 nm (`G_O1310_*`).

Na Parte 49, com trechos de 25 km, 1310 nm perdia para 1381 nm: a perda maior pesava mais que o Raman. No teste curto
(800 requisições, só a primeira carga), 1310 nm continuou atrás em todos os trechos: 40,9 contra 55,9 Mbit/s com
25 km e 0,15 contra 0,41 com 100 km. As cargas completas, com mais canais acesos e mais Raman, dirão se a diferença
fecha. Com o trecho
mais longo, o Raman acumulado a 1381 nm cresce, e a diferença de perda a 1310 nm também. As curvas de SKR × QTND das
duas bandas mostram se e onde elas se cruzam.

As cargas destes casos não foram calibradas: o C+L com QoT por banda é lento demais para a calibração curta. Elas
equivalem a 120 a 180 E na banda C (372 a 558 E no C+L), abaixo das da Linha de 50 km, porque o enlace de 200 km usa
modulações mais robustas, com mais slots por circuito. Estes são os casos mais lentos do conjunto.

## H: fibra bidirecional e NLI (`H_Bidirecional_NLI/Losango`)

Losango, cargas do D, grade C, QCh a 1381 nm com a correção medida, como o D2. Os dois efeitos estavam desligados em
todos os casos anteriores.

| Caso | O que muda em relação ao D2 |
|---|---|
| `H1_E1381_bidirecional` | `bidirectionalFiber: true`: os dois sentidos do enlace na mesma fibra (soma o SpRS que volta, *backward*) |
| `H2_E1381_NLI-gn` | `nliModel: gn`: ruído não linear dos canais clássicos no QCh |
| `H3_E1381_bidirecional_NLI-gn` | os dois juntos |

Pela calculadora de enlace (trecho de 25 km), o NLI no QCh é desprezível nas bandas O e E. A probabilidade de ruído por
pulso vinda do NLI fica entre 10⁻¹¹ e 10⁻⁹, contra 10⁻³ a 10⁻² do Raman, mesmo a 1490 nm. Por isso não há casos com o
QCh na banda S: lá o Raman sozinho já zera a SKR. O H2 serve para confirmar isso na rede.

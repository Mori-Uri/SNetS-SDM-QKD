# Validação da física multibanda contra a literatura

Este documento lista todas as conferências do simulador com artigos da área, o que cada uma compara e o resultado.
Cada uma é um verificador que roda com `java -cp <jar> qkd.verification.<Classe>`. Os valores lidos de figuras
foram digitalizados à mão, com precisão de cerca de 2 % da escala.

## Situação (Parte 46)

| Item | Fonte | O que confere | Resultado |
|---|---|---|---|
| VMB-1 | Mehdizadeh 2024, Dibaj 2025, Ferrari 2020 | atenuação G.652.D de 1260 a 1625 nm | OK |
| VMB-2 | Eraerds 2010, Fig. 1 | seção de choque do SpRS de −5 a +11 THz | OK, dentro de ±20 % |
| VMB-3 | Mehdizadeh 2024 | SKR e QBER nas bandas O/E com o comb C+L | OK, 6 a 13 % |
| VMB-4 | Dibaj IWCIT 2024 | SKR a 1380 nm (5, 15 e 30 km) | OK, 4 a 7 % |
| VMB-5 | Dibaj JLT 2025 | SKR a 1360 nm (100, 150 e 200 km) | OK; 1 conflito pendente |
| VMB-6 | Cai 2020 | SpRS intercore (MCF), forward e backward | OK, 0,1 a 0,2 % |
| VMB-7/8 | — | consistência interna (MF = fibra escura, limites) | OK |
| VMB-9 | — | simulador = calculadora de enlace | OK |
| VMB-10a/b | Dibaj IWCIT 2024, Figs. 3 e 5 | SKR por Q-TRx e QTND ótimo | OK; ganho de +40 % do MB pendente |
| VMB-10c | Dibaj IWCIT 2024, Fig. 7 | Q-TRx de MB × MF em 3 redes | OK, 30 a 32 % (artigo: 28 a 31 %) |
| VMB-11a–e | Poggiolini 2014 | integral do modelo GN | OK, 0,13 % |
| **VMB-12a** | Peters 2009, Fig. 1 | FWM de 2 canais CW de 0 dBm (200 e 400 GHz; 1 e 25 km) | OK, ±1 dB |
| **VMB-12b** | Peters 2009 / Inoue 1992 | NLI do simulador × FWM CW com canais estreitos | OK, ±2,4 dB |
| **VMB-12c** | Peters 2009, Fig. 2 | espectros de SpRS **medidos** (5,5, 21,5 e 41,3 km) | OK; modelo 13 a 33 % abaixo |
| **VMB-12d** | Mehdizadeh 2024, Fig. 3 e GNPy 2.14.2 | origem do "NLI" do artigo | OK, ±1 dB |
| **VMB-12e** | Mehdizadeh 2024, Fig. 5(b) | QBER de 1440 a 1522 nm (5, 30 e 70 km) | OK, ±0,026; início do ruído na banda E pendente |

## O que a Parte 46 esclareceu

1. **O NLI que Mehdizadeh mostra não é ruído no QCh.**
   - É o NLI que o GNPy calcula para o QCh tratado como canal de −80 dBm: SPM + XPM sobre o próprio QCh,
     proporcional à potência dele.
   - Reproduzimos a figura com o próprio GNPy e com a fórmula fechada (±1 dB).
   - Isso dá cerca de 1e-8 fótons por janela, desprezível.
   - O GNPy não calcula os produtos de FWM dos canais clássicos que caem no QCh. O modelo GN do simulador calcula,
     e confere com a fórmula clássica de FWM (Peters, Inoue) dentro de 2,4 dB.
2. **Os artigos do mesmo grupo usam convenções diferentes para o SpRS.**
   - Dibaj JLT 2025 (eq. 6) usa a fração 0,5 dos fótons de SpRS, que é o modelo `artigo` do simulador.
   - Mehdizadeh 2024 conta todos os fótons (fração 1). Com fração 1 e o comprimento L·e^(−α_c·L), a QBER da
     Fig. 5(b) bate na banda S.
   - O modelo `fisico` (fração 1 com a forma exata) fica a até 0,046 de QBER, porque a forma exata difere quando
     α_q > α_c.
3. **A seção de choque do simulador está um pouco baixa perto da bomba.**
   - Comparada com a medida de Peters (fibra SMF-28e), fica 13 a 33 % abaixo. O ajuste foi feito com os dados de
     Eraerds, medidos em outra fibra.
   - A dependência com a distância confere.

## Pendentes

| Item | Situação |
|---|---|
| VMB-12e | De 1392 a 1450 nm, a 14–20 THz da borda do comb, Mehdizadeh tem 2 a 5 vezes mais SpRS que o nosso perfil Raman com o fator de Bose-Einstein. A seção de choque medida (Eraerds) só vai de 1510 a 1650 nm, e o artigo não diz como a estendeu. |
| VMB-10b | Ganho de +40 % do MB de Dibaj IWCIT 2024. O artigo não define a contagem de Q-TRx para divisões não inteiras. |
| VMB-5 | Dibaj 2025 diz que o ruído é desprezível na banda O a 150 km. O modelo dá p_SpRS de 1,2e-6. |

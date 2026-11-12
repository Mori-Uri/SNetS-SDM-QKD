# Etapa 5 — Relatorio reproducivel de verificacoes cientificas

- JAR SHA-256: `ce821078c0057ba07595ed3c69a25382854d090c55d6e2861864aeaa69281448`
- Java: `openjdk version "21.0.12.1" 2026-08-18`
- Escopo: completo; total 37 verificacoes.
- **PASS: 33; PENDENTE: 4; FAIL: 0; TIMEOUT: 0.**

## Interpretacao

PASS significa que os testes programados passaram; **nao significa validacao experimental**.
PENDENTE significa que as comparacoes com a literatura NAO foram reproduzidas quantitativamente,
mesmo que a classe Java tenha retornado codigo 0.
O arquivo `jurandir_vs_oraculo_python.csv` compara equacoes da tese, nao pixels de graficos publicados.
O arquivo `dibaj2024_fig5_quantitativo.csv` calcula o ganho com as convencoes explicitadas; nao corrige artigos.
O arquivo `mehdizadeh2024_qber_divergencias.csv` inclui valores aproximados lidos de figuras pela verificacao original.
O arquivo `johannisson_eq11_15_16.csv` compara integrais em retangulos com quadratura independente,
nao experimentos SSFM; a Equacao 16 e uma aproximacao e pode ter diferenca fisica legitima.

## Pendencias detectadas

- QKDStage5MultibandQuantitativeVerification: [PENDENTE] Dibaj 2024 MB 1380nm: model optimum 20km, gain 74.6975%, article +40%; discrepancy 34.6975 percentage points. Original gain denominator/source data not fully reproducible.
- MultiBandIwcitVerification: [PENDENTE] VMB-10b MB: ganho de +40 % do artigo nao reproduzido por nenhuma convencao de contagem de saltos (ver G1_qkd_multibanda.md, 3.7).
- MultiBandLiteratureVerification: [PENDENTE] VMB-12e inicio do ruido no fim da banda E (QBER modelo x artigo): 1410 nm/30 km: 0.024 x 0.057; 1450 nm/30 km: 0.099 x 0.125; 1392 nm/70 km: 0.030 x 0.056; 1410 nm/70 km: 0.046 x 0.140; 1450 nm/70 km: 0.202 x 0.245; o artigo tem 2 a 5 vezes mais SpRS a 14-20 THz da borda do comb (1392-1450 nm), fora da janela medida da secao de choque; o artigo nao diz como a estendeu.
- MultiBandPhysicsVerification: [PENDENTE] VMB-5 com o SpRS do comb C+L a 0 dBm o modelo da 0.51 kbit/s a 150 km (p_SpRS 1.2e-06); o artigo indica ruido desprezivel no O-band -> conflito a discutir

## Reprodutibilidade

Entradas, parametros, codigo compilado e referencias de medicao precisam ser preservados.
As referencias numericas extraidas de artigos por leitura visual nao devem ser consideradas
mais precisas que a resolucao da figura original.
Falta a validacao por instrumentacao experimental ou SSFM independente.

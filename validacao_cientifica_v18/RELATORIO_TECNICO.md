# Auditoria científica — SNetS-SDM-QKD v18

**Base:** SNetS-SDM-QKD v17. Fontes: Jurandir C. Lacerda Júnior, tese (2024), Seção 3.1.2 (Figuras 21 e 22, Tabela 6), Seção 3.1.3 (Equações 3.33–3.38 e Figura 23); Johannisson & Agrell (2014), Equações 11, 15 e 16.

## 1. Reprodução das Figuras 21(b) e 22

O script `reproduzir_modelos.py` avalia **de forma independente** as equações da tese: 15 canais PM-QPSK, largura de 32 GHz, separação de 33,6 GHz, gamma de 1,3 W^-1 km^-1, atenuação de potência de 0,2 dB/km (campo 0,1 dB/km), beta2 de -21,3 ps²/km e spans de 100 km. Phi=1.0. A NLI é normalizada por P^3 conforme a Equação 3.32.

| Spans | GN sem correção (dB 1/W²) | EGN aproximado (dB 1/W²) |
|---:|---:|---:|
| 1 | 30,145823 | 28,086034 |
| 2 | 33,156123 | 31,096334 |
| 5 | 37,135523 | 35,075734 |
| 10 | 40,145823 | 38,086034 |
| 20 | 43,156123 | 41,096334 |
| 50 | 47,135523 | 45,075734 |

A aproximação de pontos visuais das Figuras 21(b) e 22 apresenta diferença máxima ~0,15 dB, **mas esses pontos são apenas leituras arredondadas dos gráficos**. Não foram obtidos dados originais dos autores e não se reivindica reprodução exata da Figura 21(a), que apresenta um modelo externo mais rigoroso.

### Comparação com o núcleo Java do SNetS

A verificação `QKDThesisSNetSPhysicalLayerVerification.java` cria uma rede A→B e 15 DChs em grade de 1,6 GHz, cada um com 20 slots (32 GHz), separados por 21 slots (33,6 GHz). Chama o método **real** `network.PhysicalLayer.getGnli2()` da v17.

* EGN da referência independente, um span: **28,086034039 dB**.
* EGN do motor do SNetS, um span: **28,086036330 dB**.
* Diferença: aproximadamente **0,000002291 dB**.
* Extrapolação de 50 spans usando acumulação incoerente da tese: **45,075736373 dB**.

A execução utiliza as classes compiladas incrementalmente da v17 em conjunto com as classes de referência precompiladas da v1. Não substitui `mvn clean package` em um ambiente com dependências completas, nem uma regressão de tráfego dinâmico.

**Observação de consistência na Tabela 6:** usando diretamente seus parâmetros arredondados, a Equação 3.25 resulta em ξ ≈ 1,5926×10²³, enquanto a tabela apresenta 1,67×10²³. Essa pequena discrepância de parâmetro deve ser conferida na fonte original; os cálculos com parâmetros declarados reproduzem o gráfico.

## 2. Johannisson — Equações 11, 15 e 16

A integral retangular das Eqs. 11 e 15 foi avaliada com `scipy.integrate.quad`. O termo XCI também foi conferido por integração dupla numérica independente, com erro relativo abaixo de 1×10^-14 para 12,5 e 25 GHz.

| B (GHz) | SCI Eq.16 / SCI Eq.11 | XCI Eq.16 / XCI Eq.11 |
|---:|---:|---:|
| 12,5 | -0,78185 | 1,19900 |
| 25,0 | 0,82641 | 1,10011 |
| 28,0 | 0,87848 | 1,09129 |
| 32,0 | 0,91923 | 1,08298 |
| 50,0 | 0,97762 | 1,07702 |

Na Eq. 16, SCI para 12,5 GHz é **negativa**, evidenciando a inviabilidade física da aproximação assintótica no limite estreito. A correção da v17, baseada no núcleo integral positivo, é sustentada pela verificação independente. O tratamento XCI ainda pode ter erro de cerca de 20% em 12,5 GHz e 10% em 25 GHz no exemplo de Δf=33,6 GHz, já que a v17 continua a empregar a expressão XCI logarítmica.

**Nota:** a comparação Johannisson refere-se a modelos de PSD por polarização com gamma efetivo Manakov aplicado conforme convenções da publicação. Os valores absolutos da tabela numérica usam potência de 1 W por polarização apenas como referência de cálculo; as razões independem da escala comum de potência.

## 3. Crosstalk de Lobato — Equações 3.33–3.38

Verificação de `network.Crosstalk.java`:

| Item da tese | Código encontrado | Resultado |
|---|---|---|
| Eq. 3.33, soma por enlace | `calculateCrosstalk` | Conforme, com enlaces dirigidos |
| Eq. 3.34, Pxt/Pm | `calculateCrosstalkInLink` | Conforme |
| Eq. 3.35, somatório ISO·Pn·h·L | `calculatePxt` | Conforme, incluindo L km→m |
| Eq. 3.36, 2k²R/(βΛ) | `calculateH` | Conforme |
| Eq. 3.37, modelo de correlação | Não é o modelo escolhido | Não implementada neste caminho; não obrigatória para a Eq.3.36 |
| Eq. 3.38, slots sobrepostos/nº de slots do interferente | `numberOfOverlapping`/`calculatePxt` | Conforme no fluxo clássico |

O motor real `Crosstalk.calculateCrosstalk` foi testado com dois circuitos adjacentes, sendo 2 slots de sinal sobrepostos num interferente de 5 slots, enlace de 100 km, potência 1 mW, k=10^-4 m^-1, R=0,05 m, β=5,9×10^6 m^-1 e pitch 41 μm. A fração ISO foi 0,4 e o **XT retornado -67,815758770 dB**, coincidente com a expressão analítica.

**Importante, correção do diagnóstico preliminar:** os slots de guarda não estão no intervalo `Circuit.getSpectrumAssigned()` na operação usual do SNetS. São registrados à parte em `Spectrum.addGuardBands()`. Logo, o cálculo clássico normal **não inclui** guarda indevidamente. A hipótese anterior de que incluía guarda não se confirmou. O teste agora verifica explicitamente essa separação real.

### Cobertura ainda pendente

* O cálculo legado de XT em vários métodos usa `getSpectrumAssigned()` global em vez de `getSpectrumAssignedByLink(link)`. Isso é equivalente no cenário clássico transparente uniforme, mas deve ser verificado para faixas por enlace ou tipos de circuito com espectro variável.
* A contribuição de PICh sobre DCh no módulo `PichClassicalCrosstalk` usa um fator de acoplamento limitado `hL/(1+hL)` e intervalos de comprimento parcial, em vez da aproximação linear `hL` da Eq. 3.35; são hipóteses físicas distintas e exigem validação específica.
* `tipoEstimativa` no construtor `Crosstalk` é definido como 1 (Lobato). Métodos Klinkowski alternativos não foram validados pelas fórmulas da tese; não se deve afirmar equivalência para esses ramos.

## 4. Estilo de código

Foi usada a **apresentação visual** do algoritmo `gprmcsa/integrated/KSPXT_v4.java` como referência, sobretudo linhas em branco entre blocos de decisão, preparação de dados, laços e retornos.

O script `scripts/formatar_qkd_estilo_kspxt.py` é **apenas cosmético**. Varre 143 arquivos `qkd` e quatro classes clássicas recentes; na versão entregue, 129 arquivos foram alterados pela inserção de 2620 linhas vazias. Verificação automática assegurou que todas as linhas originais não vazias permanecem byte a byte idênticas e na ordem original, e que a transformação é idempotente. O original `KSPXT_v4.java` e os algoritmos clássicos anteriores não foram reformatados.

## 5. Validações executadas e limitações

* Reprodução em Python: passou, incluindo integrais de Johannisson e cenário analítico de XT.
* Três verificações Java novas: passaram, incluindo execução dos motores reais GN e XT do SNetS.
* Oito verificações Java anteriores: passaram (11/11 no total).
* Compilação incremental Java 8 dos testes e classes físicas necessárias: passou.
* Compilação integral `mvn clean package`: **não executada** (Maven/Gson ausentes neste ambiente); tentativa de compilar todos os fontes do pacote `qkd` falhou exclusivamente primeiro por falta de dependências Gson e por isso não é considerada aprovada.
* Regressão dinâmica integral das topologias e comparação com dados originais de Poggiolini & Jiang (Figura 21(a)): **pendentes**.

**Conclusão:** caso de referência Habibi está numericamente reprodutível no SNetS legado; modelo Lobato corresponde às equações da tese no cenário controlado; Johannisson v17 melhora o SCI estreito, mas a expressão XCI logarítmica ainda pode divergir da integral mais precisa. O estilo do código foi atualizado sem alterar a física dos modelos.

# Etapa 5 — Verificação matemática, comparação com referências e campanhas reprodutíveis

## Escopo implementado

1. `qkd.verification.QKDStage5QuantitativeSweepVerification`: **84 comparações numéricas** entre o kernel de produção de Johannisson (2014), Eqs. 11/15, e uma quadratura bidimensional Simpson independente; SCI também é integrada independentemente. Relata separadamente o erro da aproximação logarítmica da Eq. 16. Inclui canais de 12,5, 25, 32 e 50 GHz e distâncias de 0 a 200 GHz. As comparações usam coeficientes físicos e unidades explicitados na fonte.
2. `qkd.verification.QKDStage5MultibandQuantitativeVerification`: calcula numericamente **16 configurações MB/MF de Dibaj 2024**, registra a SKR por trecho, a contagem de Q-TRx pela convenção adotada, a QTND ótima e o ganho em relação ao enlace sem nós confiáveis. O ganho MB calculado na parametrização testada foi **74,697%**, não os **40%** relatados na figura; a diferença de **34,697 pontos percentuais** permanece `PENDENTE` e é exportada em CSV. O MF resulta em ~19,204%, comparado com 15% no artigo.
3. `scripts/validar_cientificamente_etapa5.py verificar`: executa verificadores da literatura (incluindo Jurandir 2024, Johannisson 2014, Poggiolini/Jiang 2017, Dibaj 2024 e Mehdizadeh 2024) e regressões física/QKD. Salva logs individuais, SHA-256 do JAR, parâmetros do processo, resultados e **pendências explícitas**. A execução com código zero que contenha `[PENDENTE]` fica `PENDENTE`, não `PASS`.
4. Reprodução independente em Python das Eqs. 3.23–3.32 da tese de Jurandir, Tabela 6. O script compara com a curva GN/EGN aproximada emitida pelo verificador Java, para 1, 2, 5, 10, 20 e 50 spans.
5. `scripts/validar_cientificamente_etapa5.py campanha`: executa casos reais de simulação **sem sobrescrever os dados originais**, repetindo com sementes registradas, comparando `rate`/`stock` com a mesma realização de tráfego, registrando as métricas por ponto de carga e IC95 calculado sobre as réplicas independentes. Suporta múltiplas topologias.

## Diferenciar categorias de evidência

| Camada | O que é verificado | O que **não** demonstra |
|---|---|---|
| Regressão unitária | O algoritmo satisfaz os contratos de teste conhecidos | Acurácia científica geral |
| Referência analítica | O cálculo coincide com a expressão matemática adotada | Validação SSFM ou experimental |
| Resultados de artigo | A estimativa coincide com dado publicado, **sob as mesmas hipóteses** | Acurácia fora do domínio da referência |
| Tráfego dinâmico | Repetibilidade e estatísticas do sistema sob o cenário informado | Generalização para topologias não avaliadas |

**Atenção:** o EGN da tese de Jurandir é uma **aproximação**, não o EGN integral completo. Não é cientificamente legítimo comparar as duas versões com tolerância numérica igual a zero. A Fig. 21 da tese relata diferenças de até aproximadamente 1,5 dB após 50 spans entre as versões EGN utilizadas. Os resultados extraídos por leitura visual de figuras são aproximados, não dados experimentais brutos.

## Executar no Windows 11

No diretório raiz do projeto (Java 8+ e Python 3.9+):

```powershell
python .\scripts\validar_cientificamente_etapa5.py verificar --modo rapido --saida .\resultados_etapa5\verificacao_rapida
python .\scripts\validar_cientificamente_etapa5.py verificar --modo completo --saida .\resultados_etapa5\verificacao_completa
```

A opção `--strict-pending` retorna código não zero se algum teste registrar `[PENDENTE]` (p. ex., a diferença do +40% MB de Dibaj ou a extensão da seção de choque Raman na banda E). A política padrão não trata pendências como falha de execução, mas **sempre** as exibe separadamente do PASS.

### Campanha dinâmica de múltiplas topologias

```powershell
python .\scripts\validar_cientificamente_etapa5.py campanha `
  --cenario .\simulations\QKD_Multibanda_Parte49\Linha\A0_SSMF_MF `
  --cenario .\simulations\QKD_Multibanda_Parte49\Losango\A4_MCF7_MBSDM_E1381_antiStokes-medida `
  --repeticoes 10 --pedidos 20000 --qkd-prob 0.5 --modo-chaves both `
  --saida .\resultados_etapa5\campanha_10_sementes
```

O comando mantém as demais configurações de cada caso e força `threads=1`, `replications=1`, sementes explícitas e `saveState.enabled=false`. Cada repetição é executada em diretório de saída novo. Os modos `rate` e `stock` recebem **as mesmas sementes**, permitindo comparações pareadas (sem afirmar que os resultados sejam iguais). O programa não pressupõe equivalência de algoritmos de distribuição de chaves.

Resultados: `metricas_replicacoes.csv`, `resumo_ic95.csv` (média, desvio-padrão amostral e IC95 de Student, agrupados por ponto de carga), `diferencas_pareadas_stock_rate_ic95.csv` (diferenças stock-rate comparadas por semente), `manifesto_campanha.json`, logs e cópias das configurações. O argumento `--qkd-prob` substitui a probabilidade de proteção QKD, que em alguns cenários de exemplo é 0. Sem requisicoes QKD protegidas não há base científica para comparar políticas de chaves.

### Como evitar conclusões incorretas

- Não combinar pontos de carga diferentes como se fossem replicações independentes.
- Não escolher o melhor caso por uma única semente; preferir ao menos 10 réplicas independentes e intervalos de confiança.
- Para diferenças **entre rate e stock**, consultar `diferencas_pareadas_stock_rate_ic95.csv` (diferenças **por semente emparelhada**) e registrar métricas de geração, descarte e renovações não atendidas.
- Não mudar comprimento de onda, multiplicadores de ruído ou contagem de Q-TRx apenas para ajustar um gráfico de literatura; toda calibração precisa de evidência independente.
- Em papel científico, reportar o Git/JAR SHA, a configuração completa, o modelo Habibi/Johannisson e flags, distribuição temporal e parâmetros de ruído.
- Os arquivos de entrada referenciados por caminhos relativos à pasta do caso devem estar disponíveis no projeto original. Apenas os seis arquivos de configuração principais são clonados; se houver arquivos suplementares, copiá-los de forma controlada e registrar seus hashes.

## Regressões funcionais descobertas em campanhas reais da Etapa 5

A primeira campanha de tráfego dinâmico foi bloqueada por dois erros **não cobertos pela suíte anterior**; ambos foram corrigidos sem ocultar ruídos físicos inválidos:

- `network.PhysicalLayer`: filtragem dos canais candidatos pelo núcleo real da vítima para não atribuir uma conexão de outro núcleo como interferente GN do mesmo núcleo (efeito físico do outro núcleo pertence ao modelo XT). Também corrigido o teste de sobreposição do fallback Habibi: são comparadas as **duas semilarguras espectrais** em vez de usar `0,999*Bcut`; canais adjacentes 50/12,5 GHz sem sobreposição deixam de ser rejeitados. Sobreposição real continua sendo erro explícito.
- `simulationControl.SimulationFileManager`: serialização JSON da `network` preserva parâmetros opcionais finitos e omite valores **não configurados** representados internamente por `NaN`. Antes, uma simulação podia terminar com sucesso e falhar somente no salvamento, porque Gson rejeita `NaN` no JSON. A saída permanece JSON válido, sem ativar `serializeSpecialFloatingPointValues()`.
- `QKDStage5SimulationIntegrityVerification` verifica a exportação e a distinção intercore/mesmo núcleo, além de um caso com larguras 50/12,5 GHz adjacentes. Uma campanha smoke de 5 nós, 2 sementes e 2 modos (`rate` e `stock`) ultrapassou a execução de eventos e gravou métricas em disco.

Essas correções foram motivadas por **execução end-to-end**, não por necessidade de adequar à força resultados publicados.

## Pendências científicas que os arquivos existentes ainda não resolvem

1. **Dibaj 2024 MB**: a melhora relativa de 40% citada no trabalho não foi reproduzida sob nenhuma das convenções de contagem de saltos explicitadas no verificador `MultiBandIwcitVerification`. O novo cálculo direto pela convenção inteira entrega ganho de 74,697% com QTND=20 km, frente ao 40% do trabalho; diferença de 34,697 pontos percentuais. O modelo MF com denominador fracionário entrega ~19,204% em QTND=25 km frente a 15% da referência, diferença menor. Não se deve confundir contagem fracionária analítica com número discreto de transmissores físicos de um simulador de redes. A QTND ótima de 20 km para MB e 25 km para MF, por si só, não valida o percentual de melhora.
2. **Mehdizadeh 2024 E-band**: o início da elevação de QBER em aproximadamente 1392–1450 nm depende de seção de choque Raman numa faixa extrapolada, sem parametrização completa no artigo. A divergência de 2–5 vezes em determinados cálculos de ruído foi previamente documentada e permanece **pendente**, não corrigida ad hoc.
3. **Poggiolini 2017**: o modelo `Habibi integral` continua híbrido com integral GN; não é reprodução do EGN completo com momentos de ordem superior.
4. **MultiBandEdFA**: representa uma saturação paramétrica configurável, não equações físicas completas de Erbium; validação experimental de ganho/NF versus comprimento de onda requer dados de fabricante/bancada.
5. **QKD key inventory**: o modelo de agregação para altíssimo número de renovações ainda não resolve o instante exato do esgotamento em todas as condições.

Este estágio acrescenta **infraestrutura para uma validação científica reproduzível**. Não substitui medições experimentais, SSFM independente ou os dados originais não fornecidos pelos artigos.

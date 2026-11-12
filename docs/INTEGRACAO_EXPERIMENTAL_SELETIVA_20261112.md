# Integração seletiva do experimental na base SNetS-SDM-QKD Etapa 5

**Escopo:** desempenho e rastreabilidade sem substituir modelos físicos/QKD, com Java 8 e retrocompatibilidade das simulações. **Origem:** Etapa 5 reconstruída com as Etapas 1 a 5 sobre o snapshot `SNetS-SDM-QKD-v1-atualizado-20261111.zip`. **Referência a comparar:** `SNetS-SDM-QKD-experimental(2).zip`.

## Decisões por subsistema

| Subsistema | Decisão | Justificativa |
| --- | --- | --- |
| Lista de requisições (`TrackedRequestList`) | **Integrada**, reescrita/adaptada | Detecta `add`, `remove`, `set`, `replaceAll`, `removeIf`, `listIterator` e `subList`; mantém cache de taxa fiel à ordem original. Não introduz atalhos físicos globais. |
| Requisições (`RequestForConnection`) | **Integrada** geração de invalidação | Alterações de taxa em requisições já associadas a listas rastreadas invalidam o agregado. Requisições sem circuito não geram invalidações globais. |
| Taxa do circuito (`Circuit`) | **Integrado** cache opcional | Chave pela identidade da lista, contador de mutações e época global de taxas; listas externas recebem cálculo direto; cache transitório e revalidável bit a bit. |
| Consumo individual de transponders (`EnergyConsumption`) | **Integrado** cache opcional | Confronta rota, modulação, M, largura dos slots e extremos da banda. Mantém a ordem de soma dos consumos. |
| Crosstalk | **Mantido da Etapa 5** | `Crosstalk.overlappingSlots` já realiza interseção O(1) por extremos, com semântica explícita de slots válidos. Não importar implementação anterior que usa as listas de slots. |
| `SpectrumUtilization` | **Mantido da Etapa 5** | A versão atual distingue ocupação clássica, QKD reservada e núcleos desativados e contabiliza slots; o código experimental reestrutura as métricas em memória sem validação suficiente da equivalência integral. |
| Energia total de `Mesh` | **Mantida a recomputação original** | O adiamento experimental poderia deslocar os instantes de observação e introduzir riscos para salvar/retomar estados; apenas transponders foram memoizados. |
| Sessões QKD ativas | **Mantido o avaliador da Etapa 5** | A versão atual implementa reavaliação integral forçada após reservas PICh, critérios QBER opcionais e compartilhamento QKDSpan/TDM. O experimental não preserva todas essas semânticas. |
| QotLinkCache e GN/EGN | **Mantidos da Etapa 5** | O cache já fingerprinta fontes físicas, por enlace e núcleo, inclusive PICh/limites longitudinais; preservados Johannisson, Habibi e integração GN. |
| Launcher | **Integração aditiva restrita** | `-Dsnets.edition=main|experimental` permite escolher a edição e a porta sem trocar servidor/UI. O padrão anterior segue `experimental`/8766. Ajuda (`--help`) segue a porta selecionada. |
| Build e auditorias | **Corrigidos** | A classe rastreada deixou de ser proibida nos scripts e no JarBuilder; todas as classes EGN efetivamente retiradas continuam bloqueadas. |

## Exatidão numérica, testes e qualidade científica

- Compilação de 453 fontes Java de produção via `JarBuilder` para Java 8; 647 arquivos `.class` gerados no empacotamento. O `Main.class` do JAR base é mantido intencionalmente.
- O Launcher foi recompilado com `--release 8` e empacotado com os recursos web/templates originais.
- `SelectiveIntegrationVerification` verifica inserção, remoção, mutação por `set`, iteradores/sublistas, troca por lista externa, clonagem e serialização.
- `JarBuilderRemovedClassesTest` verifica exclusões EGN antigas e a preservação do novo `TrackedRequestList`.
- Onze verificadores físicos/QKD foram repetidos em quatro modalidades (Etapa 5, integrado, `noPerfCache`, `verifyPerf`): **44 PASS**.
- Suíte científica completa no JAR recompilado: **33 PASS, 4 PENDENTE, 0 FAIL, 0 TIMEOUT** (as quatro pendências são as mesmas divergências multibanda, Dibaj e Mehdizadeh, não corrigidas por este pacote).
- Campanhas pequenas com quatro tipos de cenários e **duas sementes** (`20261112` e `20261114`) em três versões: **24 execuções concluídas, 96 CSVs por versão, conteúdos idênticos byte a byte depois de remover apenas o prefixo do nome de arquivo que identifica a variante**. Cada seed produz 77/77/77/82 métricas para clássico, QKD taxa, QKD estoque e multibanda.
- Execuções do script portátil `scripts/comparar_integracao_seletiva.py` (seed `20261115`, 35 pedidos) confirmaram equivalência CSV em modos rate e stock.
- Em multibanda (65 pedidos), `-Dsnets.perfStats=true` mostrou 28.417 acertos/65 faltas para taxas, 3.092 acertos/58 faltas para energia de transponders. São **acertos de cache, não percentuais de aceleração**.
- Uma tentativa de campanha com 700 pedidos excedeu o tempo disponível **já no JAR original Etapa 5** e não sustenta conclusão de desempenho para carga alta.

### Medição indicativa (1 replicação, Java 21, ambiente de testes; segundos)

| Cenário | Pedidos | Etapa 5 | Integrado | Integrado sem caches |
| --- | ---: | ---: | ---: | ---: |
| Clássico, seed 20261114 | 135 | 1,33 | 1,85 | 1,84 |
| QKD por taxa, seed 20261114 | 135 | 1,24 | 1,39 | 1,24 |
| QKD por estoque, seed 20261114 | 135 | 2,42 | 2,24 | 2,43 |
| Multibanda, seed 20261114 | 65 | 2,18 | 2,55 | 2,99 |

**Conclusão de desempenho:** os caches são operacionais, mas **não há aceleração global consistente demonstrada** nestas campanhas pequenas. JVM, aquecimento de JIT, IO e custo fixo dominam parte relevante do tempo. São necessários benchmarks maiores, com repetições e ambiente estável, antes de alegar ganho ou adotar otimizações mais agressivas.

## Comandos de verificação (Windows)

```bat
scripts\verificar_integracao_seletiva.bat
python scripts\auditar_simbolos_obsoletos.py . --jar simulations\SNetS-SDM-QKD-exp.jar
java -Dsnets.verifyPerf=true -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.SelectiveIntegrationVerification
java -Dsnets.edition=main -jar launcher\snets-launcher.jar --help
java -Dsnets.edition=experimental -jar launcher\snets-launcher.jar --help
```

Comparação A/B com mesmo arquivo, seed e pedidos, preservando o JAR da Etapa 5 sob outro nome **antes de instalar**:

```bat
python scripts\comparar_integracao_seletiva.py --base-jar simulations\SNetS-SDM-QKD-etapa5.jar --integrated-jar simulations\SNetS-SDM-QKD-exp.jar --cenario simulations\QKD_qualificacao\Exemplo_5nos_QTND30 --seed 20261112 --pedidos 100 --modo-chaves both --saida resultados\ab_integracao
```

Para comparar no mesmo JAR, `-Dsnets.noPerfCache=true` desativa os caches de desempenho; `-Dsnets.verifyPerf=true` recalcula e compara bit a bit os valores reutilizados; `-Dsnets.perfStats=true` imprime estatísticas ao encerrar.

## Limitações e próximos passos

Esta integração não modifica o EGN integral, ISRS-GN, modelos de EDFA ou modelos quânticos. Permanecem as divergências científicas registradas na Etapa 5. As medições não substituem estudo com dezenas de milhares de pedidos, várias topologias e replicações, nem garantem melhora de desempenho nas condições do usuário.

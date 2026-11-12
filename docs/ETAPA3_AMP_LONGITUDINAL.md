# Etapa 3 — amplificadores e propagação longitudinal (2026-11-11)

## Escopo e base

Aplicar **depois da Etapa 2** (NLI). Os arquivos deste incremento foram comparados com a árvore da Etapa 2; não há modificação de protocolo QKD, reserva temporal de chaves ou algoritmos RMCSA nesta entrega.

## Alterações efetivas

1. `qkd.multiband.ClassicalPowerEvolution`: construção dos estados de amplificadores a partir da potência **realmente presente** na fibra direcionada, com booster, fontes injetadas em posições intermediárias, amplificadores de linha e fontes retiradas. A otimização percorre as fontes uma única vez por amplificador (O(fontes × amplificadores), em vez de reler todos os amplificadores anteriores para cada fonte), mantendo ganho individual por portadora e saturação pela carga compartilhada.
2. `network.LongitudinalClassicalQoT`: cálculo da transferência dos spans até o receptor em percurso inverso O(nº de amplificadores). Mantidos booster, linha, pré-amplificador, atenuação e ASE aditivo de cada estágio. Incluído controle explícito de resultado não físico.
3. `qkd.multiband.ClassicalPowerEvolution`: a rotina de vazamento **opt-in** de ASE para o filtro quântico passa a contar a emissão do **booster no início do enlace**, antes desconsiderada. A ASE dos amplificadores de linha continua propagada pela cadeia clássica até o ponto de transferência espectral no receptor. **Não** são introduzidos ganhos para os fótons QCh, nem ASE no QCh quando o vazamento está desabilitado. O pré-amplificador não é automaticamente considerado como fonte de vazamento para a fibra quântica — depende de uma caracterização de acoplamento ao equipamento receptor não disponível no projeto.
4. `qkd.multiband.MultiBandEdFA`: preservado o comportamento padrão de saturação **compartilhada** C/L. Acrescentado modo opcional `per-band` para representar **dois blocos de amplificação independentes**, sem competição entre as cargas dos respectivos blocos. O ponto de operação agora expõe ganho, compressão, carga e ASE por frequência. O modo escolhido é preservado ao configurar fatores de ruído A1/A2.
5. `qkd.physical.QKDPhysicalLayerConfig`: campo opcional `qkdPhysicalLayer.multiband.edfaSaturationScope` (`shared`, padrão, ou `per-band`). Só afeta os perfis EDFA multibanda explicitamente configurados em `edfaBands`. A opção aplica-se aos **amplificadores de linha** desse modelo; os modelos atuais de booster e pré-amplificação não são EDFA C/L completos, nem passam a ser circuitos de bombeio independentes por essa configuração.
6. Segurança física: utilizar `gainLinearAt(indice,alpha,frequenciaHz)` e `powerRatio(inicio,fim,alpha,frequenciaHz)` com modelo espectral carregado. Consultas **sem frequência** passam a falhar, pois não existe um único ganho C/L fisicamente significativo. O modelo simples de banda única mantém suas APIs anteriores.

## Exemplo de configuração (fragmento)

Dentro do arquivo **`qkdPhysicalLayer`**, acrescente a chave ao objeto `multiband` já existente, junto a `edfaBands` e aos demais parâmetros. Por exemplo, o trecho relevante desse arquivo é:

```json
{
  "multiband": {
    "edfaSaturationScope": "shared"
  }
}
```

**Não substitua o arquivo inteiro por esse fragmento**; ele omite `source`, `protocol`, `fiber`, `detector`, `edfaBands` etc.

Substitua por `"per-band"` **somente** quando as bandas correspondam a módulos independentes com caracterização de saturação compatível. Este exemplo é um fragmento: preserve os demais grupos e parâmetros do arquivo completo. Quando não houver `edfaBands`, o novo campo não muda a simulação.

## Verificações locais

```powershell
java -cp .\simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDStage3AmplifierChainVerification
java -cp .\simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDv26AseFrequencyParityVerification
java -cp .\simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDBoosterIntegratedVerification
java -cp .\simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDUnifiedMultibandVerification
```

A verificação nova cobre: condição analítica sem amplificadores de linha (booster + pré-amplificador), cadeia de duas linhas com último trecho parcial, somatória de ASE propagada, ganho por banda, saturação compartilhada versus separada, presença de PICh depois do primeiro EDFA, recusa de ganho multibanda sem frequência e estado imutável.

## Limitações que NÃO foram solucionadas

- `MultiBandEdFA` **permanece paramétrico**. Não resolve equações de taxa de inversão de população do érbio, bombeio, largura espectral de ASE, buracos espectrais (*spectral hole burning*) nem ISRS-GN rigoroso. Os ganhos e NFs de cada banda precisam ser determinados por medidas ou referências calibradas.
- O modo `per-band` não significa que qualquer amplificador físico tenha saturação independente nas bandas. Depende da arquitetura do hardware.
- O modelo longitudinal representa a carga por uma atenuação clássica comum nos spans para determinar a saturação. Perda dependente de frequência por seção, ISRS longitudinal rigoroso e EGN de campo não foram implementados.
- O resultado de `LongitudinalClassicalQoT` só corresponde ao mesmo perfil usado em QKD/GN quando o **snapshot** contém o booster configurado antes de construir o histórico de ganhos (como faz o caminho de produção `PhysicalLayer`). Não se deve combinar um snapshot sem booster com uma transferência de saída que inclua outro booster e chamar os dois de estado idêntico.
- Os verificadores internos aprovados são regressões e checagens analíticas. Eles **não comprovam** validação experimental do EDFA nem reprodução completa de Poggiolini 2017 e dos resultados multi-banda. Isso faz parte da Etapa 5.

## Passos restantes no plano

**Etapa 4:** estoque temporal de chaves (`rate-reservation` versus `temporal-inventory`), eventos, esgotamento e reposição.
**Etapa 5:** reprodução quantitativa externa, testes multibanda de literatura ainda divergentes, replicações estatísticas e comparação de modelos de amplificadores calibrados.

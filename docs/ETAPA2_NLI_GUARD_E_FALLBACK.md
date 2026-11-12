# Etapa 2 — Modelagem de NLI GN/EGN (11/11/2026)

## Escopo e base de aplicação

**Pré-requisito obrigatório:** etapa 1 aplicada (`SNetS-SDM-QKD-etapa1`), inclusive a correção de continuidade QKD entre trechos independentes. Este pacote é INCREMENTAL e não restaura nem modifica a etapa 1.

Esta etapa trata **somente** da detecção de NLI inválida e da cobertura do fallback Habibi para 8-QAM/32-QAM, mantendo as opções `johannissonNliMode` e `habibiNliMode`. Não afirma reproduzir o EGN completo nem resolve os modelos físicos dos amplificadores (etapa 3), estoques de chaves (etapa 4) ou a validação externa/estatística abrangente (etapa 5).

## Mudanças feitas

1. `network/GnModelMath.nonnegative`: PSD de NLI `NaN`, infinita ou negativa gera `ArithmeticException` imediatamente, com contexto do cálculo. Não se transforma ruído inválido em zero (o que inflaria OSNR). O contador legado `negativeClampCount` permanece como alias de `negativeRejectionCount`, para preservar consumidores; **não há mais clamp**.
2. `network/GnModelMath` Johannisson: `approximate` exige que a aproximação logarítmica seja matematicamente válida. Se SCI log for não positiva ou XCI log apresentar sobreposição indefinida, o modo explícito rejeita o valor. `adaptive` e `integral` usam a integração positiva existente para a geometria correspondente.
3. `network/PhysicalLayer` e `network/LongitudinalClassicalGN`: o modo Habibi com fallback **não utiliza** o resultado EGN analítico negativo. Caso não consiga obter integral GN válida para o CUT ou um dos interferentes, falha explicitamente. Contribuições incoerentes de PICh mantêm a modelagem analítica corrente.
4. `network/Poggiolini2017GnIntegral` e `PhysicalLayer.supportedChannel`: foram incluídas as identificações de **8-QAM (Φ = 2/3)** e **32-QAM (Φ = 0,69)** documentadas na Tabela 5 da tese de Jurandir. A integração GN **não depende de Φ**; o valor é metadado para subtrair as contribuições analíticas anteriores durante o fallback híbrido. `integral` permite Φ finito para integração GN; `adaptive` utiliza apenas identificações explícitas suportadas. Modulações desconhecidas não são atribuídas arbitrariamente a uma referência EGN.
5. `network/OpticalNoiseCascade.addNli`: rejeita NLI inválida ao acumular o ruído no receptor.
6. `qkd/multiband/NonlinearInterferenceModel`: verifica a finitude e não negatividade das PSDs de entrada, do perfil de perdas e da saída GN; um trecho de comprimento exatamente zero permanece fisicamente isento de NLI. NLI inválida não é mais passada para o cálculo de ruído QKD.
7. Testes atualizados que anteriormente exigiam *clamp* passam a exigir falha explícita; novo `QKDStage2NliSafetyVerification` verifica os dois modelos GN, cascata, banda quântica e pares SCI/XCI de 8-QAM e 32-QAM. `QKDNarrowbandHabibiVerification` agora atravessa o **caminho de produção** para 4-, 8- e 32-QAM a 12,5 GHz.

## Modos e política de segurança numérica

```json
{
  "johannissonNliMode": "adaptive",
  "habibiNliMode": "adaptive",
  "nliNarrowbandGHz": 25.0
}
```

- `approximate`: GN Johannisson logarítmico ou Habibi EGN aproximado sem fallback. **Se matematicamente não físicos, a avaliação falha explicitamente**. A execução pode terminar com exceção; trocar para `adaptive` ou `integral` e investigar o cenário, não desativar a NLI.
- `adaptive` (padrão): ativa o integral para CUT estreito ou aproximação inválida; pode manter equações aproximadas quando são válidas. **Não é equivalente a EGN rigoroso**.
- `integral`: Johannisson com kernel SCI/XCI integral (Eqs. 11/15) e Habibi com substituições por integrais **GN** numéricas quando elegíveis. Os termos restantes, incluindo PICh e correções da implementação legada, não são uma integral EGN completa.

Em particular, **a potência NLI dos três formatos no fallback GN pode ser igual para a mesma potência, banda e frequência**, porque GN não contém os momentos de ordem superior que distinguem modulações. Não interpretar isso como resultado EGN específico das modulações.

## Evidências determinísticas

Na verificação SCI isolada com `B=12,5 GHz`, parâmetros ópticos fixos (`alpha=0,2 dB/km`, `gamma=1,3/(W km)`, `beta2=-21,3 ps²/km`, `L=80 km`), foram obtidos:

| Modulação | SCI Habibi bruta (W/Hz) | SCI GN integral (W/Hz) |
|---|---:|---:|
| 8-QAM | -2,00608e-17 | 2,34417e-17 |
| 32-QAM | -2,17301e-17 | 2,34417e-17 |

A SCI negativa não é uma medida física; ela é um exemplo de falha na aproximação, recuperada pela integral GN. A integral GN **não valida** a correção EGN do formato.

Os testes de regressão de 47 verificadores Java terminaram com PASS (zero falhas/timeout). Detalhes em `RESULTADOS_TESTES_ETAPA2.csv`. Referências de literatura multibanda podem terminar com PASS de software e ainda mostrar divergências quantitativas: não as declarar como validação científica concluída.

## Comandos de verificação no Windows

No diretório raiz do projeto, após instalar e usar o JAR fornecido neste pacote:

```bat
java -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDStage2NliSafetyVerification
java -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDNliIntegrationModesVerification
java -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDNarrowbandHabibiVerification
scripts\verificar_modos_nli.bat
scripts\verificar_ber_polarizacao_gn.bat
```

Para produzir tabelas de comparação da aproximação Habibi com integral GN:

```bat
java -cp simulations\SNetS-SDM-QKD-exp.jar verification.HabibiGnComparisonVerification build\habibi_gn_ab_etapa2.csv
java -cp simulations\SNetS-SDM-QKD-exp.jar network.Poggiolini2017GnIntegral build\poggiolini_gn_etapa2.csv
```

Essas curvas são referências internas para reprodutibilidade, não uma validação externa por medições.

## Próximas iterações

- Etapa 3: lei de saturação, ASE, booster/linha/pré e evolução longitudinal; revisar `MultiBandEdFA.java` e calibrar regimes multibanda. 
- Etapa 4: parâmetro de seleção `rate-reservation` (legado) versus `temporal-inventory` (saldo/eventos/reposição/esgotamento).
- Etapa 5: confronto quantitativo com Johannisson (2014), Poggiolini (2017) e Jurandir (2024) sob as respectivas hipóteses; séries multibanda Dibaj/Mehdizadeh e experimentos de rede estatísticos com IC de 95%, sementes e topologias maiores.

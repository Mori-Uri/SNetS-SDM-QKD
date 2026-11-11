# SNetS-SDM-QKD v17 — correções dos modelos GN Johannisson e Habibi

## Escopo

Foram modificadas as rotinas clássicas e o cálculo longitudinal de NLI dos DChs e PIChs.
Os modelos continuam selecionáveis por `physicalLayerModel` (`MODEL_JOHANNISSON=1`, `MODEL_HABIBI=0`).
Não foi introduzido GGN, ISRS-GN ou outro modelo no lugar do GN do SNetS.

## Alterações

1. **Johannisson (SCI de canais estreitos):** não força mais `rho >= 1.000001`.
   Usa a integração retangular positiva associada às Eqs. (11) e (15) do artigo,
   evitando um salto artificial em 28 GHz. A fórmula logarítmica da Eq. (16)
   permanece referência assintótica, não a única expressão calculada. A XCI
   continua com o núcleo logarítmico do SNetS.
2. **Seleção no longitudinal:** o modelo `MODEL_HABIBI` usa agora a aproximação
   GN + correção por modulação das Eqs. 3.23–3.31 da tese, aplicada a subspans
   delimitados por EDFAs e origens/términos de fontes. `MODEL_JOHANNISSON`
   conserva a integração local Johannisson. Ambos usam o mesmo snapshot físico.
3. **Spans parciais na via legada:** o enlace é decomposto em `L` e último trecho
   `<= L`. Para Johannisson, usa-se correção aproximada de comprimento efetivo;
   para Habibi, Leff, E e correções são recalculados usando o comprimento real.
4. **PICh como interferente em Habibi:** o termo XCI tem a correção da Eq. 3.31
   calculada com o coeficiente Phi da modulação SNetS configurada no PICh,
   ponderada por seu trecho longitudinal no span físico.
5. **Convenções:** gamma representa o coeficiente físico W^-1 m^-1 SEM Manakov.
   Johannisson usa PSD por polarização e fator (8/9)^2; Habibi usa PSD total
   e coeficiente 8/27 com correção EGN. Atenuação `alphaPowerPerM` em nepers/m.
6. **Não negatividade:** toda PSD NLI final negativa ou não finita é limitada a
   zero. `GnModelMath.negativeClampCount()`, `.mostNegativeUnclamped()` e
   `.nonFiniteCount()` permitem registrar problemas numéricos. Até 5 avisos
   de cada tipo são exibidos no stderr para evitar logs excessivos.
7. **Grade de frequência:** `GnModelMath.centerHz()` centraliza os canais no
   intervalo de slots efetivamente ocupado; banda efetiva de modulação é usada
   para PSD, em vez de confundir slots reservados com largura de sinal.

## Limites científicos

- A expressão Johannisson SCI escolhida é a integração retangular do modelo
  aproximado do artigo (não uma integração GN completa de fase para todos spans).
- A versão longitudinal Habibi é uma extensão de subspans com potência
  aproximadamente constante por seção, com acumulação incoerente. Não se deve
  chamá-la de EGN coerente completo com ganho distribuído.
- A correção de comprimento do span residual de Johannisson é uma aproximação
  por comprimento efetivo; para alta exatidão precisaria integrar o núcleo de
  casamento de fase em cada seção.
- O limite inferior zero permanece por solicitação: uma SNR imensa quando
  somente a NLI está ativa **não é** evidência de ausência física de ruído.
- Os coeficientes Phi para BPSK e M>64 são extensões legadas do SNetS e não
  aparecem na Tabela 5 da tese.
- Não foram concluídas uma compilação Maven limpa nem regressões dinâmicas
  completas MF, MB e MB-SDM; a compilação executada foi incremental Java 8
  utilizando classes v16 e v1 previamente compiladas como dependências.

## Verificação

Nova classe: `qkd.verification.QKDGnScientificCorrectionsVerification`.

Após compilar com as dependências do projeto:

```bash
java -cp target/classes qkd.verification.QKDGnScientificCorrectionsVerification
```

Para Windows, use `tools/validar_gn_v17.bat` com Maven instalado e acessível.
Acompanham a entrega arquivos CSV e JSON gerados pela validação incremental.

## Referências

Johannisson & Agrell (2014), Journal of Lightwave Technology 32(23),
Eqs. (11), (15) e (16), DOI 10.1109/JLT.2014.2361357.

J. C. Lacerda Jr. (2024), tese UFMA/UFPI, seção 3.1.2, Eqs. (3.21)–(3.31)
e Tabela 5 (Poggiolini e Jiang / Habibi e Beyranvand).

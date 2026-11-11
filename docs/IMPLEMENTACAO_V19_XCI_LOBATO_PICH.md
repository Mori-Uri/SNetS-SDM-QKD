# SNetS-SDM-QKD v19 — Correções Johannisson XCI e Lobato/PICh

## Escopo

Base: projeto v18. Implementadas correções físicas e testes de regressão sem substituir os modelos GN originais, sem criar contrapropagação e sem permitir que QKDSpan atravesse um nó SNetS.

### XCI Johannisson

- Os termos XCI de `PhysicalLayer.getGnli` e de `LongitudinalClassicalGN.sectionPsd` usam a integral retangular das Equações (11) e (15) de Johannisson e Agrell (2014), no lugar do logaritmo assintótico da Equação (16).
- `PichClassicalNonlinearity.crossTermJohannissonExact` usa o mesmo núcleo, a largura do canal vítima e o tempo/comprimento efetivo de coexistência longitudinal do PICh. A assinatura antiga permanece por compatibilidade, mas **não é usada no caminho físico principal**.
- SCI e XCI compartilham a mesma primitiva numérica A(x) = integral[0,x] atan(t)/t dt, com quadratura Simpson e continuação estável para x>1.
- O resultado de NLI continua limitado a zero por `GnModelMath.nonnegative` (sem PSD negativas).
- O modelo Habibi não foi alterado.

### Lobato e espectro por enlace

- Criada `LobatoSpectralOverlap`: ISO = slots de sinal sobrepostos / slots de sinal do *interferente*, Eq. 3.38, excluindo os slots de guarda reservados separadamente.
- `Crosstalk.calculatePxt` lê `getSpectrumAssignedByLink(link)` e `getModulationByLink(link)` para cada interferente; a vítima também usa o espectro e núcleo do enlace local.
- Avaliação de XT clássica e modo `XT_SEPARATE` normalizam a potência recebida usando os parâmetros da vítima por enlace.
- PICh participa como fonte física apenas em seu enlace dirigido e QKDSpan. A expressão do PICh->DCh segue o modelo linear de Lobato `Pxt = Pn * ISO * h * L_span`, em vez da aproximação limitada `hL/(1+hL)`. Esse modelo linear é uma aproximação de baixo acoplamento e **não deve ser extrapolado sem calibração** para acoplamento forte.
- `QKDPichCircuit` valida sua fronteira física no construtor. A avaliação própria do PICh usa a mesma função central de sobreposição por enlace para DChs vizinhos.
- Espectro livre e uso dos slots são gerenciados pelo SNetS; esta atualização **não cria reutilização espectral de PICh entre subtrechos**, nem modifica a alocação.

### Cálculos controlados

Na verificação Java independente, para |β2|=21,3 ps²/km, atenuação 0,2 dB/km e espaçamento óptico Δf=75 GHz:

| B da vítima | B do interferente | Eq. 15 (XCI normalizada) | Eq. 16 (log) | Superestimação da Eq.16 |
|---|---|---:|---:|---:|
| 12,5 GHz | 12,5 GHz | 0,1546283 | 0,1670541 | 8,04% |
| 12,5 GHz | 25 GHz | 0,3110939 | 0,3364722 | 8,16% |
| 25 GHz | 12,5 GHz | 0,1608198 | 0,1670541 | 3,88% |
| 25 GHz | 25 GHz | 0,3237372 | 0,3364722 | 3,93% |

Nota: esses percentuais correspondem à configuração acima, não à mesma configuração dos resultados anteriores de ~20%/~10%. O teste usa quadratura bidimensional independente da Equação 11 para 16 combinações de largura.

Para o Lobato, com duas fibras dirigidas A->B (40 km) e B->C (60 km), ISO_AB = 2/5 e ISO_BC = 3/5. A XT normalizada esperada e calculada é h*(0,4*40000 + 0,6*60000) = 2,149648615e-7 (-66,676325 dB) para as potências de teste iguais. O PICh de 15 km influencia apenas o primeiro enlace, enquanto um segundo PICh confinado a 30 km do segundo enlace contribui apenas no respectivo trecho.

### Validações executadas e limites

- Compilação **incremental** Java 8 aprovada com classes v17/v18 e classes compiladas do SNetS v1 como dependências.
- 16 verificadores Java aprovados, inclusive testes anteriores da tese, GN, ASE, PICh, amplificadores, rollback e reprodutibilidade.
- `QKDPichSharedClassicalQoTVerification` passa agora a escolher explicitamente Johannisson no caso de NLI positiva, pois a correção EGN de Habibi pode legitimamente zerar a NLI por truncamento. Isso modifica apenas a definição do teste, não a implementação física Habibi.
- **Não houve compilação Maven limpa, nem simulação dinâmica integral das arquiteturas MF, MB e MB-SDM**. Os resultados não constituem calibração experimental nem validação científica integral.
- As variantes legadas Klinkowski e métodos auxiliares antigos de comparação de XT não foram rederivados cientificamente; o escopo é Lobato, GN Johannisson e PICh.

## Execução no projeto

Com o projeto completo, JDK e dependências Maven instalados:

```
mvn -DskipTests package
java -cp target/classes qkd.verification.QKDJohannissonExactXciVerification
java -cp target/classes qkd.verification.QKDLobatoPerLinkPichVerification
java -cp target/classes qkd.verification.QKDThesisCrosstalkVerification
```

Se as dependências forem externas e não estiverem em `target/classes`, acrescente ao classpath de execução conforme a configuração Maven do SNetS.

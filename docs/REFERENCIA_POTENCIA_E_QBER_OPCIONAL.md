# Referência numérica de potência e critério opcional de QBER

Atualização de 08/11/2026. Escopo: SNetS-SDM-QKD, `PhysicalLayer` clássica e admissão QKD.

## 1. `referenceW` — 1 mW não é um parâmetro experimental

Em `network.PhysicalLayer.computeCascadedMaximumPower()`, o valor de produção continua

```java
final double referenceW = 1.0e-3; // 1 mW = 0 dBm
```

Trata-se de **escolha numérica de normalização para medir o coeficiente cúbico da SCI**, não de potência calibrada experimentalmente nem de valor imposto por Vale e Almeida Jr. (2019).

Para o modelo de ganho constante e ruído ASE independente da potência, conforme as hipóteses da aproximação de Vale e Almeida Jr. (2019, Eqs. 18 e 23):

\[
\mathrm{OSNR}(P)=\frac{dP}{aP^3+bP+c},\qquad
P_{\mathrm{otimo}}=\sqrt[3]{\frac{c}{2a}}
\]

Na computação com PSDs referidas ao mesmo receptor:

\[
\mathrm{SCI}_{\mathrm{ref}}=a P_{\mathrm{ref}}^3,\qquad
P_{\mathrm{otimo}}=P_{\mathrm{ref}}\left(\frac{\mathrm{ASE}}{2\,\mathrm{SCI}_{\mathrm{ref}}}\right)^{1/3}.
\]

Por isso, se a SCI segue precisamente a lei cúbica e a ASE não muda com `P_ref`, o valor final da potência ótima **não depende da referência numérica escolhida**. Não é suficiente testar somente a identidade algébrica: o teste abaixo chama o caminho real de propagação da camada física do SNetS.

Em `PhysicalLayer`, foi incluído `computeCascadedMaximumPowerAtReference(...)` **com acesso de pacote apenas para verificação**, preservando a API pública e o uso de 1 mW em produção. O método rejeita valores não positivos ou não finitos. A otimização longitudinal para amplificadores saturados continua sendo uma busca numérica independente, sem assumir a invariância cúbica.

### Testes quantitativos

Classe: `network.CascadedMaximumPowerReferenceVerification`, executável por:

```bash
java -cp <classes-e-dependencias> network.CascadedMaximumPowerReferenceVerification
```

O teste utiliza **7 referências distintas**: `0.1`, `0.25`, `0.5`, `1`, `2`, `5` e `10` mW. Para cada uma, recalcula a potência ótima a partir da simulação física (ASE e SCI acumuladas), sem alterar a potência armazenada no circuito. Compara os resultados com o de produção, a 1 mW, sob tolerância relativa de `1e-7`.

| Modelo | SCI | Último enlace | P_ótima a 1 mW (mW) | Erro relativo máximo entre referências |
|---|---|---:|---:|---:|
| Johannisson | logarítmica | 80 km | 1.35984518126 | 3.19e-16 |
| Johannisson | logarítmica | 37 km | 1.18451577204 | 1.83e-16 |
| Johannisson | integral | 80 km | 1.34962615796 | 1.61e-16 |
| Johannisson | integral | 37 km | 1.17561432176 | 3.69e-16 |
| Habibi | logarítmica | 80 km | 1.61210275056 | 2.69e-16 |
| Habibi | logarítmica | 37 km | 1.56860318013 | 4.15e-16 |
| Habibi | integral | 80 km | 1.61210275056 | 2.69e-16 |
| Habibi | integral | 37 km | 1.56860318013 | 4.15e-16 |

Resultado: **56 comparações numéricas aprovadas** em 8 configurações (2 modelos GN × 2 modos SCI × 2 comprimentos de trecho final). Erro máximo observado: `4.15e-16`. O teste também verifica a rejeição de referência nula, negativa, NaN e infinita e a não alteração da potência de lançamento do circuito. **A conclusão de invariância vale somente para o caminho modelado de ganho fixo/SCI cúbica, não para saturação de amplificadores nem configurações em que o modelo deixe de ser cúbico.**

Esses resultados são verificação matemática/numérica da implementação, **não** validação experimental de potência óptima nem de modelos GN/EGN em cenários físicos reais.

## 2. QBER independente agora é opcional

Novo campo em `network.qkd`:

```json
"qkd": {
  "qberThresholdEnabled": false,
  "maximumQber": 0.11
}
```

O valor de `maximumQber` é preservado para fins de configuração/experimentos, mas **não impõe rejeição independente** quando `qberThresholdEnabled=false` (novo padrão). A QBER continua sendo calculada, registrada e usada internamente na fórmula de SKR do BB84 com estados *decoy*.

- `qberThresholdEnabled=false` (padrão): admissão de QKD por **SKR suficiente para a demanda de chaves**, além de requisitos de espectro/equipamentos/PICh e demais restrições já existentes. QBER não impõe segunda rejeição.
- `qberThresholdEnabled=true`: SKR exigida **e**, adicionalmente, `QBER <= maximumQber` em cada trecho, inclusive em alocação compartilhada e proteção de sessões ativas.

O limite de 11% é **uma referência teórica idealizada de BB84**, não um valor de admissão explicitamente imposto por Moghaddam (2021) ou Dibaj (2024). Evitar atribuí-lo a esses trabalhos.

Os cenários `simulations/QKD_Referencias/Moghaddam2021_MCF7_Inspirado` e `.../Dibaj2024_MF_Inspirado` fixam explicitamente `qberThresholdEnabled=false`. O cenário `MCF7_Qualificacao_SDM` demonstra a **opção experimental ativada** (`true` e `maximumQber=0.11`): não deve ser apresentado como uma reprodução pura dos limiares de Moghaddam ou Dibaj.

Propagação da configuração para o motor físico, reserva de capacidade e proteção de sessões usa `QKDNetworkParameters.getEffectiveMaximumQber()` (limite real ou `1.0` como sentinela não restritiva). Isso permite preservar os atuais contratos físicos sem introduzir um critério adicional quando desativado.

### Verificações

`qkd.verification.QKDOptionalQberVerification` compara o **mesmo canal físico** nos dois modos, usando configuração `maximumQber=0.01`:

- QBER calculada: `0.015000123796674295` (> 0.01).
- SKR calculada: `50,101,963.62457788 bit/s`, **positiva e idêntica nos dois modos**.
- Gate desligado: viável pela SKR.
- Gate ligado: não viável pela QBER.
- A QBER observada permanece reportada sem alterações.

`qkd.verification.QKDCoexistenceCorrectionsVerification` também foi expandido para verificar a configuração desativada, a habilitação explícita e o efeito de `maximumQber` quando o gate está desligado. `scripts/validar_perfis_qkd.py --strict` foi executado: os três cenários passaram, e o validador alerta se limites adicionais de QBER forem impostos aos cenários bibliográficos.

## 3. Verificações feitas no ambiente da entrega

- `javac` Java 8 (`-source 8 -target 8`) dos arquivos modificados/adicionados: **aprovado** (warnings de toolchain JDK 21).
- `network.CascadedMaximumPowerReferenceVerification`: **aprovado**, com os erros acima.
- `qkd.verification.QKDOptionalQberVerification`: **aprovado**.
- `qkd.verification.QKDCoexistenceCorrectionsVerification`: **aprovado**.
- `qkd.verification.QKDCascadedOsnrVerification`: **aprovado**.
- `qkd.verification.QKDPhysicalVerification`: **aprovado**.
- `qkd.verification.QKDThesisFiguresVerification`: **aprovado**.
- `qkd.verification.QKDThesisSNetSPhysicalLayerVerification`: **aprovado**.
- `python scripts/validar_perfis_qkd.py --strict`: **aprovado**.

**Limitação:** os testes Java foram executados com classpath híbrido (fontes alteradas recompiladas e classes compiladas anteriores para dependências inalteradas). Não foi possível executar `mvn clean package` nem a regressão integral de todo o simulador neste ambiente, que não dispõe do Maven/dependências completas. Os scripts `ci_qkd_clean.bat/.sh` passaram a incluir a nova verificação de invariância para que você possa executar **compilação limpa e regressão** no seu ambiente Windows/Linux.

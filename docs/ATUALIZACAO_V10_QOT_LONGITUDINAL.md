# SNetS-SDM-QKD v10 - QoT classico longitudinal

## Implementado

- `PhysicalLayer.computeSNRSegment()` passa pelo novo caminho opt-in longitudinal quando `enableLongitudinalAmplifierQoT()` foi habilitado; o caminho legado permanece intacto quando desabilitado.
- O metodo `computeSNRSegment2()` utiliza a mesma integracao quando o modo longitudinal esta ativo, evitando divergencia entre as variantes.
- Propagacao da PSD de sinal e do ruido acumulado atraves de perda, amplificadores de linha, booster e pre-amplificador, incluindo ASE individual.
- Calculo local de GN com potencia longitudinal das fontes DCh/PICh por trechos de ate 2 km, usando o espectro e os limites fisicos do PICh. Metodo incoerente aproximado (kernel logaritmico de Johannisson), nao EGN coerente.
- `ClassicalPowerEvolution.LoadSource` inclui largura de banda em Hz, alem da frequencia, potencia e intervalo longitudinal. O construtor antigo continua funcionando.
- Carga antes do pre-amplificador calculada a partir das fontes presentes no ponto, incluindo fontes adicionadas e retiradas em nos confiaveis.
- Ganho ideal/saturado e figura de ruido dos equipamentos de fronteira seguem o modelo longitudinal habilitado, compartilhando a lei `AmplifierGainLaw` e os parametros espectrais de `MultiBandEdFA`.
- So trafego no sentido do enlace fisico; nenhum canal contrapropagante e introduzido.

## Hipoteses e limitacoes cientificas

- O modelo GN longitudinal soma incoerentemente emissoes de pequenos trechos. Nao implementa fase coerente, ISRS-GN/EGN ou todas as correcoes de modulacao Habibi.
- Booster e pre-amplificador utilizam a mesma lei de ganho e os parametros espectrais, mas seus estados de carga nao estao embutidos nos snapshots de amplificadores de linha consumidos pela QKD. A unificacao completa dos tres tipos de equipamentos nos dois dominios ainda requer evolucao.
- A origem do sinal e o tratamento das comutacoes de nos mantem a abstracao de cada enlace do SNetS.
- A carga da cadeia de linha e calculada por `ClassicalPowerEvolution` preexistente; nao incorpora automaticamente a potencia adicionada pelo booster nos calculos de saturacao dos amplificadores de linha. Ainda ha uma divergencia fisica nessa aproximacao.
- A contribuicao GN de fontes com espectro sobreposto ao canal vitima e descartada do termo cruzado: requer tratamento espectral mais completo.
- A contabilizacao de crosstalk XT preserva a aproximacao preexistente e nao faz integracao longitudinal de cada evento de acoplamento.
- As parametrizacoes de EDFA continuam fenomenologicas, nao baseadas nas equacoes de taxa do erbio.
- A compilacao foi incremental, usando classes do ZIP v1 previamente compiladas; nenhuma compilacao Maven limpa nem validacao cientifica com artigos foi concluida.

## Verificacoes executadas

- Compilacao incremental Java 8 dos fontes afetados: PASS.
- `QKDClassicalLongitudinalQoTVerification`: PASS.
- `QKDLongitudinalGNVerification`: PASS.
- `QKDSharedAmplifierGainVerification` com classes existentes: PASS.
- `QKDPhysicalVerification` com classes existentes: PASS.
- Regressoes integrais de MF, MB, MB-SDM: NAO EXECUTADAS.

## Como verificar

Executar no ambiente do projeto, com Maven e suas dependencias carregadas:

```sh
mvn clean test
java -cp target/classes qkd.verification.QKDClassicalLongitudinalQoTVerification
java -cp target/classes qkd.verification.QKDLongitudinalGNVerification
```

O modo longitudinal continua opt-in e o comportamento classico legado permanece disponivel.

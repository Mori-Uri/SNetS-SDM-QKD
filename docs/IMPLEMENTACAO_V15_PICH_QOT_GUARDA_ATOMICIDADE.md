# SNetS-SDM-QKD v15 — Verificação de SNR PICh, guarda de mesma banda e rollback

## Escopo implementado

- `PichSnrAdmission`: estimativa **opt-in**, explicitamente calibrada, de SNR recebida do PICh. Soma piso de ruído recebido, XT intercore de fontes espectralmente sobrepostas e um termo cúbico GN de potência agregada dos canais ativos e do DCh candidato. A decisão ocorre antes da reserva do espectro. Sem coeficientes calibrados, a opção fica desligada.
- `SameBandSpectrumGuard`: rejeita sobreposição espectral entre QCh e fontes clássicas, incluindo faixa de guarda, em ambos os sentidos de admissão. **Requer alinhamento real das grades espectrais**, declarado pelo experimento via `sameBandAlignedGrids`; não se aplica automaticamente a MB/MF. Uma restrição de admissão é aplicada; não há reserva de blocos de guarda em cada núcleo.
- `QKDControlPlane`: tenta reverter a alocação clássica mesmo se a chamada ao método legado de commit lançar exceção depois de alterar parcialmente o estado. Na finalização, só remove a sessão do registro após liberação quântica confirmada. A captura de falhas de rollback não garante transação ACID em todas as rotinas legadas.
- Modelos GN existentes, direção unidirecional da fibra e fronteiras dos QKDSpans em nós ópticos SNetS preservados.

## Configuração opcional — dentro de `network.qkd`

```json
{
  "pichSnrCheckEnabled": true,
  "pichMinimumSnrDb": 20.0,
  "pichReceiverNoiseW": 1.0e-7,
  "pichXtCouplingPerKm": 0.0,
  "pichGnCoefficientPerW2Km": 0.0,
  "pichFiberLossDbPerKm": 0.2,
  "pichInsertionLossDb": 0.0,
  "sameBandAlignedGrids": false,
  "sameBandGuardSlots": 1
}
```

**Atenção:** valores apresentados são EXEMPLOS para demonstrar a interface e **não são calibração experimental**. Os coeficientes XT/GN são escalares fenomenológicos, não equivalem ao modelo GN completo do SNetS para os PICh. Não ativar a checagem sem parametrização física validada. `sameBandAlignedGrids=true` só é permitido se a correspondência real de frequência por slot entre todos os núcleos já foi estabelecida no cenário. No modo MB/MF, manter falso.

## Limitações ainda abertas

1. **QoT PICh completa:** este é um teste SNR calibrável, não cálculo BER por modulação, ASE espectral por EDFA e GN integral por QKDSpan. Falta integrar QoT PICh com o mesmo motor físico SNetS e parâmetros de receptor validados.
2. **Guarda física:** implementada como restrição cruzada de admissão para grades alinhadas. Não trata conversão de grades ou reserva física adicional em núcleos independentes.
3. **Atomicidade integral:** rollback foi fortalecido, mas não existe transação ACID abrangendo todos os caminhos clássicos de grooming, Q-TRx e recursos compartilhados. Necessita testes de injeção de falhas por etapa.
4. **Validação científica e cenários:** verificações numéricas/unitárias executadas. A regressão completa de 17 cenários foi preparada e inventariada, mas **não executada**, porque o ambiente não tem JAR atual construído integralmente/Maven e dependências.

## Compilação e regressão em ambiente com Maven

```bash
mvn clean package
python tools/regressao/executar_regressao_integrada.py --jar target/snets-simulator-1.0.0.jar
```

O comando de regressão exige um JAR efetivamente executável com as dependências empacotadas. O `mvn package` padrão pode não gerar um *fat JAR*; nesse caso use o procedimento de empacotamento do projeto. Não atualizar os golden masters automaticamente.

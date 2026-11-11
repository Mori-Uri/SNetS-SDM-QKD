# SNetS-SDM-QKD v24: amplificadores de fronteira, cache de QoT e retomada

## Origem e escopo

Base: SNetS-SDM-QKD v23. O código de `SaveStateStore`, `Simulator`, `EventMachine` e as alterações do launcher foram adaptados da v2 alternativa fornecida pelo usuário. O cache de QoT da v2 foi **reprojetado**, em vez de copiado, porque a v2 não tinha toda a ocupação auxiliar PICh e o estado físico da v23.

## 1. Booster, pré-amplificador e perdas passivas

- **MUX:** atenua sinal e ruídos anteriores à fibra; o booster tem ganho nominal que compensa apenas o MUX.
- **Linha:** EDFAs de linha compensam, nominalmente, as perdas de suas seções de fibra.
- **Pré-amplificador:** o ganho nominal compensa a **última seção de fibra**, inclusive parcial, **mais o DEMUX**.
- **DEMUX:** sua perda é aplicada ao final do enlace; a compensação nominal correspondente ocorre no pré-amplificador anterior.
- **OXC/switch:** nenhuma perda de inserção.

As correções foram aplicadas em `PhysicalLayer.propagateLegacyCascade()`, `computeSNRSegmentLongitudinal()` e `evaluatePich()` quando o PICh chega à fronteira física do enlace. No caso de amplificador saturado, ganho efetivo é calculado pela lei configurada e não se garante compensação exata. O PICh que termina em nó trusted **interno** ao enlace não recebe o ganho ou a perda DEMUX da extremidade física.

Exemplo de ganhos ideais, fibra 80 km, alpha=0,2 dB/km, MUX 3 dB, DEMUX 5 dB: ganho booster=3 dB; ganho preamp=16+5=21 dB, sem EDFA de linha em span único. Saldo líquido: -3+3-16+21-5=0 dB.

## 2. Cache de QoT por enlace

Classe `network.QotLinkCache`, com LRU de até 2.048 estados por instância de camada física. Aplica-se às avaliações de **DChs clássicos por enlace** em ambos os caminhos (cascata e longitudinal). Cacheia a evolução completa por enlace dos termos de sinal/ASE/NLI/XT da cascata, ou sinal/ruído longitudinal. Não inventa cache independente de SKR/QBER do QCh; porém **inclui PIChs ativos** na identidade do estado óptico que invalida entradas DCh.

A chave considera: enlace e núcleo, circuito vítima, modulação e potência, espectro **por enlace**, banda e frequência, ruídos/potência na entrada do enlace, amplificação ideal/saturada, fontes DCh e PICh em todos os núcleos (potências, modulações, espectros, taxas, guarda e QKDSpan físico), além de modos de avaliação. Candidatos e avaliações provisórias não são guardados. Mudanças de configuração do modelo longitudinal e PSD fixa apagam o cache. O cache é `transient`, logo não é transferido para save state.

Para desabilitar: `-Dsnets.noPerfCache=true`. Os ganhos dependem da razão entre consultas repetidas e custo de construir a chave; não foi demonstrado ganho de tempo em benchmark de produção. Não se deve estender essa otimização a QoT quântica antes de definir invalidação específica de SKR/QBER/Raman e parâmetros de amplificadores.

## 3. Parada, progresso e retomada da v2

Exemplo de seção em `simulation`:

```json
"saveState": {
  "enabled": true,
  "intervalMinutes": 10,
  "keepAfterFinish": false
}
```

Por compatibilidade com a v2, `saveState` **ausente** implica ativação por padrão com 10 minutos; `enabled:false` desliga. `savestate/progresso.json` registra andamento e estado; `savestate/lpN_repM.state` contém checkpoints de cada replicação; `savestate/STOP` solicita parada entre eventos, e o programa usa código de saída `3` quando interrompido. O hash de entradas e do JAR impede retomada de estado gravado para outra versão/configuração. Ao concluir, os estados são removidos, salvo `keepAfterFinish:true`.

O launcher da v2 foi atualizado para mostrar o andamento e disponibilizar pausa/cancelamento/retomada. Os writers CSV de `LineDataManager` não são serializados; após a leitura do checkpoint são recriados para evitar `NotSerializableException` e arquivos abertos inválidos. Templates compartilhados de amplificador QKD/longitudinal agora são serializados.

A captura de Ctrl+C depende do mecanismo de encerramento normal da JVM e não garante checkpoint durante desligamento abrupto ou falta de energia. A retomada sempre parte do último checkpoint íntegro.

## 4. Verificações executadas

- Compilação incremental com `javac --release 8` e stubs **temporários** de dependências externas para o ambiente de teste.
- Compilação do launcher `bash launcher/build.sh`.
- 16 verificadores de regressão existentes, mais `QKDv24CacheAndBoundaryVerification` e `QKDv24SaveStateVerification`: **18/18 aprovados**.
- Enlace com perdas MUX e DEMUX diferentes; circuitos clássicos e propagação longitudinal; estados de PICh adicionado/alterado/liberado; potências e espectros alterados; cache ligado/desligado; serialização e retomada de checkpoint parcial/concluído.
- A validação de checkpoint utilizou serialização Java real. O ambiente de compilação não dispunha de Gson real, e o progresso JSON foi exercitado apenas com stub (que não grava JSON válido). O launcher Java e a sintaxe JS foram verificados, mas não houve teste end-to-end de navegador + backend + Gson real.

**Ainda não executados:** `mvn clean test` com dependências reais, teste de carga com múltiplas replicações/QKD por longa duração e regressão dinâmica completa MF, MB, MB-SDM. Esta versão não deve ser declarada validada para publicação científica sem essas etapas.

## 5. Preservação de arquitetura

PICh permanece independente por QKDSpan e não pode atravessar nós do SNetS. Não foram adicionados canais contrapropagantes na mesma fibra. A escolha Johannisson/Habibi, as flags SCI/XCI (integral/logarítmica) e o truncamento das contribuições negativas de NLI em zero permanecem.

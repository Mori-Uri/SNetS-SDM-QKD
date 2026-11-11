# Correção do campo de atualização de chaves QKD (2026-11-10)

Campo removido como opção aceita: `updateIntervalsMileseconds`.
Campo substituto: `updateIntervalsMiliseconds`, unidade **milissegundos**.

Exemplo: `"qkdKeyDemand":{"profile":"custom","keySizeBits":256,"updateIntervalsMiliseconds":[64,128,256]}`.

O campo `updateIntervalsMilliseconds` (grafia inglesa ortograficamente correta) permanece como alias para não interromper cenários existentes. Também continuam disponíveis `updateIntervalsMicroseconds` e `updateIntervalSeconds`. Use somente um campo de intervalo por configuração.

Para impedir o uso silencioso de intervalos padrão por causa de uma grafia removida, `SimulationConfig` mantém uma sentinela privada com o identificador antigo exclusivamente para produzir uma exceção de validação quando o Gson encontra esse campo. A sentinela não participa da seleção de intervalos.

## Testes no Windows

Recompile o simulador e execute `qkd.verification.QKDKeyIntervalSecondsVerification` e as regressões QKD de seu ambiente. O Launcher 1.24.1 valida a nova grafia antes de salvar/executar o caso.

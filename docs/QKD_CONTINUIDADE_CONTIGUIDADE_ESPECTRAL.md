# Continuidade e contiguidade espectral QKD — Etapa 1 (11/11/2026)

## Regras de admissão

- `qchGranularity=span`: cada `QKDSpan` delimitado por **nós confiáveis** é uma sessão óptica QKD independente. A portadora QCh precisa ocupar slots contíguos *dentro* de cada seção física, mas **não** precisa repetir a frequência/slot/núcleo do trecho QKD anterior. Vários QChs em paralelo para atender uma sessão podem utilizar portadoras diferentes; isso não significa fragmentar um único QCh.
- `qchGranularity=link`: cada portadora QCh usa um bloco contíguo no enlace. Se `qchSlotContinuityAtNetworkNodes=true` em uma rota com vários enlaces, preserva-se o comportamento estrito histórico entre enlaces, inclusive para combinações de múltiplas portadoras para as quais ainda não há mapeamento por lane. Quando a continuidade entre nós físicos está desabilitada, múltiplas portadoras independentes podem ser utilizadas.
- `qchGranularity=route`: o circuito óptico é associado à rota e mantém a regra de continuidade ou conversão entre *nós físicos da rede SNetS* conforme `qchSlotContinuityAtNetworkNodes`.
- PICh óptico dedicado: cada `QKDSpan` estabelece sua seção independente. `pichSlotContinuityAtNetworkNodes` continua a controlar apenas o que se aplica à travessia de nós físicos de rede nos fluxos que usam continuidade; a restrição de banda *entre* QKDSpans não existe. `pichTransport=ip` não reserva PICh óptico.
- O core e os slots reservados são liberados no encerramento e revertidos em falha de admissão. Reuso longitudinal de QCh/PICh não equivale a permitir ocupações simultâneas de um mesmo recurso espectral por circuitos incompatíveis.
- Sem alterações nas regras de continuidade dos DCh clássicos, na propagação direcional (duas fibras independentes), na física da QKD ou no cálculo de SKR/QBER.

## Configuração

```json
{ "qkd": { "enabled": true, "qchGranularity": "span", "qchSlotContinuityAtNetworkNodes": true, "pichSlotContinuityAtNetworkNodes": true } }
```

Mesmo com ambos os campos `true`, o modo `span` permite conversão/reseleção entre trechos QKD separados por nós confiáveis; não há exigência de que a banda de um trecho coincida com a do próximo.

## Testes

```bat
java -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDSpanGranularityVerification
java -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDSlotContinuityVerification
java -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.QKDPichAdmissionSafetyVerification
java -cp simulations\SNetS-SDM-QKD-exp.jar qkd.verification.ModulationReachStartupVerification
```

Para executar, recompilar o núcleo/gerar o JAR experimental primeiro; executar testes contra um JAR antigo não verifica os arquivos atualizados. O script `scripts/verificar_continuidade_espectral_qkd.bat` também está disponível no projeto original.

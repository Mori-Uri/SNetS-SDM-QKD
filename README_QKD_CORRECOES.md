# Correções SNetS-SDM-QKD — 08/11/2026

**Código-fonte Java incluído, não binários compilados.** Para conferir alterações, cenários, testes e limitações, leia [`docs/RELATORIO_IMPLEMENTACAO_QKD_2026-11-08.md`](docs/RELATORIO_IMPLEMENTACAO_QKD_2026-11-08.md).

## Verificação obrigatória no Windows 11

Instale/configure JDK compatível e Maven com acesso às dependências do `pom.xml`. No terminal, a partir da raiz do repositório:

```bat
scripts\ci_qkd_clean.bat
```

O script apaga `target`, recompila as fontes, resolve as dependências e executa as classes Java `qkd.verification.*Verification` individualmente, com logs em `target\qkd-regression\`. **A execução integral não foi possível no ambiente de entrega**, que não possui Maven.

## Perfil de demanda de chaves

No arquivo `simulation`, em `qkdKeyDemand`: `"profile":"moghaddam2021"` para períodos **64/128/256 ms** (0,004/0,002/0,001 Mbit/s), ou `"profile":"dibaj2024"` para **64/128/256 µs** (4/2/1 Mbit/s), com chave de 256 bits. Use `"preferGeneratorFixedRate":false` para impedir que um `traffic.requestGenerators[].keyRateMbps` substitua o perfil.

## Parâmetros de rede

`network.qkd.pichOsnrCheckEnabled=true` e `network.qkd.qberThresholdEnabled=false` por padrão. O valor `maximumQber=0.11` continua configurável, mas só é imposto quando `qberThresholdEnabled=true`. `network.qkd.pichRxPowerCheckEnabled=false` permanece por padrão porque demanda sensibilidade de receptor calibrada. `network.qkd.longitudinalXtEnabled` e `network.qkd.pichLongitudinalReuse` são opções explícitas e conservadoras (padrão `false`).

## Cenários e otimização

- `simulations/QKD_Referencias/*` — cenários distintos, com descrições em cada `REFERENCIA.txt`.
- `python scripts/validar_perfis_qkd.py --strict` — verifica consistência estrutural das hipóteses.
- `python scripts/otimizar_nos_confiaveis_qkd.py --help` — gera alternativas QTND/por enlace e classifica resultados **medidos no SNetS**.

**Limites não resolvidos:** atomicidade completa de *grooming* com expansão por algoritmos arbitrários; reutilização de espectro de DCh em trechos internos da fibra; posicionamento arbitrário geográfico de nós confiáveis; validação física experimental e build/regressão Maven integral.

## Referência de potência e QBER opcional (08/11/2026)

`network.PhysicalLayer.computeCascadedMaximumPower()` conserva `referenceW=1.0e-3` (1 mW) como **escolha numérica de normalização**, não calibração experimental. O novo `network.CascadedMaximumPowerReferenceVerification` compara 7 referências entre 0,1 e 10 mW em 8 configurações GN (56 comparações); todas passaram, com erro relativo máximo de 4,15e-16 no caminho de ganho fixo/SCI cúbica. O gate independente de QBER é opcional (`qberThresholdEnabled=false` por padrão): Moghaddam/Dibaj usam SKR na admissão. Detalhes e limites em [`docs/REFERENCIA_POTENCIA_E_QBER_OPCIONAL.md`](docs/REFERENCIA_POTENCIA_E_QBER_OPCIONAL.md).

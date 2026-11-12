# SNetS-SDM-QKD — Etapa 5 (validação científica e correções end-to-end)

## Dependência

Instalar **depois** da Etapa 4. O manifesto SHA-256 rejeita arquivos locais divergentes em vez de sobrescrevê-los.

## Arquivos do patch

- `src/main/java/network/PhysicalLayer.java` — separação entre GN do mesmo núcleo e XT entre núcleos; correção da condição matemática de sobreposição entre canais de diferentes larguras.
- `src/main/java/simulationControl/SimulationFileManager.java` — exportação de `network` como JSON válido quando campos opcionais QKD usam `NaN` como sentinela de ausência.
- `src/main/java/qkd/verification/QKDStage5QuantitativeSweepVerification.java` — 84 comparações da Eq. 11/15 do GN de Johannisson com quadratura independente, diferença da Eq. 16 relatada separadamente.
- `src/main/java/qkd/verification/QKDStage5SimulationIntegrityVerification.java` — testes de exportação, mesmo núcleo/núcleos diferentes e larguras não uniformes.
- `src/main/java/qkd/verification/QKDStage5MultibandQuantitativeVerification.java` — dados da comparação MF/MB com Dibaj, incluindo a divergência MB **não resolvida**.
- `scripts/validar_cientificamente_etapa5.py` — orquestrador, rastreio de pendências, campanha com replicações e IC95 de Student, comparação pareada stock-rate.
- `scripts/validar_etapa5.bat` — comando simplificado no Windows.
- `docs/ETAPA5_VALIDACAO_CIENTIFICA_REPRODUTIVEL.md` — protocolos, hipóteses, convenções e limitações.
- `simulations/SNetS-SDM-QKD-exp.jar` — somente no pacote **com binários**.

## Instalação (Windows 11)

Na pasta extraída do ZIP:

```powershell
powershell -ExecutionPolicy Bypass -File .\instalar_etapa5.ps1 -ProjectRoot "C:\Users\SEU_USUARIO\eclipse-workspace\SNetS-SDM-qkd-v1" -VerificarApenas
powershell -ExecutionPolicy Bypass -File .\instalar_etapa5.ps1 -ProjectRoot "C:\Users\SEU_USUARIO\eclipse-workspace\SNetS-SDM-qkd-v1"
```

Para o pacote **somente fontes**, acrescentar `-SomenteFontes` e recompilar o JAR com o build existente após instalar as fontes. O instalador cria backup e verifica os hashes antes e depois da cópia. Se detectar alteração local diferente da Etapa 4, não sobrescreve.

## Verificação

```powershell
cd "C:\Users\SEU_USUARIO\eclipse-workspace\SNetS-SDM-qkd-v1"
.\scripts\validar_etapa5.bat completo resultados_etapa5\suite_completa
```

Também é possível executar:

```powershell
python .\scripts\validar_cientificamente_etapa5.py verificar --modo completo --strict-pending --saida resultados_etapa5\strict
```

Esta segunda forma retorna erro intencional enquanto persistirem pendências quantitativas de literatura, mesmo que nenhum teste tenha falhado.

## Resultados executados nesta entrega

- Compilação de **452 fontes Java** do núcleo com `javac --release 8` (OpenJDK 21).
- **36 verificadores Java executados**, 32 PASS, 4 PENDENTE, zero FAIL/TIMEOUT. Mais um oráculo Python: **33 PASS + 4 PENDENTE**, 37 verificações registradas.
- **84 casos Johannisson** SCI/XCI: maior desvio relativo frente à quadratura independente aproximadamente **1,48×10⁻⁹**.
- Equações da tese de Jurandir: 12 valores GN/EGN para 1–50 spans, concordância com oráculo Python a menos de **10⁻⁶ dB**. Resultado do motor SNetS 28,086036330 dB (1 span) e 45,075736373 dB (50 spans), sujeito às aproximações do modelo.
- Dibaj 2024: MB 1380 nm QTND ótima 20 km, ganho calculado de **74,697%** versus **40%** da figura. Essa divergência **permanece pendente**; não ajustamos parâmetros físicos sem dados.
- Mehdizadeh 2024: cinco casos QBER na banda E com divergência, explicitados em `mehdizadeh2024_qber_divergencias.csv`.
- **Quatro execuções end-to-end** na topologia de cinco nós (2 sementes × modos `rate` e `stock`, 40 requisições e 3 pontos de carga, proteção QKD 50%) concluídas e com métricas exportadas. Esses são **testes rápidos**, não campanha de resultados publicáveis.
- O teste de campanha nas topologias maiores Linha/Losango com configuração multibanda foi interrompido por limite de tempo no ambiente desta análise. Os comandos/scripts estão disponíveis para a execução completa no Windows, com o tempo e número de réplicas adequados.

## Não considerar validado

Aprovação de testes matemáticos e execução de poucos eventos não demonstram calibração experimental do EDFA, EGN integral rigoroso, ISRS-GN completo, equivalência com SSFM, reprodução do ganho MB de 40%, seção de choque Raman não documentada, nem significância estatística em redes grandes. Os relatórios CSV distinguem explicitamente o que passou, o que permanece pendente e o que não foi executado.

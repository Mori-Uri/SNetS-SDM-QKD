@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
set "JAR=simulations\SNetS-SDM-QKD-exp.jar"
if not exist "%JAR%" (
 echo [ERRO] JAR nao encontrado: %JAR%
 exit /b 2
)
for %%C in (QKDTemporalKeyInventoryVerification QKDStage4InventoryDynamicsVerification QKDStage4EventLoopVerification QKDSpanGranularityVerification QKDDynamicLifecycleVerification) do (
 echo.
 echo [TESTE] %%C
 java -cp "%JAR%" qkd.verification.%%C
 if errorlevel 1 (
   echo [ERRO] Falhou: %%C
   exit /b 1
 )
)
echo.
echo [OK] Etapa 4: todos os verificadores passaram.
exit /b 0

@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "JAR=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
if not "%~1"=="" set "JAR=%~1"
if not exist "%JAR%" (
  echo [ERRO] JAR recompilado nao encontrado: "%JAR%"
  exit /b 2
)
where python >nul 2>nul
if errorlevel 1 (
  echo [ERRO] Python requerido para auditoria estaticas e cenarios.
  exit /b 2
)
python "%~dp0auditar_fibras_direcionais.py" "%ROOT%"
if errorlevel 1 exit /b 1
for %%C in (qkd.verification.QKDDirectedFibresVerification qkd.verification.MultiBandPhysicsVerification qkd.verification.MultiBandIwcitVerification qkd.verification.MultiBandNetworkVerification qkd.verification.QKDSpanGranularityVerification) do (
  echo [TESTE] %%C
  java -cp "%JAR%" %%C
  if errorlevel 1 exit /b 1
)
echo [OK] Fibras dirigidas e verificacoes aprovadas.
exit /b 0

@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "JAR=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
if not exist "%JAR%" (echo [ERRO] JAR nao encontrado: "%JAR%" & exit /b 2)
set "JAVAEXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVAEXE=%JAVA_HOME%\bin\java.exe"
for %%T in (SelectiveIntegrationVerification QKDSpanGranularityVerification QKDStage4InventoryDynamicsVerification QKDExistingSessionProtectionVerification QKDStage3AmplifierChainVerification QKDStage5QuantitativeSweepVerification) do (
    echo [INFO] Verificando %%T...
    "%JAVAEXE%" -Dsnets.verifyPerf=true -cp "%JAR%" qkd.verification.%%T
    if errorlevel 1 (echo [ERRO] %%T falhou. & exit /b 1)
)
where python >nul 2>nul
if not errorlevel 1 (
    python "%~dp0auditar_simbolos_obsoletos.py" "%ROOT%" --jar "%JAR%"
    if errorlevel 1 exit /b 1
)
echo [OK] Integracao seletiva verificada.
exit /b 0

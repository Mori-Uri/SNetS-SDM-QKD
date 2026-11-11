@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem Reconstrói o simulador ANTES de testar a continuidade espectral QCh/PICh.
rem Executar da pasta scripts: .\verificar_continuidade_espectral_qkd.bat
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "SRC=%ROOT%\src\main\java"
set "BASE=%ROOT%\simulations\SNetS-SDM-QKD.jar"
set "OUT=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
set "LAUNCHER=%ROOT%\launcher\snets-launcher.jar"
set "BACKUP=%ROOT%\simulations\backup_jar"
set "CHECKS=QKDSlotContinuityVerification,QKDPichAdmissionSafetyVerification,QKDMaxSkrRouteVerification"
set "JAVA_CMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not exist "%SRC%" (
    echo [ERRO] Fontes nao encontradas: %SRC%
    exit /b 1
)
if not exist "%BASE%" (
    echo [ERRO] JAR base nao encontrado: %BASE%
    exit /b 1
)
if not exist "%LAUNCHER%" (
    echo [ERRO] Launcher nao encontrado: %LAUNCHER%
    exit /b 1
)
echo [INFO] Reconstruindo simulador e verificando continuidade QCh/PICh...
echo [INFO] Compilacao: %SRC%
echo [INFO] JAR base: %BASE%
echo [INFO] Verificadores: %CHECKS%
"%JAVA_CMD%" -cp "%LAUNCHER%" launcher.JarBuilder --src "%SRC%" --base "%BASE%" --out "%OUT%" --backup "%BACKUP%" --verify --verify-only "%CHECKS%" --verify-timeout-seconds 1800
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
    echo [ERRO] Compilacao ou verificacoes falharam ^(exit=%RC%^).
    echo [INFO] Logs: %ROOT%\simulations\verification-reports
    exit /b %RC%
)
echo [OK] Compilacao e verificacoes espectrais QCh/PICh aprovadas.
exit /b 0

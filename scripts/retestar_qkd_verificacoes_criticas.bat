@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "ROOT=%~dp0.."
set "LAUNCHER=%ROOT%\launcher\snets-launcher.jar"
set "SIMULATOR=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
if not exist "%LAUNCHER%" (
    echo [ERRO] Launcher nao encontrado: %LAUNCHER%
    exit /b 1
)
if not exist "%SIMULATOR%" (
    echo [ERRO] Simulador nao encontrado: %SIMULATOR%
    exit /b 1
)
set "CHECKS=QKDExistingSessionProtectionVerification,QKDQualificationScenarioVerification,QKDSpanGranularityVerification"
if /i "%~1"=="full" set "CHECKS=%CHECKS%,QKDNativeArticleBaseVerification"
set "JAVA_CMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -cp "%LAUNCHER%" launcher.JarBuilder --verify-jar "%SIMULATOR%" --verify-only "%CHECKS%" --verify-timeout-seconds 1800
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" echo [ERRO] Existe pelo menos uma falha real; veja simulations\verification-reports.
exit /b %RC%

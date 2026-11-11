@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem Recompila as fontes ANTES de verificar: --verify-jar sozinho executa bytecode antigo.
rem O verificador SaveStateInputHashVerification testa SHA dos arquivos JSON sem serializacao reflexiva.
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "SRC=%ROOT%\src\main\java"
set "BASE=%ROOT%\simulations\SNetS-SDM-QKD.jar"
set "OUT=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
set "LAUNCHER=%ROOT%\launcher\snets-launcher.jar"
set "BACKUP=%ROOT%\simulations\backup_jar"
set "CHECKS=QKDExistingSessionProtectionVerification,QKDQualificationScenarioVerification,QKDSpanGranularityVerification,SaveStateInputHashVerification"
if /i "%~1"=="full" set "CHECKS=%CHECKS%,QKDNativeArticleBaseVerification"
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
rem Corrige fontes obsoletas deixadas por extracoes ZIP incrementais.
call "%~dp0remover_egn_full_reference.bat"
if errorlevel 1 (
    echo [ERRO] Nao foi possivel limpar classes EGN antigas.
    exit /b 1
)
call "%~dp0limpar_bytecode_bifibra.bat"
if errorlevel 1 exit /b 1
rem Audita o codigo ANTES do javac, com erro claro se houver classes antigas.
rem Nao importar TrackedRequestList isoladamente da experimental.
if exist "%SRC%\network\TrackedRequestList.java" (
    where python >nul 2>nul
    if errorlevel 1 (
        echo [ERRO] TrackedRequestList experimental incompleta detectada; Python necessario para a limpeza segura.
        exit /b 1
    )
    python "%~dp0limpar_tracked_experimental.py" "%ROOT%"
    if errorlevel 1 exit /b 1
)
where python >nul 2>nul
if not errorlevel 1 (
    python "%~dp0auditar_simbolos_obsoletos.py" "%ROOT%"
    if errorlevel 1 exit /b 1
    python "%~dp0auditar_fibras_direcionais.py" "%ROOT%"
    if errorlevel 1 exit /b 1
)
echo [INFO] Reconstruindo o JAR a partir das fontes atuais...
echo [INFO] Fonte: %SRC%
echo [INFO] Base:  %BASE%
echo [INFO] Saida: %OUT%
echo [INFO] Testes: %CHECKS%
"%JAVA_CMD%" -cp "%LAUNCHER%" launcher.JarBuilder --src "%SRC%" --base "%BASE%" --out "%OUT%" --backup "%BACKUP%" --verify --verify-only "%CHECKS%" --verify-timeout-seconds 1800
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
    echo [ERRO] Recompilacao ou testes falharam ^(exit=%RC%^).
    echo [INFO] Logs completos: %ROOT%\simulations\verification-reports
    exit /b %RC%
)
echo [OK] JAR reconstruido e testes selecionados aprovados.
exit /b 0

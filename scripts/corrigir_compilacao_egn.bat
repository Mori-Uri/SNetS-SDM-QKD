@echo off
setlocal EnableExtensions DisableDelayedExpansion
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "SRC=%ROOT%\src\main\java"
set "BASE=%ROOT%\simulations\SNetS-SDM-QKD.jar"
set "OUT=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
set "LAUNCHER=%ROOT%\launcher\snets-launcher.jar"
set "JAVA_CMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not exist "%BASE%" (echo [ERRO] JAR base inexistente: "%BASE%" & exit /b 2)
if not exist "%LAUNCHER%" (echo [ERRO] Launcher inexistente: "%LAUNCHER%" & exit /b 2)
echo [1/4] Limpando as fontes antigas EGN...
call "%~dp0remover_egn_full_reference.bat"
if errorlevel 1 exit /b 1
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
if errorlevel 1 (
    echo [INFO] Python nao encontrado. Prosseguindo; javac fara a verificacao de referencias.
) else (
    echo [2/4] Auditando referencias Java...
    python "%~dp0auditar_simbolos_obsoletos.py" "%ROOT%"
    if errorlevel 1 exit /b 1
)
echo [3/4] Compilando e reconstruindo JAR sem os tipos removidos...
"%JAVA_CMD%" -cp "%LAUNCHER%" launcher.JarBuilder --src "%SRC%" --base "%BASE%" --out "%OUT%" --backup "%ROOT%\simulations\backup_jar"
if errorlevel 1 (echo [ERRO] Falha ao construir o JAR. & exit /b 1)
echo [4/4] Testando GN e Habibi atuais...
"%JAVA_CMD%" -cp "%OUT%" verification.HabibiGnComparisonVerification "%ROOT%\simulations\habibi_gn_pos_correcao.csv"
if errorlevel 1 exit /b 1
"%JAVA_CMD%" -cp "%OUT%" qkd.verification.QKDNliIntegrationModesVerification
if errorlevel 1 exit /b 1
where python >nul 2>nul
if not errorlevel 1 (
    python "%~dp0auditar_simbolos_obsoletos.py" "%ROOT%" --jar "%OUT%"
    if errorlevel 1 exit /b 1
)
echo [OK] Fontes antigas removidas, JAR reconstruido e testes GN aprovados.
exit /b 0

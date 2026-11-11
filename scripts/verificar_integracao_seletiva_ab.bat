@echo off
setlocal EnableExtensions DisableDelayedExpansion
REM A/B reproducivel do mesmo JAR experimental, variando APENAS as flags de cache.
set "ROOT=%~dp0.."
set "JAR=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
if not "%~1"=="" set "JAR=%~1"
if not exist "%JAR%" (
  echo [ERRO] JAR ausente: %JAR%
  exit /b 2
)
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
set "DEST=%ROOT%\simulations\verification-reports\AB_seletivo"
if not exist "%DEST%" mkdir "%DEST%"
set "VERIFY=qkd.verification.QKDSelectivePerformanceABVerification"
echo [A] Mesmo JAR, caches desativados (referencia funcional)...
"%JAVA%" -Dsnets.noPerfCache=true -Dsnets.gnPairCache=false -cp "%JAR%" %VERIFY% > "%DEST%\A_sem_cache.log" 2>&1
if errorlevel 1 (
  echo [ERRO] Falha na referencia A; veja %DEST%\A_sem_cache.log
  exit /b 2
)
echo [B] Caches v1 + otimizacao GN experimental ativada e verificada...
"%JAVA%" -Dsnets.noPerfCache=false -Dsnets.gnPairCache=true -Dsnets.verifyPerf=true -Dsnets.perfStats=true -cp "%JAR%" %VERIFY% > "%DEST%\B_cache_ativo.log" 2>&1
if errorlevel 1 (
  echo [ERRO] Falha no tratamento B; veja %DEST%\B_cache_ativo.log
  exit /b 2
)
findstr /B /C:"METRIC " "%DEST%\A_sem_cache.log" > "%DEST%\A_metricas.txt"
findstr /B /C:"METRIC " "%DEST%\B_cache_ativo.log" > "%DEST%\B_metricas.txt"
fc /b "%DEST%\A_metricas.txt" "%DEST%\B_metricas.txt" > "%DEST%\comparacao.txt"
if errorlevel 1 (
  echo [ERRO] RESULTADOS A/B DIFERENTES; ver %DEST%\comparacao.txt
  exit /b 1
)
echo [OK] As metricas selecionadas sao identicas byte a byte.
echo [INFO] Tempos, contadores e demais efeitos devem ser comparados separadamente.
echo [INFO] Nao extrapolar para todas as simulacoes nem para desempenho global.
exit /b 0

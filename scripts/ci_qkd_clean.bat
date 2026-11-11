@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0.."
where mvn >nul 2>&1
if errorlevel 1 (
  echo [ERRO] Maven nao foi encontrado no PATH. Instale Maven e JDK e repita.
  exit /b 2
)
where java >nul 2>&1
if errorlevel 1 (
  echo [ERRO] Java nao foi encontrado no PATH.
  exit /b 2
)
if exist target rmdir /s /q target
if exist target (
  echo [ERRO] Nao foi possivel remover target antigo.
  exit /b 2
)
call mvn -B -q -DskipTests clean package
if errorlevel 1 (
  echo [ERRO] Compilacao limpa falhou.
  exit /b 1
)
call mvn -B -q dependency:build-classpath -Dmdep.outputFile=target\qkd-deps.cp
if errorlevel 1 (
  echo [ERRO] Falha ao obter o classpath Maven.
  exit /b 1
)
set /p DEPS=<target\qkd-deps.cp
if not exist target\qkd-regression mkdir target\qkd-regression
set /a ok=0
set /a failed=0
for %%F in (src\main\java\qkd\verification\*Verification.java) do (
  echo [RUN] %%~nF
  java -cp "target\classes;!DEPS!" qkd.verification.%%~nF > "target\qkd-regression\%%~nF.log" 2>&1
  if errorlevel 1 (
    set /a failed+=1
    echo [FAIL] %%~nF
    type "target\qkd-regression\%%~nF.log"
  ) else (
    set /a ok+=1
    echo [OK] %%~nF
  )
)
echo [RUN] CascadedMaximumPowerReferenceVerification
java -cp "target\classes;!DEPS!" network.CascadedMaximumPowerReferenceVerification > "target\qkd-regression\CascadedMaximumPowerReferenceVerification.log" 2>&1
if errorlevel 1 (
  set /a failed+=1
  echo [FAIL] CascadedMaximumPowerReferenceVerification
  type "target\qkd-regression\CascadedMaximumPowerReferenceVerification.log"
) else (
  set /a ok+=1
  echo [OK] CascadedMaximumPowerReferenceVerification
)
echo [RESULTADO] OK=!ok! FAIL=!failed!
if !failed! GTR 0 exit /b 1
if !ok! LEQ 0 exit /b 1
exit /b 0

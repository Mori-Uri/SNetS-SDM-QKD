@echo off
setlocal
where mvn >nul 2>&1
if errorlevel 1 (
  echo [ERRO] Maven nao encontrado no PATH.
  exit /b 2
)
call mvn -q -DskipTests clean package
if errorlevel 1 exit /b 1
java -cp target\classes qkd.verification.QKDGnScientificCorrectionsVerification
if errorlevel 1 exit /b 1
echo [OK] GN v17: verificacao local aprovada.
endlocal

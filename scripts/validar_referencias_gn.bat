@echo off
setlocal
cd /d "%~dp0.."

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Maven nao encontrado no PATH.
    exit /b 1
)

call mvn -q -DskipTests dependency:copy-dependencies package
if errorlevel 1 exit /b 1

set "CP=target\classes;target\dependency\*"

for %%C in (
    qkd.verification.QKDThesisFiguresVerification
    qkd.verification.QKDThesisSNetSPhysicalLayerVerification
    qkd.verification.QKDThesisCrosstalkVerification
) do (
    java -cp "%CP%" %%C
    if errorlevel 1 exit /b 1
)

python validacao_cientifica_v18\reproduzir_modelos.py
if errorlevel 1 exit /b 1

echo [OK] Validacoes GN e XT executadas.
endlocal

@echo off
setlocal
cd /d "%~dp0.."
if not exist "simulations\SNetS-SDM-QKD-exp.jar" (
    echo [ERRO] JAR nao encontrado em simulations\SNetS-SDM-QKD-exp.jar
    exit /b 2
)
where python >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Python 3 nao encontrado no PATH
    exit /b 2
)
if "%~1"=="" (
    echo Uso: scripts\validar_etapa5.bat rapid ou completo [caminho_saida]
    echo Exemplo: scripts\validar_etapa5.bat rapido resultados_etapa5\rapido
    exit /b 2
)
set "MODO=%~1"
if "%~2"=="" (set "SAIDA=resultados_etapa5\%MODO%") else (set "SAIDA=%~2")
python scripts\validar_cientificamente_etapa5.py verificar --modo %MODO% --saida "%SAIDA%"
exit /b %errorlevel%

@echo off
REM SNetS Launcher - interface local para configurar e executar o SNetS.
REM Versao EXPERIMENTAL: porta 8766 (a principal usa a 8765).
REM Parte 59: abre como aplicativo (janela propria e icone na bandeja do sistema), sem janela de console.
REM As mensagens vao para launcher\launcher.log. Para ve-las numa janela, use SNetS-Launcher-console.bat.
cd /d "%~dp0"

REM Atualizacao baixada pelo launcher (Configuracoes > Aplicativo): aplicada antes de abrir.
if exist "%~dp0.update\aplicar.zip" (
  echo Aplicando a atualizacao baixada...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -LiteralPath '%~dp0.update\aplicar.zip' -DestinationPath '%~dp0..'"
  if errorlevel 1 (
    echo Nao foi possivel aplicar a atualizacao. O launcher abre na versao atual.
    pause
  ) else (
    move /y "%~dp0.update\aplicar.zip" "%~dp0.update\aplicada.zip" >nul
  )
)

where javaw >nul 2>nul
if errorlevel 1 (
  java -jar "%~dp0snets-launcher.jar" %*
  if errorlevel 1 pause
  exit /b
)
start "" javaw -jar "%~dp0snets-launcher.jar" %*

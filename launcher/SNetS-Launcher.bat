@echo off
REM SNetS Launcher - interface web local para configurar e executar o SNetS.
REM Versao EXPERIMENTAL: abre o navegador em http://localhost:8766 (a principal usa a 8765). Feche esta janela para encerrar.
cd /d "%~dp0"
java -jar "%~dp0snets-launcher.jar" %*
if errorlevel 1 pause

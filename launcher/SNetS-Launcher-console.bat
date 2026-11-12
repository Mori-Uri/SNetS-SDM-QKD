@echo off
REM SNetS Launcher com janela de console (como antes da Parte 59): mostra as mensagens do launcher.
REM Versao EXPERIMENTAL: porta 8766. Fechar esta janela encerra o launcher e as execucoes.
cd /d "%~dp0"
java -Dsnets.logFile=false -jar "%~dp0snets-launcher.jar" %*
if errorlevel 1 pause

@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "JAR=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
if not exist "%JAR%" (
  echo [ERRO] %JAR% nao encontrado. Reconstrua pelo Launcher primeiro.
  exit /b 2
)
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
for %%T in (
  qkd.verification.OSNRNamingVerification
  qkd.verification.ClassicalBerQotVerification
  qkd.verification.JurandirBerThesisVerification
  qkd.verification.QKDThesisSNetSPhysicalLayerVerification
  qkd.verification.QKDv26AseFrequencyParityVerification
  qkd.verification.QKDPichSharedClassicalQoTVerification
) do (
  echo [OSNR] %%T
  "%JAVA_EXE%" -cp "%JAR%" %%T
  if errorlevel 1 (
    echo [ERRO] %%T falhou.
    exit /b 1
  )
)
echo [OK] Testes de nomenclatura e QoT/OSNR aprovados.
exit /b 0

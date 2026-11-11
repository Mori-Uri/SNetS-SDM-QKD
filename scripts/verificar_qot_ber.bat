@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "JAR=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
if not exist "%JAR%" (
  echo [ERRO] %JAR% nao encontrado.
  echo [INFO] Execute primeiro: .\recompilar_e_retestar_qkd.bat full
  exit /b 1
)
set "JAVAEXE=java.exe"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVAEXE=%JAVA_HOME%\bin\java.exe"
echo [INFO] Verificando BER classica no JAR atual: %JAR%
"%JAVAEXE%" -cp "%JAR%" qkd.verification.ClassicalBerQotVerification
if errorlevel 1 (
  echo [ERRO] Verificacao BER reprovada.
  exit /b 2
)
"%JAVAEXE%" -cp "%JAR%" qkd.verification.JurandirBerThesisVerification
if errorlevel 1 (
  echo [ERRO] Equacoes da tese de Jurandir nao conferem com os valores de referencia.
  exit /b 3
)
"%JAVAEXE%" -cp "%JAR%" qkd.verification.BerModelABComparisonVerification
if errorlevel 1 (
  echo [ERRO] Comparacao numerica BER A/B reprovada.
  exit /b 4
)
echo [OK] Verificacoes BER, tese e comparacao A/B aprovadas.
exit /b 0

@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "JAR=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
if not exist "%JAR%" (
  echo [ERRO] JAR nao encontrado: "%JAR%"
  echo Execute antes: .\recompilar_e_retestar_qkd.bat full
  exit /b 2
)
for %%T in (
  qkd.verification.QKDNliIntegrationModesVerification
  qkd.verification.QKDNarrowbandHabibiVerification
  qkd.verification.QKDJohannissonModeVerification
  qkd.verification.QKDJohannissonSciModeVerification
  qkd.verification.QKDJohannissonExactXciVerification
  qkd.verification.QKDLongitudinalGNVerification
  qkd.verification.QKDv26AseFrequencyParityVerification
  verification.HabibiGnComparisonVerification
) do (
  echo [TESTE] %%T
  java -cp "%JAR%" %%T
  if errorlevel 1 (echo [FALHA] %%T & exit /b 1)
)
python "%~dp0auditar_simbolos_obsoletos.py" "%ROOT%"
if errorlevel 1 exit /b 1
echo [OK] Integracao NLI + referencias estaticas.
exit /b 0

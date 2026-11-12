@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "JAR=%ROOT%\simulations\SNetS-SDM-QKD-exp.jar"
if not exist "%JAR%" (
  echo [ERRO] JAR nao encontrado: "%JAR%"
  echo Execute antes: .\recompilar_e_retestar_qkd.bat
  exit /b 2
)
for %%T in (
  qkd.verification.JurandirBerThesisVerification
  qkd.verification.ClassicalBerQotVerification
  qkd.verification.QKDNarrowbandHabibiVerification
  qkd.verification.QKDStage2NliSafetyVerification
  qkd.verification.QKDJohannissonModeVerification
  qkd.verification.QKDJohannissonSciModeVerification
  qkd.verification.QKDJohannissonExactXciVerification
  qkd.verification.QKDLongitudinalGNVerification
  verification.PhysicalModelRefactorVerification
  verification.HabibiGnComparisonVerification
  network.Poggiolini2017GnIntegral
) do (
  echo [TESTE] %%T
  java -cp "%JAR%" %%T
  if errorlevel 1 (echo [FALHA] %%T & exit /b 1)
)
echo [OK] Verificacoes BER, polarizacoes, Johannisson, Habibi e GN.
exit /b 0

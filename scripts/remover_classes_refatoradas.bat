@echo off
setlocal enableextensions
REM Execute in Windows after extracting the 'somente-alteracoes' ZIP into the project.
set "ROOT=%~dp0.."
for %%C in (AmplifierGainLaw HabibiEgnHybrid HabibiEgnComparisonVerification LobatoSpectralOverlap PoggioliniGnIntegral Poggiolini2017HybridVerification) do (
  if exist "%ROOT%\src\main\java\network\%%C.java" del /q "%ROOT%\src\main\java\network\%%C.java"
  if exist "%ROOT%\target\classes\network\%%C.class" del /q "%ROOT%\target\classes\network\%%C.class"
)
echo [OK] Classes antigas removidas. Execute a compilacao limpa.

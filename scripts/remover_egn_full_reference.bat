@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem Remove fontes obsoletas que ZIPs incrementais nao conseguem excluir.
rem Os .java removidos sao copiados para um backup fora da arvore src.
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "BACKUP=%ROOT%\simulations\backup_fontes_egn_removidas"
if not exist "%BACKUP%\network" mkdir "%BACKUP%\network" || exit /b 1
if not exist "%BACKUP%\verification" mkdir "%BACKUP%\verification" || exit /b 1
for %%C in (EgnFullReference Poggiolini2017EgnReference AmplifierGainLaw HabibiEgnHybrid LobatoSpectralOverlap PoggioliniGnIntegral Poggiolini2017HybridVerification) do (
    if exist "%ROOT%\src\main\java\network\%%C.java" (
        echo [INFO] Removendo fonte descontinuada network/%%C.java
        if not exist "%BACKUP%\network\%%C.java" copy /y "%ROOT%\src\main\java\network\%%C.java" "%BACKUP%\network\%%C.java" >nul || exit /b 1
        del /f /q "%ROOT%\src\main\java\network\%%C.java" || exit /b 1
    )
)
for %%P in (network verification) do (
    if exist "%ROOT%\src\main\java\%%P\HabibiEgnComparisonVerification.java" (
        echo [INFO] Removendo fonte descontinuada %%P/HabibiEgnComparisonVerification.java
        if not exist "%BACKUP%\%%P\HabibiEgnComparisonVerification.java" copy /y "%ROOT%\src\main\java\%%P\HabibiEgnComparisonVerification.java" "%BACKUP%\%%P\HabibiEgnComparisonVerification.java" >nul || exit /b 1
        del /f /q "%ROOT%\src\main\java\%%P\HabibiEgnComparisonVerification.java" || exit /b 1
    )
)
rem Apaga SOMENTE classes removidas, incluindo classes internas e backups Eclipse.
for %%D in ("%ROOT%\bin" "%ROOT%\target\classes" "%ROOT%\build\classes") do (
    for %%C in (EgnFullReference Poggiolini2017EgnReference AmplifierGainLaw HabibiEgnHybrid LobatoSpectralOverlap PoggioliniGnIntegral Poggiolini2017HybridVerification) do (
        del /f /q "%%~D\network\%%C.class" 2>nul
        del /f /q "%%~D\network\%%C$*.class" 2>nul
    )
    del /f /q "%%~D\network\HabibiEgnComparisonVerification*.class" 2>nul
    del /f /q "%%~D\verification\HabibiEgnComparisonVerification*.class" 2>nul
    del /f /q "%%~D\network\PhysicalLayer$HabibiEgnDecision*.class" 2>nul
    del /f /q "%%~D\network\PhysicalLayer$HabibiEgnCacheKey*.class" 2>nul
)
if exist "%ROOT%\src\main\java\network\EgnFullReference.java" exit /b 1
if exist "%ROOT%\src\main\java\network\Poggiolini2017EgnReference.java" exit /b 1
if exist "%ROOT%\src\main\java\verification\HabibiEgnComparisonVerification.java" exit /b 1
if not exist "%ROOT%\src\main\java\network\Poggiolini2017GnIntegral.java" (
    echo [ERRO] O integrador GN atual nao existe; aplique o projeto mais recente.
    exit /b 2
)
if not exist "%ROOT%\src\main\java\verification\HabibiGnComparisonVerification.java" (
    echo [ERRO] O verificador GN atual nao existe; aplique o projeto mais recente.
    exit /b 2
)
echo [OK] Fontes EGN antigas e suas classes locais retiradas.
echo [INFO] Backup opcional em "%BACKUP%"
endlocal
exit /b 0

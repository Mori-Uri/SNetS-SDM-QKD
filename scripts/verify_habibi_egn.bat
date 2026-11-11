@echo off
setlocal enableextensions
REM The hybrid kernels now belong to PhysicalLayer; use a full clean build.
set "ROOT=%~dp0.."
set "CSV=%ROOT%\build\habibi_gn_ab_results.csv"
if not "%~1"=="" set "CSV=%~1"
where mvn >nul 2>nul || (echo [ERRO] Maven nao encontrado. & exit /b 2)
pushd "%ROOT%"
call mvn -q -DskipTests clean package
if errorlevel 1 (popd & exit /b 1)
popd
java -cp "%ROOT%\target\classes" verification.HabibiGnComparisonVerification "%CSV%"
if errorlevel 1 exit /b 1
java -cp "%ROOT%\target\classes" network.Poggiolini2017GnIntegral "%ROOT%\build\poggiolini_gn_integral.csv"
exit /b %errorlevel%

@echo off
REM Compila o SNetS Launcher e empacota usando a biblioteca Java (sem jar.exe).
REM Requer javac.exe (JDK) e java.exe (JRE ou JDK); nao requer Maven.
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
if errorlevel 1 (
    echo [ERRO] Nao foi possivel acessar a pasta launcher.
    pause
    exit /b 1
)

call :localizar_java
if not defined JAVAC goto :erro_javac
if not defined JAVAEXE goto :erro_java

echo =============================================
echo Compilando SNetS Launcher para Java 8
 echo =============================================
echo [INFO] Compilador: "%JAVAC%"
echo [INFO] Runtime:    "%JAVAEXE%"
"%JAVAC%" -version
"%JAVAEXE%" -version

if not exist "src\launcher\LauncherArchiveBuilder.java" (
    echo [ERRO] LauncherArchiveBuilder.java nao encontrado na pasta src\launcher.
    echo [INFO] Aplique o ZIP completo desta correcao, incluindo o novo arquivo Java.
    goto :erro_compilacao
)

if exist "build" rmdir /s /q "build"
if not exist "build\classes" mkdir "build\classes"
if errorlevel 1 goto :erro_compilacao

"%JAVAC%" -help 2>&1 | findstr /c:"--release" >nul
if errorlevel 1 (
    echo [INFO] Compilacao Java 8 com -source/-target.
    "%JAVAC%" -source 8 -target 8 -encoding UTF-8 -d "build\classes" src\launcher\*.java
) else (
    echo [INFO] Compilacao Java 8 com --release 8.
    "%JAVAC%" --release 8 -Xlint:-options -encoding UTF-8 -d "build\classes" src\launcher\*.java
)
if errorlevel 1 goto :erro_compilacao

if not exist "web\index.html" (
    echo [ERRO] Recursos web ausentes.
    goto :erro_compilacao
)
if not exist "templates\physicalLayer" (
    echo [ERRO] Templates ausentes.
    goto :erro_compilacao
)
xcopy /e /i /q /y "web" "build\classes\web" >nul
if errorlevel 1 goto :erro_recursos
xcopy /e /i /q /y "templates" "build\classes\templates" >nul
if errorlevel 1 goto :erro_recursos

REM Java padrao: JarOutputStream + Manifest (independente de jar.exe).
"%JAVAEXE%" -cp "build\classes" launcher.LauncherArchiveBuilder "build\classes" "build\snets-launcher-new.jar"
if errorlevel 1 goto :erro_empacotamento
if not exist "build\snets-launcher-new.jar" goto :erro_empacotamento

REM Salva o JAR anterior antes da substituicao.
if exist "snets-launcher.jar" (
    copy /y "snets-launcher.jar" "snets-launcher.jar.bak" >nul
    if errorlevel 1 (
        echo [ERRO] Nao foi possivel salvar o backup snets-launcher.jar.bak.
        goto :erro_empacotamento
    )
)
move /y "build\snets-launcher-new.jar" "snets-launcher.jar" >nul
if errorlevel 1 goto :erro_empacotamento
rmdir /s /q "build"
echo.
echo [OK] snets-launcher.jar atualizado.
echo [INFO] O backup anterior esta em snets-launcher.jar.bak, se existia.
echo [INFO] Para testar: java -jar snets-launcher.jar --help
pause
exit /b 0

:localizar_java
set "JAVAC="
set "JAVAEXE="
REM 1. Prioriza os binarios reais no JAVA_HOME.
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC=%JAVA_HOME%\bin\javac.exe"
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVAEXE=%JAVA_HOME%\bin\java.exe"
)
REM 2. Permite ambientes onde javac e java estao no PATH, sem exigir jar.exe.
if not defined JAVAC for /f "delims=" %%J in ('where.exe javac 2^>nul') do if not defined JAVAC set "JAVAC=%%J"
REM Preferir java.exe ao lado de javac.exe; se nao existir, usar PATH.
if defined JAVAC if not defined JAVAEXE for %%J in ("%JAVAC%") do if exist "%%~dpJjava.exe" set "JAVAEXE=%%~dpJjava.exe"
if not defined JAVAEXE for /f "delims=" %%J in ('where.exe java 2^>nul') do if not defined JAVAEXE set "JAVAEXE=%%J"
exit /b 0

:erro_javac
echo.
echo [ERRO] javac.exe nao encontrado. E necessario instalar/selecionar um JDK.
echo [INFO] Verifique: where javac  e  echo %%JAVA_HOME%%
echo [INFO] Exemplo: set "JAVA_HOME=C:\Program Files\Java\jdk-21"
echo [INFO] Depois:  set "PATH=%%JAVA_HOME%%\bin;%%PATH%%"
pause
exit /b 1

:erro_java
echo.
echo [ERRO] java.exe nao encontrado. JDK/JRE ou PATH incompleto.
echo [INFO] Verifique: where java  e  echo %%JAVA_HOME%%
pause
exit /b 1

:erro_recursos
echo [ERRO] Falha ao copiar os arquivos web/templates.
goto :erro_compilacao

:erro_empacotamento
echo [ERRO] Falha ao empacotar ou substituir o JAR do Launcher.
echo [INFO] A versao antiga nao foi excluida intencionalmente.
pause
exit /b 1

:erro_compilacao
echo [ERRO] Compilacao/empacotamento nao concluido.
pause
exit /b 1

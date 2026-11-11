@echo off
setlocal EnableExtensions DisableDelayedExpansion
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
rem Exclui somente o inner-class antigo da direcao por fibra.
rem O JarBuilder tambem evita recopia-lo do JAR base.
for %%D in ("%ROOT%\target\classes" "%ROOT%\bin" "%ROOT%\simulations\.jarbuild\classes") do (
  if exist "%%~D\qkd\multiband\ClassicalChannel$Direction.class" (
    del /f /q "%%~D\qkd\multiband\ClassicalChannel$Direction.class"
    if errorlevel 1 exit /b 1
    echo [OK] Bytecode antigo removido: %%~D\qkd\multiband\ClassicalChannel$Direction.class
  )
)
echo [OK] Nenhum bytecode de direcao antiga pendente nas pastas conhecidas.
exit /b 0

@echo off
REM Parte 55: esconde do GitHub Desktop as alteracoes locais nos casos versionados em simulations\
REM (configuracoes que mudam quando voce roda estudos e que nao vao para o git).
REM Nao muda o repositorio nem o GitHub: vale so para esta pasta. Rode de novo depois de um merge ou pull
REM que traga casos novos em simulations\.
REM Para desfazer:  ocultar_simulations_locais.bat desfazer
REM Requer o git no PATH (Git for Windows). No GitHub Desktop: Repository > Open in Command Prompt.
setlocal
cd /d "%~dp0..\.."
where git >nul 2>nul
if errorlevel 1 (
  echo O comando git nao foi encontrado. Instale o Git for Windows ou rode pelo GitHub Desktop:
  echo Repository ^> Open in Command Prompt, e depois: tools\git\ocultar_simulations_locais.bat
  pause
  exit /b 1
)
if /i "%~1"=="desfazer" (
  git ls-files -- simulations | git update-index --no-skip-worktree --stdin
  echo As alteracoes em simulations\ voltam a aparecer no GitHub Desktop.
) else (
  git ls-files -- simulations | git update-index --skip-worktree --stdin
  echo Pronto: as alteracoes nos casos de simulations\ nao aparecem mais no GitHub Desktop.
)
pause

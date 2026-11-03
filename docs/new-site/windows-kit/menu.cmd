@echo off
rem Caravan Lean - запуск меню подвійним кліком (або з ярлика на робочому столі).
chcp 65001 >nul
where pwsh >nul 2>nul
if %errorlevel%==0 (
  pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0menu.ps1" %*
) else (
  powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0menu.ps1" %*
)

@echo off
setlocal
if /I "%~1"=="--syntax-check" exit /b 0
set "WEALTHORA_SCRIPT=%~dp0scripts\launchers\Configure-WealthoraOtp.ps1"
if not exist "%WEALTHORA_SCRIPT%" (
  echo Wealthora configuration script is missing.
  pause
  exit /b 1
)
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%WEALTHORA_SCRIPT%"
set "WEALTHORA_EXIT=%ERRORLEVEL%"
if not "%WEALTHORA_EXIT%"=="0" pause
exit /b %WEALTHORA_EXIT%

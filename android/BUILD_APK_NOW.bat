@echo off
call "%~dp0BUILD_APK.bat" build
if errorlevel 1 (
  echo.
  echo [ERROR] APK-Build fehlgeschlagen.
)
pause

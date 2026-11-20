@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat :server:assembleDebug :client:assembleDebug
if errorlevel 1 (
  echo.
  echo [ERRO] Build falhou. Confira Android SDK 35 e JDK 17.
  pause
  exit /b 1
)
echo.
echo [OK] APKs gerados:
echo %CD%\server\build\outputs\apk\debug\server-debug.apk
echo %CD%\client\build\outputs\apk\debug\client-debug.apk
pause

@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat :client:assembleDebug
if errorlevel 1 exit /b 1
echo APK: %CD%\client\build\outputs\apk\debug\client-debug.apk

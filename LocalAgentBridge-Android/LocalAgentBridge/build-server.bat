@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat :server:assembleDebug
if errorlevel 1 exit /b 1
echo APK: %CD%\server\build\outputs\apk\debug\server-debug.apk

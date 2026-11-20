@echo off
setlocal
set "PROJECT_DIR=%~dp0"
where gradle >nul 2>nul
if not errorlevel 1 (
  gradle -p "%PROJECT_DIR%" %*
  exit /b %ERRORLEVEL%
)
set "GRADLE_HOME_LOCAL=%USERPROFILE%\.gradle\local-agent-bridge\gradle-8.9"
if not exist "%GRADLE_HOME_LOCAL%\bin\gradle.bat" (
  echo Baixando Gradle 8.9 na primeira execucao...
  powershell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%gradle\wrapper\bootstrap-gradle.ps1"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_HOME_LOCAL%\bin\gradle.bat" -p "%PROJECT_DIR%" %*
exit /b %ERRORLEVEL%

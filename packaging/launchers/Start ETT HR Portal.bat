@echo off
rem Starts the ETT HR portal. Keep this window open while you use the portal; close it to stop.
setlocal
title ETT HR portal
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 goto nojava

set "JAVA_VERSION="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VERSION=%%~v"
for /f "delims=." %%m in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%m"
if not defined JAVA_MAJOR goto nojava
if %JAVA_MAJOR% LSS 25 goto nojava

java -jar ett-hr-portal.jar %*
if errorlevel 1 pause
exit /b

:nojava
echo The ETT HR portal needs Java 25 or later.
echo The download page will open. Install Java, then start the portal again.
start "" "https://adoptium.net/temurin/releases/?version=25"
pause
exit /b 1

@echo off
setlocal

cd /d "%~dp0"

call mvn -q package -DskipTests
if errorlevel 1 exit /b %errorlevel%

java -jar target\jmegamania.jar %*

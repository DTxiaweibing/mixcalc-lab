@echo off
setlocal
cd /d "%~dp0.."

set "APK=%~1"
if "%APK%"=="" set /p "APK=APK path (or drag the apk onto this file): "

if not exist "%APK%" goto :missing

echo [1/2] self check
python "%~dp0vivo_publish.py" doctor --apk "%APK%"
if errorlevel 1 goto :end

echo.
set /p "GO=Upload to vivo now? (y/N): "
if /i not "%GO%"=="y" goto :cancel

python "%~dp0vivo_publish.py" upload --apk "%APK%"
goto :end

:missing
echo File not found: %APK%
goto :end

:cancel
echo Cancelled, nothing was sent.

:end
echo.
pause

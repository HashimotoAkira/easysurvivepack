@echo off
setlocal
cd /d "%~dp0"

echo Building Easy Survival Pack for Minecraft 26.3...
call gradlew.bat build
set "BUILD_RESULT=%ERRORLEVEL%"

if not "%BUILD_RESULT%"=="0" (
	echo.
	echo Build failed with exit code %BUILD_RESULT%.
	if /I not "%~1"=="--no-pause" pause
	exit /b %BUILD_RESULT%
)

echo.
echo Build successful.
echo Output: "%~dp0dist\EasySurvivalPack-0.1.0+mc26.3.jar"
if /I not "%~1"=="--no-pause" pause
exit /b 0

@echo off
setlocal
cd /d "%~dp0"

echo Building Easy Survival Pack...
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
set "RELEASE_JAR="
for /f "delims=" %%F in ('dir /b /a-d /o-d "%~dp0dist\EasySurvivalPack-*.jar" 2^>nul') do if not defined RELEASE_JAR set "RELEASE_JAR=%%F"
if not defined RELEASE_JAR (
	echo No Easy Survival Pack JAR was found in the dist directory.
	if /I not "%~1"=="--no-pause" pause
	exit /b 1
)
echo Output: "%~dp0dist\%RELEASE_JAR%"
if not exist "%~dp0run\mods" mkdir "%~dp0run\mods"
copy /Y "%~dp0dist\%RELEASE_JAR%" "%~dp0run\mods\%RELEASE_JAR%" >nul
if errorlevel 1 (
	echo.
	echo Failed to install the mod JAR into "%~dp0run\mods".
	if /I not "%~1"=="--no-pause" pause
	exit /b 1
)
echo Installed for the local server: "%~dp0run\mods\%RELEASE_JAR%"
if /I not "%~1"=="--no-pause" pause
exit /b 0

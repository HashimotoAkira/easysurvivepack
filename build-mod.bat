@echo off
setlocal
cd /d "%~dp0"

set "MC_VERSION="
set "PAUSE_ON_EXIT=1"

:parse_args
if "%~1"=="" goto args_done
if /I "%~1"=="--no-pause" (
	set "PAUSE_ON_EXIT=0"
	shift
	goto parse_args
)
if /I "%~1"=="--help" goto usage
if not defined MC_VERSION (
	set "MC_VERSION=%~1"
	shift
	goto parse_args
)
echo Unknown argument: %~1
goto usage_error

:args_done
if defined MC_VERSION goto validate_version

echo Select Minecraft version to build:
echo   0^) Build all versions
echo   1^) Minecraft 1.21.1  ^(Java 21^)
echo   2^) Minecraft 26.3    ^(Java 25^)
choice /c 012 /n /m "Choose [0/1/2]: "
if errorlevel 3 (
	set "MC_VERSION=26.3"
) else (
	if errorlevel 2 (
		set "MC_VERSION=1.21.1"
	) else (
		set "MC_VERSION=all"
	)
)

:validate_version
if "%MC_VERSION%"=="1.21.1" goto configure_1211
if "%MC_VERSION%"=="26.3" goto configure_263
if "%MC_VERSION%"=="0" goto build_all
if /I "%MC_VERSION%"=="all" goto build_all
echo Unsupported Minecraft version: %MC_VERSION%
goto usage_error

:configure_1211
set "LOADER_VERSION=0.16.14"
set "FABRIC_API_VERSION=0.116.18+1.21.1"
goto build_selected

:configure_263
set "LOADER_VERSION=0.20.1"
set "FABRIC_API_VERSION=0.166.0+26.3"
goto build_selected

:build_selected
call :build_version "%MC_VERSION%" "%LOADER_VERSION%" "%FABRIC_API_VERSION%" "1"
set "BUILD_RESULT=%ERRORLEVEL%"
if not "%BUILD_RESULT%"=="0" goto build_failed
goto build_success

:build_all
call :build_version "1.21.1" "0.16.14" "0.116.18+1.21.1" "0"
set "BUILD_RESULT=%ERRORLEVEL%"
if not "%BUILD_RESULT%"=="0" goto build_failed
call :build_version "26.3" "0.20.1" "0.166.0+26.3" "0"
set "BUILD_RESULT=%ERRORLEVEL%"
if not "%BUILD_RESULT%"=="0" goto build_failed
echo.
echo Select the Minecraft version to install into run\mods:
echo   1^) Minecraft 1.21.1
echo   2^) Minecraft 26.3
choice /c 12 /n /m "Choose [1/2]: "
if errorlevel 2 (
	call :install_version "26.3"
) else (
	call :install_version "1.21.1"
)
set "BUILD_RESULT=%ERRORLEVEL%"
if not "%BUILD_RESULT%"=="0" goto build_failed
goto build_success

:build_success
echo.
echo Build successful.
if "%PAUSE_ON_EXIT%"=="1" pause
exit /b 0

:build_failed
echo.
echo Build failed with exit code %BUILD_RESULT%.
if "%PAUSE_ON_EXIT%"=="1" pause
exit /b %BUILD_RESULT%

:build_version
set "BUILD_MC_VERSION=%~1"
echo.
echo Building Easy Survival Pack for Minecraft %BUILD_MC_VERSION%...
call gradlew.bat build --no-daemon "-Pminecraft_version=%BUILD_MC_VERSION%" "-Ploader_version=%~2" "-Pfabric_api_version=%~3"
if errorlevel 1 exit /b %ERRORLEVEL%

:build_version_artifact
if "%~4"=="1" call :install_version "%BUILD_MC_VERSION%"
if errorlevel 1 exit /b 1
exit /b 0

:install_version
set "INSTALL_MC_VERSION=%~1"
set "RELEASE_JAR="
for /f "delims=" %%F in ('dir /b /a-d /o-d "%~dp0dist\EasySurvivalPack-*+mc%INSTALL_MC_VERSION%.jar" 2^>nul') do if not defined RELEASE_JAR set "RELEASE_JAR=%%F"
if not defined RELEASE_JAR (
	echo No Minecraft %INSTALL_MC_VERSION% JAR was found in the dist directory.
	exit /b 1
)
echo Output: "%~dp0dist\%RELEASE_JAR%"
if not exist "%~dp0run\mods" mkdir "%~dp0run\mods"
for %%F in ("%~dp0run\mods\EasySurvivalPack-*.jar") do (
	if exist "%%~fF" (
		del /q "%%~fF"
		if errorlevel 1 exit /b 1
	)
)
copy /Y "%~dp0dist\%RELEASE_JAR%" "%~dp0run\mods\%RELEASE_JAR%" >nul
if errorlevel 1 (
	echo.
	echo Failed to install the mod JAR into "%~dp0run\mods".
	exit /b 1
)
echo Installed for the local server: "%~dp0run\mods\%RELEASE_JAR%"
exit /b 0

:usage
echo Usage: build-mod.bat [0^|all^|1.21.1^|26.3] [--no-pause]
echo Use 0 or all to build every supported Minecraft version.
echo Build-all prompts for which version to install into run\mods.
echo Run without a version to choose from an interactive menu.
exit /b 0

:usage_error
echo Usage: build-mod.bat [0^|all^|1.21.1^|26.3] [--no-pause]
if "%PAUSE_ON_EXIT%"=="1" pause
exit /b 2

@echo off
setlocal
cd /d "%~dp0"

set "DRY_RUN=0"

:parse_args
if "%~1"=="" goto args_done
if /I "%~1"=="--dry-run" (
	set "DRY_RUN=1"
	shift
	goto parse_args
)
echo Unknown argument: %~1
goto usage_error

:args_done
set "MOD_VERSION="
for /f "tokens=1,* delims==" %%A in ('findstr /b /c:"mod_version=" gradle.properties') do set "MOD_VERSION=%%B"
if not defined MOD_VERSION (
	echo Could not read mod_version from gradle.properties.
	exit /b 1
)
set "RELEASE_TAG=v%MOD_VERSION%"

if "%DRY_RUN%"=="1" goto select_artifacts

where gh >nul 2>&1
if errorlevel 1 (
	echo GitHub CLI "gh" was not found. Install it and authenticate with "gh auth login".
	exit /b 1
)

gh auth status >nul 2>&1
if errorlevel 1 (
	echo GitHub CLI is not authenticated. Run "gh auth login" first.
	exit /b 1
)

git rev-parse --show-toplevel >nul 2>&1
if errorlevel 1 (
	echo This directory is not inside a Git repository.
	exit /b 1
)

set "HAS_CHANGES="
for /f "delims=" %%S in ('git status --porcelain --untracked-files^=all') do set "HAS_CHANGES=1"
if defined HAS_CHANGES (
	echo The Git worktree has uncommitted changes.
	echo Commit or stash the changes before publishing a release.
	exit /b 1
)

set "RELEASE_COMMIT="
for /f "delims=" %%C in ('git rev-parse HEAD 2^>nul') do set "RELEASE_COMMIT=%%C"
if not defined RELEASE_COMMIT (
	echo Could not determine the Git commit for this release.
	exit /b 1
)

echo Building Easy Survival Pack for Minecraft 1.21.1...
call gradlew.bat build --no-daemon "-Pminecraft_version=1.21.1" "-Ploader_version=0.16.14" "-Pfabric_api_version=0.116.18+1.21.1"
if errorlevel 1 goto build_failed

echo Building Easy Survival Pack for Minecraft 26.3...
call gradlew.bat build --no-daemon "-Pminecraft_version=26.3" "-Ploader_version=0.20.1" "-Pfabric_api_version=0.166.0+26.3"
if errorlevel 1 goto build_failed

:select_artifacts
set "RELEASE_JAR_1211=EasySurvivalPack-%MOD_VERSION%+mc1.21.1.jar"
set "RELEASE_JAR_263=EasySurvivalPack-%MOD_VERSION%+mc26.3.jar"
if not exist "dist\%RELEASE_JAR_1211%" (
	echo No Minecraft 1.21.1 JAR was found in dist.
	if "%DRY_RUN%"=="1" echo Run build-mod.bat all first, or run release.cmd without --dry-run.
	exit /b 1
)
if not exist "dist\%RELEASE_JAR_263%" (
	echo No Minecraft 26.3 JAR was found in dist.
	if "%DRY_RUN%"=="1" echo Run build-mod.bat all first, or run release.cmd without --dry-run.
	exit /b 1
)

echo Release tag: %RELEASE_TAG%
echo Artifacts:
echo   dist\%RELEASE_JAR_1211%
echo   dist\%RELEASE_JAR_263%
if "%DRY_RUN%"=="1" (
	echo Dry run complete; nothing was published.
	exit /b 0
)

gh release view "%RELEASE_TAG%" >nul 2>&1
if not errorlevel 1 (
	echo GitHub Release "%RELEASE_TAG%" already exists.
	exit /b 1
)

echo Publishing GitHub Release...
gh release create "%RELEASE_TAG%" "dist\%RELEASE_JAR_1211%" "dist\%RELEASE_JAR_263%" ^
	--title "Easy Survival Pack %RELEASE_TAG%" ^
	--generate-notes ^
	--target "%RELEASE_COMMIT%" ^
	--latest
if errorlevel 1 (
	echo Release creation failed.
	exit /b 1
)

echo Release published successfully with both Minecraft versions.
exit /b 0

:build_failed
echo Build failed; no release was published.
exit /b 1

:usage_error
echo Usage: release.cmd [--dry-run]
echo Builds Minecraft 1.21.1 and 26.3, then publishes both JARs in one GitHub Release.
exit /b 2

@echo off
setlocal
cd /d "%~dp0"

set "DRY_RUN=0"
set "MC_VERSION="

:parse_args
if "%~1"=="" goto args_done
if /I "%~1"=="--dry-run" (
	set "DRY_RUN=1"
	shift
	goto parse_args
)
if not defined MC_VERSION (
	set "MC_VERSION=%~1"
	shift
	goto parse_args
)
echo Unknown argument: %~1
goto usage_error

:args_done
if defined MC_VERSION goto validate_version

echo Select Minecraft version to release:
echo   1^) Minecraft 1.21.1  ^(Java 21^)
echo   2^) Minecraft 26.3    ^(Java 25^)
choice /c 12 /n /m "Choose [1/2]: "
if errorlevel 2 (
	set "MC_VERSION=26.3"
) else (
	set "MC_VERSION=1.21.1"
)

:validate_version
if "%MC_VERSION%"=="1.21.1" goto configure_1211
if "%MC_VERSION%"=="26.3" goto configure_263
echo Unsupported Minecraft version: %MC_VERSION%
goto usage_error

:configure_1211
set "LOADER_VERSION=0.16.14"
set "FABRIC_API_VERSION=0.116.18+1.21.1"
goto check_gh

:configure_263
set "LOADER_VERSION=0.20.1"
set "FABRIC_API_VERSION=0.166.0+26.3"

:check_gh
where gh >nul 2>&1
if errorlevel 1 (
	echo GitHub CLI "gh" was not found. Install it and authenticate with "gh auth login".
	exit /b 1
)

if "%DRY_RUN%"=="1" goto select_artifact

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

echo Building Easy Survival Pack for Minecraft %MC_VERSION%...
call gradlew.bat build --no-daemon "-Pminecraft_version=%MC_VERSION%" "-Ploader_version=%LOADER_VERSION%" "-Pfabric_api_version=%FABRIC_API_VERSION%"
if errorlevel 1 (
	echo Build failed; no release was published.
	exit /b 1
)

:select_artifact
set "RELEASE_JAR="
for /f "delims=" %%F in ('dir /b /a-d "dist\EasySurvivalPack-*+mc%MC_VERSION%.jar" 2^>nul') do set "RELEASE_JAR=%%F"
if not defined RELEASE_JAR (
	echo No Minecraft %MC_VERSION% JAR was found in dist.
	if "%DRY_RUN%"=="1" echo Run build-mod.bat %MC_VERSION% first, or run release.cmd without --dry-run.
	exit /b 1
)

set "RELEASE_TAG=%RELEASE_JAR:EasySurvivalPack-=%"
set "RELEASE_TAG=%RELEASE_TAG:.jar=%"
set "RELEASE_TAG=v%RELEASE_TAG%"
set "RELEASE_COMMIT="
for /f "delims=" %%C in ('git rev-parse HEAD 2^>nul') do set "RELEASE_COMMIT=%%C"
if not defined RELEASE_COMMIT (
	echo Could not determine the Git commit for this release.
	exit /b 1
)

echo Release tag: %RELEASE_TAG%
echo Artifact:    dist\%RELEASE_JAR%
echo Target:      %RELEASE_COMMIT%
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
gh release create "%RELEASE_TAG%" "dist\%RELEASE_JAR%" ^
	--title "Easy Survival Pack %RELEASE_TAG%" ^
	--generate-notes ^
	--target "%RELEASE_COMMIT%"
if errorlevel 1 (
	echo Release creation failed.
	exit /b 1
)

echo Release published successfully.
exit /b 0

:usage_error
echo Usage: release.cmd [1.21.1^|26.3] [--dry-run]
echo Run without a version to choose from an interactive menu.
exit /b 2

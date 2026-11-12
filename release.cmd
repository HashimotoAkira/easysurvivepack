@echo off
setlocal
cd /d "%~dp0"

set "DRY_RUN=0"
if /I "%~1"=="--dry-run" set "DRY_RUN=1"
if not "%~1"=="" if /I not "%~1"=="--dry-run" (
	echo Usage: release.cmd [--dry-run]
	exit /b 2
)

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

echo Building Easy Survival Pack...
call gradlew.bat build --no-daemon
if errorlevel 1 (
	echo Build failed; no release was published.
	exit /b 1
)

:select_artifact
set "RELEASE_JAR="
for /f "delims=" %%F in ('dir /b /a-d /o-d "dist\EasySurvivalPack-*.jar" 2^>nul') do if not defined RELEASE_JAR set "RELEASE_JAR=%%F"
if not defined RELEASE_JAR (
	echo No Easy Survival Pack JAR was found in dist.
	if "%DRY_RUN%"=="1" echo Run a build first, or run release.cmd without --dry-run.
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

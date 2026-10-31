@echo off
setlocal
cd /d "%~dp0"
if errorlevel 1 (pause & exit /b 1)
title Invaders - One Click Launcher
where java >nul 2>&1
if errorlevel 1 (
  echo Java was not found. Install JDK 17 or later and try again.
  pause
  exit /b 1
)
where javac >nul 2>&1
if errorlevel 1 (
  echo The Java compiler was not found. This launcher requires JDK 17 or later.
  pause
  exit /b 1
)
echo Building the latest game code...
powershell.exe -NoLogo -NoProfile -Command "$ErrorActionPreference='Stop'; try { New-Item -ItemType Directory -Force 'bin/launcher-game' | Out-Null; $sources=@(Get-ChildItem -LiteralPath 'src' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }); if($sources.Count -eq 0){throw 'Game source files were not found.'}; & javac -encoding UTF-8 -d 'bin/launcher-game' $sources; if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}; Write-Host 'Starting Invaders...'; & java -cp 'bin/launcher-game;res' engine.Core; exit $LASTEXITCODE } catch { Write-Host $_.Exception.Message; exit 1 }"
if errorlevel 1 (
  echo.
  echo The game could not start. See the message above.
  pause
  exit /b 1
)
endlocal

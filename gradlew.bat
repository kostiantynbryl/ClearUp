@echo off
setlocal
set "GRADLE_VERSION=9.5.1"
set "GRADLE_SHA256=bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f"
set "GRADLE_BASE=%USERPROFILE%\.gradle\clearup-bootstrap"
set "GRADLE_DIR=%GRADLE_BASE%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_DIR%\bin\gradle.bat"

if not exist "%GRADLE_BIN%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ErrorActionPreference='Stop';" ^
      "$version='%GRADLE_VERSION%';" ^
      "$expected='%GRADLE_SHA256%';" ^
      "$base='%GRADLE_BASE%';" ^
      "$dest='%GRADLE_DIR%';" ^
      "$temp=Join-Path ([System.IO.Path]::GetTempPath()) ('clearup-gradle-'+[guid]::NewGuid());" ^
      "New-Item -ItemType Directory -Force -Path $temp | Out-Null;" ^
      "$zip=Join-Path $temp 'gradle.zip';" ^
      "Invoke-WebRequest -UseBasicParsing ('https://services.gradle.org/distributions/gradle-'+$version+'-bin.zip') -OutFile $zip;" ^
      "$actual=(Get-FileHash -Algorithm SHA256 $zip).Hash.ToLowerInvariant();" ^
      "if($actual -ne $expected){throw 'Gradle checksum mismatch'};" ^
      "Expand-Archive -Path $zip -DestinationPath $temp -Force;" ^
      "New-Item -ItemType Directory -Force -Path $base | Out-Null;" ^
      "if(Test-Path $dest){Remove-Item -Recurse -Force $dest};" ^
      "Move-Item (Join-Path $temp ('gradle-'+$version)) $dest;" ^
      "Remove-Item -Recurse -Force $temp"
    if errorlevel 1 exit /b 1
)

call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%

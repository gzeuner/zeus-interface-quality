@echo off
rem Author: tiny-tool.de
rem Copyright 2026 tiny-tool.de
rem SPDX-License-Identifier: Apache-2.0
rem See LICENSE in the project root.
setlocal
cd /d "%~dp0"

rem Prefer an installed JDK 21 when Windows still resolves Java 17 first.
for /d %%D in ("%ProgramFiles%\Amazon Corretto\jdk21*") do (
  if exist "%%~fD\bin\java.exe" (
    set "JAVA_HOME=%%~fD"
    set "PATH=%%~fD\bin;%PATH%"
  )
)

where java >nul 2>&1
if errorlevel 1 (
  echo Java wurde nicht gefunden. Bitte JDK 21 oder neuer installieren.
  pause
  exit /b 2
)

if not exist "target\zeus-interface-quality-0.1.0-SNAPSHOT.jar" (
  echo ZEUS wird fuer den ersten Start gebaut ...
  call mvnw.cmd -B -DskipTests package
  if errorlevel 1 (
    echo Der Build ist fehlgeschlagen.
    pause
    exit /b 2
  )
)

java -jar "target\zeus-interface-quality-0.1.0-SNAPSHOT.jar" gui %*
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" pause
exit /b %EXIT_CODE%

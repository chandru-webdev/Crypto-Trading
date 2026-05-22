@echo off
REM Run Spring Boot without installing Maven globally.
REM Uses mvnw + auto-detects JAVA_HOME from `java` on PATH.

where java >nul 2>&1
if errorlevel 1 (
  echo Java not found on PATH. Install JDK 21+ and try again.
  exit /b 1
)

if "%JAVA_HOME%"=="" (
  for /f "delims=" %%i in ('where java') do (
    set "JAVA_EXE=%%i"
    goto foundJava
  )
)
:foundJava
if "%JAVA_HOME%"=="" (
  for %%i in ("%JAVA_EXE%") do set "JAVA_HOME=%%~dpi.."
  set "JAVA_HOME=%JAVA_HOME:~0,-1%"
)

echo Using JAVA_HOME=%JAVA_HOME%
call "%~dp0mvnw.cmd" %*

@echo off
rem Mealwise backend launcher (used by the Windows scheduled task and for manual start)
setlocal

set "ROOT=%~dp0"
set "JAVA=%ROOT%..\.tools\jdk17\jdk-17.0.2\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"

set "TMPDIR=%ROOT%..\.tools\tmp"
set "JAR=%ROOT%mealwise-server\target\mealwise-server-1.0.0.jar"
set "LOG=%ROOT%..\.tools\app.log"

if not exist "%JAR%" (
  echo [mealwise] jar not found: %JAR%
  echo [mealwise] build it first: mvn -f mealwise-server\pom.xml -DskipTests package
  exit /b 1
)
if not exist "%TMPDIR%" mkdir "%TMPDIR%"

echo [mealwise] starting backend with %JAVA%
"%JAVA%" -Djava.io.tmpdir="%TMPDIR%" -jar "%JAR%" --spring.profiles.active=dev >> "%LOG%" 2>&1

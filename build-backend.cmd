@echo off
chcp 65001 >nul
rem 一键打包后端（使用工作区自带的 JDK17 + Maven + 本地依赖仓库）
setlocal
set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%..\.tools\jdk17\jdk-17.0.2"
set "MVN=%ROOT%..\.tools\maven\apache-maven-3.9.9\bin\mvn.cmd"
set "REPO=%ROOT%..\.tools\m2repo"

echo [build] JAVA_HOME=%JAVA_HOME%
echo [build] 正在编译，大约 20 秒...
"%MVN%" -B -f "%ROOT%mealwise-server\pom.xml" "-Dmaven.repo.local=%REPO%" -DskipTests package
if errorlevel 1 (
  echo.
  echo [build] 失败，往上翻看第一条 ERROR
  exit /b 1
)
echo.
echo [build] 成功: mealwise-server\target\mealwise-server-1.0.0.jar

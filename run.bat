@echo off
setlocal

set JAR=api\build\libs\api-1.0-SNAPSHOT.jar
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8
set JAVA="%JAVA_HOME%\bin\java.exe"

cd /d "%~dp0"

if not exist "%JAR%" (
  echo JAR not found - building...
  call gradlew.bat clean build shadowJar -x test
  if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
  )
)

echo Killing any process on port 9090...
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr ":9090 "') do (
  taskkill /f /pid %%a 2>nul
)

echo Starting application...
set LOGFILE=%TEMP%\genre-classifier.log
start /b %JAVA% -Xmx4g -jar %JAR% > "%LOGFILE%" 2>&1

echo Waiting for server on port 9090 (logs: %LOGFILE%)...

:wait
ping -n 3 127.0.0.1 > nul
curl -s http://localhost:9090 > nul 2>&1
if errorlevel 1 goto wait

echo Server ready - opening browser...
start http://localhost:9090

@echo off
set JAVA_HOME=C:\Users\tahae\OneDrive\Desktop\EntityFix\.tools\jdk
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d C:\Users\tahae\OneDrive\Desktop\EntityFix
call .tools\gradle\bin\gradle.bat %* > C:\Users\tahae\AppData\Local\Temp\ef_build.log 2>&1
echo EXITCODE=%ERRORLEVEL% >> C:\Users\tahae\AppData\Local\Temp\ef_build.log

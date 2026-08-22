# EntityFix local build helper.
# Uses the JDK 17 downloaded into .tools (see .setup/download.ps1) so the
# system JDK version does not matter.
$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = Join-Path $PSScriptRoot '..\.tools\jdk'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& (Join-Path $PSScriptRoot '..\.tools\gradle\bin\gradle.bat') @args *>&1 |
    Tee-Object -FilePath (Join-Path $env:TEMP 'ef_build.log')

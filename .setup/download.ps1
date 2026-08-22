$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$log = 'C:\Users\tahae\AppData\Local\Temp\ef_dl.txt'
Remove-Item $log -ErrorAction SilentlyContinue
function Log($m) { "$m" | Out-File $log -Encoding utf8 -Append }

$tools = 'C:\Users\tahae\OneDrive\Desktop\EntityFix\.tools'
New-Item -ItemType Directory -Force -Path $tools | Out-Null

# --- JDK 17 (Temurin) ---
$jdkZip = "$tools\jdk17.zip"
if (-not (Test-Path "$tools\jdk")) {
  Log 'Downloading Temurin JDK 17...'
  Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $jdkZip -UseBasicParsing
  Log ('JDK download size: ' + (Get-Item $jdkZip).Length)
  Log 'Extracting JDK...'
  Expand-Archive -Path $jdkZip -DestinationPath "$tools\jdktmp" -Force
  $inner = Get-ChildItem "$tools\jdktmp" -Directory | Select-Object -First 1
  Move-Item $inner.FullName "$tools\jdk"
  Remove-Item "$tools\jdktmp" -Recurse -Force
  Remove-Item $jdkZip -Force
}
& "$tools\jdk\bin\java.exe" -version 2>&1 | Out-File $log -Encoding utf8 -Append

# --- Gradle 8.12 ---
$gradleZip = "$tools\gradle.zip"
if (-not (Test-Path "$tools\gradle")) {
  Log 'Downloading Gradle 8.12...'
  Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.12-bin.zip' -OutFile $gradleZip -UseBasicParsing
  Log ('Gradle download size: ' + (Get-Item $gradleZip).Length)
  Log 'Extracting Gradle...'
  Expand-Archive -Path $gradleZip -DestinationPath "$tools\gradletmp" -Force
  $innerG = Get-ChildItem "$tools\gradletmp" -Directory | Select-Object -First 1
  Move-Item $innerG.FullName "$tools\gradle"
  Remove-Item "$tools\gradletmp" -Recurse -Force
  Remove-Item $gradleZip -Force
}
Log 'ALL DONE'

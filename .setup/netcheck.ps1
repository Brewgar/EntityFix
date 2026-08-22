$ErrorActionPreference = 'Continue'
$log = 'C:\Users\tahae\AppData\Local\Temp\ef_net.txt'
Remove-Item $log -ErrorAction SilentlyContinue
try {
  $r = Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/' -Method Head -UseBasicParsing -TimeoutSec 20
  "gradle.org: OK $($r.StatusCode)" | Out-File $log -Encoding utf8 -Append
} catch { "gradle.org: FAIL $_" | Out-File $log -Encoding utf8 -Append }
try {
  $r2 = Invoke-WebRequest -Uri 'https://api.github.com' -Method Head -UseBasicParsing -TimeoutSec 20
  "github: OK $($r2.StatusCode)" | Out-File $log -Encoding utf8 -Append
} catch { "github: FAIL $_" | Out-File $log -Encoding utf8 -Append }
try {
  $r3 = Invoke-WebRequest -Uri 'https://maven.fabricmc.net/' -Method Head -UseBasicParsing -TimeoutSec 20
  "fabricmc: OK $($r3.StatusCode)" | Out-File $log -Encoding utf8 -Append
} catch { "fabricmc: FAIL $_" | Out-File $log -Encoding utf8 -Append }
try {
  $r4 = Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/info/available_releases' -UseBasicParsing -TimeoutSec 20
  "adoptium: OK" | Out-File $log -Encoding utf8 -Append
} catch { "adoptium: FAIL $_" | Out-File $log -Encoding utf8 -Append }
"DONE" | Out-File $log -Encoding utf8 -Append

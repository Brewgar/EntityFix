$tmp="$env:TEMP\efcls"
$jp='C:\Users\tahae\OneDrive\Desktop\EntityFix\.tools\jdk\bin\javap.exe'
& $jp -classpath $tmp net.minecraft.server.world.ServerWorld 2>&1 | Out-File "$env:TEMP\ef_sw.txt" -Encoding utf8
Select-String -Path "$env:TEMP\ef_sw.txt" -Pattern 'setBlockState' | ForEach-Object { $_.Line }
"TOTAL LINES: " + (Get-Content "$env:TEMP\ef_sw.txt").Count

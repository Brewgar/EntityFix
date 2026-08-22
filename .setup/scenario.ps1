# EntityFix runtime validation via RCON.
# Boots against an already-running dev server on localhost:25575.
$ErrorActionPreference = 'Stop'

function Read-Exact([System.Net.Sockets.NetworkStream]$s, [int]$n) {
    $buf = New-Object byte[] $n
    $off = 0
    while ($off -lt $n) {
        $r = $s.Read($buf, $off, $n - $off)
        if ($r -le 0) { throw 'connection closed' }
        $off += $r
    }
    return $buf
}

function Read-Packet([System.Net.Sockets.NetworkStream]$s) {
    $lenBuf = Read-Exact $s 4
    $len = [BitConverter]::ToInt32($lenBuf, 0)
    if ($len -lt 10 -or $len -gt 8192) { throw "bad packet length $len" }
    $data = Read-Exact $s $len
    $id = [BitConverter]::ToInt32($data, 0)
    $body = [Text.Encoding]::ASCII.GetString($data, 8, $len - 10)
    return @($id, $body)
}

function Send-Packet([System.Net.Sockets.NetworkStream]$s, [int]$id, [int]$type, [string]$body) {
    $payload = [Text.Encoding]::ASCII.GetBytes($body)
    $len = $payload.Length + 10
    $pkt = New-Object byte[] ($len + 4)
    [BitConverter]::GetBytes([int]$len).CopyTo($pkt, 0)
    [BitConverter]::GetBytes($id).CopyTo($pkt, 4)
    [BitConverter]::GetBytes($type).CopyTo($pkt, 8)
    $payload.CopyTo($pkt, 12)
    # trailing two null bytes already zero
    $s.Write($pkt, 0, $pkt.Length)
    $s.Flush()
}

$client = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 25575)
$stream = $client.GetStream()
$stream.ReadTimeout = 15000

Send-Packet $stream 1 3 'entityfix'
$id, $authBody = Read-Packet $stream
if ($id -eq -1) { throw 'RCON auth failed' }
Write-Output ('AUTH OK')

function Invoke-Rcon([System.Net.Sockets.NetworkStream]$s, [string]$cmd) {
    Send-Packet $s 2 2 $cmd
    $rid, $resp = Read-Packet $s
    return $resp
}

# ---- scenario ---------------------------------------------------------
Write-Output ('=== initial status ===')
Invoke-Rcon $stream 'entityfix status'

Write-Output ('=== workload: force-load test area ===')
[void](Invoke-Rcon $stream 'forceload add -48 -48 48 48')
Start-Sleep -Seconds 3

Write-Output ('=== workload: time set to night, spawn entity stress ===')
[void](Invoke-Rcon $stream 'time set night')

for ($i = 0; $i -lt 120; $i++) {
    $x = [math]::Round((Get-Random -Minimum -25) + (Get-Random -Maximum 50) , 0)
    $z = [math]::Round((Get-Random -Minimum -25) + (Get-Random -Maximum 50), 0)
    $kinds = @('villager','villager','cow','sheep','husk')
    $kind = $kinds[(Get-Random -Maximum $kinds.Count)]
    [void](Invoke-Rcon $stream ("summon minecraft:$kind $x 90 $z"))
}
Write-Output ('spawned 120 entities')
Invoke-Rcon $stream 'effect give @e[type=minecraft:cow] minecraft:jump_boost 2 0 true'

Write-Output ('=== workload: path-relevant block changes ===')
for ($i = 0; $i -lt 30; $i++) {
    $x = [math]::Round((Get-Random -Minimum -20) + (Get-Random -Maximum 40), 0)
    $z = [math]::Round((Get-Random -Minimum -20) + (Get-Random -Maximum 40), 0)
    [void](Invoke-Rcon $stream ("setblock $x 99 $z minecraft:stone"))
    [void](Invoke-Rcon $stream ("setblock $x 100 $z minecraft:stone"))
    [void](Invoke-Rcon $stream ("setblock $x 101 $z minecraft:air"))
    [void](Invoke-Rcon $stream ("setblock $x 99 $z minecraft:air"))   # irrelevant-ish churn too
}
Write-Output ('block changes issued')

Start-Sleep -Seconds 45   # let it tick: sensors, brains, wandering, repaths

Write-Output ('=== status after workload ===')
Invoke-Rcon $stream 'entityfix status'
Write-Output ('=== debug ===')
Invoke-Rcon $stream 'entityfix debug'
Write-Output ('=== profile dump ===')
Invoke-Rcon $stream 'entityfix profile dump'

[void](Invoke-Rcon $stream 'stop')
Write-Output ('STOP sent')
$client.Close()

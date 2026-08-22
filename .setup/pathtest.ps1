# Final validation: pathfinding counters (paced to avoid RCON desync).
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
    if ($len -lt 10 -or $len -gt 65536) { throw "bad packet length $len" }
    $data = Read-Exact $s $len
    return @([BitConverter]::ToInt32($data, 0), [Text.Encoding]::UTF8.GetString($data, 8, $len - 10))
}

function Send-Packet([System.Net.Sockets.NetworkStream]$s, [int]$id, [int]$type, [string]$body) {
    $payload = [Text.Encoding]::UTF8.GetBytes($body)
    $len = $payload.Length + 10
    $pkt = New-Object byte[] ($len + 4)
    [BitConverter]::GetBytes([int]$len).CopyTo($pkt, 0)
    [BitConverter]::GetBytes($id).CopyTo($pkt, 4)
    [BitConverter]::GetBytes($type).CopyTo($pkt, 8)
    $payload.CopyTo($pkt, 12)
    $s.Write($pkt, 0, $pkt.Length)
    $s.Flush()
}

function Invoke-Rcon([System.Net.Sockets.NetworkStream]$s, [string]$cmd) {
    Send-Packet $s (Get-Random -Maximum 100000) 2 $cmd
    Start-Sleep -Milliseconds 40
    return (Read-Packet $s)[1]
}

$client = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 25575)
$stream = $client.GetStream()
$stream.ReadTimeout = 15000

Send-Packet $stream 1 3 'entityfix'
$id, $null = Read-Packet $stream
if ($id -eq -1) { throw 'RCON auth failed' }

Write-Output ('=== baseline ===')
Invoke-Rcon $stream 'entityfix debug'
[void](Invoke-Rcon $stream 'time set night')
[void](Invoke-Rcon $stream 'gamerule mobGriefing false')

Write-Output ('=== spawning 40 husks tightly around spawn (paced) ===')
for ($i = 0; $i -lt 40; $i++) {
    $dx = ($i % 8) - 4
    $dz = [math]::Floor($i / 8) - 2
    $resp = Invoke-Rcon $stream ("summon minecraft:husk $dx 80 $dz")
}

Start-Sleep -Seconds 45   # husks wander/pathfind toward nearby targets

Write-Output ('=== after workload ===')
Invoke-Rcon $stream 'entityfix debug'
Invoke-Rcon $stream 'entityfix profile dump'

[void](Invoke-Rcon $stream 'stop')
Write-Output ('STOP sent')
$client.Close()

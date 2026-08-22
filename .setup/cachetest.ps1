# Path cache validation: villagers repeatedly repath to unreachable beds.
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
Write-Output 'AUTH OK'

Write-Output ('=== baseline ===')
Invoke-Rcon $stream 'entityfix status'

# Build a floating platform at build-safe height with a walled pen.
Write-Output ('=== building test arena ===')
[void](Invoke-Rcon $stream 'difficulty peaceful')          # no hostile spawns/churn
[void](Invoke-Rcon $stream 'gamerule randomTickSpeed 0')   # freeze random ticks -> stable topology
[void](Invoke-Rcon $stream 'fill -15 89 -7 15 89 7 minecraft:smooth_stone')
[void](Invoke-Rcon $stream 'forceload add -16 -16 16 16')
[void](Invoke-Rcon $stream 'time set night')

# Sealed bed room in the middle: 24 beds, all unreachable from outside.
[void](Invoke-Rcon $stream 'fill -7 90 -3 6 93 3 minecraft:smooth_stone')   # solid block
[void](Invoke-Rcon $stream 'fill -6 91 -2 5 92 2 minecraft:air')            # hollow interior
$n = 0
for ($bx = -6; $bx -le 5; $bx += 2) {
    [void](Invoke-Rcon $stream ("setblock $bx 91 -2 minecraft:red_bed"))
    [void](Invoke-Rcon $stream ("setblock $bx 91 2 minecraft:red_bed"))
    $n += 2
}
Write-Output ("placed $n sealed beds")
[void](Invoke-Rcon $stream 'fill -7 94 -3 6 94 3 minecraft:smooth_stone')   # seal top

# Spawn villagers on the platform around the box.
for ($i = 0; $i -lt 24; $i++) {
    $x = -14 + ($i % 12) * 2
    $z = -5 + [math]::Floor($i / 12) * 10
    [void](Invoke-Rcon $stream ("summon minecraft:villager $x 90 $z"))
}
Write-Output 'spawned 24 villagers; beds sealed at center'

Start-Sleep -Seconds 90   # night sleep-seeking -> repeated identical-target A*

Write-Output ('=== after sleep-seek window ===')
Invoke-Rcon $stream 'entityfix debug'
Invoke-Rcon $stream 'entityfix profile dump'

[void](Invoke-Rcon $stream 'stop')
Write-Output ('STOP sent')
$client.Close()

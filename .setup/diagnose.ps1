# Diagnostic: try spawning entities and SHOW every RCON response.
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
    $s.Write($pkt, 0, $pkt.Length)
    $s.Flush()
}

$client = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 25575)
$stream = $client.GetStream()
$stream.ReadTimeout = 15000

Send-Packet $stream 1 3 'entityfix'
$id, $null = Read-Packet $stream
if ($id -eq -1) { throw 'RCON auth failed' }

function Invoke-Rcon([System.Net.Sockets.NetworkStream]$s, [string]$cmd) {
    Send-Packet $s 2 2 $cmd
    $rid, $resp = Read-Packet $s
    Write-Output ("> $cmd")
    Write-Output ("< $resp")
}

Invoke-Rcon $stream 'summon minecraft:cow 0 100 0'
Invoke-Rcon $stream 'summon minecraft:villager 2 100 2'
Invoke-Rcon $stream 'kill @e[type=minecraft:cow]'
Invoke-Rcon $stream 'kill @e[type=minecraft:villager]'
Invoke-Rcon $stream 'entityfix debug'

Start-Sleep -Seconds 20

Invoke-Rcon $stream 'summon minecraft:cow 0 100 0'
Invoke-Rcon $stream 'summon minecraft:villager 2 100 2'
Invoke-Rcon $stream 'entityfix debug'

Invoke-Rcon $stream 'stop'
Write-Output ('STOP sent')
$client.Close()

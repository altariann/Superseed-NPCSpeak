#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$RequestFile = '',
    [string]$ResponseFile = '',
    [string]$Endpoint = 'http://127.0.0.1:11434/api/chat',
    [ValidateRange(1, 600)][int]$TimeoutSeconds = 120,
    [int]$ServerProcessId = 0,
    [switch]$NoMinecraftProcess,
    [string]$ExpectedModelDigest = '',
    [ValidateRange(256, 65536)][int]$MinimumAvailableMB = 1024
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-PilotEndpoint([string]$Value) {
    $uri = [uri]$Value
    if (!$uri.IsAbsoluteUri -or $uri.Scheme -ne 'http' -or $uri.Host -ne '127.0.0.1' -or
        $uri.AbsolutePath -ne '/api/chat' -or $uri.Query -or $uri.UserInfo -or $uri.Fragment) {
        throw 'Pilot yalnız açık 127.0.0.1 HTTP /api/chat adresine bağlanabilir.'
    }
    return $uri
}
function Read-PilotRequest([string]$Path) {
    $json = [IO.File]::ReadAllText($Path)
    $request = $json | ConvertFrom-Json
    $keys = @($request.PSObject.Properties.Name | Sort-Object)
    if (($keys -join ',') -ne 'messages,model,stream' -or $request.stream -isnot [bool] -or $request.stream -or
        $request.model -isnot [string] -or [string]::IsNullOrWhiteSpace($request.model) -or @($request.messages).Count -lt 2) {
        throw 'İstek yalnız model, stream=false ve messages içermeli; inference seçenekleri eklenmez.'
    }
    foreach ($message in $request.messages) {
        if ($message.role -notin @('system','user','assistant') -or $message.content -isnot [string]) { throw 'Geçersiz mesaj.' }
    }
    return [pscustomobject]@{Body=$json;Model=$request.model}
}
function Get-PilotHostState([int]$ServerId) {
    $os = Get-CimInstance Win32_OperatingSystem
    $processes = @(Get-Process -ErrorAction Stop | Where-Object {$_.Id -eq $ServerId -or $_.ProcessName -like 'ollama*'} |
        Select-Object Id,ProcessName,CPU,WorkingSet64)
    if ($NoMinecraftProcess) {
        if ($ServerId -ne 0) { throw 'Sunucu yok modu ile izlenen PID birlikte kullanılamaz.' }
        $candidates = @(Get-CimInstance Win32_Process | Where-Object {
            $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match '(?i)-jar\s|paper|purpur|spigot|net\.minecraft'
        })
        if ($candidates.Count -gt 0) { throw 'Sunucu yok modu doğrulanamadı; olası Java sunucusu mevcut.' }
    } elseif ($ServerId -le 0 -or @($processes | Where-Object Id -eq $ServerId).Count -ne 1) {
        throw 'İzlenecek sunucu PID değeri açıkça verilmeli ve mevcut olmalı.'
    }
    return @{Utc=[datetime]::UtcNow.ToString('o');AvailableMB=[math]::Round($os.FreePhysicalMemory/1024);Processes=$processes;MinecraftProcessAbsent=[bool]$NoMinecraftProcess}
}
function Invoke-PilotRequest {
    $uri = Assert-PilotEndpoint $Endpoint
    if (![IO.Path]::IsPathRooted($RequestFile) -or ![IO.Path]::IsPathRooted($ResponseFile)) { throw 'Açık mutlak özel dosya yolları gerekli.' }
    if (Test-Path -LiteralPath $ResponseFile) { throw 'Mevcut yanıt üzerine yazılmaz; otomatik tekrar yok.' }
    $request = Read-PilotRequest $RequestFile
    if ([string]::IsNullOrWhiteSpace($ExpectedModelDigest)) { throw 'Dondurulmuş model digest değeri gerekli.' }
    $base = $uri.GetLeftPart([UriPartial]::Authority)
    $tags = Invoke-RestMethod -Uri ($base+'/api/tags') -TimeoutSec 10 -MaximumRedirection 0
    $name = if ($request.Model.Contains(':')) {$request.Model} else {$request.Model+':latest'}
    $match = @($tags.models | Where-Object name -eq $name)
    if ($match.Count -ne 1 -or $match[0].digest -cne $ExpectedModelDigest) { throw 'Model/digest değişmiş; inference yapılmadı.' }
    $parent = Split-Path ([IO.Path]::GetFullPath($ResponseFile))
    $lock = [IO.File]::Open((Join-Path $parent '.pilot.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $record = [ordered]@{RequestSha256=(Get-FileHash -LiteralPath $RequestFile).Hash;Model=$request.Model;ModelDigest=$ExpectedModelDigest;TimeoutSeconds=$TimeoutSeconds;Sent=$false}
    $watch = [Diagnostics.Stopwatch]::new()
    $client = $null
    try {
        $record.Before = Get-PilotHostState $ServerProcessId
        if ($record.Before.AvailableMB -lt $MinimumAvailableMB) { throw 'Yeterli boş fiziksel bellek yok; yeni model isteği gönderilmedi.' }
        Add-Type -AssemblyName System.Net.Http
        $handler = [Net.Http.HttpClientHandler]::new(); $handler.AllowAutoRedirect=$false; $handler.UseProxy=$false
        $client = [Net.Http.HttpClient]::new($handler); $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
        $content = [Net.Http.StringContent]::new($request.Body, [Text.Encoding]::UTF8, 'application/json')
        $watch.Start(); $record.Sent=$true
        try { $response = $client.PostAsync($uri,$content).GetAwaiter().GetResult() } finally { $content.Dispose() }
        try {
            $record.HttpStatus = [int]$response.StatusCode
            $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $record.Response = $raw | ConvertFrom-Json
            if (!$response.IsSuccessStatusCode) { throw 'Ollama başarısız HTTP yanıtı döndürdü.' }
            if (!$record.Response.done -or $record.Response.message.content -isnot [string] -or [string]::IsNullOrWhiteSpace($record.Response.message.content)) {
                throw 'Tam ve dolu model yanıtı alınamadı.'
            }
            $record.TransportStatus = 'COMPLETED_REQUIRES_FACT_REVIEW'
        } finally { $response.Dispose() }
    } catch {
        $record.TransportStatus = 'STOP_REVIEW_REQUIRED'
        $record.ErrorType = $_.Exception.GetType().FullName
        throw 'Pilot durduruldu; özel yanıt dosyasını inceleyin. Otomatik retry veya işlem sonlandırma yapılmadı.'
    } finally {
        $watch.Stop(); $record.ElapsedSeconds=$watch.Elapsed.TotalSeconds
        try { $record.After=Get-PilotHostState $ServerProcessId } catch { $record.HostStateAfterUnavailable=$true }
        $json = $record | ConvertTo-Json -Depth 30
        $stream = [IO.File]::Open($ResponseFile,'CreateNew','Write','None')
        try { $bytes=[Text.Encoding]::UTF8.GetBytes($json); $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
        if ($client) { $client.Dispose() }; $lock.Dispose()
    }
    Write-Host ("Tek pilot yanıtı özel alana kaydedildi; süre {0:N2} sn. Sonraki çağrıdan önce olgu/bellek incelemesi gerekli." -f $record.ElapsedSeconds)
}
if ($MyInvocation.InvocationName -ne '.') { Invoke-PilotRequest }

#Requires -Version 5.1
[CmdletBinding(SupportsShouldProcess)]
param(
    [ValidateSet('Inspect', 'Build', 'Deploy')][string]$Mode = 'Inspect',
    [string]$ServerRoot = '',
    [string]$LocalSettingsPath = (Join-Path $PSScriptRoot 'Deploy-Superseed.local.json'),
    [string]$StartBat = 'START.bat',
    [string]$JavaHome = '',
    [switch]$ApproveDeployment,
    [switch]$RconFirewallVerified,
    [ValidateRange(30, 1800)][int]$StopTimeoutSeconds = 180,
    [ValidateRange(30, 1800)][int]$StartTimeoutSeconds = 300
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$RepoRoot = $PSScriptRoot
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Resolve-DeploymentServerRoot([string]$ExplicitRoot, [string]$SettingsPath) {
    $configured = $ExplicitRoot
    if ([string]::IsNullOrWhiteSpace($configured)) {
        if (!(Test-Path -LiteralPath $SettingsPath -PathType Leaf)) {
            throw 'Sunucu yolu gerekli: -ServerRoot kullanın veya takip edilmeyen Deploy-Superseed.local.json dosyasını hazırlayın.'
        }
        try {
            $json = [IO.File]::ReadAllText($SettingsPath)
            # Windows PowerShell may unwrap a one-element JSON array; require an object explicitly.
            if (!$json.TrimStart().StartsWith('{')) { throw 'Expected JSON object' }
            $settings = $json | ConvertFrom-Json -ErrorAction Stop
        }
        catch { throw 'Yerel dağıtım ayarı geçerli JSON olmalı; dosya içeriği güvenlik nedeniyle çıktıya yazılmadı.' }
        if ($null -eq $settings -or $settings -is [array] -or
            $null -eq $settings.PSObject.Properties['ServerRoot'] -or
            $settings.ServerRoot -isnot [string] -or [string]::IsNullOrWhiteSpace($settings.ServerRoot)) {
            throw 'Yerel dağıtım ayarında ServerRoot alanı dolu bir metin olmalı.'
        }
        $configured = $settings.ServerRoot
    }
    # No fallback to the current directory, root-relative paths or a remembered live server.
    if ($configured -notmatch '^(?:[a-zA-Z]:[\\/]|\\\\[^\\/]+[\\/][^\\/]+)') {
        throw 'ServerRoot mutlak bir Windows klasör yolu olmalı.'
    }
    if (!(Test-Path -LiteralPath $configured -PathType Container)) { throw 'Belirtilen sunucu klasörü bulunamadı.' }
    return (Resolve-Path -LiteralPath $configured).Path
}

function Get-ServerProperties([string]$Root) {
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $Root 'server.properties'))) {
        if ($line -match '^\s*([^#!\s][^=]*?)\s*=(.*)$') { $values[$matches[1]] = $matches[2].Trim() }
    }
    return $values
}

function Get-PluginName([string]$Jar) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        $entry = $zip.GetEntry('plugin.yml')
        if ($null -eq $entry) { return '' }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $yaml = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($yaml -match '(?m)^name:\s*[''"'']?([^\s''"''#]+)') { return $matches[1] }
        return ''
    } finally { $zip.Dispose() }
}

function Get-NpcSpeakJar([string]$Plugins) {
    $jars = @(Get-ChildItem -LiteralPath $Plugins -File -Filter '*.jar' | Where-Object {
        $_.Extension -ieq '.jar' -and (Get-PluginName $_.FullName) -eq 'NPCSpeak'
    })
    if ($jars.Count -ne 1) { throw "Tam olarak bir NPCSpeak JAR gerekli; bulunan: $($jars.Count). Hiçbir JAR değiştirilmedi." }
    return $jars[0]
}

function Assert-NoReparsePoint([string]$Path) {
    # A junction inside a backup tree could copy data from an unexpected location.
    foreach ($item in @(Get-Item -LiteralPath $Path) + @(Get-ChildItem -LiteralPath $Path -Recurse -Force)) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Bağlantı/junction desteklenmiyor: $($item.FullName)" }
    }
}

function Get-Listener([int]$Port) {
    $rows = @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {$_.LocalPort -eq $Port})
    if ($rows.Count -eq 0) { throw "Dinleyen port bulunamadı: $Port" }
    $owners = @($rows.OwningProcess | Sort-Object -Unique)
    if ($owners.Count -ne 1) { throw "Portun işlem kimliği belirsiz: $Port" }
    return $rows
}

function Get-ServerIdentity([hashtable]$Properties, [string]$BatText) {
    if ($Properties['enable-rcon'] -ne 'true') { throw 'RCON etkin değil. Betik ayarları otomatik değiştirmez.' }
    $rcon = @(Get-Listener ([int]$Properties['rcon.port']))
    $game = @(Get-Listener ([int]$Properties['server-port']))
    $ownerId = $rcon[0].OwningProcess
    if ($ownerId -ne $game[0].OwningProcess) { throw 'Minecraft ve RCON portları aynı işleme ait değil.' }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $ownerId"
    if (!$process -or !$process.ExecutablePath -or !$process.CommandLine) { throw 'Java işlem bilgileri okunamadı.' }
    $expected = $BatText.Trim()
    # This implementation deliberately supports only the inspected, one-line BAT.
    if ($expected -match '[\r\n&|<>%]' -or $expected -notmatch '^java\s+.+io\.papermc\.paperclip\.Main\s+-nogui$') {
        throw 'BAT biçimi değişmiş. Yeni başlatma yöntemi incelenmeden dağıtım yapılamaz.'
    }
    $actual = ($process.CommandLine -replace '\s+', ' ').Trim()
    if ($actual -cne ($expected -replace '\s+', ' ')) { throw 'Çalışan Java komutu seçilen BAT ile eşleşmiyor.' }
    if ([IO.Path]::GetFileName($process.ExecutablePath) -ne 'java.exe') { throw 'Beklenen Java işlemi bulunamadı.' }
    $processObject = Get-Process -Id $ownerId
    $null = $processObject.Handle
    return [pscustomobject]@{
        Process = $processObject
        Java = $process.ExecutablePath
        RconPort = [int]$Properties['rcon.port']
        Wildcard = @($rcon | Where-Object {$_.LocalAddress -notin @('127.0.0.1', '::1')}).Count -gt 0
    }
}

function Read-Exact([IO.Stream]$Stream, [int]$Count) {
    $buffer = [byte[]]::new($Count)
    $offset = 0
    while ($offset -lt $Count) {
        $read = $Stream.Read($buffer, $offset, $Count - $offset)
        if ($read -eq 0) { throw 'RCON bağlantısı kapandı.' }
        $offset += $read
    }
    return ,$buffer
}

function Read-RconPacket([IO.Stream]$Stream) {
    $size = [BitConverter]::ToInt32((Read-Exact $Stream 4), 0)
    if ($size -lt 10 -or $size -gt 65536) { throw 'Geçersiz RCON paket boyutu.' }
    $data = Read-Exact $Stream $size
    if ($data[$size - 1] -ne 0 -or $data[$size - 2] -ne 0) { throw 'Geçersiz RCON paket sonu.' }
    return [pscustomobject]@{Id=[BitConverter]::ToInt32($data,0); Type=[BitConverter]::ToInt32($data,4)}
}

function Write-RconPacket([IO.Stream]$Stream, [int]$Id, [int]$Type, [string]$Body) {
    $payload = [Text.Encoding]::UTF8.GetBytes($Body)
    $packet = [byte[]]::new($payload.Length + 14)
    [BitConverter]::GetBytes([int]($payload.Length + 10)).CopyTo($packet, 0)
    [BitConverter]::GetBytes($Id).CopyTo($packet, 4)
    [BitConverter]::GetBytes($Type).CopyTo($packet, 8)
    $payload.CopyTo($packet, 12)
    $Stream.Write($packet, 0, $packet.Length)
    $Stream.Flush()
}

function Open-Rcon([hashtable]$Properties) {
    if ([string]::IsNullOrWhiteSpace($Properties['rcon.password'])) { throw 'RCON parolası boş.' }
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect('127.0.0.1', [int]$Properties['rcon.port'], $null, $null)
        try {
            if (!$pending.AsyncWaitHandle.WaitOne(5000)) { throw 'Localhost RCON bağlantısı zaman aşımına uğradı.' }
            $client.EndConnect($pending)
        } finally { $pending.AsyncWaitHandle.Close() }
        $stream = $client.GetStream()
        $stream.ReadTimeout = 5000
        $stream.WriteTimeout = 5000
        Write-RconPacket $stream 101 3 $Properties['rcon.password']
        # Some servers send an empty response before the authentication response.
        for ($i = 0; $i -lt 3; $i++) {
            $reply = Read-RconPacket $stream
            if ($reply.Id -eq -1) { throw 'RCON kimlik doğrulaması başarısız.' }
            if ($reply.Type -eq 2 -and $reply.Id -eq 101) { return $client }
        }
        throw 'RCON kimlik doğrulama yanıtı alınamadı.'
    } catch { $client.Dispose(); throw }
}

function Stop-ServerSafely($Identity, [hashtable]$Properties, [int]$Timeout) {
    if ($Identity.Process.HasExited) { throw 'İncelenen sunucu işlemi zaten kapanmış; stop gönderilmedi.' }
    $listeners = @(Get-Listener $Identity.RconPort)
    if ($listeners[0].OwningProcess -ne $Identity.Process.Id) { throw 'RCON portunun sahibi değişmiş; stop gönderilmedi.' }
    $client = Open-Rcon $Properties
    try { Write-RconPacket ($client.GetStream()) 102 2 'stop' } finally { $client.Dispose() }
    # Holding the Process object prevents PID reuse from being mistaken for shutdown.
    if (!$Identity.Process.WaitForExit($Timeout * 1000)) {
        throw 'Sunucu zamanında kapanmadı. Zorla kapatma yapılmadı; JAR değiştirilmeyecek.'
    }
    Assert-PortsClosed $Properties
}

function Assert-PortsClosed([hashtable]$Properties) {
    foreach ($port in @([int]$Properties['rcon.port'], [int]$Properties['server-port'])) {
        if (@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {$_.LocalPort -eq $port}).Count -gt 0) {
            throw "Port $port hâlâ açık; sunucu dosyalarına dokunulmayacak."
        }
    }
    # A restarted Paper can exist before binding ports. Do not replace its live JAR.
    $paper = @(Get-CimInstance Win32_Process -Filter "name = 'java.exe'" | Where-Object {
        $_.CommandLine -match 'io\.papermc\.paperclip\.Main'
    })
    if ($paper.Count -gt 0) { throw 'Paper işlemi hâlâ mevcut; JAR değiştirilmeyecek.' }
}

function Invoke-ProjectBuild([string]$Jdk) {
    $oldHome = $env:JAVA_HOME
    $oldCache = $env:GRADLE_USER_HOME
    try {
        if ($Jdk) { $env:JAVA_HOME = $Jdk }
        $env:GRADLE_USER_HOME = Join-Path $RepoRoot '.gradle-user-home'
        Push-Location $RepoRoot
        try {
            & (Join-Path $RepoRoot 'gradlew.bat') clean build --console=plain | ForEach-Object { Write-Host $_ }
            if ($LASTEXITCODE -ne 0) { throw "Gradle başarısız (çıkış $LASTEXITCODE). Sunucuya dokunulmadı." }
            & (Join-Path $RepoRoot 'tests\Test-Deployment.ps1')
        } finally { Pop-Location }
        $outputs = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'build\libs') -Filter '*.jar' |
            Where-Object { $_.Extension -ieq '.jar' -and (Get-PluginName $_.FullName) -eq 'NPCSpeak' })
        if ($outputs.Count -ne 1) { throw 'Derleme çıktısında tek NPCSpeak JAR bulunamadı.' }
        return $outputs[0].FullName
    } finally { $env:JAVA_HOME = $oldHome; $env:GRADLE_USER_HOME = $oldCache }
}

function Start-ServerBat([string]$Root, [string]$Bat, [string]$Java) {
    $oldPath = $env:PATH
    $oldHome = $env:JAVA_HOME
    try {
        $env:PATH = "$(Split-Path $Java);$oldPath"
        $env:JAVA_HOME = Split-Path (Split-Path $Java)
        # Keep the exact BAT and JVM arguments. cmd stays hidden; Java keeps its own log.
        $null = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/c', ('""{0}""' -f $Bat)) `
            -WorkingDirectory $Root -WindowStyle Hidden -PassThru
    } finally { $env:PATH = $oldPath; $env:JAVA_HOME = $oldHome }
}

function Get-StartupLogStatus([string]$Text, [string[]]$KnownPluginNames = @()) {
    $fatal = [Collections.Generic.List[string]]::new()
    $otherPluginErrors = 0
    $npcStarted = $false
    $citizensStarted = $false
    $done = $false
    # Keep stack traces with the event that owns them; a bare Exception search loses attribution.
    $events = [regex]::Split($Text, '(?m)(?=^\[(?:\d{2}:\d{2}:\d{2}(?:\.\d+)?\]\s*\[)?[^\]\r\n]+\](?:\s|:)|^Done \()')
    $platformLoggers = @('Minecraft', 'Paper', 'PluginInitializerManager', 'PaperPluginInstanceManager', 'JavaPluginLoader', 'PluginClassLoader')
    foreach ($event in $events) {
        if ([string]::IsNullOrWhiteSpace($event)) { continue }
        $firstLine = ($event -split '\r?\n', 2)[0]
        # Player text is not a health signal, even if it quotes an error or plugin prefix.
        if ($firstLine -match '\[(?:Async Chat|AsyncChat|Chat Thread)[^\]]*/INFO\]|\]\s*:\s*<[^>]+>\s') { continue }
        $owner = ''
        if ($firstLine -match '\]\s*:?\s*\[(?<plugin>[^\]]+)\]\s') { $owner = $matches['plugin'] }
        elseif ($firstLine -match '^\[(?<plugin>[a-zA-Z][\w .-]*)\]\s') { $owner = $matches['plugin'] }
        # Unknown bracketed loggers can be Paper subsystems, not necessarily plugins.
        $namedPlugin = $owner -and $owner -notin $platformLoggers -and $owner -in (@('NPCSpeak', 'Citizens') + $KnownPluginNames)
        $errorEvent = $firstLine -match '/(?:ERROR|SEVERE|FATAL)\]|\[(?:ERROR|SEVERE|FATAL)\]'
        $exception = $event -match '(?m)^\s*(?:Caused by:\s*)?(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?::|\s|$)'
        $failureText = $firstLine -match '(?i)\b(?:failed|failure|could not|unable to|error occurred|exception)\b'

        # These affect the whole JVM/server, including when another plugin triggers them.
        if ($event -match '(?i)(?:java\.lang\.)?(?:OutOfMemoryError|VirtualMachineError)|GC overhead limit exceeded|unable to create native thread|No space left on device|There is not enough space on the disk') {
            $fatal.Add('JVM_OR_DISK_FAILURE')
        }
        if ((!$namedPlugin -and $firstLine -match '(?i)FAILED TO BIND TO PORT|Failed to (?:start the minecraft server|start server|check session lock|acquire.*lock)|Encountered an unexpected exception|Exception in server tick loop|The server has stopped responding|A single server tick took') -or $firstLine -match '/FATAL\]') {
            $fatal.Add('CRITICAL_SERVER_FAILURE')
        }
        if (!$namedPlugin -and $firstLine -match '(?i)\b(?:Stopping server|Closing Server|All dimensions are saved)\b') {
            $fatal.Add('SERVER_STOPPING')
        }
        if ($owner -in @('NPCSpeak', 'Citizens')) {
            if ($errorEvent -or $exception -or $failureText -or $firstLine -match '(?i)\bDisabling (?:NPCSpeak|Citizens)\b') {
                $fatal.Add('TARGET_PLUGIN_FAILURE')
            }
            if ($owner -eq 'NPCSpeak' -and $firstLine -match '\bEnabling NPCSpeak\b') { $npcStarted = $true }
            if ($owner -eq 'Citizens' -and $firstLine -match '\bEnabling Citizens\b') { $citizensStarted = $true }
        } elseif (!$namedPlugin -and $firstLine -match '(?i)(?:Error occurred while (?:enabling|loading|disabling)|Could not pass event .+? to)\s+(?<failedPlugin>[\w.-]+)') {
            if ($matches['failedPlugin'] -in @('NPCSpeak', 'Citizens')) { $fatal.Add('TARGET_PLUGIN_FAILURE') }
            else { $otherPluginErrors++ }
        } elseif (!$namedPlugin -and $firstLine -match '(?i)(?:Could not|Failed to|Unable to) (?:load|enable|initialize)\b.*\b(?:NPCSpeak|Citizens)\b') {
            $fatal.Add('TARGET_PLUGIN_FAILURE')
        } elseif ($errorEvent -or $exception) {
            if ($namedPlugin) { $otherPluginErrors++ }
            else { $fatal.Add('UNATTRIBUTED_SERVER_ERROR') }
        }
        if (!$namedPlugin -and $firstLine -match '\bDone \([\d.,]+s\)!' -and $npcStarted -and $citizensStarted) { $done = $true }
    }
    return [pscustomobject]@{
        Ready = $npcStarted -and $citizensStarted -and $done -and $fatal.Count -eq 0
        FatalReasons = @($fatal | Sort-Object -Unique)
        OtherPluginErrorCount = $otherPluginErrors
    }
}

function Test-StartupLog([string]$Text, [string[]]$KnownPluginNames = @()) {
    return (Get-StartupLogStatus $Text $KnownPluginNames).Ready
}

function Get-LogHeader([string]$Root) {
    $path = Join-Path $Root 'logs\latest.log'
    if (!(Test-Path -LiteralPath $path)) { return '' }
    $reader = [IO.File]::OpenText($path)
    try { return $reader.ReadLine() } finally { $reader.Dispose() }
}

function Wait-ServerReady {
    [CmdletBinding()]
    param([string]$Root, [hashtable]$Properties, [string]$BatText, [datetime]$Since, [int]$Timeout, [string]$OldHeader, [string[]]$KnownPluginNames = @())
    $deadline = [datetime]::UtcNow.AddSeconds($Timeout)
    $log = Join-Path $Root 'logs\latest.log'
    while ([datetime]::UtcNow -lt $deadline) {
        if ((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).LastWriteTimeUtc -ge $Since) {
            $text = [IO.File]::ReadAllText($log)
            # Never accept the previous boot's Done/Enabling lines during log rotation.
            if ($OldHeader -and $text.StartsWith($OldHeader)) { Start-Sleep -Seconds 2; continue }
            $status = Get-StartupLogStatus $text $KnownPluginNames
            if ($status.FatalReasons.Count -gt 0) {
                throw "Yeni başlangıçta kritik veya NPCSpeak/Citizens hatası: $($status.FatalReasons -join ', '). Ayrıntılar yerel logs/latest.log dosyasında."
            }
            if ($status.Ready) {
                $identity = Get-ServerIdentity $Properties $BatText
                if ($identity.Process.StartTime.ToUniversalTime() -lt $Since.AddSeconds(-2)) { throw 'Eski işlem başarı olarak kabul edilmedi.' }
                if ($status.OtherPluginErrorCount -gt 0) {
                    Write-Warning "NPCSpeak/Citizens başlangıcı doğrulandı; diğer pluginlere ait $($status.OtherPluginErrorCount) hata kaydı var. Bunlar geri alma başlatmadı; logs/latest.log dosyasını ayrıca inceleyin."
                }
                return $identity
            }
        }
        Start-Sleep -Seconds 2
    }
    throw 'NPCSpeak/Citizens etkinleşmesi ve Paper Done satırı zamanında doğrulanamadı.'
}

function Invoke-Deployment {
    [CmdletBinding(SupportsShouldProcess)]
    param()
    if ($Mode -eq 'Build') {
        $built = Invoke-ProjectBuild $JavaHome
        Write-Host "Derleme ve betik testleri başarılı: $built"
        return
    }
    $root = Resolve-DeploymentServerRoot $ServerRoot $LocalSettingsPath
    if ((Get-Item -LiteralPath $root).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Sunucu kökü junction olamaz.' }
    if ($StartBat -ne [IO.Path]::GetFileName($StartBat) -or [IO.Path]::GetExtension($StartBat) -ne '.bat') {
        throw 'StartBat sunucu ana klasöründeki bir BAT dosyası olmalı.'
    }
    $bat = Join-Path $root $StartBat
    $batText = [IO.File]::ReadAllText($bat)
    $batHash = (Get-FileHash -LiteralPath $bat -Algorithm SHA256).Hash
    $properties = Get-ServerProperties $root
    $plugins = Join-Path $root 'plugins'
    if ((Get-Item -LiteralPath $plugins).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'plugins klasörü junction olamaz.' }
    $oldJar = Get-NpcSpeakJar $plugins
    $knownPluginNames = @(Get-ChildItem -LiteralPath $plugins -File -Filter '*.jar' | Where-Object {$_.Extension -ieq '.jar'} |
        ForEach-Object { Get-PluginName $_.FullName } | Where-Object {$_})
    Write-Host "Başlatıcı: $bat"
    Write-Host "Mevcut NPCSpeak: $($oldJar.Name)"
    if ($Mode -eq 'Inspect') {
        Write-Host "BAT içeriği: $($batText.Trim())"
        $identity = Get-ServerIdentity $properties $batText
        Write-Host "Doğrulanan Java: $($identity.Java); PID: $($identity.Process.Id)"
        Write-Host "RCON: localhost:$($identity.RconPort); geniş arayüzde dinleme: $($identity.Wildcard)"
        Write-Host 'Yalnızca okuma tamamlandı. RCON bağlantısı/stop, yedekleme ve dosya değişikliği yapılmadı.'
        return
    }
    $newJar = Invoke-ProjectBuild $JavaHome
    if (!$ApproveDeployment) { throw 'Canlı dağıtım için kullanıcının onayı ve -ApproveDeployment gerekli.' }
    $identity = Get-ServerIdentity $properties $batText
    if ($identity.Wildcard -and !$RconFirewallVerified) {
        throw 'RCON geniş arayüzde dinliyor. Dış erişimin engellendiğini doğruladıktan sonra -RconFirewallVerified kullanın.'
    }
    if (!$PSCmdlet.ShouldProcess($root, 'Yedekle, RCON stop gönder, NPCSpeak JAR değiştir ve aynı BAT ile başlat')) { return }
    # Server-local lock also blocks deployments from another checkout.
    $lock = [IO.File]::Open((Join-Path $root '.npcspeak-deploy.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $stopped = $false
    $changed = $false
    $backup = $null
    try {
        $oldHash = (Get-FileHash -LiteralPath $oldJar.FullName -Algorithm SHA256).Hash
        $newHash = (Get-FileHash -LiteralPath $newJar -Algorithm SHA256).Hash
        $backupRoot = Join-Path $root 'deployment-backups'
        if (!(Test-Path -LiteralPath $backupRoot)) { $null = New-Item -ItemType Directory -Path $backupRoot }
        Assert-NoReparsePoint $backupRoot
        $backup = Join-Path $backupRoot ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N'))
        $null = New-Item -ItemType Directory -Path $backup
        Copy-Item -LiteralPath $oldJar.FullName -Destination (Join-Path $backup $oldJar.Name)
        foreach ($file in @($StartBat, 'server.properties')) {
            Copy-Item -LiteralPath (Join-Path $root $file) -Destination $backup
        }
        # Pre-stop safety copy; the second copy after shutdown is the consistent backup.
        foreach ($folder in @('NPCSpeak', 'Citizens')) {
            $data = Join-Path $plugins $folder
            if (Test-Path -LiteralPath $data) {
                Assert-NoReparsePoint $data
                Copy-Item -LiteralPath $data -Destination (Join-Path $backup ('before-stop-' + $folder)) -Recurse
            }
        }
        if ((Get-FileHash -LiteralPath (Join-Path $backup $oldJar.Name)).Hash -ne $oldHash) { throw 'JAR yedeği doğrulanamadı.' }
        # Authenticate before stop; credentials and responses are never printed.
        $probe = Open-Rcon $properties
        $probe.Dispose()
        if ((Get-FileHash -LiteralPath $bat).Hash -ne $batHash) { throw 'BAT incelemeden sonra değişmiş.' }
        Write-Host "Yedek: $backup. Sunucuya güvenli stop gönderiliyor."
        Stop-ServerSafely $identity $properties $StopTimeoutSeconds
        $stopped = $true
        foreach ($folder in @('NPCSpeak', 'Citizens')) {
            $data = Join-Path $plugins $folder
            if (Test-Path -LiteralPath $data) {
                Assert-NoReparsePoint $data
                Copy-Item -LiteralPath $data -Destination (Join-Path $backup $folder) -Recurse
            }
        }
        $current = Get-NpcSpeakJar $plugins
        if ($current.FullName -ne $oldJar.FullName -or (Get-FileHash -LiteralPath $current.FullName).Hash -ne $oldHash) {
            throw 'Çalışan JAR incelemeden sonra değişmiş.'
        }
        if ((Get-FileHash -LiteralPath $newJar).Hash -ne $newHash) { throw 'Derlenmiş JAR sonradan değişmiş.' }
        Assert-PortsClosed $properties
        # Replace the existing filename; never introduce a second loadable NPCSpeak JAR.
        $changed = $true
        Copy-Item -LiteralPath $newJar -Destination $oldJar.FullName -Force
        if ((Get-FileHash -LiteralPath $oldJar.FullName).Hash -ne $newHash) { throw 'Yeni JAR doğrulanamadı.' }
        $since = [datetime]::UtcNow
        $header = Get-LogHeader $root
        Start-ServerBat $root $bat $identity.Java
        $ready = Wait-ServerReady $root $properties $batText $since $StartTimeoutSeconds $header $knownPluginNames
        Write-Host "Dağıtım başarılı. NPCSpeak ve Citizens başlangıcı doğrulandı; PID: $($ready.Process.Id). Yedek: $backup"
    } catch {
        $failure = $_
        if ($stopped) {
            try {
                # A failed startup may still be running. Stop it gracefully before rollback.
                $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {
                    $_.LocalPort -in @([int]$properties['server-port'], [int]$properties['rcon.port'])
                })
                if ($listeners.Count -gt 0) {
                    $failedIdentity = Get-ServerIdentity $properties $batText
                    Stop-ServerSafely $failedIdentity $properties $StopTimeoutSeconds
                }
                Assert-PortsClosed $properties
                # Ports may close before the process exits, or before a slow startup binds.
                $possible = @(Get-CimInstance Win32_Process -Filter "name = 'java.exe'" | Where-Object {
                    $_.CommandLine -match 'io\.papermc\.paperclip\.Main'
                })
                if ($possible.Count -gt 0) { throw 'Paper işlemi hâlâ mevcut. Otomatik geri alma güvenli değil; dosyalar değiştirilmedi.' }
                if ($changed) {
                    Copy-Item -LiteralPath (Join-Path $backup $oldJar.Name) -Destination $oldJar.FullName -Force
                    if ((Get-FileHash -LiteralPath $oldJar.FullName).Hash -ne $oldHash) { throw 'Geri alınan JAR doğrulanamadı.' }
                }
                $since = [datetime]::UtcNow
                $header = Get-LogHeader $root
                Start-ServerBat $root $bat $identity.Java
                $null = Wait-ServerReady $root $properties $batText $since $StartTimeoutSeconds $header $knownPluginNames
                Write-Warning 'Önceki JAR ile sunucu yeniden başlatıldı. Veri klasörleri otomatik geri sarılmadı.'
            } catch {
                Write-Warning "Otomatik geri alma tamamlanamadı: $($_.Exception.Message) Yedek: $backup. Zorla kapatma yapılmadı."
            }
        }
        throw $failure
    } finally { $lock.Dispose() }
}

# Dot-sourcing exposes helpers for isolated tests without contacting the server.
if ($MyInvocation.InvocationName -ne '.') { Invoke-Deployment }

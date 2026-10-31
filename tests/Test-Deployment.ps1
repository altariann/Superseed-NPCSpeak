#Requires -Version 5.1
# Offline tests: generated JARs, memory streams and a disposable server tree only.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$WhatIfPreference = $false
$ConfirmPreference = 'None'
. (Join-Path (Split-Path $PSScriptRoot) 'Deploy-Superseed.ps1')
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('npcspeak-deploy-tests-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $testRoot
$checks = 0
function Assert-True([bool]$Value, [string]$Message) {
    if (!$Value) { throw "TEST BAŞARISIZ: $Message" }
    $script:checks++
}
function Assert-Throws([scriptblock]$Action, [string]$Message) {
    $threw = $false
    try { & $Action } catch { $threw = $true }
    Assert-True $threw $Message
}
function New-TestJar([string]$Path, [string]$Name) {
    $zip = [IO.Compression.ZipFile]::Open($Path, 'Create')
    try {
        $writer = [IO.StreamWriter]::new($zip.CreateEntry('plugin.yml').Open())
        try { $writer.Write("name: '$Name'`nversion: 'test'`n") } finally { $writer.Dispose() }
    } finally { $zip.Dispose() }
}
try {
    Assert-True ($LocalSettingsPath -eq (Join-Path $RepoRoot 'Deploy-Superseed.local.json')) 'Yerel ayar varsayılanı çağıran betiğe değil giriş betiğinin klasörüne bağlanmalı.'
    $localSettings = Join-Path $testRoot 'Deploy-Superseed.local.json'
    $missingSettings = Join-Path $testRoot 'missing.local.json'
    Assert-Throws { Resolve-DeploymentServerRoot '' $missingSettings } 'Sunucu yolu yoksa mevcut klasöre veya eski sunucuya dönülmemeli.'
    [IO.File]::WriteAllText($localSettings, (@{ServerRoot=$testRoot} | ConvertTo-Json))
    Assert-True ((Resolve-DeploymentServerRoot '' $localSettings) -eq $testRoot) 'Takip edilmeyen yerel JSON sunucu yolunu seçebilmeli.'
    [IO.File]::WriteAllText($localSettings, '{ invalid json')
    Assert-True ((Resolve-DeploymentServerRoot $testRoot $localSettings) -eq $testRoot) 'Komut parametresi yerel ayardan öncelikli olmalı; JSON okunmamalı.'
    Assert-Throws { Resolve-DeploymentServerRoot '' $localSettings } 'Bozuk yerel JSON reddedilmeli.'
    [IO.File]::WriteAllText($localSettings, ('[' + (@{ServerRoot=$testRoot} | ConvertTo-Json -Compress) + ']'))
    Assert-Throws { Resolve-DeploymentServerRoot '' $localSettings } 'Tek elemanlı JSON dizisi ayar nesnesi sayılmamalı.'
    [IO.File]::WriteAllText($localSettings, '{"ServerRoot":42}')
    Assert-Throws { Resolve-DeploymentServerRoot '' $localSettings } 'Sayısal sunucu yolu reddedilmeli.'
    [IO.File]::WriteAllText($localSettings, '{}')
    Assert-Throws { Resolve-DeploymentServerRoot '' $localSettings } 'Eksik yerel ServerRoot reddedilmeli.'
    [IO.File]::WriteAllText($localSettings, '{"ServerRoot":" "}')
    Assert-Throws { Resolve-DeploymentServerRoot '' $localSettings } 'Boş yerel ServerRoot reddedilmeli.'
    Assert-Throws { Resolve-DeploymentServerRoot '.' $missingSettings } 'Göreli sunucu yolu reddedilmeli.'
    Assert-Throws { Resolve-DeploymentServerRoot 'C:relative' $missingSettings } 'Sürücüye göreli yol mutlak yol sayılmamalı.'
    Assert-Throws { Resolve-DeploymentServerRoot (Join-Path $testRoot 'not-a-server') $missingSettings } 'Var olmayan klasör reddedilmeli.'

    $jar = Join-Path $testRoot 'arbitrary-name.jar'
    New-TestJar $jar 'NPCSpeak'
    New-TestJar (Join-Path $testRoot 'Citizens.jar') 'Citizens'
    New-TestJar (Join-Path $testRoot 'NPCSpeak.jar_original') 'NPCSpeak'
    Assert-True ((Get-NpcSpeakJar $testRoot).Name -eq 'arbitrary-name.jar') 'Plugin kimliği dosya adından bağımsız okunmalı.'
    Assert-True ((Get-NpcSpeakJar $testRoot).Name -ne 'NPCSpeak.jar_original') 'Windows joker karakter eşleşmesi .jar_original yedeğini plugin saymamalı.'
    New-TestJar (Join-Path $testRoot 'duplicate.jar') 'NPCSpeak'
    Assert-Throws { Get-NpcSpeakJar $testRoot } 'İki NPCSpeak JAR reddedilmeli.'
    Remove-Item -LiteralPath (Join-Path $testRoot 'duplicate.jar')
    Remove-Item -LiteralPath $jar
    Assert-Throws { Get-NpcSpeakJar $testRoot } 'Eksik NPCSpeak JAR reddedilmeli.'

    [IO.File]::WriteAllText((Join-Path $testRoot 'server.properties'), "# test`nenable-rcon=true`nrcon.password=a=b`nrcon.port=25575`nserver-port=25565`n")
    $props = Get-ServerProperties $testRoot
    Assert-True ($props['rcon.password'] -eq 'a=b') 'Paroladaki eşittir korunmalı.'
    $memory = [IO.MemoryStream]::new()
    Write-RconPacket $memory 42 2 'stop'
    $bytes = $memory.ToArray()
    Assert-True ($bytes.Length -eq 18 -and [Text.Encoding]::UTF8.GetString($bytes,12,4) -eq 'stop') 'RCON stop paket biçimi.'
    $memory.Position = 0
    $packet = Read-RconPacket $memory
    Assert-True ($packet.Id -eq 42 -and $packet.Type -eq 2) 'RCON paket kimliği/türü.'
    $memory.Dispose()
    $short = [IO.MemoryStream]::new([byte[]]@(10,0,0,0,1))
    Assert-Throws { Read-RconPacket $short } 'Kesik RCON paketi reddedilmeli.'
    $short.Dispose()
    $bad = [IO.MemoryStream]::new([BitConverter]::GetBytes([int]999999))
    Assert-Throws { Read-RconPacket $bad } 'Aşırı büyük RCON paketi reddedilmeli.'
    $bad.Dispose()

    $validLog = "[NPCSpeak] Enabling NPCSpeak v1.0`n[Citizens] Enabling Citizens v2`nDone (12.345s)! For help"
    Assert-True (Test-StartupLog $validLog) 'İki plugin ve Done başarı olarak algılanmalı.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[NPCSpeak] Disabling NPCSpeak"))) 'Sonradan kapanan plugin başarı sayılmamalı.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[Server/ERROR]: Exception test"))) 'Başlangıç hatası reddedilmeli.'
    Assert-True (!(Test-StartupLog 'Done (1.0s)!')) 'Sadece Done yeterli olmamalı.'

    $otherError = "[02:00:00] [Server thread/ERROR]: [OtherPlugin] Optional service failed`njava.lang.IllegalStateException: test-only`n`t at other.plugin.Startup.run(Startup.java:1)`n"
    $otherStatus = Get-StartupLogStatus ($otherError + $validLog) @('OtherPlugin')
    Assert-True $otherStatus.Ready 'Bilinen ilgisiz plugin stack trace hatası hedef pluginlerin başarısını bozmamalı.'
    Assert-True ($otherStatus.OtherPluginErrorCount -eq 1 -and $otherStatus.FatalReasons.Count -eq 0) 'İlgisiz hata uyarı olarak sayılmalı.'
    Assert-True (Test-StartupLog ($validLog + "`n[Server thread/ERROR]: [OtherPlugin] Could not enable Citizens integration`n at com.l299l.npcspeak.SomeClass.run(Test.java:1)") @('OtherPlugin')) 'İlgisiz pluginin entegrasyon/stack trace içindeki hedef adı hedef hatası sayılmamalı.'
    Assert-True (Test-StartupLog ($otherError.Replace('Optional service failed','Failed to start server for an optional HTTP service') + $validLog) @('OtherPlugin')) 'İlgisiz pluginin kendi HTTP sunucusu hatası Minecraft çökmesi sayılmamalı.'
    Assert-True (Test-StartupLog ("[Server thread/ERROR]: Error occurred while enabling OtherPlugin v1.0`njava.lang.RuntimeException: test`n" + $validLog)) 'Bukkit tarafından açıkça başka plugine atfedilen etkinleşme hatası engellememeli.'
    Assert-True (Test-StartupLog ($validLog + "`n[Server thread/INFO]: Previous Exception count: 0")) 'Bilgilendirme metnindeki Exception sözcüğü hata olmamalı.'
    Assert-True (Test-StartupLog ($validLog + "`n[Async Chat Thread - #0/INFO]: <TestPlayer> OutOfMemoryError [NPCSpeak] ERROR")) 'Oyuncu metni sağlık sinyali sayılmamalı.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[NPCSpeak] Enabling NPCSpeak v1.0`njava.lang.IllegalStateException: enable failure"))) 'Hedef pluginin stack trace hatası reddedilmeli.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[Server thread/ERROR]: [Citizens] Failed to initialize registry"))) 'Citizens hatası reddedilmeli.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[Server thread/INFO]: [Citizens] Disabling Citizens v2"))) 'Citizens kapanmışsa başarı kabul edilmemeli.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[Server thread/ERROR]: Error occurred while enabling NPCSpeak v1.0"))) 'Bukkit NPCSpeak etkinleşme hatası reddedilmeli.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[Server thread/ERROR]: Could not pass event TestEvent to Citizens v2"))) 'Bukkit Citizens olay hatası reddedilmeli.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[Server thread/ERROR]: [PluginInitializerManager] Could not load plugins/Superseed-NPCSpeak.jar"))) 'Hedef JAR yükleme hatası platform logger altında da reddedilmeli.'
    Assert-True (!(Test-StartupLog ($otherError.Replace('java.lang.IllegalStateException','java.lang.OutOfMemoryError') + $validLog) @('OtherPlugin'))) 'Başka plugin tetiklese de JVM bellek hatası kritik olmalı.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[Server thread/ERROR]: [OtherPlugin] No space left on device") @('OtherPlugin'))) 'Disk dolması başka plugin altında da kritik olmalı.'
    foreach ($criticalMessage in @('**** FAILED TO BIND TO PORT!', 'Failed to start the minecraft server', 'Encountered an unexpected exception', 'Exception in server tick loop', 'The server has stopped responding!', 'Failed to check session lock')) {
        Assert-True (!(Test-StartupLog ($validLog + "`n[Server thread/ERROR]: $criticalMessage"))) "Kritik sunucu hatası reddedilmeli: $criticalMessage"
    }
    Assert-True (!(Test-StartupLog ($validLog + "`n[Server thread/INFO]: Stopping server"))) 'Done sonrasındaki güvenli kapanış başarı sayılmamalı.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[Server thread/ERROR]: [ChunkTaskScheduler] Unknown chunk failure") @('OtherPlugin'))) 'Bilinmeyen Paper logger bir plugin varsayılarak geçilmemeli.'
    Assert-True (!(Test-StartupLog ($validLog + "`n[Server thread/FATAL]: Unrecoverable failure"))) 'FATAL sunucu hatası reddedilmeli.'
    Assert-True (!(Test-StartupLog "[NPCSpeak] Enabling NPCSpeak v1.0`nDone (1.0s)!")) 'Eksik Citizens etkinleşmesi kabul edilmemeli.'
    Assert-True (!(Test-StartupLog "Done (1.0s)!`n[NPCSpeak] Enabling NPCSpeak v1.0`n[Citizens] Enabling Citizens v2")) 'Done hedef pluginlerden önceyse başarı sayılmamalı.'

    # No listeners/processes/real RCON are used below. Mocks record lifecycle order.
    $server = Join-Path $testRoot 'server'
    $plugins = Join-Path $server 'plugins'
    $null = New-Item -ItemType Directory -Path $plugins
    $null = New-Item -ItemType Directory -Path (Join-Path $plugins 'NPCSpeak')
    [IO.File]::WriteAllText((Join-Path $plugins 'NPCSpeak\knowledge.yml'), 'preserve-this')
    [IO.File]::WriteAllText((Join-Path $server 'START.bat'), 'java -jar paper.jar')
    [IO.File]::WriteAllText((Join-Path $server 'server.properties'), 'server-port=25565')
    $runningJar = Join-Path $plugins 'old.jar'
    $candidate = Join-Path $testRoot 'new.jar'
    New-TestJar $runningJar 'NPCSpeak'
    New-TestJar $candidate 'NPCSpeak'
    # Add a marker so candidate and original have different content hashes.
    $zip = [IO.Compression.ZipFile]::Open($candidate, 'Update')
    $null = $zip.CreateEntry('new-version'); $zip.Dispose()
    $originalHash = (Get-FileHash -LiteralPath $runningJar).Hash
    $candidateHash = (Get-FileHash -LiteralPath $candidate).Hash
    $script:events = [Collections.Generic.List[string]]::new()
    $script:failBuild = $false
    $script:failStop = $false
    $script:failReady = $false
    $script:wildcard = $false
    function Invoke-ProjectBuild { param($Jdk); $script:events.Add('build'); if ($script:failBuild) { throw 'build failed' }; return $candidate }
    function Get-ServerIdentity { param($Properties, $BatText); return [pscustomobject]@{Java='java.exe'; Wildcard=$script:wildcard; Process=[pscustomobject]@{Id=1;StartTime=Get-Date}} }
    function Open-Rcon { param($Properties); return [IO.MemoryStream]::new() }
    function Stop-ServerSafely { param($Identity,$Properties,$Timeout); $script:events.Add('stop'); if ($script:failStop) { throw 'stop timeout' } }
    function Assert-PortsClosed { param($Properties); $script:events.Add('ports-closed') }
    function Get-NetTCPConnection { param($State,$LocalPort,$ErrorAction); return @() }
    function Get-CimInstance { param($ClassName,$Filter); return @() }
    function Start-ServerBat { param($Root,$Bat,$Java); $script:events.Add('start') }
    # Exercise the actual polling path before replacing it with the lifecycle mock.
    $logRoot = Join-Path $testRoot 'log-fixture'
    $null = New-Item -ItemType Directory -Path (Join-Path $logRoot 'logs')
    $since = [datetime]::UtcNow.AddSeconds(-1)
    [IO.File]::WriteAllText((Join-Path $logRoot 'logs\latest.log'), $otherError + $validLog)
    $ready = Wait-ServerReady $logRoot @{} '' $since 1 '' @('OtherPlugin') -WarningVariable healthWarnings -WarningAction SilentlyContinue
    Assert-True ($ready.Process.Id -eq 1) 'Gerçek bekleme döngüsü ilgisiz plugin hatasına rağmen başarı döndürmeli.'
    Assert-True (@($healthWarnings).Count -eq 1) 'Gerçek bekleme döngüsü ilgisiz hataları uyarı olarak bildirmeli.'
    [IO.File]::WriteAllText((Join-Path $logRoot 'logs\latest.log'), $validLog + "`n[Server/ERROR]: Failed to start the minecraft server")
    Assert-Throws { Wait-ServerReady $logRoot @{} '' $since 1 '' } 'Gerçek bekleme döngüsü kritik hatada hemen durmalı.'
    function Wait-ServerReady {
        param($Root,$Properties,$BatText,$Since,$Timeout,$OldHeader)
        $script:events.Add('ready')
        if ($script:failReady) { $script:failReady=$false; throw 'startup failed' }
        return [pscustomobject]@{Process=[pscustomobject]@{Id=2}}
    }
    $Mode = 'Deploy'; $ServerRoot = $server; $StartBat = 'START.bat'; $ApproveDeployment = $true
    $Mode = 'Build'; $ServerRoot = ''; $LocalSettingsPath = $missingSettings
    Invoke-Deployment
    Assert-True (($script:events -join ',') -eq 'build') 'Build modu sunucu yolu veya yerel ayar gerektirmemeli.'
    $Mode = 'Deploy'; $ServerRoot = $server; $script:events.Clear()
    Invoke-Deployment -WhatIf
    Assert-True (($script:events -join ',') -eq 'build') 'WhatIf sunucu komutu göndermemeli.'
    Assert-True (!(Test-Path -LiteralPath (Join-Path $server 'deployment-backups'))) 'WhatIf sunucuya yazmamalı.'
    $ApproveDeployment = $false; $script:events.Clear()
    Assert-Throws { Invoke-Deployment } 'Açık onay bayrağı olmadan dağıtım reddedilmeli.'
    Assert-True (($script:events -join ',') -eq 'build') 'Onaysız dağıtım sunucuya dokunmamalı.'
    $ApproveDeployment = $true; $script:wildcard = $true; $script:events.Clear()
    Assert-Throws { Invoke-Deployment } 'Geniş arayüzde dinleyen RCON için güvenlik kontrolü gerekli.'
    Assert-True (($script:events -join ',') -eq 'build') 'RCON dış erişim kontrolü olmadan stop yapılmamalı.'
    $script:wildcard = $false; $script:events.Clear()
    $script:failBuild = $true
    Assert-Throws { Invoke-Deployment } 'Başarısız derleme dağıtımı durdurmalı.'
    Assert-True (($script:events -join ',') -eq 'build') 'Derleme başarısızsa stop/başlat yapılmamalı.'
    Assert-True (!(Test-Path -LiteralPath (Join-Path $server 'deployment-backups'))) 'Derleme başarısızsa sunucuya yedek bile yazılmamalı.'
    $script:failBuild = $false; $script:failStop = $true; $script:events.Clear()
    Assert-Throws { Invoke-Deployment } 'Stop zaman aşımı dağıtımı durdurmalı.'
    Assert-True ((Get-FileHash -LiteralPath $runningJar).Hash -eq $originalHash) 'Stop tamamlanmadan JAR değişmemeli.'
    Assert-True (!$script:events.Contains('start')) 'Stop başarısızsa ikinci sunucu başlatılmamalı.'
    $script:failStop = $false; $script:events.Clear()
    Invoke-Deployment
    Assert-True ((Get-FileHash -LiteralPath $runningJar).Hash -eq $candidateHash) 'Başarıda yeni JAR kurulmalı.'
    Assert-True (($script:events -join ',') -eq 'build,stop,ports-closed,start,ready') 'Güvenli işlem sırası korunmalı.'
    Assert-True ([IO.File]::ReadAllText((Join-Path $plugins 'NPCSpeak\knowledge.yml')) -eq 'preserve-this') 'Bilgi dosyası korunmalı.'
    $script:failReady = $true; $script:events.Clear()
    # Restore original fixture before testing failed startup rollback.
    $backupJar = Get-ChildItem -LiteralPath (Join-Path $server 'deployment-backups') -Filter old.jar -Recurse |
        Where-Object {(Get-FileHash -LiteralPath $_.FullName).Hash -eq $originalHash} | Select-Object -First 1
    Copy-Item -LiteralPath $backupJar.FullName -Destination $runningJar -Force
    Assert-Throws { Invoke-Deployment } 'Başlangıç hatası sonrasında genel sonuç başarısız kalmalı.'
    Assert-True ((Get-FileHash -LiteralPath $runningJar).Hash -eq $originalHash) 'Başlangıç hatasında eski JAR geri alınmalı.'
    Assert-True (@($script:events | Where-Object {$_ -eq 'start'}).Count -eq 2) 'Başarısız yeni sürümden sonra eski JAR başlatılmalı.'
    Write-Host "Dağıtım betiği: $checks denetim başarılı (canlı sunucu kullanılmadı)."
} finally {
    # Validate the disposable absolute path before recursive removal on Windows.
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\npcspeak-deploy-tests-'
    if (!$resolved.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Güvenli test temizleme yolu doğrulanamadı.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

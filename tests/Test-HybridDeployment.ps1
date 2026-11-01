#Requires -Version 5.1
# Offline transaction tests: a disposable fake server tree and lifecycle mocks only.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$WhatIfPreference = $false
$ConfirmPreference = 'None'
. (Join-Path (Split-Path $PSScriptRoot) 'Deploy-Superseed.ps1')
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('npcspeak-hybrid-tests-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $testRoot
$checks = 0
$fixtureCount = 0
$marker = 'HYBRID_READY_V1 ' + ('c' * 64)
function Assert-True([bool]$Value, [string]$Message) {
    if (!$Value) { throw "TEST BAŞARISIZ: $Message" }
    $script:checks++
}
function Assert-Throws([scriptblock]$Action, [string]$Message) {
    $threw = $false
    try { & $Action } catch { $threw = $true }
    Assert-True $threw $Message
}
function New-TestJar([string]$Path, [string]$Name, [string]$Extra = '') {
    $zip = [IO.Compression.ZipFile]::Open($Path, 'Create')
    try {
        $writer = [IO.StreamWriter]::new($zip.CreateEntry('plugin.yml').Open())
        try { $writer.Write("name: '$Name'`nversion: 'test'`n") } finally { $writer.Dispose() }
        if ($Extra) { $null = $zip.CreateEntry($Extra) }
    } finally { $zip.Dispose() }
}
function Get-Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }
function Write-Plan($Fixture) {
    $json = $Fixture.Plan | ConvertTo-Json -Depth 6
    [IO.File]::WriteAllText($Fixture.Manifest, $json)
    return Get-Hash $Fixture.Manifest
}
function Write-Validation($Fixture, [hashtable]$Change = @{}) {
    $validation = @{Valid=$true;ArtifactClassesVerified=$true;LocalChecks=4;Marker=$marker
        JarSha256=(Get-Hash $Fixture.Candidates.Jar);NpcSha256=(Get-Hash $Fixture.Candidates.Npc);KnowledgeSha256=(Get-Hash $Fixture.Candidates.Knowledge)}
    foreach ($key in $Change.Keys) { $validation[$key] = $Change[$key] }
    [IO.File]::WriteAllText($Fixture.Validation, ($validation | ConvertTo-Json))
    $Fixture.Plan.ValidationSha256 = Get-Hash $Fixture.Validation
}
function New-Fixture {
    $script:fixtureCount++
    $base = Join-Path $testRoot ([string]$script:fixtureCount)
    $root = Join-Path $base 'server'
    $folder = Join-Path $base 'plan'
    foreach ($path in @('plugins\NPCSpeak\npcs', 'logs')) { $null = New-Item -ItemType Directory -Path (Join-Path $root $path) }
    $null = New-Item -ItemType Directory -Path (Join-Path $folder 'files')
    [IO.File]::WriteAllText((Join-Path $root 'START.bat'), 'java -cp paper.jar io.papermc.paperclip.Main -nogui')
    [IO.File]::WriteAllText((Join-Path $root 'server.properties'), "enable-rcon=true`nrcon.password=fixture`nrcon.port=25575`nserver-port=25565`n")
    [IO.File]::WriteAllText((Join-Path $root 'logs\latest.log'), "old boot`n")
    $targets = @{Jar=(Join-Path $root 'plugins\old.jar');Npc=(Join-Path $root 'plugins\NPCSpeak\npcs\dogan-tirpan.yml');Knowledge=(Join-Path $root 'plugins\NPCSpeak\knowledge.yml')}
    $candidates = @{Jar=(Join-Path $folder 'files\new.jar');Npc=(Join-Path $folder 'files\npc.yml');Knowledge=(Join-Path $folder 'files\knowledge.yml')}
    New-TestJar $targets.Jar 'NPCSpeak'
    New-TestJar (Join-Path $root 'plugins\Citizens.jar') 'Citizens'
    New-TestJar $candidates.Jar 'NPCSpeak' 'new-version'
    [IO.File]::WriteAllText($targets.Npc, "citizens-id: 313`n")
    [IO.File]::WriteAllText($candidates.Npc, "citizens-id: 313`nknowledge-entity: 'fixture'`n")
    [IO.File]::WriteAllText($targets.Knowledge, "enabled: true`n")
    [IO.File]::WriteAllText($candidates.Knowledge, "enabled: true`nmunicipal-records: []`n")
    $fixture = [pscustomobject]@{Root=$root;Folder=$folder;Manifest=(Join-Path $folder 'deployment-plan.json');Validation=(Join-Path $folder 'validation.json')
        Targets=$targets;Candidates=$candidates;Plan=$null;OldHashes=@{};NewHashes=@{};PlanHash=''}
    foreach ($kind in @('Jar','Npc','Knowledge')) {
        $fixture.OldHashes[$kind] = Get-Hash $targets[$kind]
        $fixture.NewHashes[$kind] = Get-Hash $candidates[$kind]
    }
    $fixture.Plan = [ordered]@{FormatVersion=1;ServerRoot=$root;StartBat='START.bat'
        BatSha256=(Get-Hash (Join-Path $root 'START.bat'));PropertiesSha256=(Get-Hash (Join-Path $root 'server.properties'))
        ValidationSha256='';Marker=$marker;Files=@(
            [ordered]@{Kind='Jar';Target='plugins/old.jar';OriginalSha256=$fixture.OldHashes.Jar;Candidate='files/new.jar';CandidateSha256=$fixture.NewHashes.Jar},
            [ordered]@{Kind='Npc';Target='plugins/NPCSpeak/npcs/dogan-tirpan.yml';OriginalSha256=$fixture.OldHashes.Npc;Candidate='files/npc.yml';CandidateSha256=$fixture.NewHashes.Npc},
            [ordered]@{Kind='Knowledge';Target='plugins/NPCSpeak/knowledge.yml';OriginalSha256=$fixture.OldHashes.Knowledge;Candidate='files/knowledge.yml';CandidateSha256=$fixture.NewHashes.Knowledge})}
    Write-Validation $fixture
    $fixture.PlanHash = Write-Plan $fixture
    $script:ExpectedPlanSha256 = $fixture.PlanHash
    $script:events.Clear(); $script:readyMarkers.Clear()
    $script:onStop = $null; $script:onReady = $null; $script:failWritesAt = @(); $script:writeCount = 0
    $script:listeners = @(); $script:wildcard = $false; $script:current = $fixture
    return $fixture
}
function Get-Backup($Fixture) { return (Get-ChildItem -LiteralPath (Join-Path $Fixture.Root 'deployment-backups') -Directory | Select-Object -First 1).FullName }
function Get-Phase($Fixture) { return (Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path (Get-Backup $Fixture) 'transaction.json') | ConvertFrom-Json).Phase }
function Test-State($Fixture, [hashtable]$Expected) {
    foreach ($kind in @('Jar','Npc','Knowledge')) { if ((Get-Hash $Fixture.Targets[$kind]) -ne $Expected[$kind]) { return $false } }
    return $true
}
function Test-NoBackup($Fixture) { return !(Test-Path -LiteralPath (Join-Path $Fixture.Root 'deployment-backups')) }
function Get-EventCount([string]$Name) { return @($script:events | Where-Object {$_ -eq $Name}).Count }
function Invoke-Hybrid($Fixture) { Invoke-HybridDeployment -Root $Fixture.Root -ManifestPath $Fixture.Manifest 6>$null 3>$null }
function Write-Receipt($Fixture, [string]$Prefix = '[12:00:00 INFO]: [NPCSpeak] ') {
    [IO.File]::WriteAllText($Fixture.Targets.Npc, "citizens-id: 313`nknowledge-entity: fixture # rewritten at boot`n")
    [IO.File]::WriteAllText((Join-Path $Fixture.Root 'logs\latest.log'), "new boot`n$Prefix$marker NPC_SHA256 $(Get-Hash $Fixture.Targets.Npc)`n")
}

try {
    $script:events = [Collections.Generic.List[string]]::new()
    $script:readyMarkers = [Collections.Generic.List[string]]::new()
    $script:realWrite = ${function:Write-HybridTarget}
    function Get-ServerIdentity { param($Properties, $BatText); return [pscustomobject]@{Java='java.exe'; Wildcard=$script:wildcard; Process=[pscustomobject]@{Id=1;StartTime=Get-Date}} }
    function Open-Rcon { param($Properties); $script:events.Add('rcon-probe'); return [IO.MemoryStream]::new() }
    function Stop-ServerSafely { param($Identity,$Properties,$Timeout); $script:events.Add('stop'); if ($script:onStop) { & $script:onStop } }
    function Assert-PortsClosed { param($Properties); $script:events.Add('ports-closed') }
    function Get-NetTCPConnection { param($State,$LocalPort,$ErrorAction); return @($script:listeners) }
    function Start-ServerBat { param($Root,$Bat,$Java); $script:events.Add('start') }
    function Wait-ServerReady {
        param($Root,$Properties,$BatText,$Since,$Timeout,$OldHeader,$KnownPluginNames,$ExpectedHybridMarker)
        $script:events.Add('ready'); $script:readyMarkers.Add([string]$ExpectedHybridMarker)
        if ($script:onReady) { & $script:onReady }
    }
    function Write-HybridTarget([IO.Stream]$Target, [string]$Source) {
        $script:writeCount++
        if ($script:writeCount -in $script:failWritesAt) {
            $Target.Position = 0; $Target.SetLength(0); $Target.WriteByte(1); $Target.Flush()
            throw 'injected partial write'
        }
        & $script:realWrite $Target $Source
    }
    $StartBat = 'START.bat'; $RconFirewallVerified = $false; $StopTimeoutSeconds = 30; $StartTimeoutSeconds = 30

    # Startup attestation.
    $validLog = "[12:00:00 INFO]: [NPCSpeak] Enabling NPCSpeak v1.0`n[12:00:00 INFO]: [Citizens] Enabling Citizens v2`n"
    $doneLog = "[12:00:05 INFO]: Done (12.345s)! For help`n"
    $bootLine = "[12:00:01 INFO]: [NPCSpeak] $marker NPC_SHA256 $('d' * 64)`n"
    Assert-True (Get-StartupLogStatus ($validLog + $bootLine + $doneLog) @() $marker).Ready 'Beklenen açılış kanıtı hazır sayılmalı.'
    Assert-True (!(Get-StartupLogStatus ($validLog + $doneLog) @() $marker).Ready) 'Açılış kanıtı yoksa hazır sayılmamalı.'
    Assert-True (!(Get-StartupLogStatus ($validLog + $bootLine.Replace(('c' * 64), ('e' * 64)) + $doneLog) @() $marker).Ready) 'Farklı içerik parmak izi kabul edilmemeli.'
    Assert-True (!(Get-StartupLogStatus ($validLog + "[12:00:02 INFO]: <Oyuncu> [NPCSpeak] $marker`n" + $doneLog) @() $marker).Ready) 'Oyuncu sohbeti açılış kanıtı olamaz.'
    Assert-True (!(Get-StartupLogStatus ($validLog + "[12:00:02 INFO]: [Other] [NPCSpeak] $marker`n" + $doneLog) @('Other') $marker).Ready) 'Başka pluginin satırı açılış kanıtı olamaz.'
    Assert-True (Get-StartupLogStatus ($validLog + $doneLog)).Ready 'Beklenen kanıt verilmeyen eski dağıtım yolu değişmemeli.'

    # Read-only preview and dispatch from the entry point.
    $f = New-Fixture
    $Mode = 'Preview'; $ApproveDeployment = $false; $ServerRoot = $f.Root; $MigrationManifest = $f.Manifest
    $previewOutput = (& { Invoke-Deployment } 6>&1 | Out-String)
    Assert-True ($previewOutput.Contains($f.PlanHash)) 'Önizleme onaylanacak plan hash değerini göstermeli.'
    Assert-True ($script:events.Count -eq 0) 'Önizleme RCON, stop veya başlatma yapmamalı.'
    Assert-True (Test-NoBackup $f) 'Önizleme sunucuya yedek yazmamalı.'
    Assert-True (!(Test-Path -LiteralPath (Join-Path $f.Root '.npcspeak-deploy.lock'))) 'Önizleme kilit dosyası oluşturmamalı.'
    Assert-True (Test-State $f $f.OldHashes) 'Önizleme hedef dosyaları değiştirmemeli.'
    $MigrationManifest = ''
    Assert-Throws { Invoke-Deployment } 'Manifest olmadan önizleme reddedilmeli.'
    Assert-True ($script:events.Count -eq 0) 'Eksik manifest sunucuya dokunmamalı.'

    # Approval and hash gates reject before any lifecycle action.
    $Mode = 'Deploy'
    $f = New-Fixture; $ApproveDeployment = $false
    Assert-Throws { Invoke-Hybrid $f } 'ApproveDeployment olmadan dağıtım reddedilmeli.'
    $ApproveDeployment = $true; $ExpectedPlanSha256 = ''
    Assert-Throws { Invoke-Hybrid $f } 'İncelenen plan hash değeri olmadan dağıtım reddedilmeli.'
    Assert-True ($script:events.Count -eq 0 -and (Test-NoBackup $f)) 'Onaysız dağıtım sunucuya dokunmamalı.'
    $ExpectedPlanSha256 = 'a' * 64
    Assert-Throws { Invoke-Hybrid $f } 'Yanlış plan hash değeri reddedilmeli.'
    Assert-True ($script:events.Count -eq 0 -and (Test-NoBackup $f)) 'Yanlış plan hash değeri sunucuya dokunmamalı.'
    $f = New-Fixture
    Invoke-HybridDeployment -Root $f.Root -ManifestPath $f.Manifest -WhatIf 6>$null
    Assert-True ($script:events.Count -eq 0 -and (Test-NoBackup $f)) 'WhatIf sunucuya dokunmamalı.'
    $f = New-Fixture; $script:wildcard = $true
    Assert-Throws { Invoke-Hybrid $f } 'Geniş arayüzlü RCON ek doğrulama istemeli.'
    Assert-True ($script:events.Count -eq 0 -and (Test-NoBackup $f)) 'RCON doğrulaması olmadan stop yapılmamalı.'

    $gates = [ordered]@{
        'Aday JAR değişmiş' = { param($x) New-TestJar (Join-Path $x.Folder 'other.jar') 'NPCSpeak' 'tampered'; Copy-Item -LiteralPath (Join-Path $x.Folder 'other.jar') -Destination $x.Candidates.Jar -Force }
        'Aday NPC değişmiş' = { param($x) [IO.File]::AppendAllText($x.Candidates.Npc, '# tampered') }
        'Canlı NPC değişmiş' = { param($x) [IO.File]::AppendAllText($x.Targets.Npc, '# drift') }
        'Canlı knowledge değişmiş' = { param($x) [IO.File]::AppendAllText($x.Targets.Knowledge, '# drift') }
        'Canlı JAR değişmiş' = { param($x) [IO.File]::AppendAllText($x.Targets.Jar, 'drift') }
        'BAT değişmiş' = { param($x) [IO.File]::AppendAllText((Join-Path $x.Root 'START.bat'), ' ') }
        'server.properties değişmiş' = { param($x) [IO.File]::AppendAllText((Join-Path $x.Root 'server.properties'), '#') }
        'Doğrulama dosyası değişmiş' = { param($x) [IO.File]::AppendAllText($x.Validation, ' ') }
        'İkinci NPCSpeak JAR' = { param($x) New-TestJar (Join-Path $x.Root 'plugins\second.jar') 'NPCSpeak' }
        'Geçersiz kılınmış plan' = { param($x) [IO.File]::WriteAllText((Join-Path $x.Folder 'SUPERSEDED.txt'), 'fixture') }
    }
    foreach ($name in $gates.Keys) {
        $f = New-Fixture
        & $gates[$name] $f
        Assert-Throws { Invoke-Hybrid $f } "$name reddedilmeli."
        Assert-True ($script:events.Count -eq 0 -and (Test-NoBackup $f)) "$name durumunda stop/yedek yapılmamalı."
    }
    $plans = [ordered]@{
        'Başka sunucu kökü' = { param($x) $x.Plan.ServerRoot = (Join-Path $testRoot 'other') }
        'Üst klasöre taşan hedef' = { param($x) $x.Plan.Files[1].Target = 'plugins/NPCSpeak/npcs/../../../evil.yml' }
        'Beklenmeyen hedef klasörü' = { param($x) $x.Plan.Files[2].Target = 'plugins/Citizens/saves.yml' }
        'Tekrarlanan dosya türü' = { param($x) $x.Plan.Files[2] = $x.Plan.Files[1] }
        'Eksik dosya' = { param($x) $x.Plan.Files = @($x.Plan.Files[0], $x.Plan.Files[1]) }
        'Geçersiz marker' = { param($x) $x.Plan.Marker = 'HYBRID_READY_V1 short' }
    }
    foreach ($name in $plans.Keys) {
        $f = New-Fixture
        & $plans[$name] $f
        $ExpectedPlanSha256 = Write-Plan $f
        Assert-Throws { Invoke-Hybrid $f } "$name reddedilmeli."
        Assert-True ($script:events.Count -eq 0 -and (Test-NoBackup $f)) "$name durumunda stop/yedek yapılmamalı."
    }
    foreach ($change in @(@{Valid=$false}, @{ArtifactClassesVerified=$false}, @{LocalChecks=3}, @{Marker='HYBRID_READY_V1 ' + ('e' * 64)}, @{NpcSha256='0' * 64})) {
        $f = New-Fixture
        Write-Validation $f $change
        $ExpectedPlanSha256 = Write-Plan $f
        Assert-Throws { Invoke-Hybrid $f } 'Geçersiz çevrimdışı doğrulama reddedilmeli.'
        Assert-True ($script:events.Count -eq 0 -and (Test-NoBackup $f)) 'Geçersiz doğrulamada stop/yedek yapılmamalı.'
    }
    $f = New-Fixture
    $notPlugin = Join-Path $f.Folder 'not-plugin.jar'
    New-TestJar $notPlugin 'OtherPlugin'
    Copy-Item -LiteralPath $notPlugin -Destination $f.Candidates.Jar -Force
    $f.NewHashes.Jar = Get-Hash $f.Candidates.Jar; $f.Plan.Files[0].CandidateSha256 = $f.NewHashes.Jar
    Write-Validation $f
    $ExpectedPlanSha256 = Write-Plan $f
    Assert-Throws { Invoke-Hybrid $f } 'NPCSpeak olmayan aday JAR reddedilmeli.'
    Assert-True ($script:events.Count -eq 0 -and (Test-NoBackup $f)) 'Kimliği yanlış JAR ile stop yapılmamalı.'

    $f = New-Fixture
    $outside = Join-Path $testRoot 'junction-target'
    $null = New-Item -ItemType Directory -Path $outside
    Move-Item -LiteralPath (Join-Path $f.Root 'plugins\NPCSpeak\npcs') -Destination (Join-Path $outside 'npcs')
    $null = New-Item -ItemType Junction -Path (Join-Path $f.Root 'plugins\NPCSpeak\npcs') -Target (Join-Path $outside 'npcs')
    Assert-Throws { Invoke-Hybrid $f } 'Junction üzerinden hedef reddedilmeli.'
    Assert-True ($script:events.Count -eq 0 -and (Test-NoBackup $f)) 'Junction varken stop/yedek yapılmamalı.'

    # Successful transaction.
    $f = New-Fixture
    Invoke-Hybrid $f
    Assert-True (Test-State $f $f.NewHashes) 'Başarıda üç dosya da yeni hash ile kurulmalı.'
    Assert-True (($script:events -join ',') -ceq 'rcon-probe,stop,ports-closed,start,ready') 'Güvenli işlem sırası korunmalı.'
    Assert-True ($script:readyMarkers[0] -ceq $marker) 'Yeni açılış beklenen kanıtla doğrulanmalı.'
    Assert-True ((Get-Phase $f) -ceq 'READY') 'Başarı günlüğü READY olmalı.'
    foreach ($kind in @('Jar','Npc','Knowledge')) {
        Assert-True ((Get-Hash (Join-Path (Get-Backup $f) "before\$kind.bin")) -eq $f.OldHashes[$kind]) "$kind stop öncesi yedeği doğrulanmalı."
        Assert-True ((Get-Hash (Join-Path (Get-Backup $f) "final\$kind.bin")) -eq $f.OldHashes[$kind]) "$kind stop sonrası yedeği doğrulanmalı."
    }
    Assert-True ((Get-Hash (Join-Path $f.Root 'server.properties')) -eq $f.Plan.PropertiesSha256) 'Sunucu ayarları korunmalı.'

    $f = New-Fixture; $script:onReady = { Write-Receipt $script:current }
    Invoke-Hybrid $f
    Assert-True ((Get-Phase $f) -ceq 'READY') 'Açılışın kendi NPC kaydı makbuzla kabul edilmeli.'
    Assert-True ((Get-Hash $f.Targets.Npc) -ne $f.NewHashes.Npc -and (Get-Hash $f.Targets.Jar) -eq $f.NewHashes.Jar) 'Makbuzlu NPC yeniden yazımı korunmalı.'

    # Failures before the first write.
    $f = New-Fixture; $script:onStop = { throw 'stop timeout' }
    Assert-Throws { Invoke-Hybrid $f } 'Güvenli kapanamama dağıtımı durdurmalı.'
    Assert-True (Test-State $f $f.OldHashes) 'Kapanış doğrulanamazsa dosya yazılmamalı.'
    Assert-True ((Get-EventCount 'start') -eq 0) 'Kapanış doğrulanamazsa ikinci sunucu başlatılmamalı.'
    Assert-True ((Get-Phase $f) -ceq 'FAILED') 'Kapanamama FAILED kaydedilmeli.'

    $f = New-Fixture; $script:onStop = { [IO.File]::AppendAllText($script:current.Targets.Npc, '# saved at stop') }
    Assert-Throws { Invoke-Hybrid $f } 'Stop sonrası NPC hash sapması sessizce yeni taban yapılmamalı.'
    Assert-True ((Get-Hash $f.Targets.Npc) -ne $f.OldHashes.Npc) 'Stop sırasında kaydedilen NPC içeriği ezilmemeli.'
    Assert-True ((Get-Hash $f.Targets.Jar) -eq $f.OldHashes.Jar -and (Get-Hash $f.Targets.Knowledge) -eq $f.OldHashes.Knowledge) 'Sapmada hiçbir dosya kurulmamalı.'
    Assert-True ((Get-EventCount 'start') -eq 1 -and $script:readyMarkers[0] -ceq '') 'Dosya yazılmadıysa özgün sunucu açılmalı.'
    Assert-True ((Get-Phase $f) -ceq 'ORIGINAL_RESTARTED') 'Yazımsız yeniden başlatma günlüğe geçmeli.'

    $f = New-Fixture
    $plan = Read-HybridPlan $f.Root $f.Manifest
    $held = [IO.File]::Open($f.Targets.Knowledge, 'Open', 'Read', 'Read')
    try { Assert-Throws { Open-HybridTargets $plan } 'Hedeflerden biri kilitlenemiyorsa işlem durmalı.' } finally { $held.Dispose() }
    $released = $true
    try { [IO.File]::Open($f.Targets.Jar, 'Open', 'ReadWrite', 'None').Dispose() } catch { $released = $false }
    Assert-True $released 'Başarısız kilit girişimi önceki hedefleri serbest bırakmalı.'

    # Partial writes.
    $f = New-Fixture; $script:failWritesAt = @(2)
    Assert-Throws { Invoke-Hybrid $f } 'Kısmi yazım başarısız sayılmalı.'
    Assert-True (Test-State $f $f.OldHashes) 'Kısmi yazımda üç dosya da özgün hash değerine dönmeli.'
    Assert-True ((Get-EventCount 'start') -eq 1 -and $script:readyMarkers[0] -ceq '') 'Geri alınan kısmi yazımdan sonra özgün sunucu açılmalı.'
    Assert-True ((Get-Phase $f) -ceq 'ORIGINAL_RESTARTED') 'Kısmi yazımın sonucu günlüğe geçmeli.'

    $f = New-Fixture; $script:failWritesAt = @(2, 4)
    Assert-Throws { Invoke-Hybrid $f } 'Geri yazım hatası başarısız sayılmalı.'
    Assert-True ((Get-EventCount 'start') -eq 0) 'Geri yazım bozulursa sunucu başlatılmamalı.'
    Assert-True ($script:writeCount -eq 4) 'Geri yazım bozulunca daha fazla dosya yazılmamalı.'
    Assert-True ((Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Bozuk geri yazım RECOVERY_BLOCKED kaydedilmeli.'

    $f = New-Fixture; $script:failWritesAt = @(2)
    $script:realTarget = $script:realWrite
    $script:realWrite = {
        param($Target, $Source)
        & $script:realTarget $Target $Source
        [IO.File]::AppendAllText((Join-Path (Get-Backup $script:current) 'final\Knowledge.bin'), 'corrupt')
    }
    Assert-Throws { Invoke-Hybrid $f } 'Bozuk yedekle kısmi yazım başarısız sayılmalı.'
    $script:realWrite = $script:realTarget
    Assert-True ($script:writeCount -eq 2) 'Yedek bozuksa ilk geri yazımdan önce durulmalı.'
    Assert-True ((Get-Hash $f.Targets.Knowledge) -eq $f.OldHashes.Knowledge -and (Get-EventCount 'start') -eq 0) 'Bozuk yedekte başka dosya yazılmamalı ve sunucu açılmamalı.'
    Assert-True ((Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Bozuk yedek RECOVERY_BLOCKED kaydedilmeli.'

    # Startup failures after a complete install.
    $f = New-Fixture; $script:listeners = @([pscustomobject]@{LocalPort=25565})
    $script:onReady = { $script:onReady = $null; throw 'startup failed' }
    Assert-Throws { Invoke-Hybrid $f } 'Bozuk başlangıç başarısız kalmalı.'
    Assert-True (Test-State $f $f.OldHashes) 'Bozuk başlangıçta üç dosya geri alınmalı.'
    Assert-True (($script:events -join ',') -ceq 'rcon-probe,stop,ports-closed,start,ready,stop,ports-closed,start,ready') 'Geri alma güvenli kapanıştan sonra yapılmalı.'
    Assert-True ($script:readyMarkers[0] -ceq $marker -and $script:readyMarkers[1] -ceq '') 'Özgün sürümden yeni açılış kanıtı beklenmemeli.'
    Assert-True ((Get-Phase $f) -ceq 'ROLLED_BACK_READY') 'Tamamlanan geri alma günlüğe geçmeli.'

    $f = New-Fixture
    $script:onReady = { $script:onReady = $null; Write-Receipt $script:current; throw 'startup failed' }
    Assert-Throws { Invoke-Hybrid $f } 'Makbuzlu bozuk başlangıç başarısız kalmalı.'
    Assert-True (Test-State $f $f.OldHashes) 'Açılış makbuzlu NPC yazımı geri alınabilmeli.'
    Assert-True ((Get-Phase $f) -ceq 'ROLLED_BACK_READY') 'Makbuzlu geri alma tamamlanmalı.'

    $f = New-Fixture
    $script:onReady = { [IO.File]::AppendAllText($script:current.Targets.Npc, '# external edit'); throw 'startup failed' }
    Assert-Throws { Invoke-Hybrid $f } 'Dış düzenlemeli başlangıç başarısız kalmalı.'
    Assert-True ((Get-Hash $f.Targets.Jar) -eq $f.NewHashes.Jar -and (Get-Hash $f.Targets.Knowledge) -eq $f.NewHashes.Knowledge) 'Dış düzenleme varsa geri alma hiçbir dosyayı yazmamalı.'
    Assert-True ((Get-EventCount 'start') -eq 1) 'Dış düzenleme varsa sunucu yeniden açılmamalı.'
    Assert-True ((Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Dış düzenleme RECOVERY_BLOCKED kaydedilmeli.'

    $f = New-Fixture
    $script:onReady = { [IO.File]::AppendAllText($script:current.Targets.Npc, '# external edit') }
    Assert-Throws { Invoke-Hybrid $f } 'Açılış sonrası makbuzsuz NPC değişikliği kabul edilmemeli.'
    Assert-True ((Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Makbuzsuz değişiklik başarı sayılmamalı.'

    $f = New-Fixture
    $script:onReady = { Write-Receipt $script:current '[12:00:02 INFO]: <Oyuncu> [NPCSpeak] ' }
    Assert-Throws { Invoke-Hybrid $f } 'Oyuncu sohbeti NPC makbuzu sayılmamalı.'
    Assert-True ((Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Sahte makbuzlu değişiklik geri yazılmamalı.'

    $f = New-Fixture
    $script:onReady = {
        $script:onReady = $null
        [IO.File]::AppendAllText((Join-Path (Get-Backup $script:current) 'final\Npc.bin'), 'corrupt')
        throw 'startup failed'
    }
    Assert-Throws { Invoke-Hybrid $f } 'Bozuk yedekle başlangıç başarısız kalmalı.'
    Assert-True (Test-State $f $f.NewHashes) 'Rollback yedeği bozuksa ilk dosya bile yazılmamalı.'
    Assert-True ((Get-EventCount 'start') -eq 1 -and (Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Rollback yedeği bozuksa sunucu açılmamalı.'

    $f = New-Fixture; $script:onReady = { throw 'startup failed' }
    Assert-Throws { Invoke-Hybrid $f } 'Özgün sürüm de açılamıyorsa başarısız kalmalı.'
    Assert-True (Test-State $f $f.OldHashes) 'Özgün sürüm açılamasa da dosyalar özgün kalmalı.'
    Assert-True ((Get-EventCount 'start') -eq 2 -and (Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Üçüncü başlatma denenmemeli.'

    Write-Host "Üç dosyalı dağıtım: $checks denetim başarılı (canlı sunucu kullanılmadı)."
} finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\npcspeak-hybrid-tests-'
    if (!$resolved.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Güvenli test temizleme yolu doğrulanamadı.' }
    # A junction must be unlinked first so recursive removal cannot follow it.
    Get-ChildItem -LiteralPath $resolved -Recurse -Force -Attributes ReparsePoint -ErrorAction SilentlyContinue | ForEach-Object { [IO.Directory]::Delete($_.FullName) }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

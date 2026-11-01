#Requires -Version 5.1
# Offline JAR-only transaction tests: a disposable fake server tree and lifecycle mocks only.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$WhatIfPreference = $false
$ConfirmPreference = 'None'
. (Join-Path (Split-Path $PSScriptRoot) 'Deploy-Superseed.ps1')
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('npcspeak-jar-tests-' + [guid]::NewGuid().ToString('N'))
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
function New-TestJar([string]$Path, [string]$Name, [switch]$Hybrid, [string]$Extra = '') {
    $zip = [IO.Compression.ZipFile]::Open($Path, 'Create')
    try {
        $writer = [IO.StreamWriter]::new($zip.CreateEntry('plugin.yml').Open())
        try { $writer.Write("name: '$Name'`nversion: 'test'`n") } finally { $writer.Dispose() }
        if ($Hybrid) { $null = $zip.CreateEntry('com/l299l/npcspeak/knowledge/HybridReadiness.class') }
        if ($Extra) { $null = $zip.CreateEntry($Extra) }
    } finally { $zip.Dispose() }
}
function Get-Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }
function Write-Json([string]$Path, $Value) { [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 8)) }
function Write-JarFixturePlan($Fixture) {
    Write-Json $Fixture.ProtectedFile $Fixture.Inventory
    Write-Json $Fixture.Validation $Fixture.ValidationData
    $Fixture.Plan.ValidationSha256 = Get-Hash $Fixture.Validation
    $Fixture.Plan.ProtectedSha256 = Get-Hash $Fixture.ProtectedFile
    Write-Json $Fixture.Manifest $Fixture.Plan
    $script:ExpectedPlanSha256 = Get-Hash $Fixture.Manifest
}
function New-Fixture {
    $script:fixtureCount++
    $base = Join-Path $testRoot ([string]$script:fixtureCount)
    $root = Join-Path $base 'server'; $folder = Join-Path $base 'plan'
    foreach ($path in @('plugins\NPCSpeak\npcs', 'plugins\NPCSpeak\knowledge\wiki', 'plugins\Citizens', 'logs')) { $null = New-Item -ItemType Directory -Path (Join-Path $root $path) }
    $null = New-Item -ItemType Directory -Path (Join-Path $folder 'files')
    [IO.File]::WriteAllText((Join-Path $root 'START.bat'), 'java -cp paper.jar io.papermc.paperclip.Main -nogui')
    [IO.File]::WriteAllText((Join-Path $root 'server.properties'), "#Minecraft server properties`n#Sun Nov 01 21:22:21 GMT+03:00 2026`nenable-rcon=true`nrcon.password=fixture`nrcon.port=25575`nserver-port=25565`n")
    [IO.File]::WriteAllText((Join-Path $root 'logs\latest.log'), "old boot`n")
    $target = Join-Path $root 'plugins\old.jar'; $candidate = Join-Path $folder 'files\new.jar'
    New-TestJar $target 'NPCSpeak' -Hybrid
    New-TestJar $candidate 'NPCSpeak' -Hybrid -Extra 'new-version'
    New-TestJar (Join-Path $root 'plugins\Citizens.jar') 'Citizens'
    $files = @{Npc='plugins\NPCSpeak\npcs\dogan-tirpan.yml';Knowledge='plugins\NPCSpeak\knowledge.yml';Messages='plugins\NPCSpeak\messages.yml';Wiki='plugins\NPCSpeak\knowledge\wiki\A.txt';Saves='plugins\Citizens\saves.yml'}
    [IO.File]::WriteAllText((Join-Path $root $files.Npc), "citizens-id: 313`nknowledge-entity: fixture`n")
    [IO.File]::WriteAllText((Join-Path $root $files.Knowledge), "municipal-records: []`n")
    [IO.File]::WriteAllText((Join-Path $root $files.Messages), "typing: fixture`n")
    [IO.File]::WriteAllText((Join-Path $root $files.Wiki), "Title: Fixture`n")
    [IO.File]::WriteAllText((Join-Path $root $files.Saves), "npc:`n  '313':`n    name: Fixture`n    traits:`n      skintrait:`n        skinName: fixture`n      location:`n        x: 1.0`n  '314':`n    name: Other`n")
    $strict = @('plugins/NPCSpeak/knowledge.yml', 'plugins/NPCSpeak/messages.yml', 'plugins/NPCSpeak/knowledge/wiki/A.txt', 'plugins/Citizens.jar') | ForEach-Object { [ordered]@{Path=$_;Sha256=(Get-Hash (Join-Path $root $_))} }
    $inventory = [ordered]@{Strict=@($strict);Npc=@([ordered]@{Path='plugins/NPCSpeak/npcs/dogan-tirpan.yml';Sha256=(Get-Hash (Join-Path $root $files.Npc))});CitizensSaves='plugins/Citizens/saves.yml'}
    $validation = [ordered]@{Valid=$true;LocalChecks=6;JarSha256=(Get-Hash $candidate);Marker=$marker;ArtifactClassesVerified=$true;MigratedSource=$true}
    $plan = [ordered]@{FormatVersion=1;PlanType='JarOnly';ServerRoot=$root;StartBat='START.bat';BatSha256=(Get-Hash (Join-Path $root 'START.bat'))
        PropertiesFingerprint=(Get-PropertiesFingerprint (Join-Path $root 'server.properties'));CitizensId=313;SourceCommit=('a' * 40)
        Jar=[ordered]@{Target='plugins/old.jar';OriginalSha256=(Get-Hash $target);Candidate='files/new.jar';CandidateSha256=(Get-Hash $candidate)}
        Marker=$marker;ValidationSha256='';ProtectedSha256=''}
    $fixture = [pscustomobject]@{Root=$root;Folder=$folder;Manifest=(Join-Path $folder 'deployment-plan.json');Validation=(Join-Path $folder 'validation.json');ProtectedFile=(Join-Path $folder 'protected.json')
        Target=$target;Candidate=$candidate;OldHash=(Get-Hash $target);NewHash=(Get-Hash $candidate);Files=$files;Plan=$plan;Inventory=$inventory;ValidationData=$validation;Snapshot=@{}}
    Write-JarFixturePlan $fixture
    foreach ($file in @($files.Npc, $files.Knowledge, $files.Messages, $files.Wiki, 'plugins\Citizens.jar', 'START.bat')) { $fixture.Snapshot[$file] = Get-Hash (Join-Path $root $file) }
    $script:events.Clear(); $script:readyMarkers.Clear()
    $script:onStop = $null; $script:onReady = $null; $script:failWritesAt = @(); $script:writeCount = 0
    $script:failPortChecksAt = @(); $script:portChecks = 0; $script:listeners = @(); $script:wildcard = $false; $script:players = 0; $script:current = $fixture
    return $fixture
}
function Get-Backup($Fixture) { return (Get-ChildItem -LiteralPath (Join-Path $Fixture.Root 'deployment-backups') -Directory | Select-Object -First 1).FullName }
function Get-Phase($Fixture) { return (Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path (Get-Backup $Fixture) 'transaction.json') | ConvertFrom-Json).Phase }
function Test-Protected($Fixture) {
    foreach ($key in $Fixture.Snapshot.Keys) { if ((Get-Hash (Join-Path $Fixture.Root $key)) -ne $Fixture.Snapshot[$key]) { return $false } }
    return $true
}
function Test-Untouched($Fixture) { return $script:events.Count -eq 0 -and !(Test-Path -LiteralPath (Join-Path $Fixture.Root 'deployment-backups')) -and (Get-Hash $Fixture.Target) -eq $Fixture.OldHash }
function Get-EventCount([string]$Name) { return @($script:events | Where-Object {$_ -eq $Name}).Count }
function Invoke-Jar($Fixture) { Invoke-JarDeployment -Root $Fixture.Root -ManifestPath $Fixture.Manifest 6>$null 3>$null }
function Write-Boot($Fixture, [string]$Hash) { [IO.File]::WriteAllText((Join-Path $Fixture.Root 'logs\latest.log'), "new boot`n[12:00:00 INFO]: [NPCSpeak] $marker NPC_SHA256 $Hash`n") }

try {
    $script:events = [Collections.Generic.List[string]]::new()
    $script:readyMarkers = [Collections.Generic.List[string]]::new()
    $script:realWrite = ${function:Write-HybridTarget}
    function Get-ServerIdentity { param($Properties, $BatText); return [pscustomobject]@{Java='java.exe'; Wildcard=$script:wildcard; Process=[pscustomobject]@{Id=1;StartTime=Get-Date}} }
    function Get-OnlinePlayerCount { param($Properties); $script:events.Add('list'); return $script:players }
    function Open-Rcon { param($Properties); $script:events.Add('rcon'); return [IO.MemoryStream]::new() }
    function Stop-ServerSafely { param($Identity,$Properties,$Timeout); $script:events.Add('stop'); if ($script:onStop) { & $script:onStop } }
    function Assert-PortsClosed { param($Properties); $script:events.Add('closed'); $script:portChecks++; if ($script:portChecks -in $script:failPortChecksAt) { throw 'Paper process exists' } }
    function Get-NetTCPConnection { param($State,$LocalPort,$ErrorAction); return @($script:listeners) }
    function Start-ServerBat { param($Root,$Bat,$Java); $script:events.Add('start') }
    function Wait-ServerReady {
        param($Root,$Properties,$BatText,$Since,$Timeout,$OldHeader,$KnownPluginNames,$ExpectedHybridMarker)
        $script:events.Add('ready'); $script:readyMarkers.Add([string]$ExpectedHybridMarker)
        if ($script:onReady) { & $script:onReady }
        return [pscustomobject]@{Process=[pscustomobject]@{Id=4242}}
    }
    function Write-HybridTarget([IO.Stream]$Target, [string]$Source) {
        $script:writeCount++
        if ($script:writeCount -in $script:failWritesAt) { $Target.Position = 0; $Target.SetLength(0); $Target.WriteByte(1); $Target.Flush(); throw 'injected partial write' }
        & $script:realWrite $Target $Source
    }
    $StartBat = 'START.bat'; $RconFirewallVerified = $false; $StopTimeoutSeconds = 30; $StartTimeoutSeconds = 30; $ApproveDeployment = $true

    # Mode separation from the entry point.
    $f = New-Fixture; $ServerRoot = $f.Root; $JarPlan = $f.Manifest; $MigrationManifest = ''
    $Mode = 'JarPreview'
    $output = (& { Invoke-Deployment } 6>&1 | Out-String)
    Assert-True ($output.Contains($ExpectedPlanSha256) -and $output.Contains('JAR ONLY')) 'Önizleme plan hash değerini ve JAR-only kapsamını göstermeli.'
    Assert-True (Test-Untouched $f) 'Önizleme RCON, yedek veya dosya işlemi yapmamalı.'
    Assert-True (!(Test-Path -LiteralPath (Join-Path $f.Root '.npcspeak-deploy.lock'))) 'Önizleme kilit dosyası oluşturmamalı.'
    Assert-True (!(Test-Path -LiteralPath (Join-Path $f.Folder 'CONSUMED.txt'))) 'Önizleme planı tüketmemeli.'
    $Mode = 'Deploy'
    Assert-Throws { Invoke-Deployment } 'Üç dosyalı Deploy modu JAR planını kabul etmemeli.'
    $Mode = 'Preview'
    Assert-Throws { Invoke-Deployment } 'Üç dosyalı Preview modu JAR planını kabul etmemeli.'
    $Mode = 'JarDeploy'; $MigrationManifest = $f.Manifest
    Assert-Throws { Invoke-Deployment } 'JAR ve göç planı birlikte verilememeli.'
    $MigrationManifest = ''; $JarPlan = ''
    Assert-Throws { Invoke-Deployment } 'JarDeploy açık JAR planı istemeli.'
    Assert-True (Test-Untouched $f) 'Reddedilen mod sunucuya dokunmamalı.'
    $Mode = 'Deploy'; $JarPlan = ''; $MigrationManifest = $f.Manifest
    Assert-Throws { Invoke-Deployment } 'Üç dosyalı akış JAR planını göç planı olarak okumamalı.'
    $MigrationManifest = ''

    # Gates: every one rejects before any lifecycle action.
    $Mode = 'JarDeploy'
    $f = New-Fixture; $ApproveDeployment = $false
    Assert-Throws { Invoke-Jar $f } 'Onay bayrağı olmadan JAR dağıtımı reddedilmeli.'
    $ApproveDeployment = $true; $ExpectedPlanSha256 = ''
    Assert-Throws { Invoke-Jar $f } 'İncelenen plan hash değeri olmadan JAR dağıtımı reddedilmeli.'
    $ExpectedPlanSha256 = 'a' * 64
    Assert-Throws { Invoke-Jar $f } 'Yanlış plan hash değeri reddedilmeli.'
    Assert-True (Test-Untouched $f) 'Onaysız/yanlış hash sunucuya dokunmamalı.'
    $f = New-Fixture
    Invoke-JarDeployment -Root $f.Root -ManifestPath $f.Manifest -WhatIf 6>$null
    Assert-True (Test-Untouched $f) 'WhatIf sunucuya dokunmamalı.'
    $f = New-Fixture; $script:wildcard = $true
    Assert-Throws { Invoke-Jar $f } 'Geniş arayüzlü RCON ek doğrulama istemeli.'
    Assert-True (Test-Untouched $f) 'RCON doğrulaması olmadan işlem başlamamalı.'
    $f = New-Fixture; $script:players = 1
    Assert-Throws { Invoke-Jar $f } 'Oyuncu varken dağıtım başlamamalı.'
    Assert-True ((Get-EventCount 'stop') -eq 0 -and (Get-Hash $f.Target) -eq $f.OldHash) 'Oyuncu varken stop gönderilmemeli.'

    $tamper = [ordered]@{
        'Aday JAR değişmiş' = { param($x) New-TestJar (Join-Path $x.Folder 'o.jar') 'NPCSpeak' -Hybrid -Extra 'tampered'; Copy-Item -LiteralPath (Join-Path $x.Folder 'o.jar') -Destination $x.Candidate -Force }
        'Kurulu JAR değişmiş' = { param($x) [IO.File]::AppendAllText($x.Target, 'drift') }
        'NPC değişmiş' = { param($x) [IO.File]::AppendAllText((Join-Path $x.Root $x.Files.Npc), '# drift') }
        'knowledge değişmiş' = { param($x) [IO.File]::AppendAllText((Join-Path $x.Root $x.Files.Knowledge), '# drift') }
        'messages değişmiş' = { param($x) [IO.File]::AppendAllText((Join-Path $x.Root $x.Files.Messages), '# drift') }
        'wiki değişmiş' = { param($x) [IO.File]::AppendAllText((Join-Path $x.Root $x.Files.Wiki), 'drift') }
        'Diğer plugin değişmiş' = { param($x) [IO.File]::AppendAllText((Join-Path $x.Root 'plugins\Citizens.jar'), 'drift') }
        'Yeni plugin eklenmiş' = { param($x) New-TestJar (Join-Path $x.Root 'plugins\Extra.jar') 'Extra' }
        'BAT değişmiş' = { param($x) [IO.File]::AppendAllText((Join-Path $x.Root 'START.bat'), ' ') }
        'Sunucu ayarı değişmiş' = { param($x) [IO.File]::AppendAllText((Join-Path $x.Root 'server.properties'), "max-players=99`n") }
        'Doğrulama dosyası değişmiş' = { param($x) [IO.File]::AppendAllText($x.Validation, ' ') }
        'Koruma listesi değişmiş' = { param($x) [IO.File]::AppendAllText($x.ProtectedFile, ' ') }
        'Geçersiz kılınmış plan' = { param($x) [IO.File]::WriteAllText((Join-Path $x.Folder 'SUPERSEDED.txt'), 'x') }
        'Kullanılmış plan' = { param($x) [IO.File]::WriteAllText((Join-Path $x.Folder 'CONSUMED.txt'), 'x') }
    }
    foreach ($name in $tamper.Keys) {
        $f = New-Fixture; & $tamper[$name] $f
        Assert-Throws { Invoke-Jar $f } "$name reddedilmeli."
        Assert-True ($script:events.Count -eq 0 -and !(Test-Path -LiteralPath (Join-Path $f.Root 'deployment-backups'))) "$name durumunda işlem başlamamalı."
    }
    $plans = [ordered]@{
        'Göç planı' = { param($x) $x.Plan.Remove('PlanType') }
        'Dosya listeli plan' = { param($x) $x.Plan['Files'] = @(@{Kind='Npc'}) }
        'Başka sunucu kökü' = { param($x) $x.Plan.ServerRoot = (Join-Path $testRoot 'other') }
        'Üst klasöre taşan hedef' = { param($x) $x.Plan.Jar.Target = 'plugins/../evil.jar' }
        'NPC hedefi' = { param($x) $x.Plan.Jar.Target = 'plugins/NPCSpeak/npcs/dogan-tirpan.yml' }
        'Aynı JAR' = { param($x) $x.Plan.Jar.CandidateSha256 = $x.Plan.Jar.OriginalSha256 }
        'Geçersiz marker' = { param($x) $x.Plan.Marker = 'HYBRID_READY_V1 short' }
        'Güvensiz koruma yolu' = { param($x) $x.Inventory.CitizensSaves = '../saves.yml' }
    }
    foreach ($name in $plans.Keys) {
        $f = New-Fixture; & $plans[$name] $f; Write-JarFixturePlan $f
        Assert-Throws { Invoke-Jar $f } "$name reddedilmeli."
        Assert-True ($script:events.Count -eq 0) "$name durumunda işlem başlamamalı."
    }
    foreach ($change in @(@{Valid=$false}, @{ArtifactClassesVerified=$false}, @{MigratedSource=$false}, @{LocalChecks=3}, @{Marker='HYBRID_READY_V1 ' + ('e' * 64)}, @{JarSha256='0' * 64})) {
        $f = New-Fixture; foreach ($key in $change.Keys) { $f.ValidationData[$key] = $change[$key] }; Write-JarFixturePlan $f
        Assert-Throws { Invoke-Jar $f } 'Geçersiz çevrimdışı doğrulama reddedilmeli.'
        Assert-True ($script:events.Count -eq 0) 'Geçersiz doğrulamada işlem başlamamalı.'
    }
    $f = New-Fixture; $plain = Join-Path $f.Folder 'plain.jar'; New-TestJar $plain 'NPCSpeak'
    Copy-Item -LiteralPath $plain -Destination $f.Candidate -Force
    $f.Plan.Jar.CandidateSha256 = Get-Hash $f.Candidate; $f.ValidationData.JarSha256 = $f.Plan.Jar.CandidateSha256; Write-JarFixturePlan $f
    Assert-Throws { Invoke-Jar $f } 'Hibrit sınıfı olmayan aday reddedilmeli.'
    $f = New-Fixture; $plain = Join-Path $f.Folder 'plain.jar'; New-TestJar $plain 'OtherPlugin' -Hybrid
    Copy-Item -LiteralPath $plain -Destination $f.Candidate -Force
    $f.Plan.Jar.CandidateSha256 = Get-Hash $f.Candidate; $f.ValidationData.JarSha256 = $f.Plan.Jar.CandidateSha256; Write-JarFixturePlan $f
    Assert-Throws { Invoke-Jar $f } 'NPCSpeak olmayan aday reddedilmeli.'
    Assert-True ($script:events.Count -eq 0) 'Yanlış aday ile işlem başlamamalı.'

    # Success: only the JAR is written.
    $f = New-Fixture
    $script:onStop = { [IO.File]::AppendAllText((Join-Path $script:current.Root $script:current.Files.Saves), "# saved at stop`n") }
    Invoke-Jar $f
    Assert-True ((Get-Hash $f.Target) -eq $f.NewHash) 'Başarıda yeni JAR kurulmalı.'
    Assert-True (Test-Protected $f) 'Başarıda korunan hiçbir dosya değişmemeli.'
    Assert-True (($script:events -join ',') -ceq 'list,stop,closed,closed,closed,start,ready') 'Güvenli işlem sırası korunmalı.'
    Assert-True ($script:readyMarkers[0] -ceq $marker) 'Yeni açılış beklenen kanıtla doğrulanmalı.'
    Assert-True ((Get-Phase $f) -ceq 'READY') 'Başarı günlüğü READY olmalı.'
    Assert-True ($script:writeCount -eq 1) 'Yalnız tek dosya yazılmalı.'
    Assert-True (Test-Path -LiteralPath (Join-Path $f.Folder 'CONSUMED.txt')) 'Kullanılan plan işaretlenmeli.'
    $backup = Get-Backup $f
    Assert-True ((Get-Hash (Join-Path $backup 'before\Jar.bin')) -eq $f.OldHash -and (Get-Hash (Join-Path $backup 'final\Jar.bin')) -eq $f.OldHash) 'JAR yedekleri doğrulanmalı.'
    foreach ($name in @('plugins__NPCSpeak__knowledge.yml', 'plugins__NPCSpeak__messages.yml', 'plugins__Citizens__saves.yml', 'plugins__NPCSpeak__npcs__dogan-tirpan.yml', 'server.properties')) {
        Assert-True (Test-Path -LiteralPath (Join-Path $backup "protected\$name")) "Koruma kopyası olmalı: $name"
    }
    $ExpectedPlanSha256 = Get-Hash $f.Manifest
    Assert-Throws { Invoke-Jar $f } 'Aynı plan ikinci kez kullanılamamalı.'
    Assert-True ((Get-EventCount 'stop') -eq 1) 'Plan tekrarında stop gönderilmemeli.'

    $f = New-Fixture; $script:onReady = {
        [IO.File]::WriteAllText((Join-Path $script:current.Root $script:current.Files.Npc), "citizens-id: 313`nknowledge-entity: fixture # boot`n")
        Write-Boot $script:current (Get-Hash (Join-Path $script:current.Root $script:current.Files.Npc))
    }
    Invoke-Jar $f
    Assert-True ((Get-Phase $f) -ceq 'READY') 'Açılışın kendi NPC kaydı makbuzla kabul edilmeli.'

    $f = New-Fixture; $script:onReady = {
        $p = Join-Path $script:current.Root $script:current.Files.Saves
        [IO.File]::WriteAllText($p, [IO.File]::ReadAllText($p).Replace('x: 1.0', 'x: 2.0'))
    }
    Invoke-Jar $f
    Assert-True ((Get-Phase $f) -ceq 'READY') 'Citizens olağan kayıt farkı dağıtımı bozmamalı.'
    Assert-True ((Get-Content -Raw -LiteralPath (Join-Path (Get-Backup $f) 'transaction.json') | ConvertFrom-Json).CitizensLinesChanged -gt 0) 'Citizens farkı sessizce geçilmemeli.'

    $f = New-Fixture; $script:onReady = {
        $script:onReady = $null; $p = Join-Path $script:current.Root $script:current.Files.Saves
        [IO.File]::WriteAllText($p, [IO.File]::ReadAllText($p).Replace('skinName: fixture', 'skinName: other'))
    }
    Assert-Throws { Invoke-Jar $f } 'Citizens skin değişimi başarı sayılmamalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.OldHash -and (Get-Phase $f) -ceq 'ROLLED_BACK_READY') 'Skin değişiminde JAR geri alınmalı.'
    $f = New-Fixture; $script:onReady = {
        $script:onReady = $null; $p = Join-Path $script:current.Root $script:current.Files.Saves
        [IO.File]::WriteAllText($p, "npc:`n  '314':`n    name: Other`n")
    }
    Assert-Throws { Invoke-Jar $f } 'Citizens 313 kaydının kaybı başarı sayılmamalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.OldHash) 'Citizens kaydı kaybolursa JAR geri alınmalı.'

    # Failures before the first write.
    $f = New-Fixture; $script:onStop = { throw 'stop timeout' }
    Assert-Throws { Invoke-Jar $f } 'Güvenli kapanamama dağıtımı durdurmalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.OldHash -and (Get-EventCount 'start') -eq 0) 'Kapanamazsa yazma/başlatma olmamalı.'
    Assert-True (!(Test-Path -LiteralPath (Join-Path $f.Folder 'CONSUMED.txt'))) 'Hiç yazılmadıysa plan tüketilmemeli.'

    foreach ($case in @(@{Name='NPC';Path='Npc'}, @{Name='knowledge';Path='Knowledge'}, @{Name='wiki';Path='Wiki'})) {
        $f = New-Fixture; $script:driftFile = Join-Path $f.Root $f.Files[$case.Path]
        $script:onStop = { [IO.File]::AppendAllText($script:driftFile, '# saved at stop') }
        Assert-Throws { Invoke-Jar $f } "Stop sonrası $($case.Name) sapması durdurmalı."
        Assert-True ((Get-Hash $f.Target) -eq $f.OldHash -and (Get-EventCount 'start') -eq 1) "$($case.Name) sapmasında JAR yazılmamalı, özgün sunucu açılmalı."
        Assert-True ($script:readyMarkers[0] -ceq '' -and (Get-Phase $f) -ceq 'ORIGINAL_RESTARTED') "$($case.Name) sapması günlüğe geçmeli."
    }

    $f = New-Fixture; $script:onStop = { Remove-Item -LiteralPath (Join-Path $script:current.Root $script:current.Files.Saves) }
    Assert-Throws { Invoke-Jar $f } 'Stop sonrası Citizens kaydı yoksa durmalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.OldHash) 'Citizens kaydı yoksa JAR yazılmamalı.'

    # Unexpected Paper (restart-on-crash): nothing is stopped, written or started.
    $unexpected = @([pscustomobject]@{LocalPort=25565})
    foreach ($at in 1, 2) {
        $f = New-Fixture; $script:listeners = $unexpected; $script:failPortChecksAt = @($at)
        Assert-Throws { Invoke-Jar $f } 'Beklenmedik Paper dağıtımı durdurmalı.'
        Assert-True ((Get-Hash $f.Target) -eq $f.OldHash -and $script:writeCount -eq 0) 'Beklenmedik Paper varken dosya yazılmamalı.'
        Assert-True ((Get-EventCount 'stop') -eq 1 -and (Get-EventCount 'start') -eq 0 -and (Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Beklenmedik Paper sürecine dokunulmamalı.'
    }
    $f = New-Fixture; $script:listeners = $unexpected; $script:failPortChecksAt = @(3)
    Assert-Throws { Invoke-Jar $f } 'Başlatma öncesi görülen Paper durdurmalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.NewHash -and (Get-EventCount 'stop') -eq 1 -and (Get-EventCount 'start') -eq 0) 'Başlatma öncesi Paper varken geri yazım/stop yapılmamalı.'
    Assert-True ((Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Başlatma öncesi durma kaydedilmeli.'

    # Partial write.
    $f = New-Fixture; $script:failWritesAt = @(1)
    Assert-Throws { Invoke-Jar $f } 'Kısmi yazım başarısız sayılmalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.OldHash -and (Get-EventCount 'start') -eq 1 -and (Get-Phase $f) -ceq 'ORIGINAL_RESTARTED') 'Kısmi yazımda özgün JAR geri yazılıp açılmalı.'
    $f = New-Fixture; $script:failWritesAt = @(1, 2)
    Assert-Throws { Invoke-Jar $f } 'Geri yazım hatası başarısız sayılmalı.'
    Assert-True ((Get-EventCount 'start') -eq 0 -and $script:writeCount -eq 2 -and (Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Geri yazım bozulursa durulmalı.'

    # Startup failures and rollback.
    $f = New-Fixture; $script:listeners = $unexpected; $script:onReady = { $script:onReady = $null; throw 'startup failed' }
    Assert-Throws { Invoke-Jar $f } 'Bozuk başlangıç başarısız kalmalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.OldHash -and (Test-Protected $f)) 'Bozuk başlangıçta yalnız JAR geri alınmalı.'
    Assert-True (($script:events -join ',') -ceq 'list,stop,closed,closed,closed,start,ready,stop,closed,closed,closed,start,ready') 'Geri alma güvenli kapanıştan sonra yapılmalı.'
    Assert-True ($script:readyMarkers[1] -ceq '' -and (Get-Phase $f) -ceq 'ROLLED_BACK_READY') 'Özgün sürümden hibrit kanıt beklenmemeli.'

    $f = New-Fixture; $script:onReady = { $script:onReady = $null; [IO.File]::AppendAllText((Join-Path $script:current.Root $script:current.Files.Messages), '# changed') }
    Assert-Throws { Invoke-Jar $f } 'Açılışta korunan dosya değişimi başarı sayılmamalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.OldHash -and (Get-Phase $f) -ceq 'ROLLED_BACK_READY') 'Beklenmeyen dosya değişiminde JAR geri alınmalı.'

    $f = New-Fixture; $script:onReady = { $script:onReady = $null; [IO.File]::AppendAllText((Join-Path $script:current.Root $script:current.Files.Npc), '# external') }
    Assert-Throws { Invoke-Jar $f } 'Makbuzsuz NPC değişimi başarı sayılmamalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.OldHash) 'Makbuzsuz NPC değişiminde JAR geri alınmalı, NPC ezilmemeli.'
    Assert-True ((Get-Hash (Join-Path $f.Root $f.Files.Npc)) -ne $f.Snapshot[$f.Files.Npc]) 'JAR-only geri alma NPC dosyasını yazmamalı.'

    $f = New-Fixture; $script:onReady = {
        $script:onReady = $null
        [IO.File]::AppendAllText((Join-Path (Get-Backup $script:current) 'final\Jar.bin'), 'corrupt')
        throw 'startup failed'
    }
    Assert-Throws { Invoke-Jar $f } 'Bozuk yedekle başlangıç başarısız kalmalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.NewHash -and (Get-EventCount 'start') -eq 1 -and (Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Yedek bozuksa yazma/başlatma olmamalı.'

    $f = New-Fixture; $script:onReady = { $script:onReady = $null; [IO.File]::AppendAllText($script:current.Target, 'external'); throw 'startup failed' }
    Assert-Throws { Invoke-Jar $f } 'Dış JAR değişiminde başarısız kalmalı.'
    Assert-True ((Get-EventCount 'start') -eq 1 -and (Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Dış JAR değişimi ezilmemeli.'

    $f = New-Fixture; $script:onReady = { throw 'startup failed' }
    Assert-Throws { Invoke-Jar $f } 'Özgün sürüm de açılamıyorsa başarısız kalmalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.OldHash -and (Get-EventCount 'start') -eq 2 -and (Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Üçüncü başlatma denenmemeli.'

    $f = New-Fixture; $script:failPortChecksAt = @(4); $script:onReady = { throw 'startup failed' }
    Assert-Throws { Invoke-Jar $f } 'Paper hâlâ açılırken kurtarma durmalı.'
    Assert-True ((Get-Hash $f.Target) -eq $f.NewHash -and (Get-EventCount 'stop') -eq 1 -and (Get-Phase $f) -ceq 'RECOVERY_BLOCKED') 'Açılmakta olan sürece zorla dokunulmamalı.'

    # A live server keeps these files open; the bug from the first real deployment stays covered.
    $f = New-Fixture; Write-Boot $f ('d' * 64)
    $live = [IO.File]::Open((Join-Path $f.Root 'logs\latest.log'), 'Open', 'Write', 'ReadWrite')
    $saves = [IO.File]::Open((Join-Path $f.Root $f.Files.Saves), 'Open', 'Write', 'ReadWrite')
    try {
        Assert-True ((Get-HybridNpcReceipt $f.Root $marker ([datetime]::UtcNow.AddMinutes(-1)) 'old boot') -ceq ('d' * 64)) 'Makbuz açık log üzerinden okunmalı.'
        Assert-True ((Get-LogHeader $f.Root) -ceq 'new boot') 'Log başlığı açık log üzerinden okunmalı.'
        Assert-True (@(Get-CitizensBlock (Join-Path $f.Root $f.Files.Saves) 313).Count -gt 0) 'Citizens kaydı açık dosyadan okunmalı.'
    } finally { $live.Dispose(); $saves.Dispose() }
    $blocks = @(Get-CitizensBlock (Join-Path $f.Root $f.Files.Saves) 313)
    Assert-True (!($blocks -join "`n").Contains('Other')) 'Citizens bloğu komşu NPC verisini içermemeli.'

    Write-Host "Yalnız JAR dağıtımı: $checks denetim başarılı (canlı sunucu kullanılmadı)."
} finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\npcspeak-jar-tests-'
    if (!$resolved.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Güvenli test temizleme yolu doğrulanamadı.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

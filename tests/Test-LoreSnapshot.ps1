#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('npcspeak-snapshot-tests-' + [guid]::NewGuid().ToString('N'))
$checks = 0
function Assert-True([bool]$Value, [string]$Message) { if (!$Value) { throw $Message }; $script:checks++ }
function Assert-Throws([scriptblock]$Action, [string]$Message) {
    $threw = $false; try { & $Action } catch { $threw = $true }; Assert-True $threw $Message
}
try {
    $source = Join-Path $testRoot 'source'
    $null = New-Item -ItemType Directory -Path (Join-Path $source 'knowledge\wiki')
    [IO.File]::WriteAllText((Join-Path $source 'knowledge.yml'), 'enabled: true')
    [IO.File]::WriteAllText((Join-Path $source 'server.properties'), 'private-test-sentinel')
    foreach ($name in @('A.txt','B.txt')) { [IO.File]::WriteAllText((Join-Path $source ('knowledge\wiki\' + $name)), 'Title: Example') }
    $copyScript = Join-Path (Split-Path $PSScriptRoot) 'scripts\Copy-LoreSnapshot.ps1'
    $destination = Join-Path $testRoot 'snapshot'
    & $copyScript -SourceData $source -SnapshotRoot $destination -ExpectedWikiCount 2
    $manifest = Get-Content -LiteralPath (Join-Path $destination 'snapshot-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-True ($manifest.Files.Count -eq 3) 'Yalnız knowledge.yml ve iki wiki kopyalanmalı.'
    Assert-True (!(Test-Path -LiteralPath (Join-Path $destination 'data\server.properties'))) 'Sunucu yapılandırması kopyalanmamalı.'
    Assert-True ((Get-Item -LiteralPath (Join-Path $destination 'data\knowledge.yml')).IsReadOnly) 'Kopya salt okunur olmalı.'
    Assert-True (!(Get-Item -LiteralPath (Join-Path $source 'knowledge.yml')).IsReadOnly) 'Canlı kaynağın dosya nitelikleri değiştirilmemeli.'
    foreach ($entry in $manifest.Files) {
        Assert-True ((Get-FileHash -LiteralPath (Join-Path $destination ('data\' + $entry.Path))).Hash -eq $entry.SHA256) 'Kopya hash doğrulaması.'
    }
    Assert-Throws { & $copyScript -SourceData $source -SnapshotRoot $destination -ExpectedWikiCount 2 } 'Var olan snapshot üzerine yazılmamalı.'
    $mismatch = Join-Path $testRoot 'mismatch'
    Assert-Throws { & $copyScript -SourceData $source -SnapshotRoot $mismatch -ExpectedWikiCount 221 } 'Makale sayısı uyuşmazlığı reddedilmeli.'
    Assert-True (!(Test-Path -LiteralPath $mismatch)) 'Sayı hatasında kopyalama başlamamalı.'
    Assert-Throws { & $copyScript -SourceData $source -SnapshotRoot (Join-Path $source 'unsafe') -ExpectedWikiCount 2 } 'Canlı kaynak altında snapshot oluşturulmamalı.'
    Write-Host "Snapshot betiği: $checks denetim başarılı; yalnız geçici test dosyaları kullanıldı."
} finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $prefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\npcspeak-snapshot-tests-'
    if (!$resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Test temizleme yolu güvenli değil.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

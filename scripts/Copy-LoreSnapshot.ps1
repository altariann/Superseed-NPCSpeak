#Requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SourceData,
    [Parameter(Mandatory)][string]$SnapshotRoot,
    [ValidateRange(1, 10000)][int]$ExpectedWikiCount = 221
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourceData).Path
$destination = [IO.Path]::GetFullPath($SnapshotRoot)
if (Test-Path -LiteralPath $destination) { throw 'Yeni bir snapshot klasörü seçin; mevcut dosyaların üzerine yazılmaz.' }
if ($destination.StartsWith($source.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Snapshot canlı veri klasörünün içinde olamaz.'
}
$wiki = Join-Path $source 'knowledge\wiki'
$pages = @(Get-ChildItem -LiteralPath $wiki -File | Where-Object {$_.Extension -ieq '.txt'} | Sort-Object Name)
if ($pages.Count -ne $ExpectedWikiCount) { throw "Beklenen wiki sayısı $ExpectedWikiCount; bulunan $($pages.Count). Kopyalama başlamadı." }
$files = @((Get-Item -LiteralPath (Join-Path $source 'knowledge.yml'))) + $pages
$manifest = @($files | ForEach-Object {
    if ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Bağlantı dosyaları kopyalanmaz.' }
    [pscustomobject]@{Path=$(if ($_.Name -eq 'knowledge.yml') {'knowledge.yml'} else {'knowledge/wiki/' + $_.Name}); Bytes=$_.Length; SHA256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$null = New-Item -ItemType Directory -Path (Join-Path $destination 'data\knowledge\wiki')
foreach ($entry in $manifest) {
    Copy-Item -LiteralPath (Join-Path $source $entry.Path) -Destination (Join-Path $destination ('data\' + $entry.Path))
}
$after = @(Get-ChildItem -LiteralPath $wiki -File | Where-Object {$_.Extension -ieq '.txt'} | Sort-Object Name)
if (($after.Name -join "`n") -cne ($pages.Name -join "`n")) { throw 'Wiki dosya listesi kopyalama sırasında değişti; snapshot geçersiz.' }
foreach ($entry in $manifest) {
    $original = Join-Path $source $entry.Path
    $copy = Join-Path $destination ('data\' + $entry.Path)
    if ((Get-FileHash -LiteralPath $original).Hash -ne $entry.SHA256 -or (Get-FileHash -LiteralPath $copy).Hash -ne $entry.SHA256) {
        throw 'Kaynak veya kopya hash değeri değişti; snapshot geçersiz. Canlı kaynağa yazılmadı.'
    }
    (Get-Item -LiteralPath $copy).IsReadOnly = $true
}
@{WikiCount=$pages.Count;CapturedUtc=[datetime]::UtcNow.ToString('o');Files=$manifest} | ConvertTo-Json -Depth 5 |
    Set-Content -LiteralPath (Join-Path $destination 'snapshot-manifest.json') -Encoding UTF8
Write-Host "Salt okunur snapshot doğrulandı: $($pages.Count) wiki, $($manifest.Count) dosya. Özel alan: $destination"

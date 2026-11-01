#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path (Split-Path $PSScriptRoot) 'scripts\Invoke-LorePilot.ps1')
$root=Join-Path ([IO.Path]::GetTempPath()) ('npcspeak-pilot-tests-'+[guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $root
$checks=0
function Assert-True([bool]$Value,[string]$Text) {if(!$Value){throw $Text};$script:checks++}
function Assert-Throws([scriptblock]$Action,[string]$Text) {$ok=$false;try{&$Action}catch{$ok=$true};Assert-True $ok $Text}
try {
    Assert-True ((Assert-PilotEndpoint 'http://127.0.0.1:11434/api/chat').Host -eq '127.0.0.1') 'Loopback kabul edilmeli.'
    foreach($uri in @('http://example.com/api/chat','http://localhost:11434/api/chat','http://127.0.0.1:11434/api/pull','http://127.0.0.1:11434/api/chat?x=1','http://user:password@127.0.0.1/api/chat')) {
        Assert-Throws {Assert-PilotEndpoint $uri} 'Loopback dışı/istenmeyen endpoint reddedilmeli.'
    }
    $file=Join-Path $root 'request.json'
    $valid=@{model='fixture-model';stream=$false;messages=@(@{role='system';content='test'},@{role='user';content='question'})}
    [IO.File]::WriteAllText($file,($valid|ConvertTo-Json -Depth 6))
    Assert-True ((Read-PilotRequest $file).Model -eq 'fixture-model') 'Üretim istek biçimi kabul edilmeli.'
    $valid.options=@{temperature=0}
    [IO.File]::WriteAllText($file,($valid|ConvertTo-Json -Depth 6))
    Assert-Throws {Read-PilotRequest $file} 'Pilot gizli inference ayarı ekleyememeli.'
    $valid.Remove('options');$valid.stream=$true
    [IO.File]::WriteAllText($file,($valid|ConvertTo-Json -Depth 6))
    Assert-Throws {Read-PilotRequest $file} 'Pilot akışsız olmalı.'
    # This preflight fails before tags/HTTP and never overwrites an existing private response.
    $RequestFile=$file;$ResponseFile=Join-Path $root 'existing.json';[IO.File]::WriteAllText($ResponseFile,'preserve')
    Assert-Throws {Invoke-PilotRequest} 'Var olan yanıtta ağ isteği yapılmamalı.'
    Assert-True ([IO.File]::ReadAllText($ResponseFile) -eq 'preserve') 'Var olan yanıt korunmalı.'
    Write-Host "Pilot aracı: $checks çevrimdışı denetim başarılı; ağ/model kullanılmadı."
} finally {
    $resolved=[IO.Path]::GetFullPath($root)
    $prefix=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')+'\npcspeak-pilot-tests-'
    if(!$resolved.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)){throw 'Temizleme yolu güvenli değil.'}
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

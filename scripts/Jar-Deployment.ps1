#Requires -Version 5.1
# Dot-sourced by Deploy-Superseed.ps1. JAR-only update of an already migrated server: this path
# writes exactly one file, and its plan format cannot describe an NPC or knowledge write.
function Get-PropertiesFingerprint([string]$Path) {
    # Minecraft rewrites a dated comment on every boot; only the settings identify the file.
    $lines=@([IO.File]::ReadAllLines($Path)|Where-Object{$_ -notlike '#*'})
    $sha=[Security.Cryptography.SHA256]::Create()
    try{return [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($lines -join "`n"))).Replace('-','')}finally{$sha.Dispose()}
}
function Assert-JarRelativePath([string]$Path) {
    if($Path -notmatch '^[^:\\]+$' -or $Path.StartsWith('/') -or ($Path -split '/') -contains '..'){throw 'Unsafe protected path in plan.'}
}
function Read-JarPlan([string]$Root,[string]$ManifestPath) {
    if(![IO.Path]::IsPathRooted($ManifestPath)){throw 'An explicit absolute JAR plan is required.'}
    Assert-HybridPath $ManifestPath
    $folder=Split-Path ([IO.Path]::GetFullPath($ManifestPath))
    foreach($mark in 'SUPERSEDED.txt','CONSUMED.txt'){if(Test-Path -LiteralPath (Join-Path $folder $mark)){throw 'This plan is superseded or already used; prepare a new reviewed plan.'}}
    if($ExpectedPlanSha256){Assert-HybridHash $ManifestPath $ExpectedPlanSha256 'reviewed plan'}
    $plan=Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8|ConvertFrom-Json
    # A migration plan carries Files; a JAR plan carries exactly one Jar entry.
    if($plan.PSObject.Properties['PlanType'] -eq $null -or $plan.PlanType -cne 'JarOnly' -or $plan.FormatVersion -ne 1 -or
            $plan.PSObject.Properties['Files'] -ne $null -or
            [IO.Path]::GetFullPath($plan.ServerRoot).TrimEnd('\') -ine $Root.TrimEnd('\') -or
            $plan.StartBat -cne $StartBat -or $StartBat -ne [IO.Path]::GetFileName($StartBat) -or
            $plan.Marker -notmatch '^HYBRID_READY_V1 [a-f0-9]{64}$' -or $plan.CitizensId -isnot [int] -or
            $plan.Jar.Target -cnotmatch '^plugins/[^/\\]+\.jar$' -or $plan.Jar.Candidate -cne 'files/new.jar' -or
            $plan.Jar.OriginalSha256 -ieq $plan.Jar.CandidateSha256){throw 'Invalid JAR plan or server identity.'}
    Assert-HybridPath $Root
    Assert-HybridHash (Join-Path $Root $StartBat) $plan.BatSha256 'launcher'
    if((Get-PropertiesFingerprint (Join-Path $Root 'server.properties')) -ine $plan.PropertiesFingerprint){throw 'Server settings changed since the plan.'}
    $validationPath=Join-Path $folder 'validation.json'; $protectedPath=Join-Path $folder 'protected.json'
    Assert-HybridHash $validationPath $plan.ValidationSha256 'offline validation'
    Assert-HybridHash $protectedPath $plan.ProtectedSha256 'protected inventory'
    $validation=Get-Content -LiteralPath $validationPath -Raw -Encoding UTF8|ConvertFrom-Json
    if(!$validation.Valid -or !$validation.ArtifactClassesVerified -or !$validation.MigratedSource -or $validation.LocalChecks -lt 4 -or
            $validation.Marker -cne $plan.Marker -or $validation.JarSha256 -ine $plan.Jar.CandidateSha256){throw 'Offline production-code validation is missing.'}
    $target=[IO.Path]::GetFullPath((Join-Path $Root $plan.Jar.Target)); $candidate=[IO.Path]::GetFullPath((Join-Path $folder $plan.Jar.Candidate))
    Assert-HybridPath $target; Assert-HybridPath $candidate
    Assert-HybridHash $target $plan.Jar.OriginalSha256 'installed JAR'
    Assert-HybridHash $candidate $plan.Jar.CandidateSha256 'candidate JAR'
    if((Get-NpcSpeakJar (Join-Path $Root 'plugins')).FullName -ine $target -or (Get-PluginName $candidate) -ne 'NPCSpeak'){throw 'Unexpected installed or candidate JAR identity.'}
    $zip=[IO.Compression.ZipFile]::OpenRead($candidate)
    try{if($null -eq $zip.GetEntry('com/l299l/npcspeak/knowledge/HybridReadiness.class')){throw 'Candidate lacks hybrid readiness support.'}}finally{$zip.Dispose()}
    $protected=Get-Content -LiteralPath $protectedPath -Raw -Encoding UTF8|ConvertFrom-Json
    foreach($entry in @($protected.Strict)+@($protected.Npc)){Assert-JarRelativePath $entry.Path}
    Assert-JarRelativePath $protected.CitizensSaves
    $result=[pscustomobject]@{Target=$target;Candidate=$candidate;OldHash=$plan.Jar.OriginalSha256;NewHash=$plan.Jar.CandidateSha256;Marker=$plan.Marker;CitizensId=$plan.CitizensId
        Folder=$folder;Manifest=$ManifestPath;Validation=$validationPath;ProtectedFile=$protectedPath;Protected=$protected;Fingerprint=$plan.PropertiesFingerprint}
    Assert-JarProtected $result $Root ''
    return $result
}
function Assert-JarProtected($Plan,[string]$Root,[string]$Receipt) {
    # Called before stop, after stop and after boot: anything else changing is a failure, never a new baseline.
    foreach($entry in @($Plan.Protected.Strict)){Assert-HybridHash (Join-Path $Root $entry.Path) $entry.Sha256 'protected file'}
    foreach($entry in @($Plan.Protected.Npc)){
        $hash=(Get-FileHash -LiteralPath (Join-Path $Root $entry.Path) -Algorithm SHA256).Hash
        if($hash -ine $entry.Sha256 -and !($Receipt -and $hash -ieq $Receipt)){throw 'NPC file changed outside the reviewed plan.'}
    }
    if((Get-PropertiesFingerprint (Join-Path $Root 'server.properties')) -ine $Plan.Fingerprint){throw 'Server settings changed outside the reviewed plan.'}
    $jars=@(Get-ChildItem -LiteralPath (Join-Path $Root 'plugins') -File -Filter '*.jar'|Where-Object{$_.Extension -ieq '.jar'}|ForEach-Object{'plugins/'+$_.Name})
    # ForEach-Object's member form returns nothing under -WhatIf.
    $expected=@(@($Plan.Protected.Strict|Where-Object{$_.Path -cmatch '^plugins/[^/]+\.jar$'}|ForEach-Object{$_.Path})+(($Plan.Target.Substring($Root.TrimEnd('\').Length+1)) -replace '\\','/'))
    if((@($jars|Sort-Object) -join '|') -ine (@($expected|Sort-Object) -join '|')){throw 'The plugin JAR set changed outside the reviewed plan.'}
}
function Get-CitizensBlock([string]$Path,[int]$Id) {
    $lines=@((Read-LogText $Path) -split '\r?\n')
    $start=-1
    for($i=0;$i -lt $lines.Count;$i++){if($lines[$i] -match ("^\s+'?"+$Id+"'?:\s*$")){$start=$i;break}}
    if($start -lt 0){throw 'Citizens NPC record is missing.'}
    $indent=($lines[$start] -replace '\S.*$','').Length; $block=@()
    for($i=$start+1;$i -lt $lines.Count;$i++){
        if($lines[$i].Trim() -and ($lines[$i] -replace '\S.*$','').Length -le $indent){break}
        $block+=$lines[$i]
    }
    return ,$block
}
function Get-CitizensReview([string]$Baseline,[string]$Current,[int]$Id) {
    # saves.yml is rewritten by normal saves. Identity and skin must match; anything else is reported.
    $before=Get-CitizensBlock $Baseline $Id; $after=Get-CitizensBlock $Current $Id
    $identity='(?i)^\s*(?:name|uuid|skin\w*|signature|textureraw|player\w*):'
    $changed=@(Compare-Object @($before) @($after)|ForEach-Object{$_.InputObject})
    if(@($changed|Where-Object{$_ -match $identity}).Count -gt 0){throw 'Citizens identity or skin changed.'}
    return $changed.Count
}
function Get-OnlinePlayerCount($Properties) {
    $client=Open-Rcon $Properties
    try{
        $stream=$client.GetStream(); Write-RconPacket $stream 103 2 'list'
        $size=[BitConverter]::ToInt32((Read-Exact $stream 4),0); $data=Read-Exact $stream $size
        $body=[Text.Encoding]::UTF8.GetString($data,8,$size-10) -replace '§.',''
    }finally{$client.Dispose()}
    if($body -notmatch '(\d+)\s*(?:/|of a max|out of|çevrimiçi)'){throw 'Player count could not be read; deployment stopped.'}
    return [int]$matches[1]
}
function Write-JarJournal([string]$Backup,[string]$Phase,$Plan,[int]$CitizensChanges=0) {
    [IO.File]::WriteAllText((Join-Path $Backup 'transaction.json'),(@{Phase=$Phase;Marker=$Plan.Marker;OldHash=$Plan.OldHash;NewHash=$Plan.NewHash;CitizensLinesChanged=$CitizensChanges}|ConvertTo-Json -Depth 4))
}
function Copy-JarVerified([string]$Source,[string]$Destination) {
    $null=New-Item -ItemType Directory -Path (Split-Path $Destination) -Force
    [IO.File]::Copy($Source,$Destination)
    if((Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash){throw 'Backup copy verification failed.'}
}
function Invoke-JarDeployment {
    [CmdletBinding(SupportsShouldProcess)]
    param([string]$Root,[string]$ManifestPath)
    $plan=Read-JarPlan $Root $ManifestPath
    Write-Host "JAR ONLY: $($plan.Target)`n  installed SHA256=$($plan.OldHash)`n  candidate SHA256=$($plan.NewHash)"
    Write-Host "Protected unchanged: $(@($plan.Protected.Strict).Count) files, $(@($plan.Protected.Npc).Count) NPC file(s), server settings. Citizens NPC $($plan.CitizensId) reviewed separately."
    Write-Host "Plan SHA256: $((Get-FileHash -LiteralPath $plan.Manifest -Algorithm SHA256).Hash)"
    Write-Host 'Order: verify -> zero players -> backup -> safe RCON stop -> full shutdown -> recheck -> lock JAR -> apply -> same BAT -> readiness attestation -> protected recheck.'
    Write-Host 'Failure after stop: unchanged JAR -> restart original; applied JAR -> safe shutdown -> verified restore -> restart original. No NPC, knowledge or other file is written.'
    if($Mode -ne 'JarDeploy'){Write-Host 'Read-only preview complete. No lock, backup, RCON or process action.';return}
    if(!$PSCmdlet.ShouldProcess($Root,'Replace only the hash-pinned NPCSpeak JAR with verified rollback')){return}
    if(!$ApproveDeployment -or $ExpectedPlanSha256 -notmatch '^[a-fA-F0-9]{64}$'){throw 'Separate user approval, -ApproveDeployment and the reviewed -ExpectedPlanSha256 are required.'}
    $properties=Get-ServerProperties $Root; $bat=Join-Path $Root $StartBat; $batText=[IO.File]::ReadAllText($bat)
    $identity=Get-ServerIdentity $properties $batText
    $knownPluginNames=@(Get-ChildItem -LiteralPath (Join-Path $Root 'plugins') -File -Filter '*.jar'|ForEach-Object{Get-PluginName $_.FullName}|Where-Object{$_})
    if($identity.Wildcard -and !$RconFirewallVerified){throw 'Confirm the existing RCON external-access restriction before deployment.'}
    $lock=[IO.File]::Open((Join-Path $Root '.npcspeak-deploy.lock'),'OpenOrCreate','ReadWrite','None')
    $readLocks=@();$target=$null;$stopped=$false;$changed=$false;$started=$false;$recoveryBlocked=$false;$backup='';$since=[datetime]::UtcNow;$header=''
    try {
        foreach($path in @($plan.Manifest,$plan.Validation,$plan.ProtectedFile,$plan.Candidate)){$readLocks+=[IO.File]::Open($path,'Open','Read','Read')}
        $plan=Read-JarPlan $Root $ManifestPath
        if((Get-OnlinePlayerCount $properties) -ne 0){throw 'Players are online; deployment not started.'}
        $backupRoot=Join-Path $Root 'deployment-backups'
        if(!(Test-Path -LiteralPath $backupRoot)){$null=New-Item -ItemType Directory -Path $backupRoot}
        Assert-HybridPath $backupRoot
        $backup=Join-Path $backupRoot ('jar-'+[guid]::NewGuid().ToString('N'))
        $null=New-Item -ItemType Directory -Path $backup
        Copy-JarVerified $plan.Target (Join-Path $backup 'before\Jar.bin')
        $copies=@('server.properties',$StartBat,'plugins/NPCSpeak/knowledge.yml','plugins/NPCSpeak/messages.yml','plugins/NPCSpeak/config.yml',$plan.Protected.CitizensSaves)+@($plan.Protected.Npc|ForEach-Object{$_.Path})
        foreach($path in $copies){$source=Join-Path $Root $path;if(Test-Path -LiteralPath $source){Copy-JarVerified $source (Join-Path $backup ('protected\'+($path -replace '[/\\]','__')))}}
        Write-JarJournal $backup 'BACKED_UP_BEFORE_STOP' $plan
        $null=Read-JarPlan $Root $ManifestPath
        Stop-ServerSafely $identity $properties $StopTimeoutSeconds;$stopped=$true
        Assert-HybridServerAbsent $properties
        # Shutdown saves NPC data; a reviewed plan is never silently rebased on what it wrote.
        $null=Read-JarPlan $Root $ManifestPath
        $saves=Join-Path $Root $plan.Protected.CitizensSaves
        $citizensBaseline=Join-Path $backup 'final\saves.yml'
        Copy-JarVerified $saves $citizensBaseline
        $null=Get-CitizensBlock $citizensBaseline $plan.CitizensId
        $target=[IO.File]::Open($plan.Target,'Open','ReadWrite','None')
        if((Get-HybridStreamHash $target) -ine $plan.OldHash){throw 'Installed JAR changed before exclusive lock; nothing installed.'}
        $finalJar=Join-Path $backup 'final\Jar.bin'
        $copy=[IO.File]::Open($finalJar,'CreateNew','Write','None')
        try{$target.Position=0;$target.CopyTo($copy);$target.Position=0}finally{$copy.Dispose()}
        Assert-HybridHash $finalJar $plan.OldHash 'post-stop JAR backup'
        Write-JarJournal $backup 'VERIFIED_STOPPED_BACKUP' $plan
        Assert-HybridServerAbsent $properties
        # Burn the plan first: a failed attempt must be reviewed again, not replayed.
        [IO.File]::WriteAllText((Join-Path $plan.Folder 'CONSUMED.txt'),"Used $([datetime]::UtcNow.ToString('o'))`r`n")
        try {
            $changed=$true;Write-HybridTarget $target $plan.Candidate
            if((Get-HybridStreamHash $target) -ine $plan.NewHash){throw 'Installed JAR hash mismatch.'}
        } catch {
            try{
                Write-HybridTarget $target $finalJar
                if((Get-HybridStreamHash $target) -ine $plan.OldHash){throw 'Restored JAR verification failed.'}
                $changed=$false;Write-JarJournal $backup 'RESTORED_BEFORE_START' $plan
            }catch{$recoveryBlocked=$true;throw 'In-place recovery failed; no further file writes or restart permitted.'}
            throw
        } finally {$target.Dispose();$target=$null}
        Write-JarJournal $backup 'APPLIED' $plan
        Assert-HybridServerAbsent $properties
        $since=[datetime]::UtcNow;$header=Get-LogHeader $Root;$started=$true
        Start-ServerBat $Root $bat $identity.Java
        $ready=Wait-ServerReady $Root $properties $batText $since $StartTimeoutSeconds $header $knownPluginNames $plan.Marker
        $receipt=Get-HybridNpcReceipt $Root $plan.Marker $since $header
        Assert-HybridHash $plan.Target $plan.NewHash 'installed JAR after boot'
        Assert-JarProtected $plan $Root $receipt
        $citizensChanges=Get-CitizensReview $citizensBaseline $saves $plan.CitizensId
        if($citizensChanges -gt 0){Write-Warning "Citizens NPC $($plan.CitizensId): identity and skin match; $citizensChanges other line(s) differ. Review in game."}
        Write-JarJournal $backup 'READY' $plan $citizensChanges
        Write-Host "JAR update ready. Attestation and protected files verified. PID: $($ready.Process.Id). Backup: $backup"
    } catch {
        $failure=$_
        if($target){$target.Dispose();$target=$null}
        if("$($failure.Exception.Message)" -ceq 'UNEXPECTED_SERVER_PROCESS'){$recoveryBlocked=$true;Write-Warning "Unexpected Paper process. No stop, write or restart. Private backup: $backup"}
        $outcome='FAILED'
        if($stopped -and !$recoveryBlocked){
            try {
                Stop-HybridFailedStart $Root $properties $batText
                if($changed){
                    $finalJar=Join-Path $backup 'final\Jar.bin'
                    Assert-HybridHash $finalJar $plan.OldHash 'rollback backup'
                    $target=[IO.File]::Open($plan.Target,'Open','ReadWrite','None')
                    $hash=Get-HybridStreamHash $target
                    if($hash -ine $plan.OldHash -and $hash -ine $plan.NewHash){throw 'Unreviewed JAR change; rollback must not overwrite it.'}
                    Assert-HybridServerAbsent $properties
                    Write-HybridTarget $target $finalJar
                    if((Get-HybridStreamHash $target) -ine $plan.OldHash){throw 'Restored JAR verification failed; recovery halted.'}
                    $target.Dispose();$target=$null
                    Write-JarJournal $backup 'ROLLED_BACK' $plan
                }
                Assert-HybridServerAbsent $properties
                $recoverySince=[datetime]::UtcNow;$recoveryHeader=Get-LogHeader $Root
                Start-ServerBat $Root $bat $identity.Java
                $null=Wait-ServerReady $Root $properties $batText $recoverySince $StartTimeoutSeconds $recoveryHeader $knownPluginNames
                $outcome=if($changed){'ROLLED_BACK_READY'}else{'ORIGINAL_RESTARTED'}
                Write-Warning 'Original JAR is active; no other file was written.'
            } catch {$recoveryBlocked=$true;Write-Warning "Recovery halted. No further automatic writes/restarts. Private backup: $backup"}
            finally{if($target){$target.Dispose();$target=$null}}
        }
        if($backup){
            try{Write-JarJournal $backup $(if($recoveryBlocked){'RECOVERY_BLOCKED'}else{$outcome}) $plan}
            catch{Write-Warning "Transaction journal could not be updated. Private backup: $backup"}
        }
        throw $failure
    } finally {if($target){$target.Dispose()};foreach($stream in $readLocks){$stream.Dispose()};$lock.Dispose()}
}

#Requires -Version 5.1
# Helpers are dot-sourced by Deploy-Superseed.ps1. No actions on import.
function Assert-HybridPath([string]$Path) {
    $cursor=[IO.Path]::GetFullPath($Path)
    while ($cursor) {
        if ((Test-Path -LiteralPath $cursor) -and ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Deployment path contains a reparse point.'
        }
        $parent=[IO.Path]::GetDirectoryName($cursor)
        if ($parent -eq $cursor) { break }; $cursor=$parent
    }
}
function Assert-HybridHash([string]$Path,[string]$Hash,[string]$Label) {
    if ($Hash -notmatch '^[a-fA-F0-9]{64}$' -or !(Test-Path -LiteralPath $Path -PathType Leaf) -or
            (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ine $Hash) {
        throw "Hash verification failed: $Label. No unreviewed file may be installed."
    }
}
function Read-HybridPlan([string]$Root,[string]$ManifestPath) {
    if (![IO.Path]::IsPathRooted($ManifestPath)) { throw 'An explicit absolute migration manifest is required.' }
    Assert-HybridPath $ManifestPath
    if ($ExpectedPlanSha256) { Assert-HybridHash $ManifestPath $ExpectedPlanSha256 'reviewed plan' }
    $plan=Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $folder=Split-Path ([IO.Path]::GetFullPath($ManifestPath))
    if(Test-Path -LiteralPath (Join-Path $folder 'SUPERSEDED.txt')){throw 'This migration plan is superseded; use the newer reviewed plan.'}
    if ($plan.FormatVersion -ne 1 -or [IO.Path]::GetFullPath($plan.ServerRoot).TrimEnd('\') -ine $Root.TrimEnd('\') -or
            $plan.StartBat -cne $StartBat -or $StartBat -ne [IO.Path]::GetFileName($StartBat) -or
            $plan.Files -isnot [array] -or $plan.Files.Count -ne 3 -or
            $plan.Marker -notmatch '^HYBRID_READY_V1 [a-f0-9]{64}$') { throw 'Invalid migration manifest or server identity.' }
    Assert-HybridPath $Root
    Assert-HybridHash (Join-Path $Root $StartBat) $plan.BatSha256 'launcher'
    Assert-HybridHash (Join-Path $Root 'server.properties') $plan.PropertiesSha256 'server properties'
    $validationPath=Join-Path $folder 'validation.json'
    Assert-HybridHash $validationPath $plan.ValidationSha256 'offline validation'
    $validation=Get-Content -LiteralPath $validationPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if (!$validation.Valid -or !$validation.ArtifactClassesVerified -or $validation.LocalChecks -lt 4 -or $validation.Marker -cne $plan.Marker) {
        throw 'Offline production-code validation is missing.'
    }
    $entries=@();$kinds=@()
    foreach ($file in $plan.Files) {
        $kind=$file.Kind; $target=$file.Target; $candidate=$file.Candidate
        $valid=($kind -ceq 'Jar' -and $target -cmatch '^plugins/[^/\\]+\.jar$' -and $candidate -ceq 'files/new.jar') -or
            ($kind -ceq 'Npc' -and $target -cmatch '^plugins/NPCSpeak/npcs/[a-zA-Z0-9_-]+\.yml$' -and $candidate -ceq 'files/npc.yml') -or
            ($kind -ceq 'Knowledge' -and $target -ceq 'plugins/NPCSpeak/knowledge.yml' -and $candidate -ceq 'files/knowledge.yml')
        if (!$valid -or $target.Contains('..') -or $kinds -contains $kind) { throw 'Unexpected migration target.' }
        $kinds+=$kind
        $targetPath=[IO.Path]::GetFullPath((Join-Path $Root $target)); $candidatePath=[IO.Path]::GetFullPath((Join-Path $folder $candidate))
        Assert-HybridPath $targetPath; Assert-HybridPath $candidatePath
        Assert-HybridHash $targetPath $file.OriginalSha256 ($kind+' original')
        Assert-HybridHash $candidatePath $file.CandidateSha256 ($kind+' candidate')
        if ($validation.($kind+'Sha256') -ine $file.CandidateSha256) { throw 'Candidate differs from offline validation.' }
        $entries+=[pscustomobject]@{Kind=$kind;Target=$targetPath;Candidate=$candidatePath;OldHash=$file.OriginalSha256;NewHash=$file.CandidateSha256}
    }
    if (($kinds|Sort-Object) -join ',' -cne 'Jar,Knowledge,Npc') { throw 'Three distinct migration targets required.' }
    $installed=Get-NpcSpeakJar (Join-Path $Root 'plugins')
    if ($installed.FullName -ine ($entries|Where-Object Kind -eq 'Jar').Target -or (Get-PluginName ($entries|Where-Object Kind -eq 'Jar').Candidate) -ne 'NPCSpeak') {
        throw 'Unexpected original or candidate JAR identity.'
    }
    return [pscustomobject]@{Files=$entries;Folder=$folder;Marker=$plan.Marker;Validation=$validationPath;Manifest=$ManifestPath}
}
function Get-HybridStreamHash([IO.Stream]$Stream) {
    $position=$Stream.Position; $Stream.Position=0; $sha=[Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Stream))).Replace('-','') }
    finally { $sha.Dispose(); $Stream.Position=$position }
}
function Write-HybridTarget([IO.Stream]$Target,[string]$Source) {
    $reader=[IO.File]::Open($Source,'Open','Read','Read')
    try { $Target.Position=0;$Target.SetLength(0);$reader.CopyTo($Target);$Target.Flush() } finally { $reader.Dispose() }
}
function Open-HybridTargets($Plan) {
    $opened=@()
    try {
        foreach ($entry in $Plan.Files) {
            $stream=[IO.File]::Open($entry.Target,'Open','ReadWrite','None')
            $opened+=[pscustomobject]@{Entry=$entry;Stream=$stream}
        }
        return ,$opened
    } catch { foreach($item in $opened){$item.Stream.Dispose()};throw }
}
function Restore-HybridTargets($Opened,[string]$Backup) {
    # Validate every backup before restoring the first file. The caller owns all target locks.
    foreach($item in $Opened){Assert-HybridHash (Join-Path $Backup ('final/'+$item.Entry.Kind+'.bin')) $item.Entry.OldHash 'rollback backup'}
    foreach($item in $Opened){
        Write-HybridTarget $item.Stream (Join-Path $Backup ('final/'+$item.Entry.Kind+'.bin'))
        if((Get-HybridStreamHash $item.Stream) -ine $item.Entry.OldHash){throw 'Restored file verification failed; recovery halted.'}
    }
}
function Get-HybridNpcReceipt([string]$Root,[string]$Marker,[datetime]$Since,[string]$OldHeader) {
    $log=Join-Path $Root 'logs/latest.log'
    if(!(Test-Path -LiteralPath $log) -or (Get-Item -LiteralPath $log).LastWriteTimeUtc -lt $Since){return ''}
    $text=[IO.File]::ReadAllText($log)
    if($OldHeader -and $text.StartsWith($OldHeader)){return ''}
    # A reload logs a newer receipt; only the last one describes the current file.
    $receipt=''
    foreach($line in ($text -split '\r?\n')){
        if($line -match '\[(?:Async Chat|AsyncChat|Chat Thread)[^\]]*/INFO\]|\]\s*:\s*<[^>]+>\s'){continue}
        if($line -match ('(?:^|\]\s*:?\s*)\[NPCSpeak\]\s+'+[regex]::Escape($Marker)+' NPC_SHA256 ([a-fA-F0-9]{64})\s*$')){$receipt=$matches[1]}
    }
    return $receipt
}
function Stop-HybridFailedStart([string]$Root,[hashtable]$Properties,[string]$BatText) {
    $listeners=@(Get-NetTCPConnection -State Listen -ErrorAction Stop|Where-Object{$_.LocalPort -in @([int]$Properties['server-port'],[int]$Properties['rcon.port'])})
    if($listeners.Count){$failed=Get-ServerIdentity $Properties $BatText;Stop-ServerSafely $failed $Properties $StopTimeoutSeconds}
    Assert-PortsClosed $Properties
}
function Assert-HybridServerAbsent($Properties) {
    # restart-on-crash can spawn Paper behind us; that process is never ours to stop or overwrite.
    try{Assert-PortsClosed $Properties}catch{throw 'UNEXPECTED_SERVER_PROCESS'}
}
function Write-HybridJournal([string]$Backup,[string]$Phase,$Plan) {
    [IO.File]::WriteAllText((Join-Path $Backup 'transaction.json'),(@{Phase=$Phase;Marker=$Plan.Marker;Files=@($Plan.Files|Select-Object Kind,OldHash,NewHash)}|ConvertTo-Json -Depth 6))
}
function Invoke-HybridDeployment {
    [CmdletBinding(SupportsShouldProcess)]
    param([string]$Root,[string]$ManifestPath)
    $plan=Read-HybridPlan $Root $ManifestPath
    foreach($entry in $plan.Files){Write-Host ("{0}: {1}`n  original SHA256={2}`n  candidate SHA256={3}" -f $entry.Kind,$entry.Target,$entry.OldHash,$entry.NewHash)}
    Write-Host "Plan SHA256: $((Get-FileHash -LiteralPath $plan.Manifest -Algorithm SHA256).Hash)"
    Write-Host "Private diffs: $($plan.Folder)\npc.binding.diff, npc.personality.diff, knowledge.diff"
    Write-Host 'Order: verify plan/backups -> safe RCON stop -> full shutdown -> lock all three files -> recheck -> apply -> readiness attestation.'
    Write-Host 'Failure after stop: unchanged files -> restart original; applied files -> safe shutdown -> verify all backups/current states -> restore only JAR/NPC/knowledge -> restart original. Other data is never rewound.'
    if($Mode -ne 'Deploy'){Write-Host 'Read-only preview complete. No lock, backup, RCON or process action.';return}
    if(!$PSCmdlet.ShouldProcess($Root,'Deploy the hash-pinned JAR, NPC and knowledge transaction with verified rollback')){return}
    if(!$ApproveDeployment -or $ExpectedPlanSha256 -notmatch '^[a-fA-F0-9]{64}$'){throw 'Separate user approval, -ApproveDeployment and the reviewed -ExpectedPlanSha256 are required.'}
    $properties=Get-ServerProperties $Root; $bat=Join-Path $Root $StartBat; $batText=[IO.File]::ReadAllText($bat)
    $identity=Get-ServerIdentity $properties $batText
    $knownPluginNames=@(Get-ChildItem -LiteralPath (Join-Path $Root 'plugins') -File -Filter '*.jar'|ForEach-Object{Get-PluginName $_.FullName}|Where-Object{$_})
    if($identity.Wildcard -and !$RconFirewallVerified){throw 'Confirm the existing RCON external-access restriction before deployment.'}
    $lock=[IO.File]::Open((Join-Path $Root '.npcspeak-deploy.lock'),'OpenOrCreate','ReadWrite','None')
    $readLocks=@();$opened=@();$stopped=$false;$changed=$false;$recoveryBlocked=$false;$started=$false;$backup='';$since=[datetime]::UtcNow;$header=''
    try {
        foreach($path in @($plan.Manifest,$plan.Validation)+@($plan.Files|ForEach-Object Candidate)){$readLocks+=[IO.File]::Open($path,'Open','Read','Read')}
        $plan=Read-HybridPlan $Root $ManifestPath
        $backupRoot=Join-Path $Root 'deployment-backups'
        if(!(Test-Path -LiteralPath $backupRoot)){$null=New-Item -ItemType Directory -Path $backupRoot}
        Assert-HybridPath $backupRoot
        $backup=Join-Path $backupRoot ('hybrid-'+[guid]::NewGuid().ToString('N'))
        $null=New-Item -ItemType Directory -Path (Join-Path $backup 'before')
        $null=New-Item -ItemType Directory -Path (Join-Path $backup 'final')
        foreach($entry in $plan.Files){Copy-Item -LiteralPath $entry.Target -Destination (Join-Path $backup ('before/'+$entry.Kind+'.bin'));Assert-HybridHash (Join-Path $backup ('before/'+$entry.Kind+'.bin')) $entry.OldHash 'pre-stop backup'}
        Write-HybridJournal $backup 'BACKED_UP_BEFORE_STOP' $plan
        $probe=Open-Rcon $properties;$probe.Dispose()
        $null=Read-HybridPlan $Root $ManifestPath
        Stop-ServerSafely $identity $properties $StopTimeoutSeconds;$stopped=$true
        Assert-HybridServerAbsent $properties
        # Shutdown itself may save NPC YAML. Never silently rebase a reviewed plan.
        $null=Read-HybridPlan $Root $ManifestPath
        $opened=Open-HybridTargets $plan
        foreach($item in $opened){
            if((Get-HybridStreamHash $item.Stream) -ine $item.Entry.OldHash){throw 'Target changed before exclusive lock; no files installed.'}
            $copy=[IO.File]::Open((Join-Path $backup ('final/'+$item.Entry.Kind+'.bin')),'CreateNew','Write','None')
            try{$item.Stream.Position=0;$item.Stream.CopyTo($copy);$item.Stream.Position=0}finally{$copy.Dispose()}
            Assert-HybridHash (Join-Path $backup ('final/'+$item.Entry.Kind+'.bin')) $item.Entry.OldHash 'post-stop backup'
        }
        Write-HybridJournal $backup 'VERIFIED_STOPPED_BACKUP' $plan
        Assert-HybridServerAbsent $properties
        try {
            foreach($item in $opened){$changed=$true;Write-HybridTarget $item.Stream $item.Entry.Candidate;if((Get-HybridStreamHash $item.Stream) -ine $item.Entry.NewHash){throw 'Installed file hash mismatch.'}}
        } catch {
            try{Restore-HybridTargets $opened $backup;$changed=$false;Write-HybridJournal $backup 'RESTORED_BEFORE_START' $plan}
            catch{$recoveryBlocked=$true;throw 'In-place recovery failed; no further file writes or restart permitted.'}
            throw
        } finally {foreach($item in $opened){$item.Stream.Dispose()};$opened=@()}
        Write-HybridJournal $backup 'APPLIED' $plan
        Assert-HybridServerAbsent $properties
        $since=[datetime]::UtcNow;$header=Get-LogHeader $Root;$started=$true
        Start-ServerBat $Root $bat $identity.Java
        $null=Wait-ServerReady $Root $properties $batText $since $StartTimeoutSeconds $header $knownPluginNames $plan.Marker
        $receipt=Get-HybridNpcReceipt $Root $plan.Marker $since $header
        foreach($entry in $plan.Files){$actual=(Get-FileHash -LiteralPath $entry.Target).Hash;if($actual -ine $entry.NewHash -and !($entry.Kind -eq 'Npc' -and $receipt -and $actual -ieq $receipt)){throw 'Unexpected post-start file state.'}}
        Write-HybridJournal $backup 'READY' $plan
        Write-Host "Hybrid deployment ready. Attestation verified. Backup: $backup"
    } catch {
        $failure=$_
        foreach($item in $opened){$item.Stream.Dispose()};$opened=@()
        if("$($failure.Exception.Message)" -ceq 'UNEXPECTED_SERVER_PROCESS'){$recoveryBlocked=$true;Write-Warning "Unexpected Paper process. No stop, write or restart. Private backup: $backup"}
        $outcome='FAILED'
        if($stopped -and !$recoveryBlocked){
            try {
                Stop-HybridFailedStart $Root $properties $batText
                if($changed){
                    $receipt=if($started){Get-HybridNpcReceipt $Root $plan.Marker $since $header}else{''}
                    $opened=Open-HybridTargets $plan
                    foreach($item in $opened){$hash=Get-HybridStreamHash $item.Stream;if($hash -ine $item.Entry.OldHash -and $hash -ine $item.Entry.NewHash -and !($item.Entry.Kind -eq 'Npc' -and $receipt -and $hash -ieq $receipt)){throw 'Unreviewed file change; rollback must not overwrite it.'}}
                    Assert-HybridServerAbsent $properties
                    Restore-HybridTargets $opened $backup
                    foreach($item in $opened){$item.Stream.Dispose()};$opened=@()
                    Write-HybridJournal $backup 'ROLLED_BACK' $plan
                }
                Assert-HybridServerAbsent $properties
                $recoverySince=[datetime]::UtcNow;$recoveryHeader=Get-LogHeader $Root
                Start-ServerBat $Root $bat $identity.Java
                $null=Wait-ServerReady $Root $properties $batText $recoverySince $StartTimeoutSeconds $recoveryHeader $knownPluginNames
                $outcome=if($changed){'ROLLED_BACK_READY'}else{'ORIGINAL_RESTARTED'}
                Write-Warning 'Original JAR, NPC and knowledge are active; other data was not rewound.'
            } catch {$recoveryBlocked=$true;Write-Warning "Recovery halted. No further automatic writes/restarts. Private backup: $backup"}
            finally{foreach($item in $opened){$item.Stream.Dispose()};$opened=@()}
        }
        if($backup){
            # The journal must never replace the original failure.
            try{Write-HybridJournal $backup $(if($recoveryBlocked){'RECOVERY_BLOCKED'}else{$outcome}) $plan}
            catch{Write-Warning "Transaction journal could not be updated. Private backup: $backup"}
        }
        throw $failure
    } finally {foreach($item in $opened){$item.Stream.Dispose()};foreach($stream in $readLocks){$stream.Dispose()};$lock.Dispose()}
}

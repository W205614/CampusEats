param([Parameter(Mandatory)][string]$ReplicaDirectory,[Parameter(Mandatory)][string]$Destination,[Parameter(Mandatory)][string]$IdentityFile,[switch]$VerifyDatabase)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'operations-support.ps1')
. (Join-Path $PSScriptRoot 'encrypted-backups.ps1')
$restored=Expand-EncryptedBackupReplica -ReplicaDirectory $ReplicaDirectory -Destination $Destination -IdentityFile $IdentityFile
if($VerifyDatabase){& (Join-Path $PSScriptRoot 'restore-check.ps1') -BackupDirectory $restored}
Write-Host "Encrypted backup decrypted and hash-verified in protected directory: $restored"

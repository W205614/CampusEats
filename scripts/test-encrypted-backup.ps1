$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'operations-support.ps1')
. (Join-Path $PSScriptRoot 'encrypted-backups.ps1')
$root=Split-Path $PSScriptRoot -Parent
$fixture=New-SecureDirectory -Path (Join-Path $root ('.local/encrypted-backup-test-'+[guid]::NewGuid().ToString('N')))
$source=New-SecureDirectory -Path (Join-Path $fixture 'source')
New-SecureDirectory -Path (Join-Path $source 'uploads/.private') | Out-Null
foreach($name in @('database.sql','source-counts.txt','all-table-counts.txt','tables.txt','table-checksums.txt','images.json','mounts.txt','.env','uploads/.private/evidence.png')){Write-SecureTextFile -Path (Join-Path $source $name) -Content ('isolated-'+$name)}
$entries=@(Get-ChildItem -LiteralPath $source -File -Recurse -Force | ForEach-Object {[ordered]@{path=[IO.Path]::GetRelativePath($source,$_.FullName);bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}})
Write-OperationsJson -Path (Join-Path $source 'sha256.json') -Value $entries
Write-OperationsJson -Path (Join-Path $source 'verified.json') -Value @{project='campuseats-encrypted-fixture';verifiedAt=[DateTimeOffset]::UtcNow.ToString('o');manifestSha256=(Get-FileHash -LiteralPath (Join-Path $source 'sha256.json') -Algorithm SHA256).Hash}
$age=Get-PinnedAge
$keygen=Join-Path (Split-Path $age -Parent) ('age-keygen'+$(if($IsWindows){'.exe'}else{''}))
$identity=Join-Path $fixture 'identity.txt'
$private=(& $keygen 2>$null) -join "`n"
if($LASTEXITCODE -ne 0){throw 'Fixture key generation failed.'}
Write-SecureTextFile -Path $identity -Content $private;$private=$null
Set-SecurePathPermissions -Path $identity
$recipient=(& $keygen -y $identity) -join ''
if($LASTEXITCODE -ne 0){throw 'Fixture public key extraction failed.'}
$rejected=$false
try {Copy-EncryptedBackupReplica -Source $source -ReplicaDirectory (Join-Path $fixture 'missing-recipient') -ProjectName 'campuseats-encrypted-fixture' -Recipient '' -AgeExecutable $age | Out-Null} catch {$rejected=$true}
if(!$rejected -or (Test-Path -LiteralPath (Join-Path $fixture 'missing-recipient'))){throw 'Missing public recipient allowed an export.'}
$replica=Copy-EncryptedBackupReplica -Source $source -ReplicaDirectory (Join-Path $fixture 'replicas') -ProjectName 'campuseats-encrypted-fixture' -Recipient $recipient -AgeExecutable $age
if((Find-VerifiedEncryptedBackupReplica -ReplicaDirectory (Join-Path $fixture 'replicas') -ProjectName 'campuseats-encrypted-fixture') -cne $replica){throw 'Encrypted replica was not recognized.'}
if(@(Get-ChildItem -LiteralPath $replica -File -Force).Count -ne 2 -or (Test-Path -LiteralPath (Join-Path $replica '.env'))){throw 'Plaintext or private key was exported.'}
$restored=Expand-EncryptedBackupReplica -ReplicaDirectory $replica -Destination (Join-Path $fixture 'decrypted') -IdentityFile $identity -AgeExecutable $age
if([IO.File]::ReadAllText((Join-Path $restored '.env')) -cne 'isolated-.env'){throw 'Decrypted backup mismatch.'}
$badIdentity=Join-Path $fixture 'wrong-identity.txt'
$private=(& $keygen 2>$null) -join "`n"
if($LASTEXITCODE -ne 0){throw 'Wrong fixture key creation failed.'}
Write-SecureTextFile -Path $badIdentity -Content $private;$private=$null
$rejected=$false
try {Expand-EncryptedBackupReplica -ReplicaDirectory $replica -Destination (Join-Path $fixture 'wrong-key') -IdentityFile $badIdentity -AgeExecutable $age | Out-Null} catch {$rejected=$true}
if(!$rejected -or (Test-Path -LiteralPath (Join-Path $fixture 'wrong-key/verified.json'))){throw 'Wrong key accepted.'}
$cipher=Join-Path $replica 'backup.tar.gz.age';$bytes=[IO.File]::ReadAllBytes($cipher);$bytes[$bytes.Length-1]=$bytes[$bytes.Length-1] -bxor 1;[IO.File]::WriteAllBytes($cipher,$bytes)
$rejected=$false
try {Expand-EncryptedBackupReplica -ReplicaDirectory $replica -Destination (Join-Path $fixture 'corrupt') -IdentityFile $identity -AgeExecutable $age | Out-Null} catch {$rejected=$true}
if(!$rejected){throw 'Corrupt ciphertext accepted.'}
$record=Get-Content -LiteralPath (Join-Path $replica 'encrypted-replica.json') -Raw | ConvertFrom-Json
$record.archiveSha256=(Get-FileHash -LiteralPath $cipher -Algorithm SHA256).Hash
Write-OperationsJson -Path (Join-Path $replica 'encrypted-replica.json') -Value $record
$rejected=$false
try {Expand-EncryptedBackupReplica -ReplicaDirectory $replica -Destination (Join-Path $fixture 'bad-authentication') -IdentityFile $identity -AgeExecutable $age | Out-Null} catch {$rejected=$true}
if(!$rejected -or (Test-Path -LiteralPath (Join-Path $fixture 'bad-authentication/verified.json'))){throw 'age authenticated corrupted ciphertext.'}
& (Join-Path $PSScriptRoot 'ops-schedule.ps1') -OutputDirectory (Join-Path $fixture 'schedules')
$xml=[xml](Get-Content -LiteralPath (Join-Path $fixture 'schedules/campuseats-v1-Backup.xml') -Raw)
if($xml.Task.Triggers.CalendarTrigger.StartBoundary -notmatch '^\d{4}-\d{2}-\d{2}T03:00:00\+08:00$' -or $xml.Task.Settings.StartWhenAvailable -cne 'false'){throw 'Shanghai schedule or maintenance-window policy mismatch.'}
Write-Host 'Encrypted backups: pinned real age roundtrip, public-only export, missing/wrong key, corruption rejection and schedule preview passed.'

$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'secure-files.ps1')
$root=Split-Path $PSScriptRoot -Parent
$fixture=New-SecureDirectory -Path (Join-Path $root ('.local/secure-files-test-'+[guid]::NewGuid().ToString('N')))
$file=Join-Path $fixture 'credentials.txt'
Write-SecureTextFile -Path $file -Content 'isolated-permission-fixture'
Set-SecurePathPermissions -Path $file
if([IO.File]::ReadAllText($file) -cne 'isolated-permission-fixture'){throw 'Protected write altered content.'}
if($IsWindows){
 $acl=Get-Acl -LiteralPath $file
 $allowed=@([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18','S-1-5-32-544')
 if(!$acl.AreAccessRulesProtected){throw 'File inherited permissions.'}
 foreach($rule in $acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])){if($rule.IdentityReference.Value -notin $allowed){throw 'Unexpected reader on protected file.'}}
}else{if([int][IO.File]::GetUnixFileMode($file) -ne 384 -or [int][IO.File]::GetUnixFileMode($fixture) -ne 448){throw 'Expected mode 0600/0700.'}}
# Inject at the protection boundary; no sensitive bytes may be written after failure.
$script:originalProtection=${function:Set-SecurePathPermissions}
function Set-SecurePathPermissions {param([string]$Path);throw 'Injected permission failure'}
$failed=$false;$blocked=Join-Path $fixture 'blocked.txt'
try {Write-SecureTextFile -Path $blocked -Content 'must-not-be-written'} catch {$failed=$true}
${function:Set-SecurePathPermissions}=$script:originalProtection
if(!$failed -or (Get-Item -LiteralPath $blocked).Length -ne 0){throw 'Permission failure did not fail before writing secret bytes.'}
if(Test-Path -LiteralPath (Join-Path $fixture 'verified.json')){throw 'Protection failure wrote a success marker.'}
Write-Host 'Sensitive files: real permission verification and fail-before-write boundary passed.'

# Sensitive bytes are written only after access protection has been established and checked.
function Assert-NoSecurePathLinks {
 param([Parameter(Mandatory)][string]$Path)
 $current=[IO.Path]::GetFullPath($Path)
 while($current){
  if(Test-Path -LiteralPath $current){
   if(((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'Sensitive paths must not contain links.'}
  }
  $parent=[IO.Path]::GetDirectoryName($current)
  if($parent -eq $current){break};$current=$parent
 }
}

function Set-SecurePathPermissions {
 param([Parameter(Mandatory)][string]$Path)
 Assert-NoSecurePathLinks -Path $Path
 $item=Get-Item -LiteralPath $Path -Force -ErrorAction Stop
 if($IsWindows){
  $owner=[Security.Principal.WindowsIdentity]::GetCurrent().User
  $allowed=@($owner.Value,'S-1-5-18','S-1-5-32-544') | Select-Object -Unique
  $acl=Get-Acl -LiteralPath $item.FullName -ErrorAction Stop
  if($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -notin $allowed){throw 'Sensitive path must have a trusted local owner.'}
  $acl.SetAccessRuleProtection($true,$false)
  foreach($existing in @($acl.GetAccessRules($true,$false,[Security.Principal.SecurityIdentifier]))){[void]$acl.RemoveAccessRuleSpecific($existing)}
  foreach($sid in $allowed){
   $identity=[Security.Principal.SecurityIdentifier]::new($sid)
   $rule=if($item.PSIsContainer){
    [Security.AccessControl.FileSystemAccessRule]::new($identity,[Security.AccessControl.FileSystemRights]::FullControl,([Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit),[Security.AccessControl.PropagationFlags]::None,[Security.AccessControl.AccessControlType]::Allow)
   }else{[Security.AccessControl.FileSystemAccessRule]::new($identity,[Security.AccessControl.FileSystemRights]::FullControl,[Security.AccessControl.AccessControlType]::Allow)}
   [void]$acl.AddAccessRule($rule)
  }
  # DACL failures are terminating; no unchecked native icacls exit status is involved.
  # Apply only the sections modified above. PowerShell Set-Acl can request SACL privileges
  # on a freshly constructed descriptor, which an ordinary owner does not possess.
  [IO.FileSystemAclExtensions]::SetAccessControl($item,$acl)
  $actual=Get-Acl -LiteralPath $item.FullName -ErrorAction Stop
  if(!$actual.AreAccessRulesProtected -or $actual.GetOwner([Security.Principal.SecurityIdentifier]).Value -notin $allowed){throw 'Sensitive path owner or inheritance is invalid.'}
  $seen=@{}
  foreach($rule in $actual.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])){
   $sid=$rule.IdentityReference.Value
   if($sid -notin $allowed -or $rule.AccessControlType -ne 'Allow' -or ($rule.FileSystemRights -band [Security.AccessControl.FileSystemRights]::FullControl) -ne [Security.AccessControl.FileSystemRights]::FullControl){throw 'Sensitive path has unexpected access permissions.'}
   $seen[$sid]=$true
  }
  if(@($allowed | Where-Object {!$seen.ContainsKey($_)}).Count){throw 'Sensitive path access protection is incomplete.'}
 }else{
  $mode=[IO.UnixFileMode]::UserRead -bor [IO.UnixFileMode]::UserWrite
  if($item.PSIsContainer){$mode=$mode -bor [IO.UnixFileMode]::UserExecute}
  [IO.File]::SetUnixFileMode($item.FullName,$mode)
  if([IO.File]::GetUnixFileMode($item.FullName) -ne $mode){throw 'Sensitive path POSIX mode verification failed.'}
 }
}

function New-SecureDirectory {
 param([Parameter(Mandatory)][string]$Path)
 Assert-NoSecurePathLinks -Path $Path
 [void][IO.Directory]::CreateDirectory([IO.Path]::GetFullPath($Path))
 Set-SecurePathPermissions -Path $Path
 return [IO.Path]::GetFullPath($Path)
}

function Write-SecureTextFile {
 param([Parameter(Mandatory)][string]$Path,[Parameter(Mandatory)][AllowEmptyString()][string]$Content)
 $absolute=[IO.Path]::GetFullPath($Path)
 Assert-NoSecurePathLinks -Path $absolute
 if(!(Test-Path -LiteralPath $absolute)){
  $empty=[IO.File]::Open($absolute,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None);$empty.Dispose()
 }
 Set-SecurePathPermissions -Path $absolute
 [IO.File]::WriteAllText($absolute,$Content,[Text.UTF8Encoding]::new($false))
 Set-SecurePathPermissions -Path $absolute
}

function Move-ProjectFileToRecycleBin {
 param([Parameter(Mandatory)][string]$Path)
 if(!(Test-Path -LiteralPath $Path)){return}
 Assert-NoSecurePathLinks -Path $Path
 $root=Split-Path $PSScriptRoot -Parent
 $local=[IO.Path]::GetFullPath((Join-Path $root '.local'))+[IO.Path]::DirectorySeparatorChar
 if(![IO.Path]::GetFullPath($Path).StartsWith($local,[StringComparison]::OrdinalIgnoreCase)){throw 'Temporary cleanup must remain inside this workspace .local directory.'}
 if($IsWindows){
  Add-Type -AssemblyName Microsoft.VisualBasic
  if((Get-Item -LiteralPath $Path -Force).PSIsContainer){
   [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory([IO.Path]::GetFullPath($Path),[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
  }else{[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile([IO.Path]::GetFullPath($Path),[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}
 }
 # On ephemeral non-Windows CI, retain protected temporary files until runner disposal.
}

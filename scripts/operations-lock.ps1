# The token permits nested scripts in this process only; another process must acquire the file lock.
function Enter-OperationsLock {
 param([Parameter(Mandatory)][string]$Root,[Parameter(Mandatory)][string]$ProjectName,[string]$Token='')
 if($ProjectName -notmatch '^[a-z0-9][a-z0-9_-]{0,63}$'){throw 'Invalid Compose project name.'}
 $directory=Join-Path $Root '.local/operations-locks'
 [void][IO.Directory]::CreateDirectory($directory)
 $path=[IO.Path]::GetFullPath((Join-Path $directory ($ProjectName+'.lock')))
 if(!(Get-Variable CampusOperationsLocks -Scope Global -ErrorAction SilentlyContinue)){$global:CampusOperationsLocks=@{}}
 if($global:CampusOperationsLocks.ContainsKey($path)){
  $held=$global:CampusOperationsLocks[$path]
  if(!$Token -or $Token -cne $held.Token){throw 'Another operation already holds this project lock.'}
  return [pscustomobject]@{Path=$path;Token=$Token;Owned=$false}
 }
 if($Token){throw 'Invalid nested operation token.'}
 try {$stream=[IO.File]::Open($path,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)}
 catch {throw 'Another operation already holds this project lock.'}
 $tokenValue=[guid]::NewGuid().ToString('N')
 $global:CampusOperationsLocks[$path]=@{Token=$tokenValue;Stream=$stream}
 return [pscustomobject]@{Path=$path;Token=$tokenValue;Owned=$true}
}

function Exit-OperationsLock {
 param($Lease)
 if($Lease -and $Lease.Owned -and $global:CampusOperationsLocks.ContainsKey($Lease.Path)){
  $held=$global:CampusOperationsLocks[$Lease.Path]
  if($held.Token -ceq $Lease.Token){$held.Stream.Dispose();$global:CampusOperationsLocks.Remove($Lease.Path)}
 }
}

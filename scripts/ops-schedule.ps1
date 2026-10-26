param([string]$ProjectName='campuseats-v1',[string]$EnvFile='.env',[string]$ConfigFile='.local/ops-config.json',[string]$OutputDirectory='.local/schedule-preview')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'secure-files.ps1')
$root=Split-Path $PSScriptRoot -Parent
if($ProjectName -notmatch '^[a-z0-9][a-z0-9_-]{0,63}$'){throw 'Invalid project identifier.'}
$destination=[IO.Path]::GetFullPath($OutputDirectory,$root)
if(!$destination.StartsWith([IO.Path]::GetFullPath((Join-Path $root '.local'))+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Keep schedule previews under protected .local.'}
New-SecureDirectory -Path $destination | Out-Null
$pwsh=(Get-Command pwsh -ErrorAction Stop).Source
$script=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'ops-run.ps1'))
foreach($value in @($root,$script,$EnvFile,$ConfigFile)){if($value -match '["\r\n]'){throw 'Unsafe schedule argument.'}}
$owner=if($IsWindows){[Security.Principal.WindowsIdentity]::GetCurrent().User.Value}else{'CURRENT_USER'}
$day=[DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(8)).ToString('yyyy-MM-dd')
$definitions=@(@{name='Check';action='Check';start=($day+'T00:00:00+08:00');repetition='<Repetition><Interval>PT5M</Interval><StopAtDurationEnd>false</StopAtDurationEnd></Repetition>'},@{name='Backup';action='Backup';start=($day+'T03:00:00+08:00');repetition=''})
foreach($definition in $definitions){
 $arguments='-NoProfile -NonInteractive -File "'+$script+'" -Action '+$definition.action+' -ProjectName "'+$ProjectName+'" -EnvFile "'+[IO.Path]::GetFullPath($EnvFile,$root)+'" -ConfigFile "'+[IO.Path]::GetFullPath($ConfigFile,$root)+'"'
 $escape={param($text)[Security.SecurityElement]::Escape([string]$text)}
 $trigger=if($definition.action -eq 'Check'){'<TimeTrigger>'+$definition.repetition+'<StartBoundary>'+$definition.start+'</StartBoundary><Enabled>true</Enabled></TimeTrigger>'}else{'<CalendarTrigger><StartBoundary>'+$definition.start+'</StartBoundary><Enabled>true</Enabled><ScheduleByDay><DaysInterval>1</DaysInterval></ScheduleByDay></CalendarTrigger>'}
 $startWhenAvailable=if($definition.action -eq 'Check'){'true'}else{'false'}
 $xml=@"
<?xml version="1.0"?>
<Task version="1.4" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
 <RegistrationInfo><Description>CampusEats $($definition.action) preview. Shanghai schedule; backup pauses writers. Import only after choosing a maintenance window.</Description></RegistrationInfo>
 <Triggers>$trigger</Triggers>
 <Principals><Principal id="Author"><UserId>$owner</UserId><LogonType>InteractiveToken</LogonType><RunLevel>LeastPrivilege</RunLevel></Principal></Principals>
 <Settings><MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy><StartWhenAvailable>$startWhenAvailable</StartWhenAvailable><ExecutionTimeLimit>PT30M</ExecutionTimeLimit></Settings>
 <Actions Context="Author"><Exec><Command>$(& $escape $pwsh)</Command><Arguments>$(& $escape $arguments)</Arguments><WorkingDirectory>$(& $escape $root)</WorkingDirectory></Exec></Actions>
</Task>
"@
 [xml]$checked=$xml
 if($IsWindows){
  # Parse with the native scheduler's definition API without registering any task.
  $scheduler=New-Object -ComObject 'Schedule.Service'
  try {$scheduler.Connect();$task=$scheduler.NewTask(0);try{$task.XmlText=$xml}finally{if($task){[void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($task)}}}
  finally {[void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($scheduler)}
 }
 Write-SecureTextFile -Path (Join-Path $destination ($ProjectName+'-'+$definition.name+'.xml')) -Content $xml
}
Write-Host "Preview only: Check every 5 minutes and Backup at 03:00 Asia/Shanghai. XML saved under $destination. No scheduled task was registered."

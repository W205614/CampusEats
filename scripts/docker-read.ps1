# Run a read-only Docker command through an actual process boundary. Never print stderr or inputs:
# operational queries and Docker errors can contain credentials or customer data.
function Invoke-BoundedDocker {
 param(
  [Parameter(Mandatory)][string[]]$Arguments,
  [string]$InputText='',
  [int]$TimeoutMilliseconds=15000,
  [string]$Executable='docker',
  [string[]]$PrefixArguments=@()
 )
 $start=[Diagnostics.ProcessStartInfo]::new()
 $start.FileName=$Executable
 $start.UseShellExecute=$false
 $start.CreateNoWindow=$true
 $start.RedirectStandardInput=$true
 $start.RedirectStandardOutput=$true
 $start.RedirectStandardError=$true
 foreach($argument in @($PrefixArguments)+@($Arguments)){$start.ArgumentList.Add($argument)}
 $process=[Diagnostics.Process]::new()
 $process.StartInfo=$start
 $started=$false
 try {
  if(!$process.Start()){throw 'Docker process could not start.'}
  $started=$true
  $output=$process.StandardOutput.ReadToEndAsync()
  $errors=$process.StandardError.ReadToEndAsync()
  # Async input prevents a blocked stdin pipe from bypassing the command deadline.
  $inputWrite=$process.StandardInput.WriteAsync($InputText)
  $deadline=[Diagnostics.Stopwatch]::StartNew()
  while(!$inputWrite.IsCompleted -and $deadline.ElapsedMilliseconds -lt $TimeoutMilliseconds){[Threading.Thread]::Sleep(10)}
  if(!$inputWrite.IsCompleted){throw 'Docker command timed out.'}
  [void]$inputWrite.GetAwaiter().GetResult()
  $process.StandardInput.Close()
  $remaining=[Math]::Max(0,$TimeoutMilliseconds-[int]$deadline.ElapsedMilliseconds)
  if(!$process.WaitForExit($remaining)){throw 'Docker command timed out.'}
  # Descendants must not hold output pipes open beyond the original deadline either.
  $remaining=[Math]::Max(0,$TimeoutMilliseconds-[int]$deadline.ElapsedMilliseconds)
  if(![Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($output,$errors),$remaining)){throw 'Docker command timed out.'}
  if($process.ExitCode -ne 0){throw 'Docker command failed.'}
  return $output.GetAwaiter().GetResult()
 } catch {
  if($started -and !$process.HasExited){try{$process.Kill($true)}catch{}}
  throw 'Docker command failed or exceeded its deadline.'
 } finally {$process.Dispose()}
}

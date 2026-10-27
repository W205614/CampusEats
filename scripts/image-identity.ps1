# docker image save on the supported CI engine emits a single-platform OCI layout.
# Verify the bytes, graph and tag mapping before interpreting either Docker ID form.
function Read-ReleaseImageGraph {
 param([Parameter(Mandatory)][string]$Archive,[Parameter(Mandatory)][string]$SourceSha)
 if($SourceSha -notmatch '^[a-f0-9]{40}$'){throw 'Invalid image source revision.'}
 $blobs=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 $roots=@{};$paths=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 $stream=[IO.File]::OpenRead([IO.Path]::GetFullPath($Archive));$reader=$null;$metadataBytes=0L
 try {
  $reader=[System.Formats.Tar.TarReader]::new($stream,$true)
  while($null -ne ($entry=$reader.GetNextEntry($false))){
   $name=$entry.Name
   if($name -match '(^/|\\|(^|/)\.\.?(/|$))' -or !$paths.Add($name)){throw 'Unsafe or duplicate image archive member.'}
   if($entry.EntryType -eq [System.Formats.Tar.TarEntryType]::Directory){continue}
   if($entry.EntryType -notin @([System.Formats.Tar.TarEntryType]::RegularFile,[System.Formats.Tar.TarEntryType]::V7RegularFile)){throw 'Unsupported image archive member type.'}
   if($paths.Count -gt 2000 -or $entry.Length -lt 0){throw 'Image archive inventory exceeds its supported bounds.'}
   if($name -match '^blobs/sha256/([a-f0-9]{64})$'){
    $digest='sha256:'+$Matches[1];$bytes=$null
    if($entry.Length -le 1MB){
     $metadataBytes+=$entry.Length;if($metadataBytes -gt 32MB){throw 'Image archive metadata exceeds its bound.'}
     $memory=[IO.MemoryStream]::new();try{$entry.DataStream.CopyTo($memory);$bytes=$memory.ToArray()}finally{$memory.Dispose()}
     $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
    }else{$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($entry.DataStream)).ToLowerInvariant()}
    if(('sha256:'+$hash) -cne $digest){throw 'Image blob bytes do not match their content digest.'}
    $blobs.Add($digest,[pscustomobject]@{size=$entry.Length;bytes=$bytes})
   }elseif($name -in @('index.json','manifest.json','oci-layout','repositories')){
    if($entry.Length -gt 1MB){throw 'Image archive root metadata exceeds its bound.'}
    $memory=[IO.MemoryStream]::new();try{$entry.DataStream.CopyTo($memory);$roots[$name]=[Text.UTF8Encoding]::new($false,$true).GetString($memory.ToArray()) | ConvertFrom-Json -Depth 50 -ErrorAction Stop}finally{$memory.Dispose()}
   }else{throw 'Unsupported image archive layout.'}
  }
 }finally{if($reader){$reader.Dispose()};$stream.Dispose()}
 if($roots['oci-layout'].imageLayoutVersion -cne '1.0.0' -or $roots['index.json'].schemaVersion -ne 2 -or @($roots['index.json'].manifests).Count -ne 2 -or @($roots['manifest.json']).Count -ne 2){throw 'Expected the supported two-image OCI save layout.'}
 $used=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);$images=@();$services=@{}
 foreach($descriptor in $roots['index.json'].manifests){
  if($descriptor.mediaType -cne 'application/vnd.oci.image.manifest.v1+json'){throw 'Only direct single-platform OCI manifests are supported.'}
  $reference=[string]$descriptor.annotations.'io.containerd.image.name'
  $reference=$reference -creplace '^docker.io/library/',''
  $service=if($reference -ceq ('campuseats-server:sha-'+$SourceSha)){'server'}elseif($reference -ceq ('campuseats-web:sha-'+$SourceSha)){'web'}else{throw 'Unexpected OCI image reference.'}
  if($services.ContainsKey($service)){throw 'Duplicate OCI application reference.'};$services[$service]=$true
  $manifest=Read-ReleaseGraphJson -Descriptor $descriptor -Blobs $blobs -Used $used
  if($manifest.schemaVersion -ne 2 -or $manifest.mediaType -cne 'application/vnd.oci.image.manifest.v1+json' -or $manifest.config.mediaType -cne 'application/vnd.oci.image.config.v1+json'){throw 'Unsupported platform image/config media type.'}
  $config=Read-ReleaseGraphJson -Descriptor $manifest.config -Blobs $blobs -Used $used
  if($config.os -cne 'linux' -or $config.architecture -cne 'amd64' -or $config.config.Labels.'org.opencontainers.image.revision' -cne $SourceSha -or $config.rootfs.type -cne 'layers'){throw 'OCI config differs from the release platform or source.'}
  $layers=@($manifest.layers);$diffIds=@($config.rootfs.diff_ids)
  if(!$layers.Count -or $layers.Count -gt 100 -or $layers.Count -ne $diffIds.Count){throw 'Invalid ordered image layer inventory.'}
  for($i=0;$i -lt $layers.Count;$i++){
   # Current CI saves raw tar layers. Unknown compression/conversion is rejected,
   # rather than weakening digest equality to accept another runtime representation.
   if($layers[$i].mediaType -cne 'application/vnd.oci.image.layer.v1.tar'){throw 'Unsupported image layer encoding.'}
   Assert-ReleaseGraphDescriptor -Descriptor $layers[$i] -Blobs $blobs -Used $used
   if($layers[$i].digest -cne $diffIds[$i]){throw 'Ordered layer content differs from config rootfs diff IDs.'}
  }
  $compat=@($roots['manifest.json'] | Where-Object {@($_.RepoTags).Count -eq 1 -and $_.RepoTags[0] -ceq $reference})
  $expectedPaths=@($layers | ForEach-Object {'blobs/sha256/'+$_.digest.Substring(7)})
  if($compat.Count -ne 1 -or $compat[0].Config -cne ('blobs/sha256/'+$manifest.config.digest.Substring(7)) -or (@($compat[0].Layers) -join ',') -cne ($expectedPaths -join ',')){throw 'Docker save tag/config/layer mapping differs from the OCI graph.'}
  if($roots.ContainsKey('repositories')){
   $repository='campuseats-'+$service;$tag='sha-'+$SourceSha;$legacy=$roots['repositories'].$repository
   if(@($roots['repositories'].PSObject.Properties).Count -ne 2 -or @($legacy.PSObject.Properties).Count -ne 1 -or $legacy.$tag -cne $layers[-1].digest.Substring(7)){throw 'Legacy repository tag mapping differs from the OCI graph.'}
  }
  $images+=[pscustomobject]@{service=$service;reference=$reference;revision=$SourceSha;configDigest=$manifest.config.digest;platformManifestDigest=$descriptor.digest;layerDigests=@($layers.digest);diffIds=$diffIds}
 }
 # docker save also carries legacy layer metadata for its Docker compatibility
 # manifest. These SHA-verified, bounded JSON blobs are auxiliary only: none can
 # supply a config, runtime identity or layer in the verified OCI graph above.
 $auxiliary=0
 foreach($digest in $blobs.Keys){
  if($used.Contains($digest)){continue}
  $blob=$blobs[$digest];$auxiliary++
  if($auxiliary -gt 200 -or $null -eq $blob.bytes -or $blob.size -gt 64KB){throw 'Unsupported auxiliary image blob.'}
  $legacy=[Text.UTF8Encoding]::new($false,$true).GetString($blob.bytes) | ConvertFrom-Json -Depth 50 -ErrorAction Stop
  $allowed=@('id','parent','created','container_config','config','architecture','os')
  if($legacy -isnot [pscustomobject] -or @($legacy.PSObject.Properties.Name | Where-Object {$_ -cnotin $allowed}).Count -or $legacy.id -cnotmatch '^[a-f0-9]{64}$' -or ($legacy.parent -and $legacy.parent -cnotmatch '^[a-f0-9]{64}$') -or $legacy.os -cne 'linux' -or $legacy.container_config -isnot [pscustomobject] -or ($legacy.architecture -and $legacy.architecture -cne 'amd64') -or ($legacy.config -and $legacy.config -isnot [pscustomobject])){throw 'Image archive contains an unknown auxiliary blob.'}
 }
 return $images
}

function Assert-ReleaseGraphDescriptor {
 param($Descriptor,$Blobs,$Used)
 if($Descriptor.digest -notmatch '^sha256:[a-f0-9]{64}$' -or !$Blobs.ContainsKey([string]$Descriptor.digest) -or $null -eq $Descriptor.size -or $Descriptor.size -lt 0 -or $Descriptor.size -ne $Blobs[[string]$Descriptor.digest].size){throw 'OCI descriptor digest or size does not match its verified blob.'}
 [void]$Used.Add([string]$Descriptor.digest)
}

function Read-ReleaseGraphJson {
 param($Descriptor,$Blobs,$Used)
 Assert-ReleaseGraphDescriptor -Descriptor $Descriptor -Blobs $Blobs -Used $Used
 $bytes=$Blobs[[string]$Descriptor.digest].bytes
 if($null -eq $bytes){throw 'OCI JSON metadata exceeds its supported bound.'}
 return [Text.UTF8Encoding]::new($false,$true).GetString($bytes) | ConvertFrom-Json -Depth 50 -ErrorAction Stop
}

function Resolve-ReleaseImageIdentity {
 param([Parameter(Mandatory)]$Expected,[Parameter(Mandatory)]$Actual)
 if($Actual.Id -cnotin @($Expected.configDigest,$Expected.platformManifestDigest) -or $Actual.Os -cne 'linux' -or $Actual.Architecture -cne 'amd64' -or $Actual.Config.Labels.'org.opencontainers.image.revision' -cne $Expected.revision -or $Actual.RootFS.Type -cne 'layers' -or (@($Actual.RootFS.Layers) -join ',') -cne (@($Expected.diffIds) -join ',')){throw 'Loaded image differs from the verified OCI config and ordered layer content.'}
 if($Actual.Descriptor -and $Actual.Descriptor.digest -cne $Expected.platformManifestDigest){throw 'Loaded platform descriptor differs from the verified OCI manifest.'}
 return [pscustomobject]@{service=$Expected.service;reference=$Expected.reference;sourceRuntimeId=$Expected.sourceRuntimeId;runtimeId=$Actual.Id;configDigest=$Expected.configDigest;platformManifestDigest=$Expected.platformManifestDigest;layerDigests=@($Expected.layerDigests);diffIds=@($Expected.diffIds);revision=$Expected.revision}
}

function Assert-ReleaseContainerImage {
 param([Parameter(Mandatory)][string]$ContainerId,[Parameter(Mandatory)]$Image)
 $raw=(& docker inspect --format '{{json .}}' $ContainerId) -join "`n"
 if($LASTEXITCODE -ne 0){throw 'Cannot inspect deployed image identity.'}
 $actual=$raw | ConvertFrom-Json -ErrorAction Stop
 if($actual.Config.Image -cne $Image.runtimeId -or $actual.Image -cnotin @($Image.configDigest,$Image.platformManifestDigest)){throw 'Container does not bind the resolved verified image identity.'}
}

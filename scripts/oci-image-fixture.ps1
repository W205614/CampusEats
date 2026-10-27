# Tiny, real OCI save archives for delivery checks; no Docker daemon is involved.
function New-ReleaseOciFixture {
 param([Parameter(Mandatory)][string]$Path,[string]$SourceSha=('a'*40),[string]$Fault='')
 $utf8=[Text.UTF8Encoding]::new($false);$members=[ordered]@{};$images=@();$descriptors=@();$compat=@();$repositories=@{}
 foreach($service in @('server','web')){
  $reference='campuseats-'+$service+':sha-'+$SourceSha
  $layerStream=[IO.MemoryStream]::new();$layerWriter=[System.Formats.Tar.TarWriter]::new($layerStream,$true)
  try{
   foreach($position in @(1,2)){
    $entry=[System.Formats.Tar.PaxTarEntry]::new([System.Formats.Tar.TarEntryType]::RegularFile,($service+$position+'.txt'))
    $entry.DataStream=[IO.MemoryStream]::new($utf8.GetBytes($service+'-'+$position));try{$layerWriter.WriteEntry($entry)}finally{$entry.DataStream.Dispose()}
   }
  }finally{$layerWriter.Dispose()}
  $layerBytes=$layerStream.ToArray();$layerStream.Dispose()
  $layerDigest='sha256:'+([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($layerBytes)).ToLowerInvariant())
  $members['blobs/sha256/'+$layerDigest.Substring(7)]=$layerBytes
  $config=@{os='linux';architecture='amd64';config=@{Labels=@{'org.opencontainers.image.revision'=$SourceSha}};rootfs=@{type='layers';diff_ids=@($layerDigest)}}
  if($Fault -eq 'diff-id' -and $service -eq 'server'){$config.rootfs.diff_ids=@('sha256:'+('d'*64))}
  $configBytes=$utf8.GetBytes(($config | ConvertTo-Json -Depth 10 -Compress));$configDigest='sha256:'+([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($configBytes)).ToLowerInvariant())
  $members['blobs/sha256/'+$configDigest.Substring(7)]=$configBytes
  $platform=@{schemaVersion=2;mediaType='application/vnd.oci.image.manifest.v1+json';config=@{mediaType='application/vnd.oci.image.config.v1+json';digest=$configDigest;size=$configBytes.Length};layers=@(@{mediaType='application/vnd.oci.image.layer.v1.tar';digest=$layerDigest;size=$layerBytes.Length})}
  if($Fault -eq 'layer-size' -and $service -eq 'server'){$platform.layers[0].size++}
  $platformBytes=$utf8.GetBytes(($platform | ConvertTo-Json -Depth 10 -Compress));$platformDigest='sha256:'+([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($platformBytes)).ToLowerInvariant())
  $members['blobs/sha256/'+$platformDigest.Substring(7)]=$platformBytes
  $descriptors+=@{mediaType='application/vnd.oci.image.manifest.v1+json';digest=$platformDigest;size=$platformBytes.Length;annotations=@{'io.containerd.image.name'=('docker.io/library/'+$reference);'org.opencontainers.image.ref.name'=('sha-'+$SourceSha)}}
  $compat+=@{Config=('blobs/sha256/'+$configDigest.Substring(7));RepoTags=@($reference);Layers=@('blobs/sha256/'+$layerDigest.Substring(7))}
  $repositories['campuseats-'+$service]=@{('sha-'+$SourceSha)=$layerDigest.Substring(7)}
  $images+=[pscustomobject]@{service=$service;reference=$reference;revision=$SourceSha;sourceRuntimeId=$configDigest;configDigest=$configDigest;platformManifestDigest=$platformDigest;layerDigests=@($layerDigest);diffIds=@($layerDigest)}
  if($Fault -eq 'layer-bytes' -and $service -eq 'server'){$members['blobs/sha256/'+$layerDigest.Substring(7)]=$utf8.GetBytes('replaced layer')}
  if($Fault -eq 'config-bytes' -and $service -eq 'server'){$members['blobs/sha256/'+$configDigest.Substring(7)]=$utf8.GetBytes('{}')}
 }
 # Legacy docker-save metadata is content-addressed but not part of the OCI DAG.
 $legacy=@{id=('c'*64);created='1970-01-01T00:00:00Z';container_config=@{};os='linux'}
 if($Fault -eq 'auxiliary-unknown'){$legacy['rootfs']=@{type='layers'}}
 if($Fault -eq 'auxiliary-id'){$legacy.id='not-an-id'}
 $legacyBytes=$utf8.GetBytes(($legacy | ConvertTo-Json -Depth 10 -Compress))
 $legacyDigest=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($legacyBytes)).ToLowerInvariant()
 $members['blobs/sha256/'+$legacyDigest]=$legacyBytes
 if($Fault -eq 'auxiliary-bytes'){$members['blobs/sha256/'+$legacyDigest]=$utf8.GetBytes('{}')}
 if($Fault -eq 'mapping'){$descriptors[0].annotations.'io.containerd.image.name'='docker.io/library/'+$images[1].reference;$descriptors[1].annotations.'io.containerd.image.name'='docker.io/library/'+$images[0].reference}
 $members['oci-layout']=$utf8.GetBytes('{"imageLayoutVersion":"1.0.0"}')
 $members['index.json']=$utf8.GetBytes((@{schemaVersion=2;mediaType='application/vnd.oci.image.index.v1+json';manifests=$descriptors} | ConvertTo-Json -Depth 10 -Compress))
 $members['manifest.json']=$utf8.GetBytes(($compat | ConvertTo-Json -Depth 10 -Compress -AsArray))
 $members['repositories']=$utf8.GetBytes(($repositories | ConvertTo-Json -Depth 10 -Compress))
 $stream=[IO.File]::Create($Path);$writer=[System.Formats.Tar.TarWriter]::new($stream,$true)
 try{foreach($name in $members.Keys){$entry=[System.Formats.Tar.PaxTarEntry]::new([System.Formats.Tar.TarEntryType]::RegularFile,$name);$entry.DataStream=[IO.MemoryStream]::new([byte[]]$members[$name]);try{$writer.WriteEntry($entry)}finally{$entry.DataStream.Dispose()}}}finally{$writer.Dispose();$stream.Dispose()}
 return $images
}

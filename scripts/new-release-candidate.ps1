#Requires -Version 7.0
[CmdletBinding()]
param([ValidateSet('Create','Verify')][string]$Command='Create',[string]$CandidatePath,[switch]$Library)
$ErrorActionPreference='Stop'
$script:ReleaseRepo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:ReleaseBase=Join-Path $script:ReleaseRepo 'bootstrap/target/release-candidates'
function Assert-Release($Condition,[string]$Reason) {if(!$Condition){throw $Reason}}
function Get-ReleaseHash([byte[]]$Bytes) {[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes))}
function Get-ReleaseFileHash([string]$Path) {Get-ReleaseHash ([IO.File]::ReadAllBytes($Path))}
function Write-ReleaseJson([string]$Path,$Value) {[IO.File]::WriteAllText($Path,($Value|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))}
function Invoke-ReleaseGit([string[]]$Arguments) {
    $info=[Diagnostics.ProcessStartInfo]::new('git');$info.WorkingDirectory=$script:ReleaseRepo
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.StandardOutputEncoding=[Text.Encoding]::UTF8;$info.StandardErrorEncoding=[Text.Encoding]::UTF8
    foreach($arg in @('-c','core.excludesFile=','-c','core.quotepath=false')+$Arguments){$info.ArgumentList.Add($arg)}
    $process=[Diagnostics.Process]::Start($info)
    try {$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync();$process.WaitForExit()
        $value=$stdout.GetAwaiter().GetResult();$null=$stderr.GetAwaiter().GetResult()
        Assert-Release ($process.ExitCode -eq 0) 'GIT_COMMAND_FAILED';return $value
    } finally {$process.Dispose()}
}
function Assert-ReleaseRelativePath([string]$Path) {
    Assert-Release (![string]::IsNullOrWhiteSpace($Path) -and $Path -notmatch '[\\:*?"<>|\x00-\x1f]' -and !$Path.StartsWith('/') -and
        @($Path.Split('/')|Where-Object {$_ -in @('','..','.') -or $_ -match '[. ]$|^(?i:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?$'}).Count -eq 0) 'UNSAFE_MEMBER_PATH'
}
function Test-ReleaseSourcePath([string]$Path) {
    Assert-ReleaseRelativePath $Path
    $root=$Path.Split('/')[0]
    $roots=@('agent','bootstrap','framework','infra-ai','system','incident','rag','mcp-server','frontend','resources','assets','scripts','docs','specs','.agents','.specify','.github','.mvn')
    $rootFiles=@('pom.xml','mvnw','mvnw.cmd','LICENSE','NOTICE','README.md','AGENTS.md','CLAUDE.md','.gitignore','.gitattributes','.dockerignore','lombok.config')
    if($root -notin $roots -and $Path -notin $rootFiles){return $false}
    if($Path -match '(?i)(^|/)(target|node_modules|dist|build|\.git|\.idea|\.vscode|\.claude|\.output|\.vite|logs|volumes|temp|tmp)(/|$)'){return $false}
    if($Path -match '(?i)(^|/)(credentials[^/]*|settings\.local[^/]*|settings\.xml|app\.properties|mcp\.properties)$' -or
        $Path -match '(?i)\.(log|db|sqlite3?|pem|key|p12|pfx|jks|keystore|class|pyc|zip|gz|bundle|jar|env)$' -and $Path -cne 'frontend/.env'){return $false}
    if($Path -match '(?i)(^|/)\.env' -and $Path -notin @('frontend/.env','resources/docker/graphrag/.env.example')){return $false}
    return $true
}
function Assert-ReleaseNoLink([string]$Path) {
    $item=Get-Item -LiteralPath $Path -Force
    while($item){
        Assert-Release (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'SOURCE_LINK_REJECTED'
        $item=if($item -is [IO.FileInfo]){$item.Directory}else{$item.Parent}
    }
}
function Assert-ReleaseContent([byte[]]$Bytes) {
    $content=[Text.Encoding]::UTF8.GetString($Bytes)
    Assert-Release ($content -notmatch '-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----|\bAKIA[A-Z0-9]{16}\b|\bgh[pousr]_[A-Za-z0-9]{30,}\b|\bgithub_pat_[A-Za-z0-9_]{40,}\b|\bsk-(?:proj-)?[A-Za-z0-9_-]{24,}') 'SECRET_PATTERN_REJECTED'
}
function Get-ReleaseRequiredPaths {
    @('LICENSE','NOTICE','README.md','pom.xml','mvnw','mvnw.cmd','frontend/package.json','frontend/package-lock.json','frontend/.env',
        'scripts/local-demo.ps1','scripts/new-release-candidate.ps1','resources/local-demo/compose.yaml',
        'resources/database/schema_pg.sql','docs/releases/configuration-boundary.md')
    foreach($module in @('framework','infra-ai','system','incident','rag','agent','bootstrap','mcp-server')){"$module/pom.xml"}
}
function New-ReleaseDirectory([string]$Directory) {
    Assert-Release ([IO.Path]::GetFullPath($Directory).StartsWith($script:ReleaseBase+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) 'OUTPUT_OUTSIDE_ROOT'
    $ancestor=Split-Path $Directory -Parent
    while(!(Test-Path -LiteralPath $ancestor)){$ancestor=Split-Path $ancestor -Parent}
    Assert-ReleaseNoLink $ancestor
    Assert-Release (!(Test-Path -LiteralPath $Directory)) 'CANDIDATE_EXISTS'
    $null=New-Item -ItemType Directory -Path $Directory
}
function Assert-ReleaseSourcesUnchanged($Records) {
    foreach($record in $Records){
        $absolute=Join-Path $script:ReleaseRepo $record.path;Assert-ReleaseNoLink $absolute
        Assert-Release ((Get-ReleaseFileHash $absolute) -ceq $record.sha256) 'SOURCE_CHANGED_DURING_COPY'
    }
}
function Assert-ReleaseSourceList([string[]]$Before,[string[]]$After) {
    Assert-Release (($Before -join "`0") -ceq ($After -join "`0")) 'SOURCE_LIST_CHANGED_DURING_COPY'
}
function Get-ReleaseSources {
    $root=(Invoke-ReleaseGit @('rev-parse','--show-toplevel')).Trim()
    Assert-Release ([IO.Path]::GetFullPath($root) -eq $script:ReleaseRepo) 'WRONG_GIT_ROOT'
    $paths=(Invoke-ReleaseGit @('ls-files','--cached','--others','--exclude-standard','-z')).Split([char]0,[StringSplitOptions]::RemoveEmptyEntries)
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $included=@(foreach($path in $paths){
        Assert-ReleaseRelativePath $path
        if(Test-ReleaseSourcePath $path){
            Assert-Release ($seen.Add($path)) 'SOURCE_PATH_COLLISION'
            $absolute=Join-Path $script:ReleaseRepo $path
            Assert-Release (Test-Path -LiteralPath $absolute -PathType Leaf) 'SOURCE_MISSING'
            Assert-ReleaseNoLink $absolute
            $path
        }
    })
    foreach($required in @(Get-ReleaseRequiredPaths)){
        Assert-Release ($required -cin $included) 'REQUIRED_SOURCE_MISSING'
    }
    return @($included|Sort-Object -CaseSensitive)
}
function Assert-ReleaseVersion {
    $settings=[Xml.XmlReaderSettings]::new();$settings.DtdProcessing=[Xml.DtdProcessing]::Prohibit;$settings.XmlResolver=$null
    foreach($module in @('','framework','infra-ai','system','incident','rag','agent','bootstrap','mcp-server')){
        $path=Join-Path $script:ReleaseRepo $(if($module){"$module/pom.xml"}else{'pom.xml'})
        $reader=[Xml.XmlReader]::Create($path,$settings)
        try {$xml=[Xml.XmlDocument]::new();$xml.Load($reader)}finally{$reader.Dispose()}
        $version=if($module){[string]$xml.DocumentElement.parent.version}else{[string]$xml.DocumentElement.version}
        Assert-Release ($version -ceq '0.1.0') 'VERSION_MISMATCH'
    }
    foreach($path in @('frontend/package.json','frontend/package-lock.json')){
        $json=Get-Content -LiteralPath (Join-Path $script:ReleaseRepo $path) -Raw|ConvertFrom-Json -AsHashtable
        Assert-Release ($json.version -ceq '0.1.0') 'FRONTEND_VERSION_MISMATCH'
        if($path -eq 'frontend/package-lock.json'){Assert-Release ($json.packages[''].version -ceq '0.1.0') 'FRONTEND_LOCK_VERSION_MISMATCH'}
    }
}
function New-ReleaseCandidate {
    Assert-ReleaseVersion
    $paths=@(Get-ReleaseSources)
    $head=(Invoke-ReleaseGit @('rev-parse','HEAD')).Trim();$branch=(Invoke-ReleaseGit @('branch','--show-current')).Trim()
    Assert-Release ($head -match '^[a-f0-9]{40}$' -and $branch) 'GIT_IDENTITY_INVALID'
    $status=Invoke-ReleaseGit @('status','--porcelain','-z')
    $id=[guid]::NewGuid().ToString();$directory=Join-Path $script:ReleaseBase $id
    New-ReleaseDirectory $directory
    $records=[Collections.Generic.List[object]]::new()
    try {
        $zip=[IO.Compression.ZipFile]::Open((Join-Path $directory 'source.zip'),[IO.Compression.ZipArchiveMode]::Create)
        try {foreach($path in $paths){
            $absolute=Join-Path $script:ReleaseRepo $path;Assert-ReleaseNoLink $absolute
            $bytes=[IO.File]::ReadAllBytes($absolute);Assert-ReleaseContent $bytes
            $records.Add([ordered]@{path=$path;sha256=(Get-ReleaseHash $bytes);bytes=$bytes.LongLength})
            $entry=$zip.CreateEntry($path,[IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime=[DateTimeOffset]::new(1980,1,1,0,0,0,[TimeSpan]::Zero)
            $stream=$entry.Open();try{$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
        }}finally{$zip.Dispose()}
        Assert-ReleaseSourcesUnchanged $records
        Assert-ReleaseSourceList $paths @(Get-ReleaseSources)
        Assert-Release ((Invoke-ReleaseGit @('rev-parse','HEAD')).Trim() -ceq $head) 'HEAD_CHANGED'
        Assert-Release ((Invoke-ReleaseGit @('status','--porcelain','-z')) -ceq $status) 'WORKTREE_CHANGED_DURING_COPY'
        $manifest=[ordered]@{schemaVersion=1;version='0.1.0';files=@($records.ToArray())}
        Write-ReleaseJson (Join-Path $directory 'source-manifest.json') $manifest
        $metadata=[ordered]@{schemaVersion=1;candidateId=$id;version='0.1.0';state='SOURCE_CANDIDATE_READY';
            createdAt=[datetime]::UtcNow.ToString('o');baselineHead=$head;baselineBranch=$branch;
            upstream='https://github.com/nageoffer/ragent';license='Apache-2.0';hasUncommittedChanges=[bool]$status;
            sourceManifestSha256=(Get-ReleaseFileHash (Join-Path $directory 'source-manifest.json'));
            fileCount=$records.Count;history='WITHHELD_POTENTIAL_HISTORICAL_SECRETS';historyPreservedInOriginalRepository=$true;
            secretCheck='RECOGNIZED_CURRENT_SOURCE_PATTERNS_NOT_SECRET_SCAN_CERTIFICATION';
            validation='SOURCE_CANDIDATE_ONLY_NOT_RELEASED_OR_CLEAN_ENVIRONMENT_ACCEPTANCE'}
        Write-ReleaseJson (Join-Path $directory 'candidate.json') $metadata
        $sums=@(foreach($file in @('source.zip','source-manifest.json','candidate.json')){"$(Get-ReleaseFileHash (Join-Path $directory $file))  $file"})
        [IO.File]::WriteAllLines((Join-Path $directory 'SHA256SUMS'),$sums,[Text.UTF8Encoding]::new($false))
        Test-ReleaseCandidate $directory
    } catch {
        Write-ReleaseJson (Join-Path $directory 'candidate.json') @{schemaVersion=1;candidateId=$id;state='FAIL';reason='CANDIDATE_INCOMPLETE'}
        throw
    }
}
function Test-ReleaseCandidate([string]$Directory) {
    $directory=[IO.Path]::GetFullPath($Directory);Assert-ReleaseNoLink $directory
    $expected=@('source.zip','source-manifest.json','candidate.json','SHA256SUMS')
    $actual=@(Get-ChildItem -LiteralPath $directory -Force)
    Assert-Release ($actual.Count -eq 4 -and @($actual|Where-Object {$_.PSIsContainer -or $_.Name -cnotin $expected}).Count -eq 0) 'CANDIDATE_FILES_INVALID'
    foreach($file in $actual){Assert-ReleaseNoLink $file.FullName}
    $sums=@(Get-Content -LiteralPath (Join-Path $directory 'SHA256SUMS'))
    Assert-Release ($sums.Count -eq 3) 'CHECKSUM_LIST_INVALID'
    $checked=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($line in $sums){
        Assert-Release ($line -cmatch '^([A-F0-9]{64})  (source\.zip|source-manifest\.json|candidate\.json)$') 'CHECKSUM_LINE_INVALID'
        $digest=$Matches[1];$name=$Matches[2]
        Assert-Release ($checked.Add($name)) 'DUPLICATE_CHECKSUM'
        Assert-Release ((Get-ReleaseFileHash (Join-Path $directory $name)) -ceq $digest) 'ARTIFACT_HASH_MISMATCH'
    }
    $metadata=Get-Content -LiteralPath (Join-Path $directory 'candidate.json') -Raw|ConvertFrom-Json
    $manifest=Get-Content -LiteralPath (Join-Path $directory 'source-manifest.json') -Raw|ConvertFrom-Json
    Assert-Release ($metadata.schemaVersion -eq 1 -and $manifest.schemaVersion -eq 1 -and $metadata.version -ceq '0.1.0' -and
        $manifest.version -ceq '0.1.0' -and $metadata.state -ceq 'SOURCE_CANDIDATE_READY' -and
        $metadata.history -ceq 'WITHHELD_POTENTIAL_HISTORICAL_SECRETS' -and $metadata.historyPreservedInOriginalRepository -eq $true -and
        $metadata.candidateId -match '^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$' -and $metadata.baselineHead -match '^[a-f0-9]{40}$' -and
        $metadata.upstream -ceq 'https://github.com/nageoffer/ragent' -and $metadata.license -ceq 'Apache-2.0' -and
        ![string]::IsNullOrWhiteSpace($metadata.baselineBranch)) 'CANDIDATE_IDENTITY_INVALID'
    Assert-Release ($metadata.sourceManifestSha256 -ceq (Get-ReleaseFileHash (Join-Path $directory 'source-manifest.json'))) 'MANIFEST_IDENTITY_MISMATCH'
    $members=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($file in @($manifest.files)){
        Assert-Release (Test-ReleaseSourcePath $file.path) 'FORBIDDEN_MANIFEST_PATH'
        Assert-Release (!$members.ContainsKey($file.path)) 'MANIFEST_PATH_COLLISION'
        Assert-Release ($file.sha256 -cmatch '^[A-F0-9]{64}$' -and $file.bytes -ge 0 -and $file.bytes -le 67108864) 'MANIFEST_FILE_INVALID'
        $members.Add($file.path,$file)
    }
    Assert-Release ($members.Count -gt 0 -and $members.Count -eq $metadata.fileCount) 'MANIFEST_COUNT_INVALID'
    foreach($required in @(Get-ReleaseRequiredPaths)){Assert-Release ($members.ContainsKey($required) -and $members[$required].path -ceq $required) 'REQUIRED_MEMBER_MISSING'}
    $zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $directory 'source.zip'))
    try {
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach($entry in $zip.Entries){
            Assert-ReleaseRelativePath $entry.FullName
            Assert-Release ((Test-ReleaseSourcePath $entry.FullName) -and $seen.Add($entry.FullName) -and $members.ContainsKey($entry.FullName)) 'ZIP_MEMBER_INVALID'
            $record=$members[$entry.FullName]
            Assert-Release ($record.path -ceq $entry.FullName -and $entry.Length -eq $record.bytes -and
                (($entry.ExternalAttributes -shr 16) -band 61440) -ne 40960) 'ZIP_MEMBER_METADATA_INVALID'
            $stream=$entry.Open();$memory=[IO.MemoryStream]::new()
            try{$stream.CopyTo($memory);$bytes=$memory.ToArray()}finally{$stream.Dispose();$memory.Dispose()}
            Assert-Release ((Get-ReleaseHash $bytes) -ceq $record.sha256) 'ZIP_CONTENT_MISMATCH'
            Assert-ReleaseContent $bytes
        }
        Assert-Release ($seen.Count -eq $members.Count) 'ZIP_MEMBER_MISSING'
    } finally {$zip.Dispose()}
    [ordered]@{state='SOURCE_VERIFIED_HISTORY_WITHHELD';candidateId=$metadata.candidateId;version=$metadata.version;files=$members.Count;
        baselineHead=$metadata.baselineHead;manifestSha256=$metadata.sourceManifestSha256;directory=$directory}
}
if(!$Library){
    try {
        if($Command -eq 'Create'){Assert-Release (!$CandidatePath) 'CREATE_PATH_NOT_SUPPORTED';$result=New-ReleaseCandidate}
        else{Assert-Release ([bool]$CandidatePath) 'CANDIDATE_PATH_REQUIRED';$result=Test-ReleaseCandidate $CandidatePath}
        $result|ConvertTo-Json -Compress;exit 0
    }catch{[ordered]@{state='FAIL';reason='CANDIDATE_CHECK_FAILED'}|ConvertTo-Json -Compress;exit 1}
}

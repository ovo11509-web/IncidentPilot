#Requires -Version 7.0
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'new-release-candidate.ps1') -Library
$checks=0
function Check($Condition,[string]$Name){if(!$Condition){throw $Name};$script:checks++}
function Rejected([scriptblock]$Code,[string]$Name){$rejected=$false;try{& $Code|Out-Null}catch{$rejected=$true};Check $rejected $Name}
foreach($path in @('incident/src/main/java/NewUntracked.java','docs/中文说明.md','frontend/.env','resources/docker/graphrag/.env.example','NOTICE')){
    Check (Test-ReleaseSourcePath $path) 'VALID_SOURCE_PATH'
}
foreach($path in @('bootstrap/target/local-demo/default/credentials.json','frontend/node_modules/pkg.js','frontend/.env.local',
    '.claude/settings.local.json','.idea/compiler.xml','resources/private.key','resources/data.sqlite','docs/run.log','unreviewed-root/file.txt',
    'resources/credentials-private.json','.mvn/settings.xml','resources/local-demo/compose.env','resources/local-demo/app.properties','resources/private.pfx')){
    Check (!(Test-ReleaseSourcePath $path)) 'FORBIDDEN_SOURCE_PATH'
}
foreach($path in @('../escape','/absolute','C:/drive','docs/../escape','docs\escape','docs//empty','docs/CON.txt','docs/trailing.','docs/file?.txt')){
    Rejected {Assert-ReleaseRelativePath $path} 'UNSAFE_PATH_REJECTED'
}
$privateMarker='-----BEGIN '+'PRIVATE KEY-----'
Rejected {Assert-ReleaseContent ([Text.Encoding]::UTF8.GetBytes($privateMarker))} 'PRIVATE_KEY_REJECTED'
Rejected {Assert-ReleaseContent ([Text.Encoding]::UTF8.GetBytes(('-----BEGIN '+'ENCRYPTED PRIVATE KEY-----')))} 'ENCRYPTED_KEY_REJECTED'
Rejected {Assert-ReleaseContent ([Text.Encoding]::UTF8.GetBytes(('sk-' + ('a1b2c3d4'*5))))} 'TOKEN_REJECTED'
Assert-ReleaseContent ([Text.Encoding]::UTF8.GetBytes('password: ${LOCAL_PASSWORD:}'))
Check $true 'ENV_REFERENCE_ALLOWED'
Assert-ReleaseVersion;Check $true 'REAL_POM_AND_LOCK_VERSIONS'
$root=Join-Path $script:ReleaseBase ('contract-'+[guid]::NewGuid())
$null=New-Item -ItemType Directory -Path $root
function New-Fixture([string]$Name,[object[]]$Files=@(@{path='docs/controlled.txt';text='controlled content'})){
    $dir=Join-Path $root $Name;$null=New-Item -ItemType Directory -Path $dir
    $zip=[IO.Compression.ZipFile]::Open((Join-Path $dir 'source.zip'),[IO.Compression.ZipArchiveMode]::Create)
    $records=@()
    $fixtureFiles=@(foreach($required in @(Get-ReleaseRequiredPaths)){@{path=$required;text='controlled content'}})+$Files
    try{foreach($f in $fixtureFiles){$bytes=[Text.Encoding]::UTF8.GetBytes($f.text);$entry=$zip.CreateEntry($f.path);$stream=$entry.Open()
        try{$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
        $records+=@{path=$f.path;bytes=$bytes.Length;sha256=(Get-ReleaseHash $bytes)}
    }}finally{$zip.Dispose()}
    Write-ReleaseJson (Join-Path $dir 'source-manifest.json') @{schemaVersion=1;version='0.1.0';files=$records}
    Write-ReleaseJson (Join-Path $dir 'candidate.json') @{schemaVersion=1;version='0.1.0';state='SOURCE_CANDIDATE_READY';
        candidateId=[guid]::NewGuid().ToString();baselineHead=('a'*40);baselineBranch='feature/incident-platform';
        upstream='https://github.com/nageoffer/ragent';license='Apache-2.0';
        sourceManifestSha256=(Get-ReleaseFileHash (Join-Path $dir 'source-manifest.json'));fileCount=$records.Count;
        history='WITHHELD_POTENTIAL_HISTORICAL_SECRETS';historyPreservedInOriginalRepository=$true}
    Update-Sums $dir;return $dir
}
function Update-Sums([string]$Directory){
    [IO.File]::WriteAllLines((Join-Path $Directory 'SHA256SUMS'),[string[]]@(foreach($file in @('source.zip','source-manifest.json','candidate.json')){
        "$(Get-ReleaseFileHash (Join-Path $Directory $file))  $file"
    }))
}
$good=New-Fixture 'good'
Check ((Test-ReleaseCandidate $good).state -eq 'SOURCE_VERIFIED_HISTORY_WITHHELD') 'VALID_CANDIDATE'
$tampered=New-Fixture 'tampered';[IO.File]::AppendAllText((Join-Path $tampered 'source.zip'),'extra')
Rejected {Test-ReleaseCandidate $tampered} 'ARTIFACT_TAMPER_REJECTED'
$extra=New-Fixture 'extra';$zip=[IO.Compression.ZipFile]::Open((Join-Path $extra 'source.zip'),[IO.Compression.ZipArchiveMode]::Update)
try{$null=$zip.CreateEntry('docs/extra.txt')}finally{$zip.Dispose()};Update-Sums $extra
Rejected {Test-ReleaseCandidate $extra} 'EXTRA_MEMBER_REJECTED'
$missing=New-Fixture 'missing';$zip=[IO.Compression.ZipFile]::Open((Join-Path $missing 'source.zip'),[IO.Compression.ZipArchiveMode]::Update)
try{$zip.GetEntry('README.md').Delete()}finally{$zip.Dispose()};Update-Sums $missing
Rejected {Test-ReleaseCandidate $missing} 'MISSING_MEMBER_REJECTED'
$content=New-Fixture 'content';$zip=[IO.Compression.ZipFile]::Open((Join-Path $content 'source.zip'),[IO.Compression.ZipArchiveMode]::Update)
try{$entry=$zip.GetEntry('README.md');$entry.Delete();$stream=$zip.CreateEntry('README.md').Open();try{$bytes=[Text.Encoding]::UTF8.GetBytes('tampered content!!');$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}}finally{$zip.Dispose()};Update-Sums $content
Rejected {Test-ReleaseCandidate $content} 'MEMBER_CONTENT_REJECTED'
$collision=New-Fixture 'collision' @(@{path='README.md';text='a'},@{path='readme.md';text='b'})
Rejected {Test-ReleaseCandidate $collision} 'CASE_COLLISION_REJECTED'
$traversal=New-Fixture 'traversal' @(@{path='../outside';text='a'})
Rejected {Test-ReleaseCandidate $traversal} 'ZIP_TRAVERSAL_REJECTED'
$history=New-Fixture 'history';[IO.File]::WriteAllText((Join-Path $history 'history.bundle'),'not distributable')
Rejected {Test-ReleaseCandidate $history} 'HISTORY_ATTACHMENT_REJECTED'
$identity=New-Fixture 'identity';$value=Get-Content (Join-Path $identity 'candidate.json') -Raw|ConvertFrom-Json;$value.version='9.9.9';Write-ReleaseJson (Join-Path $identity 'candidate.json') $value;Update-Sums $identity
Rejected {Test-ReleaseCandidate $identity} 'VERSION_IDENTITY_REJECTED'
$link=Join-Path $root 'linked-source';$null=New-Item -ItemType Junction -Path $link -Target $good
Rejected {Assert-ReleaseNoLink (Join-Path $link 'candidate.json')} 'ANCESTOR_LINK_REJECTED'
Rejected {New-ReleaseDirectory $good} 'EXISTING_OUTPUT_REJECTED'
Rejected {New-ReleaseDirectory (Join-Path $script:ReleaseRepo 'outside-candidate')} 'OUTPUT_OUTSIDE_REJECTED'
$copy=Join-Path $root 'copy.txt';[IO.File]::WriteAllText($copy,'before')
$savedRepo=$script:ReleaseRepo
try{
    $script:ReleaseRepo=$root;$record=@{path='copy.txt';sha256=(Get-ReleaseFileHash $copy)}
    Assert-ReleaseSourcesUnchanged @($record);Check $true 'STABLE_COPY'
    [IO.File]::WriteAllText($copy,'after');Rejected {Assert-ReleaseSourcesUnchanged @($record)} 'SOURCE_DRIFT_REJECTED'
}finally{$script:ReleaseRepo=$savedRepo}
Assert-ReleaseSourceList @('incident/a.java') @('incident/a.java');Check $true 'STABLE_PATH_SET'
Rejected {Assert-ReleaseSourceList @('incident/a.java') @('incident/a.java','incident/new.java')} 'UNTRACKED_DIRECTORY_ADDITION_REJECTED'
Write-Output "CANDIDATE_CONTRACT_PASS $checks"

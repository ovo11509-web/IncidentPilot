#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-demo.ps1') -Library
$checks = 0
function Check($Condition, $Name) { if (!$Condition) { throw $Name }; $script:checks++ }
function Rejected([scriptblock]$Code, $Name) { $rejected = $false; try { & $Code } catch { $rejected = $true }; Check $rejected $Name }
Check ((New-DemoSecret).Length -eq 48) 'SECRET_LENGTH'
$previous=[Environment]::GetEnvironmentVariable('SPRING_DATASOURCE_URL')
try {
    [Environment]::SetEnvironmentVariable('SPRING_DATASOURCE_URL','jdbc:postgresql://unapproved.invalid/database')
    $child=Get-DemoChildEnvironment @{INCIDENT_MCP_OUTBOUND_TOKEN='controlled-test-token'}
    Check ($child.ContainsKey('SPRING_DATASOURCE_URL') -and $null -eq $child.SPRING_DATASOURCE_URL -and $child.INCIDENT_MCP_OUTBOUND_TOKEN -eq 'controlled-test-token') 'CHILD_CONFIG_OVERRIDES_CLOSED'
} finally {[Environment]::SetEnvironmentVariable('SPRING_DATASOURCE_URL',$previous)}
Rejected {Assert-DemoCredentials @{username='local-admin';storageUser='local0000000000000000';password="';DROP TABLE t_user;--"}} 'CREDENTIAL_SQL_REJECTED'
$state = Join-Path $script:DemoBase ('test-' + [Guid]::NewGuid().ToString('N'))
Assert-DemoPath $state
Rejected { Assert-DemoPath (Join-Path $script:DemoRepo 'outside.txt') } 'OUTSIDE_REJECTED'
Rejected { Assert-DemoPath (Join-Path $script:DemoBase '../outside.txt') } 'TRAVERSAL_REJECTED'
Rejected { Assert-DemoManifest @{instanceId='bad'; project='other'; directory=$state} } 'OWNER_REJECTED'
Check (!(Test-DemoProcess @{pid=2147483647; startTimeUtc='2000-01-01T00:00:00Z'; marker='other'})) 'MISSING_PID_NOT_OWNED'
$source = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'local-demo.ps1') -Raw
Check ($source -notmatch 'down[^\r\n]*--volumes|docker\s+prune|Remove-Item.+Recurse|DROP\s+DATABASE') 'NO_DESTROY'
Check ($source -match 'StartTime' -and $source -match 'CommandLine' -and $source -match 'incidentpilot.local.instance') 'OWNERSHIP_FIELDS'
Check ($source -match 'initialized.json' -and $source -match 'SCHEMA_CHANGED') 'INIT_MARKER_AND_DRIFT'
$storageContractDirectory=Join-Path $state 'storage-contract'
$null=New-Item -ItemType Directory -Path $storageContractDirectory -Force
Copy-Item -LiteralPath (Join-Path $script:DemoRepo 'resources/local-demo/compose.yaml') -Destination (Join-Path $storageContractDirectory 'compose.yaml')
@('MAIN_IMAGE=controlled-main','SIM_IMAGE=controlled-simulation','REDIS_IMAGE=controlled-redis','STORAGE_IMAGE=controlled-storage',
  'INSTANCE_ID=controlled-volume-owner','MAIN_PASSWORD=controlled','SIM_PASSWORD=controlled','REDIS_PASSWORD=controlled',
  'STORAGE_USER=controlled','STORAGE_PASSWORD=controlled','MAIN_PORT=31111','SIM_PORT=31112','REDIS_PORT=31113','STORAGE_PORT=31114') |
    Set-Content -LiteralPath (Join-Path $storageContractDirectory 'compose.env') -Encoding utf8NoBOM
$storageConfig=Invoke-DemoCompose @{project='controlled-storage-contract';directory=$storageContractDirectory} @('config','--format','json') | ConvertFrom-Json -AsHashtable
$storageMounts=@($storageConfig.services.storage.volumes)
Check (@($storageMounts|Where-Object {$_.target -eq '/data' -and $_.type -eq 'volume' -and $_.source -eq 'storage-data'}).Count -eq 1 -and
    @($storageMounts|Where-Object {$_.target -eq '/logs' -and $_.type -eq 'volume' -and $_.source -eq 'storage-logs'}).Count -eq 1 -and
    $storageMounts.Count -eq 2) 'STORAGE_DATA_AND_LOGS_EXPLICIT'
Check ((@($storageConfig.volumes.Keys|Sort-Object) -join ',') -eq 'main-data,redis-data,simulation-data,storage-data,storage-logs' -and
    @($storageConfig.volumes.Values|Where-Object {$_.labels.'incidentpilot.local.instance' -ne 'controlled-volume-owner'}).Count -eq 0) 'FIVE_VOLUME_OWNERS_EXPLICIT'
$rootBuildVersion=([xml](Get-Content -LiteralPath (Join-Path $script:DemoRepo 'pom.xml') -Raw)).project.version
$frontendPackage=Get-Content -LiteralPath (Join-Path $script:DemoRepo 'frontend/package.json') -Raw | ConvertFrom-Json
$frontendLock=Get-Content -LiteralPath (Join-Path $script:DemoRepo 'frontend/package-lock.json') -Raw | ConvertFrom-Json -AsHashtable
$moduleVersions=@(foreach ($module in @('framework','infra-ai','system','incident','rag','agent','bootstrap','mcp-server')) {
    ([xml](Get-Content -LiteralPath (Join-Path $script:DemoRepo "$module/pom.xml") -Raw)).project.parent.version
})
Check ($rootBuildVersion -eq '0.1.0' -and @($moduleVersions | Where-Object {$_ -ne $rootBuildVersion}).Count -eq 0 -and
    $frontendPackage.version -eq $rootBuildVersion -and $frontendLock.version -eq $rootBuildVersion -and
    $frontendLock.packages[''].version -eq $rootBuildVersion) 'RELEASE_VERSION_CONSISTENT'
$versionRepo=Join-Path $state 'version-repository'
$originalRepo=$script:DemoRepo
try {
    $script:DemoRepo=$versionRepo
    $null=New-Item -ItemType Directory -Path $versionRepo -Force
    '<project><version>0.1.0</version></project>' | Set-Content -LiteralPath (Join-Path $versionRepo 'pom.xml')
    foreach ($module in @('bootstrap','mcp-server')) {
        $null=New-Item -ItemType Directory -Path (Join-Path $versionRepo "$module/target") -Force
        "<project><parent><version>0.1.0</version></parent><artifactId>$module</artifactId></project>" | Set-Content -LiteralPath (Join-Path $versionRepo "$module/pom.xml")
        'controlled-old-jar' | Set-Content -LiteralPath (Join-Path $versionRepo "$module/target/$module-0.0.1-SNAPSHOT.jar")
        $currentJar=Join-Path $versionRepo "$module/target/$module-0.1.0.jar"
        'controlled-current-jar' | Set-Content -LiteralPath $currentJar
        Check ((Get-DemoJar $module) -eq $currentJar) "CURRENT_JAR_EXACT_$module"
    }
    '<project><version>0.1.1</version></project>' | Set-Content -LiteralPath (Join-Path $versionRepo 'pom.xml')
    foreach ($module in @('bootstrap','mcp-server')) {
        "<project><parent><version>0.1.1</version></parent><artifactId>$module</artifactId></project>" | Set-Content -LiteralPath (Join-Path $versionRepo "$module/pom.xml")
        $missingCurrent=$false
        try {$null=Get-DemoJar $module} catch {$missingCurrent=$_.Exception.Message -eq 'JAR_REQUIRED'}
        Check $missingCurrent "MISSING_CURRENT_NO_FALLBACK_$module"
    }
    '<project><version>../0.1.0</version></project>' | Set-Content -LiteralPath (Join-Path $versionRepo 'pom.xml')
    $invalidVersion=$false
    try {$null=Get-DemoJar 'bootstrap'} catch {$invalidVersion=$_.Exception.Message -eq 'INVALID_BUILD_VERSION'}
    Check $invalidVersion 'INVALID_BUILD_VERSION_REJECTED'
    '<project><version>0.1.0</version></project>' | Set-Content -LiteralPath (Join-Path $versionRepo 'pom.xml')
    $parentMismatch=$false
    try {$null=Get-DemoJar 'bootstrap'} catch {$parentMismatch=$_.Exception.Message -eq 'BUILD_PARENT_VERSION_MISMATCH'}
    Check $parentMismatch 'BUILD_PARENT_VERSION_MISMATCH_REJECTED'
} finally {$script:DemoRepo=$originalRepo}
$lockPath=Join-Path $script:DemoBase '.lifecycle-contract-lock.lock'
$null=New-Item -ItemType Directory -Path $script:DemoBase -Force
$lock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try {
    $result=& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'local-demo.ps1') -Name contract-lock -Command Init
    Check ($LASTEXITCODE -eq 1 -and ($result|ConvertFrom-Json).reason -eq 'LIFECYCLE_BUSY') 'CONCURRENT_INIT_REJECTED'
} finally {$lock.Dispose()}
# Exercise new lifecycle boundaries with a real owned child and controlled Docker adapter.
$previousImage=[Environment]::GetEnvironmentVariable('MAIN_IMAGE')
try {
    [Environment]::SetEnvironmentVariable('MAIN_IMAGE','controlled-override')
    Set-DemoEnvironmentValue MAIN_IMAGE $null
    $native=Invoke-DemoNative node @('-e','process.stdout.write(JSON.stringify({absent:!Object.hasOwn(process.env,"MAIN_IMAGE")}))')
    Check ($native|ConvertFrom-Json).absent 'NATIVE_ENV_KEY_REMOVED'
} finally {Set-DemoEnvironmentValue MAIN_IMAGE $previousImage}
$composeKeys=@('MAIN_IMAGE','SIM_IMAGE','REDIS_IMAGE','STORAGE_IMAGE','INSTANCE_ID','MAIN_PASSWORD','SIM_PASSWORD','REDIS_PASSWORD','STORAGE_USER','STORAGE_PASSWORD','MAIN_PORT','SIM_PORT','REDIS_PORT','STORAGE_PORT','COMPOSE_ENV_FILES','COMPOSE_PROFILES')
$savedCompose=@{}
$nativeRunner=(Get-Item Function:Invoke-DemoNative).ScriptBlock
$script:storageVolumeOwner='foreign-owner'
function Invoke-DemoNative([string]$Executable,[string[]]$Arguments,[string]$InputText='',[string]$LogFile='') {
    if($Arguments[0] -eq 'ps'){return ''}
    if($Arguments[0] -eq 'volume' -and $Arguments[1] -eq 'ls'){return 'controlled-storage-contract_storage-logs'}
    if($Arguments[0] -eq 'volume' -and $Arguments[1] -eq 'inspect'){return $script:storageVolumeOwner}
    throw 'UNEXPECTED_STORAGE_CONTRACT_COMMAND'
}
try {
    Rejected {Assert-DemoContainers @{project='controlled-storage-contract';instanceId='controlled-volume-owner'}} 'FOREIGN_LOG_VOLUME_REJECTED'
    $script:storageVolumeOwner='controlled-volume-owner'
    Assert-DemoContainers @{project='controlled-storage-contract';instanceId='controlled-volume-owner'}
    Check $true 'OWN_LOG_VOLUME_ACCEPTED'
} finally {Set-Item Function:Invoke-DemoNative -Value $nativeRunner}
$script:composeObserved=$false
$script:composeStopped=$false
function Invoke-DemoNative([string]$Executable,[string[]]$Arguments,[string]$InputText='',[string]$LogFile='') {
    $script:composeObserved=@($composeKeys | Where-Object {[Environment]::GetEnvironmentVariables().Contains($_)}).Count -eq 0
    $script:composeStopped='down' -in $Arguments -and '--volumes' -notin $Arguments
    return ''
}
try {
    foreach ($key in $composeKeys) {$savedCompose[$key]=[Environment]::GetEnvironmentVariable($key); [Environment]::SetEnvironmentVariable($key,'unapproved-test-override')}
    $null=Invoke-DemoCompose @{project='controlled-test';directory=$state} @('ps')
    Check $script:composeObserved 'COMPOSE_SHELL_OVERRIDES_CLEARED'
    Check (@($composeKeys | Where-Object {[Environment]::GetEnvironmentVariable($_) -ne 'unapproved-test-override'}).Count -eq 0) 'COMPOSE_PARENT_ENV_RESTORED'
} finally {foreach ($key in $savedCompose.Keys) {[Environment]::SetEnvironmentVariable($key,$savedCompose[$key])}}
function Test-DemoDocker {}
function Assert-DemoContainers($Manifest) {Assert-DemoManifest $Manifest}
$oldDirectory=$script:DemoDirectory
$script:DemoDirectory=$state
$record=$null
try {
    $null=New-Item -ItemType Directory -Path $state -Force
    $instance=[Guid]::NewGuid().ToString('N')
    $manifest=@{instanceId=$instance;project=('incidentpilot-local-'+$instance);directory=$state;ports=@{}}
    Write-DemoJson (Join-Path $state 'owner.json') $manifest
    foreach ($file in @('app.properties','mcp.properties','web.json','compose.yaml','compose.env')) {'controlled-test' | Set-Content -LiteralPath (Join-Path $state $file)}
    $credentials=@{username='local-admin';storageUser=('local'+$instance.Substring(0,16))}
    foreach ($field in @('password','mainPassword','appPassword','simulationPassword','redisPassword','storagePassword','outboundToken','callbackToken','alertSecret')) {$credentials[$field]=New-DemoSecret}
    Write-DemoJson (Join-Path $state 'credentials.json') $credentials
    Write-DemoConfiguration $manifest $credentials
    $formatCheck=Join-Path $state 'ConfigurationFormatCheck.java'
    @'
import java.nio.file.*;
import java.util.Properties;
public class ConfigurationFormatCheck {
    public static void main(String[] args) throws Exception {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(args[0]))) { properties.load(reader); }
        if (!"demo".equals(properties.getProperty("incident.diagnosis.simulation-targets[incident-demo:demo:sim_service]"))) {
            throw new IllegalStateException("MAPPING_FORMAT_INVALID");
        }
        System.out.print("PROPERTIES_FORMAT_PASS");
    }
}
'@ | Set-Content -LiteralPath $formatCheck
    $formatResult=& $nativeRunner java @($formatCheck,(Join-Path $state 'app.properties'))
    Check ($formatResult -eq 'PROPERTIES_FORMAT_PASS') 'GENERATED_JAVA_PROPERTIES_MAPPING'
    Write-DemoJson (Join-Path $state 'initialized.json') @{instanceId=$instance;schemaDigest=(Get-FileHash (Join-Path $script:DemoRepo 'resources/database/schema_pg.sql')).Hash;configDigests=(Get-DemoConfigDigests $manifest)}
    $childFile=Join-Path $state 'owned-process.ps1'
    'Start-Sleep -Seconds 90' | Set-Content -LiteralPath $childFile
    $record=Start-DemoProcess $manifest 'app' 'pwsh' @('-NoProfile','-File',$childFile) @{} $childFile
    Write-DemoJson (Join-Path $state 'processes.json') @{app=$record}
    'controlled-app-drift' | Add-Content -LiteralPath (Join-Path $state 'app.properties')
    Rejected {Get-DemoManifest} 'START_CONFIG_DRIFT_REJECTED'
    Rejected {Initialize-Demo} 'REPEATED_INIT_CONFIG_DRIFT_REJECTED'
    $stopped=Stop-Demo (Get-DemoOwnedManifest)
    Check ($stopped.dataRetained -and $script:composeStopped -and !(Get-Process -Id $record.pid -ErrorAction SilentlyContinue)) 'STOP_OWN_PROCESS_AFTER_APP_DRIFT_RETAINS_VOLUMES'
    'unapproved-compose-drift' | Add-Content -LiteralPath (Join-Path $state 'compose.yaml')
    Rejected {Get-DemoOwnedManifest} 'STOP_COMPOSE_DRIFT_REJECTED'
} finally {
    if ($record -and (Test-DemoProcess $record)) {Stop-Process -Id $record.pid}
    $script:DemoDirectory=$oldDirectory
    . (Join-Path $PSScriptRoot 'local-demo.ps1') -Library
}
node --test (Join-Path $script:DemoRepo 'resources/local-demo/local-web.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'PROXY_TEST_FAILED' }
Write-Output "LOCAL_DEMO_CONTRACT_PASS $checks"

# IncidentPilot local persistent demo; no remote deployment or destructive command.
#Requires -Version 7.0
[CmdletBinding()]
param([ValidateSet('Init','Build','Start','Status','Stop','Verify')][string]$Command = 'Status',
    [ValidatePattern('^[a-z][a-z0-9-]{0,31}$')][string]$Name = 'default',
    [string]$MavenSettings = '', [string]$MavenRepository = '', [switch]$Offline, [switch]$Library)
$ErrorActionPreference = 'Stop'
$script:DemoRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:DemoBase = Join-Path $script:DemoRepo 'bootstrap/target/local-demo'
$script:DemoDirectory = Join-Path $script:DemoBase $Name
function Assert-Demo($Condition, [string]$Reason) { if (!$Condition) { throw $Reason } }
function Assert-DemoPath([string]$Path) {
    $absolute = [IO.Path]::GetFullPath($Path)
    Assert-Demo ($absolute.StartsWith($script:DemoBase + [IO.Path]::DirectorySeparatorChar, [StringComparison]::Ordinal)) 'PATH_OUTSIDE_DEMO'
    $ancestor = $absolute
    while ($ancestor -and $ancestor.Length -ge $script:DemoRepo.Length) {
        if (Test-Path -LiteralPath $ancestor) { Assert-Demo (((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'PATH_LINK' }
        $ancestor = Split-Path $ancestor -Parent
    }
}
function Protect-DemoDirectory([string]$Path) {
    if ($IsWindows) {
        $acl = Get-Acl -LiteralPath $Path
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
        if ($acl.AreAccessRulesProtected) {
            $rules=@($acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]))
            Assert-Demo ($rules.Count -gt 0 -and @($rules | Where-Object {$_.IdentityReference -ne $identity}).Count -eq 0) 'PRIVATE_DIRECTORY_OWNER_MISMATCH'
            return
        }
        $acl.SetAccessRuleProtection($true, $false)
        $rule = [Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
        Set-Acl -LiteralPath $Path -AclObject $acl
    } else { & chmod 700 $Path; Assert-Demo ($LASTEXITCODE -eq 0) 'PRIVATE_DIRECTORY_FAILED' }
}
function New-DemoSecret { return [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant() }
function Assert-DemoCredentials($Credentials) {
    Assert-Demo ($Credentials.username -eq 'local-admin' -and $Credentials.storageUser -match '^local[a-f0-9]{16}$') 'INVALID_LOCAL_CREDENTIALS'
    foreach ($field in @('password','mainPassword','appPassword','simulationPassword','redisPassword','storagePassword','outboundToken','callbackToken','alertSecret')) {
        Assert-Demo ($Credentials[$field] -match '^[a-f0-9]{48}$') 'INVALID_LOCAL_CREDENTIALS'
    }
}
function Write-DemoJson([string]$Path, $Value) { Assert-DemoPath $Path; $Value | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $Path -Encoding utf8NoBOM }
function Read-DemoJson([string]$Path) { Assert-DemoPath $Path; return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -AsHashtable }
function Assert-DemoManifest($Manifest) {
    Assert-Demo ($Manifest.instanceId -match '^[a-f0-9]{32}$' -and $Manifest.project -eq ('incidentpilot-local-' + $Manifest.instanceId)) 'INVALID_OWNER'
    Assert-Demo ($Manifest.directory -eq $script:DemoDirectory) 'OWNER_DIRECTORY_MISMATCH'
    Assert-DemoPath $Manifest.directory
}
function Invoke-DemoNative([string]$Executable, [string[]]$Arguments, [string]$InputText = '', [string]$LogFile = '') {
    if ($InputText) { $result = @($InputText | & $Executable @Arguments 2>&1) } else { $result = @(& $Executable @Arguments 2>&1) }
    $exitCode=$LASTEXITCODE
    $output=($result | ForEach-Object { $_.ToString() }) -join "`n"
    if ($LogFile) {Assert-DemoPath $LogFile; $output|Set-Content -LiteralPath $LogFile -Encoding utf8NoBOM}
    Assert-Demo ($exitCode -eq 0) 'LOCAL_COMMAND_FAILED'
    return $output
}
function Test-DemoDocker {
    foreach ($variable in @('DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','NODE_OPTIONS','MAVEN_ARGS','MAVEN_OPTS')) {
        Assert-Demo ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($variable))) 'ENVIRONMENT_OVERRIDE_REJECTED'
    }
    $endpoint = Invoke-DemoNative docker @('context','inspect','--format','{{.Endpoints.docker.Host}}')
    Assert-Demo ($endpoint -match '^(npipe://|unix:///)' -and $endpoint -notmatch '[\r\n]') 'REMOTE_DOCKER_REJECTED'
    $null = Invoke-DemoNative docker @('version','--format','{{.Server.Version}}')
}
function Set-DemoEnvironmentValue([string]$Key, $Value) {
    if ($null -eq $Value) {Remove-Item -LiteralPath ('Env:' + $Key) -ErrorAction SilentlyContinue}
    else {[Environment]::SetEnvironmentVariable($Key,$Value)}
}
function Invoke-DemoCompose($Manifest, [string[]]$Arguments, [string]$InputText = '', [string]$LogFile = '') {
    $saved=@{}
    try {
        foreach ($key in @('MAIN_IMAGE','SIM_IMAGE','REDIS_IMAGE','STORAGE_IMAGE','INSTANCE_ID','MAIN_PASSWORD','SIM_PASSWORD','REDIS_PASSWORD','STORAGE_USER','STORAGE_PASSWORD','MAIN_PORT','SIM_PORT','REDIS_PORT','STORAGE_PORT','COMPOSE_ENV_FILES','COMPOSE_PROFILES')) {
            $saved[$key]=[Environment]::GetEnvironmentVariable($key)
            Set-DemoEnvironmentValue $key $null
        }
        return Invoke-DemoNative docker (@('compose','--project-name',$Manifest.project,'--env-file',(Join-Path $Manifest.directory 'compose.env'),'-f',(Join-Path $Manifest.directory 'compose.yaml')) + $Arguments) $InputText $LogFile
    } finally {foreach ($key in $saved.Keys) {Set-DemoEnvironmentValue $key $saved[$key]}}
}
function Assert-DemoContainers($Manifest) {
    $ids=@((Invoke-DemoNative docker @('ps','-aq','--filter',('label=com.docker.compose.project=' + $Manifest.project))) -split '\r?\n' | Where-Object {$_})
    if ($ids.Count) {
        $owners=Invoke-DemoNative docker (@('inspect','--format','{{index .Config.Labels "incidentpilot.local.instance"}}') + $ids)
        foreach ($owner in $owners -split '\r?\n') {Assert-Demo ($owner -eq $Manifest.instanceId) 'CONTAINER_OWNER_MISMATCH'}
    }
    $volumes=@((Invoke-DemoNative docker @('volume','ls','-q','--filter',('name=^' + $Manifest.project + '_'))) -split '\r?\n' | Where-Object {$_})
    if ($volumes.Count) {
        $owners=Invoke-DemoNative docker (@('volume','inspect','--format','{{index .Labels "incidentpilot.local.instance"}}') + $volumes)
        foreach ($owner in $owners -split '\r?\n') {Assert-Demo ($owner -eq $Manifest.instanceId) 'VOLUME_OWNER_MISMATCH'}
    }
}
function Get-DemoConfigDigests($Manifest) {
    $hashes=@{}
    foreach ($file in @('app.properties','mcp.properties','web.json','compose.yaml','compose.env','credentials.json')) {
        $hashes[$file]=(Get-FileHash -LiteralPath (Join-Path $Manifest.directory $file) -Algorithm SHA256).Hash
    }
    return $hashes
}
function Get-DemoFreePort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try { $listener.Start(); return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Assert-DemoFreePorts($Ports) {
    foreach ($port in $Ports.Values) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, [int]$port)
        try { $listener.Start() } catch { throw 'LOCAL_PORT_CONFLICT' } finally { $listener.Stop() }
    }
}
function Get-DemoCommandLine([int]$ProcessId) {
    if ($IsWindows) { return (Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId").CommandLine }
    return [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes("/proc/$ProcessId/cmdline")).Replace([char]0, ' ')
}
function Test-DemoProcess($Record) {
    if (!$Record) { return $false }
    $process = Get-Process -Id ([int]$Record.pid) -ErrorAction SilentlyContinue
    if (!$process) { return $false }
    $sameTime = ([DateTimeOffset]$Record.startTimeUtc).UtcDateTime -eq $process.StartTime.ToUniversalTime()
    $CommandLine = Get-DemoCommandLine ([int]$Record.pid)
    return $sameTime -and $CommandLine.Contains([string]$Record.marker, [StringComparison]::Ordinal)
}
function Stop-DemoProcesses($Manifest) {
    $file = Join-Path $Manifest.directory 'processes.json'
    if (!(Test-Path -LiteralPath $file)) { return }
    $records = Read-DemoJson $file
    foreach ($record in $records.Values) {
        if (Get-Process -Id ([int]$record.pid) -ErrorAction SilentlyContinue) { Assert-Demo (Test-DemoProcess $record) 'PROCESS_OWNER_MISMATCH' }
    }
    foreach ($component in @('web','app','mcp')) {
        $record = $records[$component]
        if (Test-DemoProcess $record) { Stop-Process -Id ([int]$record.pid); Wait-Process -Id ([int]$record.pid) -Timeout 20 -ErrorAction SilentlyContinue }
    }
    Write-DemoJson $file @{}
}
function Start-DemoProcess($Manifest, [string]$Component, [string]$Executable, [string[]]$Arguments, $Environment, [string]$Marker) {
    $Environment=Get-DemoChildEnvironment $Environment
    $saved = @{}
    try {
        foreach ($key in $Environment.Keys) { $saved[$key] = [Environment]::GetEnvironmentVariable($key); Set-DemoEnvironmentValue $key $Environment[$key] }
        $options = @{FilePath=$Executable; ArgumentList=($Arguments | ForEach-Object {'"' + $_ + '"'}); WorkingDirectory=$Manifest.directory;
            RedirectStandardOutput=(Join-Path $Manifest.directory "$Component.stdout.log"); RedirectStandardError=(Join-Path $Manifest.directory "$Component.stderr.log"); PassThru=$true}
        if ($IsWindows) { $options.WindowStyle = 'Hidden' }
        $process = Start-Process @options
        return @{pid=$process.Id; startTimeUtc=$process.StartTime.ToUniversalTime().ToString('O'); marker=$Marker}
    } finally { foreach ($key in $saved.Keys) { Set-DemoEnvironmentValue $key $saved[$key] } }
}
function Get-DemoChildEnvironment($Desired) {
    $controlled=@{} + $Desired
    foreach ($key in [Environment]::GetEnvironmentVariables().Keys) {
        if ($key -match '^(SPRING_|SERVER_|INCIDENT_|RAG_|RAGENT_|AI_|AGENT_|MINERU_|OBSERVABILITY_|ROCKETMQ_|OSS_)' -and !$controlled.ContainsKey($key)) {$controlled[$key]=$null}
    }
    return $controlled
}
function Test-DemoHttp([string]$Uri, [int]$Expected = 200) {
    try { $response = Invoke-WebRequest -Uri $Uri -SkipHttpErrorCheck -TimeoutSec 3; return [int]$response.StatusCode -eq $Expected } catch { return $false }
}
function Wait-DemoHttp([string]$Uri, [int]$Expected, $Record) {
    $deadline = [DateTime]::UtcNow.AddSeconds(150)
    do {
        Assert-Demo (Test-DemoProcess $Record) 'LOCAL_PROCESS_EXITED'
        if (Test-DemoHttp $Uri $Expected) { return }
        Start-Sleep -Milliseconds 700
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'LOCAL_READINESS_TIMEOUT'
}
function Initialize-Demo {
    Test-DemoDocker
    Assert-DemoPath $script:DemoDirectory
    if (!(Test-Path -LiteralPath $script:DemoDirectory)) { $null = New-Item -ItemType Directory -Path $script:DemoDirectory }
    Protect-DemoDirectory $script:DemoDirectory
    $ownerFile = Join-Path $script:DemoDirectory 'owner.json'
    if (!(Test-Path -LiteralPath $ownerFile)) {
        Assert-Demo (@(Get-ChildItem -LiteralPath $script:DemoDirectory -Force).Count -eq 0) 'UNOWNED_DIRECTORY'
        $instance = [Guid]::NewGuid().ToString('N')
        $ports = @{}
        foreach ($key in @('main','simulation','redis','storage','app','mcp','web')) { do {$port=Get-DemoFreePort} while ($port -in $ports.Values); $ports[$key]=$port }
        $manifest = @{instanceId=$instance; project=('incidentpilot-local-' + $instance); directory=$script:DemoDirectory; ports=$ports}
        Write-DemoJson $ownerFile $manifest
        $credentials = @{username='local-admin'; password=(New-DemoSecret); mainPassword=(New-DemoSecret); appPassword=(New-DemoSecret); simulationPassword=(New-DemoSecret);
            redisPassword=(New-DemoSecret); storageUser=('local' + $instance.Substring(0,16)); storagePassword=(New-DemoSecret); outboundToken=(New-DemoSecret); callbackToken=(New-DemoSecret); alertSecret=(New-DemoSecret)}
        Write-DemoJson (Join-Path $script:DemoDirectory 'credentials.json') $credentials
    }
    $manifest = Read-DemoJson $ownerFile; Assert-DemoManifest $manifest
    $credentials = Read-DemoJson (Join-Path $manifest.directory 'credentials.json')
    Assert-DemoCredentials $credentials
    $schema = Join-Path $script:DemoRepo 'resources/database/schema_pg.sql'
    $schemaDigest = (Get-FileHash -LiteralPath $schema -Algorithm SHA256).Hash
    $marker = Join-Path $manifest.directory 'initialized.json'
    if (Test-Path -LiteralPath $marker) { $null=Get-DemoManifest; Assert-DemoContainers $manifest; return @{status='INITIALIZED'; instanceId=$manifest.instanceId; reused=$true} }
    Assert-DemoContainers $manifest
    $running = Invoke-DemoNative docker @('ps','-q','--filter',('label=com.docker.compose.project=' + $manifest.project))
    if (!$running) { Assert-DemoFreePorts $manifest.ports }
    $images = @{}
    foreach ($entry in @{MAIN_IMAGE='pgvector/pgvector:pg16'; SIM_IMAGE='postgres:16-alpine'; REDIS_IMAGE='redis:latest'; STORAGE_IMAGE='rustfs/rustfs:1.0.0-alpha.72'}.GetEnumerator()) {
        $images[$entry.Key] = Invoke-DemoNative docker @('image','inspect','--format','{{.Id}}',$entry.Value)
    }
    Copy-Item -LiteralPath (Join-Path $script:DemoRepo 'resources/local-demo/compose.yaml') -Destination (Join-Path $manifest.directory 'compose.yaml')
    $variables = $images + @{INSTANCE_ID=$manifest.instanceId; MAIN_PASSWORD=$credentials.mainPassword; SIM_PASSWORD=$credentials.simulationPassword; REDIS_PASSWORD=$credentials.redisPassword;
        STORAGE_USER=$credentials.storageUser; STORAGE_PASSWORD=$credentials.storagePassword; MAIN_PORT=$manifest.ports.main; SIM_PORT=$manifest.ports.simulation; REDIS_PORT=$manifest.ports.redis; STORAGE_PORT=$manifest.ports.storage}
    ($variables.GetEnumerator() | Sort-Object Key | ForEach-Object {$_.Key + '=' + $_.Value}) | Set-Content -LiteralPath (Join-Path $manifest.directory 'compose.env') -Encoding utf8NoBOM
    $null = Invoke-DemoCompose $manifest @('up','-d','--pull','never','--wait','--wait-timeout','120')
    $exists = Invoke-DemoCompose $manifest @('exec','-T','main-db','psql','-U','postgres','-d','incidentpilot','-tAc',"SELECT count(*) FROM information_schema.tables WHERE table_name='t_user'")
    if ($exists.Trim() -eq '0') {
        $sql = "BEGIN;`n" + (Get-Content -LiteralPath $schema -Raw) + "`nALTER ROLE ragent_app PASSWORD '$($credentials.appPassword)';`nINSERT INTO t_user(id,username,password,role) VALUES('42','local-admin','$($credentials.password)','admin');`nCOMMIT;"
        $null = Invoke-DemoCompose $manifest @('exec','-T','main-db','psql','-v','ON_ERROR_STOP=1','-U','postgres','-d','incidentpilot') $sql
    } else { Assert-Demo ($exists.Trim() -eq '1') 'PARTIAL_SCHEMA' }
    $simSql = "BEGIN;`n" + (Get-Content -LiteralPath (Join-Path $script:DemoRepo 'mcp-server/src/main/resources/db/bit-schema.sql') -Raw) + "`nCREATE TABLE IF NOT EXISTS t_schema_version(version INT PRIMARY KEY,applied_at TIMESTAMPTZ NOT NULL DEFAULT now());`nINSERT INTO t_schema_version(version) VALUES(8) ON CONFLICT DO NOTHING;`nINSERT INTO simulation_target(target_id,state) VALUES('demo','PAUSED') ON CONFLICT DO NOTHING;`nCOMMIT;"
    $null = Invoke-DemoCompose $manifest @('exec','-T','simulation-db','psql','-v','ON_ERROR_STOP=1','-U','postgres','-d','incidentpilot_simulation') $simSql
    Write-DemoConfiguration $manifest $credentials
    Write-DemoJson $marker @{instanceId=$manifest.instanceId; schemaDigest=$schemaDigest; images=$images; configDigests=(Get-DemoConfigDigests $manifest); initializedAt=[DateTimeOffset]::UtcNow.ToString('O')}
    return @{status='INITIALIZED'; instanceId=$manifest.instanceId; reused=$false}
}
function Write-DemoConfiguration($Manifest, $Credentials) {
    $app = @(
        'spring.profiles.active=isolated,local-demo', 'server.address=127.0.0.1', "server.port=$($Manifest.ports.app)",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:$($Manifest.ports.main)/incidentpilot", 'spring.datasource.username=ragent_app', "spring.datasource.password=$($Credentials.appPassword)",
        'spring.data.redis.host=127.0.0.1', "spring.data.redis.port=$($Manifest.ports.redis)", "spring.data.redis.password=$($Credentials.redisPassword)",
        "rag.storage.s3.endpoint=http://127.0.0.1:$($Manifest.ports.storage)", "rag.storage.s3.access-key=$($Credentials.storageUser)", "rag.storage.s3.secret-key=$($Credentials.storagePassword)",
        'rag.storage.kb-bucket=local-demo-kb', 'rag.storage.asset-bucket=local-demo-assets', 'rag.mcp.servers=',
        'spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration', 'rocketmq.name-server=',
        'incident.worker.enabled=true', 'incident.outbox.enabled=false', 'incident.action.worker.enabled=true', 'incident.action.allowed-simulation-targets=demo',
        'incident.diagnosis.simulation-targets[incident-demo\:demo\:sim_service]=demo', 'incident.sources[local-demo].keys[0].key-id=local-key',
        'incident.sources[local-demo].keys[0].secret-environment-variable=LOCAL_DEMO_ALERT_SECRET', 'observability.enabled=false', 'agent.trace.enabled=false',
        'mineru.api-url=http://127.0.0.1:1', 'mineru.api-key=', 'rag.graph.type=none', 'rag.keyword.type=none', 'ragent.demo-mode=false')
    foreach ($provider in @('ollama','bailian','aihubmix','deepseek')) { $app += "ai.providers.$provider.url=http://127.0.0.1:1"; $app += "ai.providers.$provider.api-key=" }
    $app | Set-Content -LiteralPath (Join-Path $Manifest.directory 'app.properties') -Encoding utf8NoBOM
    @('spring.profiles.active=local-demo', 'server.address=127.0.0.1', "server.port=$($Manifest.ports.mcp)",
        "ragent.bit.datasource.url=jdbc:postgresql://127.0.0.1:$($Manifest.ports.simulation)/incidentpilot_simulation", 'ragent.bit.datasource.username=postgres',
        "ragent.bit.datasource.password=$($Credentials.simulationPassword)", 'observability.enabled=false') | Set-Content -LiteralPath (Join-Path $Manifest.directory 'mcp.properties') -Encoding utf8NoBOM
    Write-DemoJson (Join-Path $Manifest.directory 'web.json') @{dist=(Join-Path $Manifest.directory 'dist'); backendPort=$Manifest.ports.app; webPort=$Manifest.ports.web; instanceId=$Manifest.instanceId}
}
function Get-DemoOwnedManifest {
    $manifest = Read-DemoJson (Join-Path $script:DemoDirectory 'owner.json'); Assert-DemoManifest $manifest
    Assert-Demo (Test-Path -LiteralPath (Join-Path $manifest.directory 'initialized.json')) 'INIT_REQUIRED'
    $marker=Read-DemoJson (Join-Path $manifest.directory 'initialized.json')
    Assert-Demo ($marker.instanceId -eq $manifest.instanceId) 'INIT_OWNER_MISMATCH'
    foreach ($file in @('compose.yaml','compose.env')) {
        Assert-Demo ((Get-FileHash -LiteralPath (Join-Path $manifest.directory $file)).Hash -eq $marker.configDigests[$file]) 'LOCAL_COMPOSE_CHANGED'
    }
    return $manifest
}
function Get-DemoManifest {
    $manifest=Get-DemoOwnedManifest
    $marker=Read-DemoJson (Join-Path $manifest.directory 'initialized.json')
    Assert-Demo ($marker.schemaDigest -eq (Get-FileHash (Join-Path $script:DemoRepo 'resources/database/schema_pg.sql')).Hash) 'SCHEMA_CHANGED'
    $expected=$marker.configDigests
    $actual=Get-DemoConfigDigests $manifest
    foreach ($file in $actual.Keys) {Assert-Demo ($actual[$file] -eq $expected[$file]) 'LOCAL_CONFIGURATION_CHANGED'}
    return $manifest
}
function Stop-Demo($Manifest) {
    Test-DemoDocker; Assert-DemoContainers $Manifest; Stop-DemoProcesses $Manifest
    $null=Invoke-DemoCompose $Manifest @('down','--timeout','20')
    return @{status='STOPPED';instanceId=$Manifest.instanceId;dataRetained=$true}
}
function Build-Demo($Manifest) {
    Assert-Demo (!(Test-Path (Join-Path $Manifest.directory 'processes.json')) -or @((Read-DemoJson (Join-Path $Manifest.directory 'processes.json')).Values | Where-Object {Test-DemoProcess $_}).Count -eq 0) 'STOP_BEFORE_BUILD'
    Test-DemoDocker
    Push-Location $script:DemoRepo
    try {
        $maven = if ($IsWindows) {Join-Path $script:DemoRepo 'mvnw.cmd'} else {Join-Path $script:DemoRepo 'mvnw'}
        $arguments = @('-Dspotless.apply.skip=true','-Dmaven.test.skip=true','-pl','bootstrap,mcp-server','-am','package')
        if ($Offline) { $arguments = @('-o') + $arguments }
        if ($MavenSettings) { Assert-Demo (Test-Path -LiteralPath $MavenSettings -PathType Leaf) 'MAVEN_SETTINGS_REQUIRED'; $arguments = @('-s',[IO.Path]::GetFullPath($MavenSettings)) + $arguments }
        if ($MavenRepository) {Assert-Demo (Test-Path -LiteralPath $MavenRepository -PathType Container) 'MAVEN_REPOSITORY_REQUIRED'; $arguments = @('-Dmaven.repo.local=' + [IO.Path]::GetFullPath($MavenRepository)) + $arguments}
        $null = Invoke-DemoNative $maven $arguments '' (Join-Path $Manifest.directory 'build-java.log')
        Set-Location (Join-Path $script:DemoRepo 'frontend')
        $null = Invoke-DemoNative npm @('ci','--no-audit','--no-fund') '' (Join-Path $Manifest.directory 'build-web-install.log')
        $null = Invoke-DemoNative npm @('run','build') '' (Join-Path $Manifest.directory 'build-web.log')
    } finally {Pop-Location}
    Publish-DemoArtifacts $Manifest
    return @{status='BUILT'; instanceId=$Manifest.instanceId}
}
function Get-DemoJar([string]$Module) {
    Assert-Demo ($Module -cin @('bootstrap','mcp-server')) 'INVALID_JAR_MODULE'
    $rootPom=[xml](Get-Content -LiteralPath (Join-Path $script:DemoRepo 'pom.xml') -Raw)
    $modulePom=[xml](Get-Content -LiteralPath (Join-Path $script:DemoRepo "$Module/pom.xml") -Raw)
    $version=[string]$rootPom.project.version
    Assert-Demo ($version -cmatch '\A[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?\z') 'INVALID_BUILD_VERSION'
    Assert-Demo ([string]$modulePom.project.parent.version -ceq $version) 'BUILD_PARENT_VERSION_MISMATCH'
    $artifactId=[string]$modulePom.project.artifactId
    Assert-Demo ($artifactId -ceq $Module) 'BUILD_ARTIFACT_ID_MISMATCH'
    $file=Join-Path $script:DemoRepo "$Module/target/$artifactId-$version.jar"
    Assert-Demo (Test-Path -LiteralPath $file -PathType Leaf) 'JAR_REQUIRED'
    return $file
}
function Publish-DemoArtifacts($Manifest) {
    foreach ($artifact in @{app='bootstrap'; mcp='mcp-server'}.GetEnumerator()) {
        $file = Get-DemoJar $artifact.Value
        Copy-Item -LiteralPath $file -Destination (Join-Path $Manifest.directory "$($artifact.Key).jar")
    }
    $dist = Join-Path $script:DemoRepo 'frontend/dist'; Assert-Demo (Test-Path -LiteralPath (Join-Path $dist 'index.html')) 'WEB_BUILD_REQUIRED'
    $destination = Join-Path $Manifest.directory 'dist'
    $null = New-Item -ItemType Directory -Path $destination -Force
    Copy-Item -LiteralPath (Join-Path $dist 'index.html') -Destination $destination
    Copy-Item -Path (Join-Path $dist '*') -Destination $destination -Recurse -Force
    Write-DemoJson (Join-Path $Manifest.directory 'artifacts.json') @{builtAt=[DateTimeOffset]::UtcNow.ToString('O'); app=(Get-FileHash (Join-Path $Manifest.directory 'app.jar')).Hash;
        mcp=(Get-FileHash (Join-Path $Manifest.directory 'mcp.jar')).Hash; index=(Get-FileHash (Join-Path $destination 'index.html')).Hash}
}
function Start-Demo($Manifest) {
    Test-DemoDocker; Assert-DemoContainers $Manifest
    $recordsFile = Join-Path $Manifest.directory 'processes.json'
    if (Test-Path -LiteralPath $recordsFile) {
        $existing = Read-DemoJson $recordsFile
        $live = @($existing.Values | Where-Object {Test-DemoProcess $_})
        if ($live.Count -eq 3) { $state=Get-DemoStatus $Manifest; Assert-Demo $state.ready 'LOCAL_RUNNING_NOT_READY'; return $state }
        foreach ($record in $existing.Values) { if (Get-Process -Id $record.pid -ErrorAction SilentlyContinue) { Assert-Demo (Test-DemoProcess $record) 'PROCESS_OWNER_MISMATCH' } }
        Stop-DemoProcesses $Manifest
    }
    foreach ($item in @('app.jar','mcp.jar','dist/index.html','artifacts.json')) { Assert-Demo (Test-Path -LiteralPath (Join-Path $Manifest.directory $item)) 'BUILD_REQUIRED' }
    $artifacts=Read-DemoJson (Join-Path $Manifest.directory 'artifacts.json')
    $unchanged=$artifacts.app -eq (Get-FileHash (Join-Path $Manifest.directory 'app.jar')).Hash -and $artifacts.mcp -eq (Get-FileHash (Join-Path $Manifest.directory 'mcp.jar')).Hash -and $artifacts.index -eq (Get-FileHash (Join-Path $Manifest.directory 'dist/index.html')).Hash
    Assert-Demo $unchanged 'ARTIFACT_CHANGED'
    $appPorts=@{app=$Manifest.ports.app;mcp=$Manifest.ports.mcp;web=$Manifest.ports.web}; Assert-DemoFreePorts $appPorts
    $null=Invoke-DemoCompose $Manifest @('up','-d','--pull','never','--wait','--wait-timeout','120') '' (Join-Path $Manifest.directory 'start-compose.log')
    $credentials=Read-DemoJson (Join-Path $Manifest.directory 'credentials.json')
    Assert-DemoCredentials $credentials
    $environment=@{INCIDENT_MCP_OUTBOUND_TOKEN=$credentials.outboundToken;INCIDENT_MCP_CALLBACK_TOKEN=$credentials.callbackToken;
        INCIDENT_MCP_CALLBACK_BASE_URL="http://127.0.0.1:$($Manifest.ports.app)/api/ragent";INCIDENT_MCP_PROTECTED_URL="http://127.0.0.1:$($Manifest.ports.mcp)/mcp/incident";LOCAL_DEMO_ALERT_SECRET=$credentials.alertSecret;
        BAILIAN_API_KEY=''; AIHUBMIX_API_KEY=''; DEEPSEEK_API_KEY=''; MINERU_API_KEY=''; YDC_API_KEY=''; RAG_LIVE_TESTS='false'}
    $records=@{}
    try {
        foreach ($component in @('mcp','app')) {
            $properties=Join-Path $Manifest.directory "$component.properties"
            $configUri = ([Uri]$properties).AbsoluteUri
            $records[$component]=Start-DemoProcess $Manifest $component 'java' @('-Xmx512m','-jar',(Join-Path $Manifest.directory "$component.jar"),("--spring.config.additional-location=" + $configUri)) $environment $configUri
            Write-DemoJson $recordsFile $records
            $url=if ($component -eq 'mcp') {"http://127.0.0.1:$($Manifest.ports.mcp)/mcp/incident"} else {"http://127.0.0.1:$($Manifest.ports.app)/api/ragent/incident/v1/workbench"}
            Wait-DemoHttp $url 401 $records[$component]
        }
        $webConfig=Join-Path $Manifest.directory 'web.json'
        $records.web=Start-DemoProcess $Manifest 'web' 'node' @((Join-Path $script:DemoRepo 'resources/local-demo/local-web.mjs'),$webConfig) @{} $webConfig
        Write-DemoJson $recordsFile $records
        Wait-DemoHttp "http://127.0.0.1:$($Manifest.ports.web)/__local_demo/status" 200 $records.web
        return Get-DemoStatus $Manifest
    } catch {Stop-DemoProcesses $Manifest; throw}
}
function Get-DemoStatus($Manifest) {
    Test-DemoDocker; Assert-DemoContainers $Manifest
    $records=if (Test-Path (Join-Path $Manifest.directory 'processes.json')) {Read-DemoJson (Join-Path $Manifest.directory 'processes.json')} else {@{}}
    $components=@{}
    foreach ($component in @('mcp','app','web')) {$components[$component]=Test-DemoProcess $records[$component]}
    $dependencies=@{}
    foreach ($service in @('main-db','simulation-db','redis','storage')) {$dependencies[$service]='absent'}
    $states=Invoke-DemoNative docker @('ps','-a','--format','{{.Label "com.docker.compose.service"}}|{{.State}}','--filter',('label=com.docker.compose.project=' + $Manifest.project))
    foreach ($line in $states -split '\r?\n' | Where-Object {$_}) {$parts=$line.Split('|'); if ($dependencies.ContainsKey($parts[0])) {$dependencies[$parts[0]]=$parts[1]}}
    $dependenciesReady=@($dependencies.Values | Where-Object {$_ -ne 'running'}).Count -eq 0
    $ready=$dependenciesReady -and $components.mcp -and $components.app -and $components.web -and (Test-DemoHttp "http://127.0.0.1:$($Manifest.ports.app)/api/ragent/incident/v1/workbench" 401) -and (Test-DemoHttp "http://127.0.0.1:$($Manifest.ports.mcp)/mcp/incident" 401) -and (Test-DemoHttp "http://127.0.0.1:$($Manifest.ports.web)/__local_demo/status")
    return @{status=if ($ready) {'READY'} elseif ($components.Values -contains $true) {'NOT_READY'} elseif ($dependenciesReady) {'DEPENDENCIES_READY'} else {'STOPPED'}; instanceId=$Manifest.instanceId; ready=[bool]$ready; processes=$components; dependencies=$dependencies; ports=$Manifest.ports; url="http://127.0.0.1:$($Manifest.ports.web)/login"}
}
function Verify-Demo($Manifest) {
    $state=Get-DemoStatus $Manifest; Assert-Demo $state.ready 'LOCAL_NOT_READY'
    $credentials=Read-DemoJson (Join-Path $Manifest.directory 'credentials.json')
    $base="http://127.0.0.1:$($Manifest.ports.web)/api/ragent"
    $login=Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body (@{username=$credentials.username;password=$credentials.password}|ConvertTo-Json -Compress)
    Assert-Demo ($login.code -eq '0' -and $login.data.token) 'LOCAL_LOGIN_FAILED'
    $headers=@{Authorization=$login.data.token}
    $workbench=Invoke-RestMethod -Uri "$base/incident/v1/workbench" -Headers $headers
    $runbooks=Invoke-RestMethod -Uri "$base/incident/v1/runbooks" -Headers $headers
    $incidents=Invoke-RestMethod -Uri "$base/incident/v1/incidents?limit=100" -Headers $headers
    Assert-Demo ($workbench.code -eq '0' -and $runbooks.code -eq '0' -and $incidents.code -eq '0') 'LOCAL_READ_FAILED'
    $facts=@()
    foreach ($incident in $incidents.data.items) {
        Assert-Demo ($incident.incidentId -match '^[0-9]{1,20}$') 'INVALID_INCIDENT_RESPONSE'
        $actions=Invoke-RestMethod -Uri "$base/incident/v1/incidents/$($incident.incidentId)/actions" -Headers $headers
        $diagnoses=Invoke-RestMethod -Uri "$base/incident/v1/incidents/$($incident.incidentId)/diagnoses?limit=100" -Headers $headers
        Assert-Demo ($actions.code -eq 'SUCCESS' -and $diagnoses.code -eq 'SUCCESS') 'LOCAL_HISTORY_READ_FAILED'
        $facts+=@{incidentId=$incident.incidentId;state=$incident.lifecycleState;version=$incident.version;alertCount=$incident.alertCount;diagnosisIds=@($diagnoses.data.items.diagnosisId);diagnosesHasMore=[bool]$diagnoses.data.hasMore;
            actions=@($actions.data|ForEach-Object {@{actionId=$_.actionId;state=$_.state;approvalState=$_.approval.status;receiptIds=@($_.receipts.receiptId)}})}
    }
    $result=@{status='VERIFIED';instanceId=$Manifest.instanceId;verifiedAt=[DateTimeOffset]::UtcNow.ToString('O');account=$login.data.userId;runbooks=$runbooks.data.documents.Count;incidents=$facts;incidentsHasMore=[bool]$incidents.data.hasMore}
    Write-DemoJson (Join-Path $Manifest.directory 'verify.json') $result
    $null=Invoke-RestMethod -Uri "$base/auth/logout" -Method Post -Headers $headers
    return $result
}
if ($Library) { return }
$lifecycleLock=$null
try {
    if ($Command -ne 'Status') {
        $lockPath=Join-Path $script:DemoBase ('.lifecycle-' + $Name + '.lock'); Assert-DemoPath $lockPath
        $null=New-Item -ItemType Directory -Path $script:DemoBase -Force
        try {$lifecycleLock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)} catch {throw 'LIFECYCLE_BUSY'}
    }
    if ($Command -eq 'Init') {$result=Initialize-Demo}
    else {
        $manifest=if ($Command -eq 'Stop') {Get-DemoOwnedManifest} else {Get-DemoManifest}
        switch ($Command) {
            'Build' {$result=Build-Demo $manifest}
            'Start' {$result=Start-Demo $manifest}
            'Status' {$result=Get-DemoStatus $manifest}
            'Verify' {$result=Verify-Demo $manifest}
            'Stop' {$result=Stop-Demo $manifest}
        }
    }
    $result | ConvertTo-Json -Depth 15
} catch {Write-Output (@{status='FAILED';command=$Command;reason=if ($_.Exception.Message -match '^[A-Z_]+$') {$_.Exception.Message} else {'LOCAL_OPERATION_FAILED'}} | ConvertTo-Json -Compress); exit 1}
finally {if ($lifecycleLock) {$lifecycleLock.Dispose()}}

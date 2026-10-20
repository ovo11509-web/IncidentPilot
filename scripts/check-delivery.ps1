# IncidentPilot 新增本地交付门禁。仅固定命令；原始诊断不得发布。
#Requires -Version 7.0
[CmdletBinding()]
param([ValidateSet('Smoke','Backend','Frontend','All')][string]$Gate = 'All', [switch]$CheckOnly,
    [string]$CandidatePath)
$ErrorActionPreference = 'Stop'
$script:DeliveryRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:DeliveryBase = Join-Path $script:DeliveryRepo 'bootstrap/target/delivery-gates'
function Assert-Delivery($Condition, [string]$Reason) { if (!$Condition) { throw $Reason } }
function Write-DeliveryJson($Path, $Value) {
    Assert-DeliveryPath $Path
    $Value | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $Path -Encoding utf8NoBOM
}
function Assert-DeliveryPath([string]$Path) {
    $absolute = [IO.Path]::GetFullPath($Path)
    Assert-Delivery ($absolute.StartsWith($script:DeliveryBase + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::Ordinal)) 'OUTPUT_OUTSIDE_ROOT'
    $ancestor = $absolute
    while ($ancestor -and $ancestor.Length -ge $script:DeliveryRepo.Length) {
        if (Test-Path -LiteralPath $ancestor) {
            Assert-Delivery (((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'OUTPUT_LINK'
        }
        $ancestor = Split-Path $ancestor -Parent
    }
}
function Get-DeliverySources {
    $paths = @('pom.xml','mvnw','mvnw.cmd','.mvn/wrapper/maven-wrapper.properties','scripts/check-delivery.ps1','scripts/test-check-delivery.ps1')
    foreach ($module in @('framework','infra-ai','system','incident','agent','rag','mcp-server','bootstrap')) {
        $paths += "$module/pom.xml"
        $paths += @(Get-ChildItem (Join-Path $script:DeliveryRepo "$module/src") -Recurse -File |
            ForEach-Object { [IO.Path]::GetRelativePath($script:DeliveryRepo,$_.FullName).Replace('\','/') })
    }
    foreach ($directory in @('resources/database','frontend/src','frontend/tests')) {
        $paths += @(Get-ChildItem (Join-Path $script:DeliveryRepo $directory) -Recurse -File |
            ForEach-Object { [IO.Path]::GetRelativePath($script:DeliveryRepo,$_.FullName).Replace('\','/') })
    }
    $paths += @(Get-ChildItem (Join-Path $script:DeliveryRepo 'frontend') -File -Force |
        Where-Object { $_.Name -notlike '.env*' } |
        ForEach-Object { "frontend/$($_.Name)" })
    if (Test-Path (Join-Path $script:DeliveryRepo '.github/workflows/incidentpilot-ci.yml')) { $paths += '.github/workflows/incidentpilot-ci.yml' }
    return @(foreach ($path in ($paths | Sort-Object -Unique)) {
        $file = Join-Path $script:DeliveryRepo $path
        Assert-Delivery (((Get-Item -LiteralPath $file -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'SOURCE_LINK'
        [ordered]@{path=$path;sha256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash}
    })
}
function Get-DeliveryCandidate([string]$Directory) {
    # 局部导入，避免候选脚本的参数绑定覆盖门禁入口参数。
    $directory = [IO.Path]::GetFullPath($Directory)
    . (Join-Path $PSScriptRoot 'new-release-candidate.ps1') -Library
    Assert-ReleaseNoLink $directory
    $hashes = [ordered]@{}
    foreach ($name in @('source.zip','source-manifest.json','candidate.json','SHA256SUMS')) {
        $hashes[$name] = Get-ReleaseFileHash (Join-Path $directory $name)
    }
    $verified = Test-ReleaseCandidate $directory
    Assert-Delivery ($verified.candidateId -is [string] -and
        $verified.candidateId -cmatch '^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$' -and
        $verified.baselineHead -is [string] -and $verified.baselineHead -cmatch '^[a-f0-9]{40}$') 'CANDIDATE_IDENTITY_INVALID'
    $manifest = Get-Content -LiteralPath (Join-Path $directory 'source-manifest.json') -Raw | ConvertFrom-Json
    Assert-Delivery ($verified.manifestSha256 -ceq $hashes['source-manifest.json']) 'CANDIDATE_CHANGED'
    Assert-ReleaseNoLink $script:DeliveryRepo
    $members = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach ($record in $manifest.files) { $members.Add($record.path, $record) }
    # 只剪枝已知生成根，不能把打包排除规则当作额外源码的忽略规则。
    $generated = @('frontend/node_modules','frontend/dist','frontend/.output/incident','.git') + @(
        foreach ($module in @('framework','infra-ai','system','incident','agent','rag','mcp-server','bootstrap')) { "$module/target" })
    $directories = [Collections.Generic.Stack[string]]::new()
    $directories.Push($script:DeliveryRepo)
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    while ($directories.Count) {
        foreach ($item in Get-ChildItem -LiteralPath $directories.Pop() -Force) {
            Assert-Delivery (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'CANDIDATE_SOURCE_LINK'
            $relative = [IO.Path]::GetRelativePath($script:DeliveryRepo, $item.FullName).Replace('\','/')
            if ($item.PSIsContainer) {
                if ($relative -cnotin $generated) { $directories.Push($item.FullName) }
            } else {
                Assert-Delivery ($members.ContainsKey($relative) -and $seen.Add($relative)) 'CANDIDATE_SOURCE_SET_INVALID'
                $record = $members[$relative]
                Assert-Delivery ($item.Length -eq $record.bytes -and
                    (Get-ReleaseFileHash $item.FullName) -ceq $record.sha256) 'CANDIDATE_SOURCE_MISMATCH'
            }
        }
    }
    Assert-Delivery ($seen.Count -eq $members.Count) 'CANDIDATE_SOURCE_SET_INVALID'
    foreach ($name in $hashes.Keys) {
        Assert-ReleaseNoLink (Join-Path $directory $name)
        Assert-Delivery ((Get-ReleaseFileHash (Join-Path $directory $name)) -ceq $hashes[$name]) 'CANDIDATE_CHANGED'
    }
    return [ordered]@{kind='SOURCE_CANDIDATE';candidateId=$verified.candidateId;
        manifestSha256=$hashes['source-manifest.json'];metadataSha256=$hashes['candidate.json'];
        zipSha256=$hashes['source.zip'];checksumsSha256=$hashes['SHA256SUMS'];
        baselineHead=$verified.baselineHead;fileCount=$members.Count}
}
function Test-DeliverySafety {
    foreach ($name in @('MAVEN_OPTS','MAVEN_ARGS','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS',
        'DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH','DOCKER_CONFIG',
        'TESTCONTAINERS_HOST_OVERRIDE','TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE','TESTCONTAINERS_REUSE_ENABLE',
        'TESTCONTAINERS_RYUK_DISABLED','TESTCONTAINERS_DOCKER_CLIENT_STRATEGY')) {
        Assert-Delivery ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name))) 'UNSAFE_ENVIRONMENT'
    }
    foreach ($path in @('.mvn/jvm.config','.mvn/maven.config','.testcontainers.properties')) {
        Assert-Delivery (!(Test-Path (Join-Path $script:DeliveryRepo $path))) 'UNREVIEWED_CONFIG'
    }
    $userConfig = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.testcontainers.properties'
    if (Test-Path $userConfig) {
        # Testcontainers 自动保存的本地 provider 缓存可接受；连接覆盖/复用仍拒绝。
        $lines = @(Get-Content -LiteralPath $userConfig | Where-Object { $_.Trim() -and $_ -notmatch '^\s*[#!]' })
        foreach ($line in $lines) {
            Assert-Delivery ($line -match '^docker\.client\.strategy=org\.testcontainers\.dockerclient\.(NpipeSocketClientProviderStrategy|UnixSocketClientProviderStrategy|DockerDesktopClientProviderStrategy)$') 'UNREVIEWED_CONFIG'
        }
    }
}
function Invoke-DeliveryCommand([string]$Executable, [string[]]$Arguments, [string]$WorkingDirectory,
        [string]$Log, [int]$TimeoutSeconds) {
    Assert-DeliveryPath $Log
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.CreateNoWindow = $true
    if ($IsWindows -and $Executable.EndsWith('.cmd')) {
        # 固定文件/参数；cmd 元字符及变量展开不可进入调用。
        foreach ($arg in @($Executable) + $Arguments) { Assert-Delivery ($arg -notmatch '["%&|<>^\r\n]') 'COMMAND_ARGUMENT_INVALID' }
        # 完整路径保证批处理的%~dp0定位自身目录；PATH解析后的路径仍须拒绝元字符。
        $Executable = (Get-Command -Name $Executable -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
        Assert-Delivery ($Executable -notmatch '["%&|<>^\r\n]') 'COMMAND_ARGUMENT_INVALID'
        $info.FileName = 'cmd.exe'
        $info.Arguments = '/d /s /c "' + ((@($Executable) + $Arguments | ForEach-Object { '"' + $_ + '"' }) -join ' ') + '"'
    } else {
        $info.FileName = $Executable
        foreach ($arg in $Arguments) { $info.ArgumentList.Add($arg) }
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        $null = $process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit($TimeoutSeconds * 1000)) {
            $process.Kill($true)
            $process.WaitForExit()
            [IO.File]::WriteAllText($Log, $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
            throw 'STAGE_TIMEOUT'
        }
        [IO.File]::WriteAllText($Log, $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
        return $process.ExitCode
    } finally { $process.Dispose() }
}
function Get-DeliveryTools([string]$Scope, [string]$Root, [bool]$SourceCandidate = $false) {
    Test-DeliverySafety
    $versions = [ordered]@{powershell=$PSVersionTable.PSVersion.ToString()}
    $required = @(if (!$SourceCandidate) { 'git' })
    if ($Scope -ne 'Frontend') { $required += @('java','keytool','docker') }
    if ($Scope -in @('Frontend','All')) { $required += @('node',$(if ($IsWindows) {'npm.cmd'} else {'npm'})) }
    foreach ($tool in $required) { Assert-Delivery ($null -ne (Get-Command $tool -ErrorAction SilentlyContinue)) 'TOOL_MISSING' }
    if ($Scope -ne 'Frontend') {
        $wrapper = Join-Path $script:DeliveryRepo $(if ($IsWindows) {'mvnw.cmd'} else {'mvnw'})
        Assert-Delivery (Test-Path $wrapper) 'WRAPPER_MISSING'
        $code = Invoke-DeliveryCommand 'java' @('-version') $script:DeliveryRepo "$Root/raw/java.log" 30
        Assert-Delivery ($code -eq 0) 'JAVA_UNAVAILABLE'
        $javaText = Get-Content "$Root/raw/java.log" -Raw
        $version = [regex]::Match($javaText,'version "([0-9.]+)').Groups[1].Value
        Assert-Delivery ($version -match '^21\.') 'JAVA_VERSION_UNSUPPORTED'
        $versions.java = $version
        $code = Invoke-DeliveryCommand $wrapper @('-version') $script:DeliveryRepo "$Root/raw/maven.log" 120
        Assert-Delivery ($code -eq 0) 'MAVEN_UNAVAILABLE'
        $versions.maven = [regex]::Match((Get-Content "$Root/raw/maven.log" -Raw),'Apache Maven ([0-9.]+)').Groups[1].Value
        $code = Invoke-DeliveryCommand 'docker' @('context','inspect','--format','{{.Endpoints.docker.Host}}') $script:DeliveryRepo "$Root/raw/docker-context.log" 30
        Assert-Delivery ($code -eq 0) 'DOCKER_UNAVAILABLE'
        $endpoint = (Get-Content "$Root/raw/docker-context.log" -Raw).Trim()
        Assert-Delivery ($endpoint -match '^(unix:///[^\r\n]+|npipe:////\./pipe/docker[^\r\n]+)$') 'REMOTE_DOCKER_REJECTED'
        $code = Invoke-DeliveryCommand 'docker' @('info','--format','{{.OSType}} {{.ServerVersion}}') $script:DeliveryRepo "$Root/raw/docker.log" 30
        Assert-Delivery ($code -eq 0) 'DOCKER_UNAVAILABLE'
        $dockerText = (Get-Content "$Root/raw/docker.log" -Raw).Trim()
        Assert-Delivery ($dockerText -match '^linux ([0-9.]+)$') 'DOCKER_ENGINE_UNSUPPORTED'
        $versions.docker = $Matches[1]
    }
    if ($Scope -in @('Frontend','All')) {
        $npm = if ($IsWindows) {'npm.cmd'} else {'npm'}
        foreach ($tool in @('node',$npm)) {
            $code = Invoke-DeliveryCommand $tool @('--version') $script:DeliveryRepo "$Root/raw/$tool.log" 30
            Assert-Delivery ($code -eq 0) 'NODE_UNAVAILABLE'
            $value = (Get-Content "$Root/raw/$tool.log" -Raw).Trim().TrimStart('v')
            Assert-Delivery ($value -match '^\d+\.\d+\.\d+$') 'TOOL_VERSION_INVALID'
            $versions[$tool -replace '\.cmd$',''] = $value
        }
        Assert-Delivery ([int]($versions.node.Split('.')[0]) -ge 20) 'NODE_VERSION_UNSUPPORTED'
    }
    return $versions
}
function Get-DeliveryRequired {
    return @('com.nageoffer.ai.ragent.rag.core.rewrite.QueryTermMappingServiceTest',
        'com.nageoffer.ai.ragent.rag.rewrite.MultiQuestionRewriteServiceTests',
        'com.nageoffer.ai.ragent.rag.rewrite.MultiQuestionRewriteWindowTest',
        'com.nageoffer.ai.ragent.rag.service.impl.ConversationMessageServiceImplOrderTest',
        'com.nageoffer.ai.ragent.ingestion.service.impl.IntentTreeServiceImplTest',
        'com.nageoffer.ai.ragent.rag.core.intent.DefaultIntentClassifierTest',
        'com.nageoffer.ai.ragent.infra.embedding.SiliconFlowEmbeddingClientTest',
        'com.nageoffer.ai.ragent.rag.core.vector.MilvusVectorStoreAdminTest',
        'com.nageoffer.ai.ragent.BootstrapIsolatedStartupTest',
        'com.nageoffer.ai.ragent.IncidentDiagnosisGoldenPathTest',
        'com.nageoffer.ai.ragent.IncidentDiagnosisSafetyTest',
        'com.nageoffer.ai.ragent.IncidentObservabilityIsolatedTest',
        'com.nageoffer.ai.ragent.incident.integration.IncidentReliabilityAcceptanceTest',
        'com.nageoffer.ai.ragent.IncidentReliabilityRuntimeTest')
}
function Get-DeliveryAllowedSkips {
    # 当前源码的完整类名及禁用后方法数；参数化禁用容器在 XML 中记一项。
    return @{
        'com.nageoffer.ai.ragent.index.InvoiceIndexDocumentTests'=3
        'com.nageoffer.ai.ragent.rag.embedding.SiliconFlowEmbeddingServiceTests'=1
        'com.nageoffer.ai.ragent.rag.Intent.IntentTreeServiceTests'=2
        'com.nageoffer.ai.ragent.rag.Intent.SimpleIntentClassifierTests'=2
        'com.nageoffer.ai.ragent.rag.Intent.VectorTreeIntentClassifierTests'=4
        'com.nageoffer.ai.ragent.rag.rewrite.QueryRewriteTests'=2
        'com.nageoffer.ai.ragent.RagentCoreApplicationTests'=1
        'com.nageoffer.ai.ragent.service.ConversationMessageServiceTests'=1
        'com.nageoffer.ai.ragent.vector.MilvusCollectionTests'=1
        'com.nageoffer.ai.ragent.rag.core.retrieval.channel.WebSearchChannelLiveTest'=1
        'com.nageoffer.ai.ragent.core.parser.mineru.MinerUPdfUploadFlowTest'=1
        'com.nageoffer.ai.ragent.mcp.executor.common.YouComSearchMcpExecutorLiveTest'=1
        'com.nageoffer.ai.ragent.agent.admin.AgentDashboardReaderTest'=1
    }
}
function Read-DeliveryReports([string]$Root, [string]$Scope, [string]$RunId, [datetime]$StartedAt) {
    $files = @(Get-ChildItem -LiteralPath $Root -Recurse -File -Filter 'TEST-*.xml')
    Assert-Delivery ($files.Count -gt 0) 'REPORT_EMPTY'
    $counts = [ordered]@{tests=0;passed=0;failures=0;errors=0;skipped=0}
    $classes = @{}
    $allowed = Get-DeliveryAllowedSkips
    $skipList = @()
    foreach ($file in $files) {
        Assert-DeliveryPath $file.FullName
        Assert-Delivery ($file.LastWriteTimeUtc -ge $StartedAt.ToUniversalTime()) 'REPORT_STALE'
        $settings = [Xml.XmlReaderSettings]::new()
        $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
        $settings.XmlResolver = $null
        $reader = [Xml.XmlReader]::Create($file.FullName, $settings)
        try { $xml = [Xml.XmlDocument]::new(); $xml.XmlResolver = $null; $xml.Load($reader) }
        finally { $reader.Dispose() }
        $suite = $xml.DocumentElement
        Assert-Delivery ($suite.LocalName -eq 'testsuite') 'REPORT_STRUCTURE'
        $name = $suite.GetAttribute('name')
        Assert-Delivery (!$classes.ContainsKey($name)) 'REPORT_DUPLICATE_CLASS'
        $cases = @($suite.SelectNodes('testcase'))
        $numbers = @{}
        foreach ($field in @('tests','failures','errors','skipped')) {
            $value = $suite.GetAttribute($field)
            Assert-Delivery ($value -match '^\d+$') 'REPORT_COUNT_INVALID'
            $numbers[$field] = [int]$value
        }
        Assert-Delivery ($numbers.tests -eq $cases.Count -and $numbers.tests -gt 0) 'REPORT_COUNT_CONFLICT'
        Assert-Delivery (@($cases | ForEach-Object {$_.GetAttribute('name')} | Sort-Object -Unique).Count -eq $cases.Count) 'REPORT_DUPLICATE_CASE'
        foreach ($field in @('failure','error','skipped')) {
            $actual = @($cases | Where-Object { $null -ne $_.SelectSingleNode($field) }).Count
            $key = @{failure='failures';error='errors';skipped='skipped'}[$field]
            Assert-Delivery ($actual -eq $numbers[$key]) 'REPORT_COUNT_CONFLICT'
        }
        # JUnit @DisplayName 可改变 testcase.classname；规范身份只取 testsuite.name。
        Assert-Delivery (@($cases | Where-Object {[string]::IsNullOrEmpty($_.GetAttribute('classname')) -or
            $_.SelectNodes('failure|error|skipped').Count -gt 1}).Count -eq 0) 'REPORT_STRUCTURE'
        $origin = @($suite.SelectNodes("properties/property[@name='lab.runId']"))
        Assert-Delivery ($origin.Count -eq 1 -and $origin[0].GetAttribute('value') -eq $RunId) 'REPORT_SOURCE_MISMATCH'
        Assert-Delivery ($numbers.failures -eq 0 -and $numbers.errors -eq 0) 'TEST_FAILED'
        if ($numbers.skipped -gt 0) {
            Assert-Delivery ($Scope -eq 'Backend' -and $allowed.ContainsKey($name) -and
                $numbers.tests -eq $allowed[$name] -and $numbers.skipped -eq $allowed[$name]) 'UNEXPECTED_SKIP'
            $skipList += [ordered]@{class=$name;skipped=$numbers.skipped;reasonCode='MANUAL_EXTERNAL_GATE_CLOSED'}
        }
        $classes[$name] = $numbers
        foreach ($field in @('tests','failures','errors','skipped')) { $counts[$field] += $numbers[$field] }
    }
    $required = if ($Scope -eq 'Smoke') { @('com.nageoffer.ai.ragent.IncidentDiagnosisGoldenPathTest') } else { Get-DeliveryRequired }
    foreach ($name in $required) {
        Assert-Delivery ($classes.ContainsKey($name)) 'REQUIRED_TARGET_MISSING'
        Assert-Delivery ($classes[$name].skipped -eq 0) 'REQUIRED_TARGET_SKIPPED'
    }
    if ($Scope -eq 'Smoke') { Assert-Delivery ($files.Count -eq 1 -and $counts.tests -eq 1) 'SMOKE_TARGET_INVALID' }
    if ($Scope -eq 'Backend') {
        foreach ($name in $allowed.Keys) { Assert-Delivery ($classes.ContainsKey($name) -and $classes[$name].skipped -eq $allowed[$name]) 'MANUAL_CATALOGUE_MISMATCH' }
    }
    $counts.passed = $counts.tests - $counts.failures - $counts.errors - $counts.skipped
    return [ordered]@{counts=$counts;allowedSkips=@($skipList | Sort-Object class);reportClasses=$classes.Count}
}
function Get-DeliveryCommands([string]$Scope, [string]$Root, [string]$RunId, [string]$SourceDigest=('0'*64)) {
    $commands = @()
    if ($Scope -in @('Smoke','Backend','All')) {
        $mode = if ($Scope -eq 'Smoke') {'Smoke'} else {'Backend'}
        $args = @('-Dspotless.apply.skip=true',"-Dlab.reportRoot=$Root/reports", "-Dlab.proofRoot=$Root/proof",
            "-Dlab.runId=$RunId", "-Dlab.scenarioId=DELIVERY-$($mode.ToUpperInvariant())",
            "-Dlab.sourceDigest=$SourceDigest",'-Ddelivery.gates=true')
        if ($mode -eq 'Smoke') { $args += @('-pl','bootstrap','-am','-Dtest=IncidentDiagnosisGoldenPathTest','-Dsurefire.failIfNoSpecifiedTests=false') }
        $args += 'test'
        $commands += [ordered]@{stage=$mode;executable=(Join-Path $script:DeliveryRepo $(if ($IsWindows) {'mvnw.cmd'} else {'mvnw'}));arguments=$args;cwd=$script:DeliveryRepo;timeout=5400}
    }
    if ($Scope -in @('Frontend','All')) {
        $npm = if ($IsWindows) {'npm.cmd'} else {'npm'}
        foreach ($item in @(@{stage='Install';exe=$npm;args=@('ci','--no-audit','--no-fund')},
            @{stage='IncidentView';exe='node';args=@('tests/incident-view.check.mjs')},
            @{stage='Lint';exe=$npm;args=@('run','lint')},@{stage='Build';exe=$npm;args=@('run','build')})) {
            $commands += [ordered]@{stage=$item.stage;executable=$item.exe;arguments=$item.args;cwd=(Join-Path $script:DeliveryRepo 'frontend');timeout=1200}
        }
    }
    return $commands
}
function Get-DeliverySafe($Run) {
    # 构造白名单，不复制任何原始对象、路径、异常或自由文本。
    foreach ($key in @('runId','scope','state','reasonCode','sourceDigest')) {
        Assert-Delivery ($Run[$key] -is [string]) 'SAFE_INVALID'
    }
    $stages = @(foreach ($stage in $Run.stages) {
        Assert-Delivery ($stage.stage -is [string] -and $stage.stage -in @('Smoke','Backend','Install','IncidentView','Lint','Build')) 'SAFE_INVALID'
        Assert-Delivery ($stage.reasonCode -is [string] -and $stage.reasonCode -in @('VALIDATED','COMMAND_FAILED','REPORT_INVALID','STAGE_TIMEOUT','STAGE_UNAVAILABLE')) 'SAFE_INVALID'
        Assert-Delivery ($null -eq $stage.exitCode -or ($stage.exitCode -is [int] -and $stage.exitCode -ge 0)) 'SAFE_INVALID'
        $safeStage = [ordered]@{stage=$stage.stage;exitCode=$stage.exitCode;reasonCode=$stage.reasonCode}
        if ($stage.validation) {
            $safeStage.counts = [ordered]@{}
            foreach ($key in @('tests','passed','failures','errors','skipped')) { $safeStage.counts[$key] = [int]$stage.validation.counts[$key] }
            Assert-Delivery (@($safeStage.counts.Values | Where-Object {$_ -lt 0}).Count -eq 0 -and
                $safeStage.counts.tests -eq ($safeStage.counts.passed+$safeStage.counts.failures+$safeStage.counts.errors+$safeStage.counts.skipped)) 'SAFE_INVALID'
            $safeStage.allowedSkips = @(foreach ($skip in $stage.validation.allowedSkips) {
                Assert-Delivery ((Get-DeliveryAllowedSkips).ContainsKey($skip.class)) 'SAFE_INVALID'
                [ordered]@{class=$skip.class;skipped=[int]$skip.skipped;reasonCode='MANUAL_EXTERNAL_GATE_CLOSED'}
            })
        }
        $safeStage
    })
    Assert-Delivery ($Run.state -in @('PASS','FAIL','BLOCKED','CHECKED','INCOMPLETE') -and
        $Run.scope -in @('Smoke','Backend','Frontend','All') -and $Run.reasonCode -in
        @('VALIDATED','PRECONDITIONS_CHECKED','PRECONDITION_FAILED','GATE_FAILED','INCOMPLETE')) 'SAFE_INVALID'
    Assert-Delivery ($Run.runId -match '^[a-f0-9-]{36}$' -and $Run.sourceDigest -match '^[A-F0-9]{64}$') 'SAFE_INVALID'
    $candidate = $null
    if ($Run.sourceCandidate) {
        $origin = $Run.sourceCandidate
        Assert-Delivery ($Run.schemaVersion -is [int] -and $Run.schemaVersion -eq 2 -and $null -eq $Run.head -and
            $origin.kind -is [string] -and $origin.kind -ceq 'SOURCE_CANDIDATE' -and
            $origin.candidateId -is [string] -and $origin.candidateId -cmatch '^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$' -and
            $origin.baselineHead -is [string] -and $origin.baselineHead -cmatch '^[a-f0-9]{40}$' -and
            $origin.fileCount -is [int] -and $origin.fileCount -gt 0) 'SAFE_INVALID'
        $candidate = [ordered]@{kind='SOURCE_CANDIDATE';candidateId=$origin.candidateId}
        foreach ($key in @('manifestSha256','metadataSha256','zipSha256','checksumsSha256')) {
            Assert-Delivery ($origin[$key] -is [string] -and $origin[$key] -cmatch '^[A-F0-9]{64}$') 'SAFE_INVALID'
            $candidate[$key] = $origin[$key]
        }
        $candidate.baselineHead = $origin.baselineHead
        $candidate.fileCount = $origin.fileCount
    } else { Assert-Delivery ($Run.head -is [string] -and $Run.head -match '^[a-f0-9]{40}$') 'SAFE_INVALID' }
    $versions = [ordered]@{}
    foreach ($key in @('powershell','java','maven','docker','node','npm')) {
        if ($Run.toolVersions.Contains($key)) {
            Assert-Delivery ($Run.toolVersions[$key] -is [string] -and $Run.toolVersions[$key] -match '^\d+(\.\d+){1,3}$') 'SAFE_INVALID'
            $versions[$key] = $Run.toolVersions[$key]
        }
    }
    $safe = [ordered]@{schemaVersion=$(if ($candidate) {2} else {1});runId=$Run.runId;scope=$Run.scope;state=$Run.state;reasonCode=$Run.reasonCode;
        head=$Run.head;sourceDigest=$Run.sourceDigest;startedAt=([datetime]$Run.startedAt).ToUniversalTime().ToString('o');
        finishedAt=([datetime]$Run.finishedAt).ToUniversalTime().ToString('o');toolVersions=$versions;stages=$stages}
    if ($candidate) { $safe.sourceCandidate = $candidate }
    return $safe
}
function Invoke-Delivery([string]$Scope, [bool]$OnlyCheck, [string]$SourceCandidatePath = '') {
    $saved = @{}
    foreach ($name in @('RAG_LIVE_TESTS','YDC_API_KEY')) { $saved[$name] = [Environment]::GetEnvironmentVariable($name) }
    $root = $null; $code = 1
    $run = [ordered]@{schemaVersion=1;runId=[guid]::NewGuid().ToString();scope=$Scope;state='INCOMPLETE';reasonCode='INCOMPLETE';
        startedAt=[datetime]::UtcNow.ToString('o');finishedAt=$null;head=$null;sourceDigest=$null;toolVersions=[ordered]@{};stages=@()}
    try {
        Assert-Delivery ($Scope -in @('Smoke','Backend','Frontend','All')) 'SCOPE_INVALID'
        $root = Join-Path $script:DeliveryBase $run.runId
        Assert-DeliveryPath $root
        Assert-Delivery (!(Test-Path -LiteralPath $root)) 'RUN_ALREADY_EXISTS'
        $null = New-Item -ItemType Directory -Path $root
        foreach ($directory in @('raw','reports','proof')) { $null = New-Item -ItemType Directory -Path "$root/$directory" }
        if ($SourceCandidatePath) {
            $run.sourceCandidate = Get-DeliveryCandidate $SourceCandidatePath
            $run.schemaVersion = 2
        } else {
            $gitRoot = (git -C $script:DeliveryRepo rev-parse --show-toplevel)
            Assert-Delivery ($LASTEXITCODE -eq 0 -and
                [IO.Path]::GetFullPath($gitRoot) -eq $script:DeliveryRepo) 'GIT_ROOT_MISMATCH'
            $run.head = (git -C $script:DeliveryRepo rev-parse HEAD)
            Assert-Delivery ($LASTEXITCODE -eq 0 -and $run.head -match '^[a-f0-9]{40}$') 'GIT_UNAVAILABLE'
        }
        $sources = @(Get-DeliverySources)
        Write-DeliveryJson "$root/source.json" $sources
        $run.sourceDigest = (Get-FileHash "$root/source.json").Hash
        Write-DeliveryJson "$root/run.json" $run
        [Environment]::SetEnvironmentVariable('RAG_LIVE_TESTS','false')
        [Environment]::SetEnvironmentVariable('YDC_API_KEY',$null)
        try { $run.toolVersions = Get-DeliveryTools $Scope $root ([bool]$SourceCandidatePath) }
        catch { $run.state='BLOCKED'; $run.reasonCode='PRECONDITION_FAILED'; $code=2; throw }
        if ($OnlyCheck) { $run.state='CHECKED'; $run.reasonCode='PRECONDITIONS_CHECKED'; $code=0 }
        else {
            $run.state='RUNNING'; Write-DeliveryJson "$root/run.json" $run
            $code=0
            $frontendDeadline=$null
            foreach ($command in @(Get-DeliveryCommands $Scope $root $run.runId $run.sourceDigest)) {
                $stage = [ordered]@{stage=$command.stage;startedAt=[datetime]::UtcNow.ToString('o');finishedAt=$null;
                    exitCode=$null;reasonCode='STAGE_UNAVAILABLE';validation=$null;
                    command=@($command.executable)+$command.arguments}
                try {
                    if ($command.stage -notin @('Smoke','Backend')) {
                        if (!$frontendDeadline) { $frontendDeadline=[datetime]::UtcNow.AddMinutes(20) }
                        $command.timeout=[int][Math]::Floor(($frontendDeadline-[datetime]::UtcNow).TotalSeconds)
                        Assert-Delivery ($command.timeout -gt 0) 'STAGE_TIMEOUT'
                    }
                    $stage.exitCode = Invoke-DeliveryCommand $command.executable $command.arguments $command.cwd "$root/raw/$($command.stage).log" $command.timeout
                    $stage.reasonCode = 'COMMAND_FAILED'
                    Assert-Delivery ($stage.exitCode -eq 0) 'COMMAND_FAILED'
                    if ($command.stage -in @('Smoke','Backend')) {
                        $stage.reasonCode='REPORT_INVALID'
                        $stage.validation = Read-DeliveryReports "$root/reports" $command.stage $run.runId ([datetime]$stage.startedAt)
                    }
                    $stage.reasonCode='VALIDATED'
                } catch {
                    if ($_.Exception.Message -eq 'STAGE_TIMEOUT') { $stage.reasonCode='STAGE_TIMEOUT' }
                    $code=1
                } finally {
                    $stage.finishedAt=[datetime]::UtcNow.ToString('o')
                    $run.stages += $stage
                    Write-DeliveryJson "$root/stages.json" @($run.stages)
                }
            }
            Assert-Delivery (($sources | ConvertTo-Json -Depth 5 -Compress) -eq
                (@(Get-DeliverySources) | ConvertTo-Json -Depth 5 -Compress)) 'SOURCE_CHANGED'
            $run.state = if ($code -eq 0) {'PASS'} else {'FAIL'}
            $run.reasonCode = if ($code -eq 0) {'VALIDATED'} else {'GATE_FAILED'}
        }
        if ($SourceCandidatePath) {
            Assert-Delivery (($run.sourceCandidate | ConvertTo-Json -Compress) -ceq
                ((Get-DeliveryCandidate $SourceCandidatePath) | ConvertTo-Json -Compress)) 'CANDIDATE_CHANGED'
        }
    } catch {
        if ($run.state -ne 'BLOCKED') { $run.state='FAIL'; $run.reasonCode='GATE_FAILED'; $code=1 }
    } finally {
        foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name]) }
        $run.finishedAt=[datetime]::UtcNow.ToString('o')
        if ($root -and (Test-Path -LiteralPath $root)) {
            try {
                Write-DeliveryJson "$root/run.json" $run
                Write-DeliveryJson "$root/summary.json" $run
                $safe = Get-DeliverySafe $run
                $null = New-Item -ItemType Directory -Path "$root/safe"
                Write-DeliveryJson "$root/safe/summary.json" $safe
                $artifacts = @(Get-ChildItem -LiteralPath $root -Recurse -File | ForEach-Object {
                    Assert-DeliveryPath $_.FullName
                    [ordered]@{path=[IO.Path]::GetRelativePath($root,$_.FullName).Replace('\','/');
                        sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
                })
                Write-DeliveryJson "$root/artifacts.json" $artifacts
            } catch {
                $code=1; $run.state='FAIL'; $run.reasonCode='GATE_FAILED'
                # 摘要失败不能留下可上传的部分文件；只移除本轮自有摘要。
                $safePath=Join-Path $root 'safe/summary.json'
                if (Test-Path -LiteralPath $safePath) { Assert-DeliveryPath $safePath; Remove-Item -LiteralPath $safePath }
                try { Write-DeliveryJson "$root/run.json" $run; Write-DeliveryJson "$root/summary.json" $run } catch { }
            }
        }
    }
    Write-Host "$($run.state) $($run.runId) $($run.reasonCode)"
    return $code
}
if ($MyInvocation.InvocationName -ne '.') { exit (Invoke-Delivery $Gate ([bool]$CheckOnly) $CandidatePath) }

# IncidentPilot 新增门禁契约；fixture 不启动容器或业务服务。
$ErrorActionPreference = 'Stop'
$entry = Join-Path $PSScriptRoot 'check-delivery.ps1'
if (!(Test-Path $entry)) { Write-Host 'ENTRY_MISSING'; exit 1 }
. $entry
$script:DeliveryBase = Join-Path $script:DeliveryBase 'contracts'
$script:checks = 0
function Check($condition, $label) {
    if (!$condition) { throw "CONTRACT_FAILED:$label" }
    $script:checks++
}
function Reject($action, $reason) {
    $caught = $null
    try { & $action } catch { $caught = $_.Exception.Message }
    Check ($caught -eq $reason) $reason
}
Check ($null -ne (Get-Command Invoke-Delivery -ErrorAction SilentlyContinue)) 'ENTRY_FUNCTION'
$fixture = Join-Path $script:DeliveryBase ('contract-' + [guid]::NewGuid())
Assert-DeliveryPath $fixture
$null = New-Item -ItemType Directory "$fixture/raw","$fixture/reports" -Force
$fixtureRun = [guid]::NewGuid().ToString()
$fixtureStart = [datetime]::UtcNow.AddMinutes(-1)
function Suite([string]$name, [int]$tests=1, [int]$skipped=0, [string]$origin=$fixtureRun) {
    $cases = (0..($tests-1) | ForEach-Object {
        $skip = if ($_ -lt $skipped) { '<skipped message="SECRET_FIXTURE"/>' } else { '' }
        "<testcase name='case$_' classname='$name'>$skip</testcase>"
    }) -join ''
    return "<testsuite name='$name' tests='$tests' failures='0' errors='0' skipped='$skipped'><properties><property name='lab.runId' value='$origin'/></properties>$cases<system-out>SECRET_FIXTURE</system-out></testsuite>"
}
function Report($name, $content) { $content | Set-Content -LiteralPath "$fixture/reports/TEST-$name.xml" }
$golden = 'com.nageoffer.ai.ragent.IncidentDiagnosisGoldenPathTest'
Reject { Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart } 'REPORT_EMPTY'
Report $golden (Suite $golden)
$ok = Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart
Check ($ok.counts.passed -eq 1 -and $ok.counts.skipped -eq 0) 'SMOKE_EXACT'
Report $golden ((Suite $golden) -replace "classname='$golden'", "classname='中文类显示名'")
Check ((Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart).counts.passed -eq 1) 'JUNIT_DISPLAY_NAME'
Report $golden ((Suite $golden) -replace "classname='$golden'", "classname=''")
Reject { Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart } 'REPORT_STRUCTURE'
Report $golden (Suite $golden)
Reject { Read-DeliveryReports "$fixture/reports" Smoke 'other-run' $fixtureStart } 'REPORT_SOURCE_MISMATCH'
Reject { Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun ([datetime]::UtcNow.AddDays(1)) } 'REPORT_STALE'
Report $golden (Suite $golden 1 1)
Reject { Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart } 'UNEXPECTED_SKIP'
Report $golden ((Suite $golden) -replace "tests='1'","tests='2'")
Reject { Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart } 'REPORT_COUNT_CONFLICT'
Report $golden ((Suite $golden) -replace "failures='0'","failures='1'")
Reject { Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart } 'REPORT_COUNT_CONFLICT'
Report $golden ((Suite $golden) -replace '</testcase>','<failure message="SECRET_FIXTURE"/></testcase>' -replace "failures='0'","failures='1'")
Reject { Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart } 'TEST_FAILED'
Report $golden (Suite $golden)
Report 'duplicate' (Suite $golden)
Reject { Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart } 'REPORT_DUPLICATE_CLASS'
# 只删除本契约创建的文件，绝不清理其他运行目录。
Remove-Item -LiteralPath "$fixture/reports/TEST-duplicate.xml"
Report $golden ('<!DOCTYPE testsuite [<!ENTITY secret SYSTEM "file:///unread">]>' + (Suite $golden))
$dtdRejected=$false
try { Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart | Out-Null } catch { $dtdRejected=$true }
Check $dtdRejected 'DTD_REJECTED'
Report $golden (Suite 'unrecognized.Class')
Reject { Read-DeliveryReports "$fixture/reports" Smoke $fixtureRun $fixtureStart } 'REQUIRED_TARGET_MISSING'
Report $golden (Suite $golden)
foreach ($required in (Get-DeliveryRequired)) { Report $required (Suite $required) }
foreach ($pair in (Get-DeliveryAllowedSkips).GetEnumerator()) { Report $pair.Key (Suite $pair.Key $pair.Value $pair.Value) }
$backend = Read-DeliveryReports "$fixture/reports" Backend $fixtureRun $fixtureStart
Check ($backend.counts.passed -eq (Get-DeliveryRequired).Count -and $backend.counts.skipped -eq 21) 'BACKEND_CATALOGUE'
Report 'surprise.Class' (Suite 'surprise.Class' 1 1)
Reject { Read-DeliveryReports "$fixture/reports" Backend $fixtureRun $fixtureStart } 'UNEXPECTED_SKIP'
Remove-Item -LiteralPath "$fixture/reports/TEST-surprise.Class.xml"
$manual = 'com.nageoffer.ai.ragent.rag.rewrite.QueryRewriteTests'
Report $manual (Suite $manual 3 3)
Reject { Read-DeliveryReports "$fixture/reports" Backend $fixtureRun $fixtureStart } 'UNEXPECTED_SKIP'
Report $manual (Suite $manual 2 2)
Remove-Item -LiteralPath "$fixture/reports/TEST-$golden.xml"
Reject { Read-DeliveryReports "$fixture/reports" Backend $fixtureRun $fixtureStart } 'REQUIRED_TARGET_MISSING'
Reject { Assert-DeliveryPath (Join-Path $script:DeliveryRepo 'elsewhere/result.json') } 'OUTPUT_OUTSIDE_ROOT'
Reject { Assert-DeliveryPath (Join-Path $script:DeliveryBase '../outside/result.json') } 'OUTPUT_OUTSIDE_ROOT'
# 候选来源使用真实四文件校验器；源码树独立于当前Git，不替换校验函数。
function New-DeliveryCandidateFixture([string]$Name) {
    . (Join-Path $PSScriptRoot 'new-release-candidate.ps1') -Library
    $candidateDirectory=Join-Path $fixture "candidates/$Name"
    $sourceDirectory=Join-Path $fixture "trees/$Name"
    $null=New-Item -ItemType Directory -Path $candidateDirectory,$sourceDirectory
    $paths=@(Get-ReleaseRequiredPaths)+@('docs/controlled.txt','scripts/check-delivery.ps1',
        'scripts/test-check-delivery.ps1','.mvn/wrapper/maven-wrapper.properties')
    $records=@()
    $zip=[IO.Compression.ZipFile]::Open((Join-Path $candidateDirectory 'source.zip'),[IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach($path in $paths) {
            $bytes=[Text.Encoding]::UTF8.GetBytes("controlled content: $path")
            $absolute=Join-Path $sourceDirectory $path
            $null=New-Item -ItemType Directory -Path (Split-Path $absolute -Parent) -Force
            [IO.File]::WriteAllBytes($absolute,$bytes)
            $member=$zip.CreateEntry($path);$stream=$member.Open()
            try {$stream.Write($bytes,0,$bytes.Length)} finally {$stream.Dispose()}
            $records+=@{path=$path;bytes=$bytes.Length;sha256=(Get-ReleaseHash $bytes)}
        }
    } finally {$zip.Dispose()}
    Write-ReleaseJson (Join-Path $candidateDirectory 'source-manifest.json') @{schemaVersion=1;version='0.1.0';files=$records}
    $candidateId=[guid]::NewGuid().ToString()
    Write-ReleaseJson (Join-Path $candidateDirectory 'candidate.json') @{schemaVersion=1;version='0.1.0';state='SOURCE_CANDIDATE_READY';
        candidateId=$candidateId;baselineHead=('a'*40);baselineBranch='feature/incident-platform';
        upstream='https://github.com/nageoffer/ragent';license='Apache-2.0';fileCount=$records.Count;
        sourceManifestSha256=(Get-ReleaseFileHash (Join-Path $candidateDirectory 'source-manifest.json'));
        history='WITHHELD_POTENTIAL_HISTORICAL_SECRETS';historyPreservedInOriginalRepository=$true}
    Update-DeliveryCandidateFixtureSums $candidateDirectory
    return [ordered]@{candidate=$candidateDirectory;source=$sourceDirectory;candidateId=$candidateId;fileCount=$records.Count}
}
function Update-DeliveryCandidateFixtureSums([string]$Directory) {
    [IO.File]::WriteAllLines((Join-Path $Directory 'SHA256SUMS'),[string[]]@(foreach($name in @('source.zip','source-manifest.json','candidate.json')) {
        "$((Get-FileHash -LiteralPath (Join-Path $Directory $name) -Algorithm SHA256).Hash)  $name"
    }),[Text.UTF8Encoding]::new($false))
}
function Read-DeliveryCandidateFixture($Value) {
    $savedRepo=$script:DeliveryRepo
    try {$script:DeliveryRepo=$Value.source;return Get-DeliveryCandidate $Value.candidate}
    finally {$script:DeliveryRepo=$savedRepo}
}
$candidateFixture=New-DeliveryCandidateFixture 'complete'
$candidateIdentity=Read-DeliveryCandidateFixture $candidateFixture
Check ($candidateIdentity.kind -ceq 'SOURCE_CANDIDATE' -and $candidateIdentity.candidateId -ceq $candidateFixture.candidateId -and
    $candidateIdentity.fileCount -eq $candidateFixture.fileCount -and $candidateIdentity.baselineHead -ceq ('a'*40)) 'CANDIDATE_EXACT_TREE'
foreach($pair in @{manifestSha256='source-manifest.json';metadataSha256='candidate.json';zipSha256='source.zip';checksumsSha256='SHA256SUMS'}.GetEnumerator()) {
    Check ($candidateIdentity[$pair.Key] -ceq (Get-FileHash -LiteralPath (Join-Path $candidateFixture.candidate $pair.Value)).Hash) "CANDIDATE_DIGEST_$($pair.Key)"
}
$candidateSource=Join-Path $candidateFixture.source 'docs/controlled.txt'
$candidateBytes=[IO.File]::ReadAllBytes($candidateSource)
[IO.File]::WriteAllText((Join-Path $candidateFixture.source 'docs/untracked.txt'),'unexpected source')
Reject {Read-DeliveryCandidateFixture $candidateFixture} 'CANDIDATE_SOURCE_SET_INVALID'
Remove-Item -LiteralPath (Join-Path $candidateFixture.source 'docs/untracked.txt')
[IO.File]::WriteAllText((Join-Path $candidateFixture.source 'frontend/.env.local'),'unreviewed config')
Reject {Read-DeliveryCandidateFixture $candidateFixture} 'CANDIDATE_SOURCE_SET_INVALID'
Remove-Item -LiteralPath (Join-Path $candidateFixture.source 'frontend/.env.local')
Move-Item -LiteralPath $candidateSource -Destination (Join-Path $fixture 'controlled.saved')
Reject {Read-DeliveryCandidateFixture $candidateFixture} 'CANDIDATE_SOURCE_SET_INVALID'
Move-Item -LiteralPath (Join-Path $fixture 'controlled.saved') -Destination $candidateSource
$changedBytes=[byte[]]$candidateBytes.Clone();$changedBytes[0]=$changedBytes[0] -bxor 1
[IO.File]::WriteAllBytes($candidateSource,$changedBytes)
Reject {Read-DeliveryCandidateFixture $candidateFixture} 'CANDIDATE_SOURCE_MISMATCH'
[IO.File]::AppendAllText($candidateSource,'longer')
Reject {Read-DeliveryCandidateFixture $candidateFixture} 'CANDIDATE_SOURCE_MISMATCH'
[IO.File]::WriteAllBytes($candidateSource,$candidateBytes)
$casePath=Join-Path $candidateFixture.source 'docs/Controlled.txt'
Move-Item -LiteralPath $candidateSource -Destination (Join-Path $fixture 'case.saved')
Move-Item -LiteralPath (Join-Path $fixture 'case.saved') -Destination $casePath
Reject {Read-DeliveryCandidateFixture $candidateFixture} 'CANDIDATE_SOURCE_SET_INVALID'
Move-Item -LiteralPath $casePath -Destination (Join-Path $fixture 'case.saved')
Move-Item -LiteralPath (Join-Path $fixture 'case.saved') -Destination $candidateSource
# 仅已声明的构建输出剪枝；其他层级同名目录不能掩盖额外输入。
foreach($relative in @('framework/target','infra-ai/target','system/target','incident/target','agent/target','rag/target',
    'mcp-server/target','bootstrap/target','frontend/node_modules','frontend/dist','.git')) {
    $directory=Join-Path $candidateFixture.source $relative;$null=New-Item -ItemType Directory -Path $directory -Force
    [IO.File]::WriteAllText((Join-Path $directory 'generated.txt'),'generated output')
}
Check ((Read-DeliveryCandidateFixture $candidateFixture).candidateId -ceq $candidateFixture.candidateId) 'DECLARED_OUTPUTS_IGNORED'
$incidentOutput=Join-Path $candidateFixture.source 'frontend/.output/incident/lib'
$null=New-Item -ItemType Directory -Path $incidentOutput -Force
[IO.File]::WriteAllText((Join-Path $incidentOutput 'incidentView.js'),'generated incident view')
Check ((Read-DeliveryCandidateFixture $candidateFixture).candidateId -ceq $candidateFixture.candidateId) 'INCIDENT_VIEW_GENERATED_OUTPUT_IGNORED'
$otherOutput=Join-Path $candidateFixture.source 'frontend/.output/other'
$null=New-Item -ItemType Directory -Path $otherOutput
[IO.File]::WriteAllText((Join-Path $otherOutput 'unexpected.txt'),'not an approved generated root')
Reject {Read-DeliveryCandidateFixture $candidateFixture} 'CANDIDATE_SOURCE_SET_INVALID'
Remove-Item -LiteralPath (Join-Path $otherOutput 'unexpected.txt');Remove-Item -LiteralPath $otherOutput
$nestedOutput=Join-Path $candidateFixture.source 'docs/target';$null=New-Item -ItemType Directory -Path $nestedOutput
[IO.File]::WriteAllText((Join-Path $nestedOutput 'unexpected.txt'),'source outside output roots')
Reject {Read-DeliveryCandidateFixture $candidateFixture} 'CANDIDATE_SOURCE_SET_INVALID'
Remove-Item -LiteralPath (Join-Path $nestedOutput 'unexpected.txt');Remove-Item -LiteralPath $nestedOutput
$linkTarget=Join-Path $fixture 'link-target';$null=New-Item -ItemType Directory -Path $linkTarget
[IO.File]::WriteAllText((Join-Path $linkTarget 'linked.txt'),'outside source')
$sourceLink=Join-Path $candidateFixture.source 'docs/linked'
$null=New-Item -ItemType $(if($IsWindows){'Junction'}else{'SymbolicLink'}) -Path $sourceLink -Target $linkTarget
try {Reject {Read-DeliveryCandidateFixture $candidateFixture} 'CANDIDATE_SOURCE_LINK'} finally {Remove-Item -LiteralPath $sourceLink}
# 四文件本身的完整性必须先通过，不能只读取manifest声明。
$tamperedCandidate=New-DeliveryCandidateFixture 'tampered-artifact'
[IO.File]::AppendAllText((Join-Path $tamperedCandidate.candidate 'source.zip'),'tampered')
Reject {Read-DeliveryCandidateFixture $tamperedCandidate} 'ARTIFACT_HASH_MISMATCH'
$candidateMetadata=Join-Path $candidateFixture.candidate 'candidate.json'
$originalMetadata=[IO.File]::ReadAllBytes($candidateMetadata)
$replacementMetadata=Get-Content -LiteralPath $candidateMetadata -Raw|ConvertFrom-Json
$replacementMetadata.candidateId=[guid]::NewGuid().ToString()
[IO.File]::WriteAllText($candidateMetadata,($replacementMetadata|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
Update-DeliveryCandidateFixtureSums $candidateFixture.candidate
Check ((Read-DeliveryCandidateFixture $candidateFixture).candidateId -ceq $replacementMetadata.candidateId) 'VALID_REPLACEMENT_HAS_NEW_IDENTITY'
[IO.File]::WriteAllBytes($candidateMetadata,$originalMetadata)
Update-DeliveryCandidateFixtureSums $candidateFixture.candidate
foreach($field in @('candidateId','baselineHead')) {
    $arrayMetadata=Get-Content -LiteralPath $candidateMetadata -Raw|ConvertFrom-Json
    $arrayMetadata.$field=@($arrayMetadata.$field,'SECRET_FIXTURE')
    [IO.File]::WriteAllText($candidateMetadata,($arrayMetadata|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
    Update-DeliveryCandidateFixtureSums $candidateFixture.candidate
    Reject {Read-DeliveryCandidateFixture $candidateFixture} 'CANDIDATE_IDENTITY_INVALID'
    [IO.File]::WriteAllBytes($candidateMetadata,$originalMetadata)
    Update-DeliveryCandidateFixtureSums $candidateFixture.candidate
}
foreach ($scope in @('Smoke','Backend','Frontend','All')) {
    $commands = @(Get-DeliveryCommands $scope $fixture $fixtureRun)
    Check ($commands.Count -eq @{Smoke=1;Backend=1;Frontend=4;All=5}[$scope]) "COMMANDS_$scope"
    if ($scope -ne 'Frontend') {
        Check ($commands[0].arguments -contains 'test' -and $commands[0].arguments -contains '-Dspotless.apply.skip=true') "MAVEN_$scope"
        Check ($commands[0].arguments -contains ('-Dlab.scenarioId=DELIVERY-' + $(if ($scope -eq 'Smoke') {'SMOKE'} else {'BACKEND'})) -and
            $commands[0].arguments -contains ('-Dlab.sourceDigest=' + ('0'*64))) "PROOF_IDENTITY_$scope"
    }
    if ($scope -in @('Frontend','All')) {
        $front = @($commands | Where-Object stage -ne 'Backend')
        Check (($front.stage -join ',') -eq 'Install,IncidentView,Lint,Build') "ORDER_$scope"
        Check (($front[0].arguments -join ' ') -eq 'ci --no-audit --no-fund' -and
            $front[1].executable -eq 'node' -and $front[1].arguments[0] -eq 'tests/incident-view.check.mjs') "LOCAL_FRONTEND_$scope"
    }
}
# 真正的自有短子进程：退出码与raw隔离，日志不回显。
$pwsh = (Get-Process -Id $PID).Path
$exit = Invoke-DeliveryCommand $pwsh @('-NoProfile','-Command','Write-Output SECRET_FIXTURE; exit 7') $script:DeliveryRepo "$fixture/raw/process.log" 30
Check ($exit -eq 7 -and (Get-Content "$fixture/raw/process.log" -Raw).Contains('SECRET_FIXTURE')) 'REAL_EXIT_CAPTURE'
Reject { Invoke-DeliveryCommand $pwsh @('-NoProfile','-Command','Start-Sleep -Seconds 5') $script:DeliveryRepo "$fixture/raw/timeout.log" 1 } 'STAGE_TIMEOUT'
if ($IsWindows) {
    # npm依赖脚本自身目录；裸名称经cmd引用后也必须正确定位同目录文件。
    $commandDirectory=Join-Path $fixture 'command tools'
    $commandCwd=Join-Path $fixture 'command cwd'
    $null=New-Item -ItemType Directory -Path $commandDirectory,$commandCwd
    $commandName='incident-command-'+[guid]::NewGuid().ToString('N')+'.cmd'
    $commandPath=Join-Path $commandDirectory $commandName
    [IO.File]::WriteAllText((Join-Path $commandDirectory 'payload.txt'),"CMD_LOCATION_OK`r`n",[Text.Encoding]::ASCII)
    $batch=@('@echo off','if not exist "%~dp0payload.txt" exit /b 23',
        'if not "%~1"=="argument with spaces" exit /b 24','type "%~dp0payload.txt"','echo ARG=%~1','exit /b 7') -join "`r`n"
    [IO.File]::WriteAllText($commandPath,$batch+"`r`n",[Text.Encoding]::ASCII)
    $savedCommandPath=$env:PATH
    try {
        $env:PATH=$commandDirectory+[IO.Path]::PathSeparator+$savedCommandPath
        foreach($case in @(@{name='bare';executable=$commandName},@{name='absolute';executable=$commandPath})) {
            $log="$fixture/raw/cmd-$($case.name).log"
            $exit=Invoke-DeliveryCommand $case.executable @('argument with spaces') $commandCwd $log 30
            $lines=@(Get-Content -LiteralPath $log)
            Check ($exit -eq 7 -and $lines.Count -eq 2 -and $lines[0] -ceq 'CMD_LOCATION_OK' -and
                $lines[1] -ceq 'ARG=argument with spaces') "CMD_$($case.name)_LOCATION_ARGUMENT_EXIT"
        }
        Reject { Invoke-DeliveryCommand 'invalid%name.cmd' @() $commandCwd "$fixture/raw/cmd-invalid-name.log" 30 } 'COMMAND_ARGUMENT_INVALID'
        foreach($character in @('%','&','|','<','>','^','"',"`r","`n")) {
            Reject { Invoke-DeliveryCommand $commandName @('unsafe'+$character+'argument') $commandCwd "$fixture/raw/cmd-invalid-argument.log" 30 } 'COMMAND_ARGUMENT_INVALID'
        }
        Check (!(Test-Path -LiteralPath "$fixture/raw/cmd-invalid-argument.log")) 'CMD_UNSAFE_ARGUMENT_NOT_STARTED'
        foreach($character in @('&','%')) {
            $unsafeDirectory=Join-Path $fixture ('command'+$character+'tools')
            $null=New-Item -ItemType Directory -Path $unsafeDirectory
            $unsafeName='incident-unsafe-'+[guid]::NewGuid().ToString('N')+'.cmd'
            [IO.File]::WriteAllText((Join-Path $unsafeDirectory $unsafeName),"@echo off`r`necho executed> `"%~dp0started.txt`"`r`n",[Text.Encoding]::ASCII)
            $env:PATH=$unsafeDirectory+[IO.Path]::PathSeparator+$savedCommandPath
            $unsafeLog=Join-Path $fixture ('raw/cmd-unsafe-'+[int][char]$character+'.log')
            Reject { Invoke-DeliveryCommand $unsafeName @() $commandCwd $unsafeLog 30 } 'COMMAND_ARGUMENT_INVALID'
            Check (!(Test-Path -LiteralPath $unsafeLog) -and !(Test-Path -LiteralPath (Join-Path $unsafeDirectory 'started.txt'))) 'CMD_UNSAFE_RESOLVED_PATH_NOT_STARTED'
        }
    } finally { $env:PATH=$savedCommandPath }
}
$realDeliveryTools=${function:Get-DeliveryTools}
function Check-DeliveryCandidateToolDependencies {
    function Test-DeliverySafety { }
    function Get-Command {
        [CmdletBinding()]param([string]$Name)
        $script:dependencyLookups+=@($Name)
        if ($Name -eq 'git') { return $null }
        return [pscustomobject]@{Name=$Name}
    }
    function Invoke-DeliveryCommand($Executable,$Arguments,$WorkingDirectory,$Log,$TimeoutSeconds) {
        [IO.File]::WriteAllText($Log,$(if($Executable -eq 'node'){'v20.20.0'}else{'10.8.2'}))
        return 0
    }
    $script:dependencyLookups=@()
    $versions=& $realDeliveryTools Frontend $fixture $true
    Check ($versions.node -eq '20.20.0' -and $script:dependencyLookups -notcontains 'git') 'CANDIDATE_TOOLS_DO_NOT_REQUIRE_GIT'
    Reject { & $realDeliveryTools Frontend $fixture $false } 'TOOL_MISSING'
    Check ($script:dependencyLookups -contains 'git') 'GIT_MODE_STILL_REQUIRES_GIT'
}
Check-DeliveryCandidateToolDependencies
$live = [Environment]::GetEnvironmentVariable('RAG_LIVE_TESTS')
$key = [Environment]::GetEnvironmentVariable('YDC_API_KEY')
$opts = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS')
try {
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS','SECRET_FIXTURE')
    Reject { Test-DeliverySafety } 'UNSAFE_ENVIRONMENT'
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS',$opts)
    # 内部函数替换只属于测试会话；公共入口没有任意执行或输出根参数。
    function Get-DeliveryTools($Scope,$Root,[bool]$SourceCandidate=$false) {
        $script:allToolCalls++
        Check ($env:RAG_LIVE_TESTS -eq 'false' -and !$env:YDC_API_KEY) 'ENV_CLOSED_IN_CHILD'
        if ($script:fakeBlocked) { throw 'UNSAFE_ENVIRONMENT' }
        if ($SourceCandidate) {
            $script:candidateToolCalls++
            if ($script:candidateMutation) { & $script:candidateMutation }
        }
        return [ordered]@{powershell='7.5.0'}
    }
    function Get-DeliverySources { return @([ordered]@{path='fixture';sha256=('A'*64)}) }
    function Invoke-DeliveryCommand($Executable,$Arguments,$WorkingDirectory,$Log,$TimeoutSeconds) {
        $script:fakeCalls++
        if ($script:fakeCalls -eq 1 -and $script:fakeFailure) { return 9 }
        if ($Arguments -contains 'test') {
            $rootArg = @($Arguments | Where-Object {$_ -like '-Dlab.reportRoot=*'})[0]
            $root = $rootArg.Substring('-Dlab.reportRoot='.Length)
            $idArg = @($Arguments | Where-Object {$_ -like '-Dlab.runId=*'})[0]
            $id = $idArg.Substring('-Dlab.runId='.Length)
            if ($Arguments -contains '-Dtest=IncidentDiagnosisGoldenPathTest') {
                (Suite $golden 1 0 $id) | Set-Content "$root/TEST-golden.xml"
            } else {
                foreach ($required in (Get-DeliveryRequired)) { (Suite $required 1 0 $id) | Set-Content "$root/TEST-$required.xml" }
                foreach ($pair in (Get-DeliveryAllowedSkips).GetEnumerator()) { (Suite $pair.Key $pair.Value $pair.Value $id) | Set-Content "$root/TEST-$($pair.Key).xml" }
            }
            # fixture 明确给出本轮时间，避免NTFS时间采样比UtcNow慢数毫秒；真实入口仍严格拒绝旧报告。
            Get-ChildItem -LiteralPath $root -File | ForEach-Object { $_.LastWriteTimeUtc=[datetime]::UtcNow.AddSeconds(1) }
        }
        return 0
    }
    [Environment]::SetEnvironmentVariable('RAG_LIVE_TESTS','true')
    [Environment]::SetEnvironmentVariable('YDC_API_KEY','SECRET_FIXTURE')
    foreach ($scope in @('Smoke','Backend','Frontend','All')) {
        $script:fakeCalls=0; $script:fakeFailure=$false; $script:fakeBlocked=$false
        Check ((Invoke-Delivery $scope $true) -eq 0 -and $script:fakeCalls -eq 0) "CHECK_ONLY_$scope"
        Check ((Invoke-Delivery $scope $false) -eq 0) "SUCCESS_$scope"
        Check ($env:RAG_LIVE_TESTS -eq 'true' -and $env:YDC_API_KEY -eq 'SECRET_FIXTURE') "RESTORE_SUCCESS_$scope"
        $script:fakeCalls=0; $script:fakeFailure=$true
        Check ((Invoke-Delivery $scope $false) -eq 1) "FAIL_PROPAGATES_$scope"
        Check ($env:RAG_LIVE_TESTS -eq 'true' -and $env:YDC_API_KEY -eq 'SECRET_FIXTURE') "RESTORE_FAIL_$scope"
    }
    $script:fakeBlocked=$true
    Check ((Invoke-Delivery All $true) -eq 2) 'BLOCKED_EXIT'
    Check ($env:RAG_LIVE_TESTS -eq 'true' -and $env:YDC_API_KEY -eq 'SECRET_FIXTURE') 'RESTORE_BLOCKED'
    $candidateRunRepo=$script:DeliveryRepo
    $previousGitFunction=Get-Item Function:git -ErrorAction SilentlyContinue
    try {
        $parentGitFixture=New-DeliveryCandidateFixture 'inside-parent-git'
        $script:DeliveryRepo=$parentGitFixture.source
        $parentGitRoot=git -C $script:DeliveryRepo rev-parse --show-toplevel
        Check ($LASTEXITCODE -eq 0 -and [IO.Path]::GetFullPath($parentGitRoot) -eq $candidateRunRepo -and
            [IO.Path]::GetFullPath($parentGitRoot) -ne $script:DeliveryRepo) 'PARENT_GIT_CONTEXT_CONFIRMED'
        $script:allToolCalls=0;$script:fakeCalls=0;$script:fakeBlocked=$false
        Check ((Invoke-Delivery Frontend $true) -eq 1 -and $script:allToolCalls -eq 0 -and
            $script:fakeCalls -eq 0) 'NO_FLAG_PARENT_GIT_REJECTED_BEFORE_TOOLS'
        $parentRun=Get-ChildItem -LiteralPath $script:DeliveryBase -Directory |
            Where-Object {$_.Name -match '^[a-f0-9-]{36}$'} | Sort-Object LastWriteTime | Select-Object -Last 1
        $parentRunRecord=Get-Content "$($parentRun.FullName)/run.json" -Raw|ConvertFrom-Json
        Check ($parentRunRecord.state -ceq 'FAIL' -and $null -eq $parentRunRecord.head -and
            $null -eq $parentRunRecord.sourceDigest -and $parentRunRecord.stages.Count -eq 0) 'NO_FLAG_HAS_NO_FALSE_IDENTITY'
        $script:DeliveryRepo=$candidateFixture.source
        $script:candidateGitCalls=0
        function git { $script:candidateGitCalls++;throw 'CANDIDATE_MUST_NOT_BORROW_GIT' }
        $script:fakeCalls=0;$script:fakeFailure=$false;$script:fakeBlocked=$false
        $script:candidateMutation=$null;$script:candidateToolCalls=0
        Check ((Invoke-Delivery Frontend $true $candidateFixture.candidate) -eq 0 -and $script:fakeCalls -eq 0 -and
            $script:candidateToolCalls -eq 1 -and $script:candidateGitCalls -eq 0) 'CANDIDATE_CHECK_ONLY_NO_GIT_OR_BUILD'
        $candidateRun=Get-ChildItem -LiteralPath $script:DeliveryBase -Directory |
            Where-Object {$_.Name -match '^[a-f0-9-]{36}$'} | Sort-Object LastWriteTime | Select-Object -Last 1
        $candidateRunRecord=Get-Content "$($candidateRun.FullName)/run.json" -Raw|ConvertFrom-Json
        $candidateSafeRecord=Get-Content "$($candidateRun.FullName)/safe/summary.json" -Raw|ConvertFrom-Json
        Check ($candidateRunRecord.state -ceq 'CHECKED' -and $candidateRunRecord.schemaVersion -eq 2 -and
            $null -eq $candidateRunRecord.head -and $candidateRunRecord.sourceCandidate.candidateId -ceq $candidateFixture.candidateId -and
            $candidateSafeRecord.schemaVersion -eq 2 -and $null -eq $candidateSafeRecord.head -and
            $candidateSafeRecord.sourceCandidate.candidateId -ceq $candidateFixture.candidateId) 'CANDIDATE_CHECK_ONLY_PROVENANCE'
        $script:candidateMutation={ [IO.File]::WriteAllText($candidateSource,'changed during preflight') }
        Check ((Invoke-Delivery Frontend $true $candidateFixture.candidate) -eq 1 -and $script:fakeCalls -eq 0) 'CHECK_ONLY_SOURCE_DRIFT_REJECTED'
        [IO.File]::WriteAllBytes($candidateSource,$candidateBytes)
        $script:candidateMutation={
            $metadata=Get-Content -LiteralPath $candidateMetadata -Raw|ConvertFrom-Json
            $metadata.candidateId=[guid]::NewGuid().ToString()
            [IO.File]::WriteAllText($candidateMetadata,($metadata|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
            Update-DeliveryCandidateFixtureSums $candidateFixture.candidate
        }
        Check ((Invoke-Delivery Frontend $true $candidateFixture.candidate) -eq 1 -and $script:fakeCalls -eq 0) 'CHECK_ONLY_VALID_CANDIDATE_SWAP_REJECTED'
        [IO.File]::WriteAllBytes($candidateMetadata,$originalMetadata)
        Update-DeliveryCandidateFixtureSums $candidateFixture.candidate
        $script:candidateMutation=$null
        [IO.File]::WriteAllText((Join-Path $candidateFixture.source 'docs/before-preflight.txt'),'not in candidate')
        $script:candidateToolCalls=0
        Check ((Invoke-Delivery Frontend $true $candidateFixture.candidate) -eq 1 -and $script:candidateToolCalls -eq 0 -and
            $script:fakeCalls -eq 0 -and $script:candidateGitCalls -eq 0) 'BAD_CANDIDATE_REJECTED_BEFORE_TOOLS'
        Remove-Item -LiteralPath (Join-Path $candidateFixture.source 'docs/before-preflight.txt')
        $arrayMetadata=Get-Content -LiteralPath $candidateMetadata -Raw|ConvertFrom-Json
        $arrayMetadata.candidateId=@($arrayMetadata.candidateId,'SECRET_FIXTURE')
        [IO.File]::WriteAllText($candidateMetadata,($arrayMetadata|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
        Update-DeliveryCandidateFixtureSums $candidateFixture.candidate
        $script:candidateToolCalls=0
        Check ((Invoke-Delivery Frontend $true $candidateFixture.candidate) -eq 1 -and $script:candidateToolCalls -eq 0 -and
            $script:fakeCalls -eq 0 -and $script:candidateGitCalls -eq 0) 'CANDIDATE_IDENTITY_ARRAY_REJECTED_BEFORE_TOOLS'
        [IO.File]::WriteAllBytes($candidateMetadata,$originalMetadata)
        Update-DeliveryCandidateFixtureSums $candidateFixture.candidate
        Check ($env:RAG_LIVE_TESTS -eq 'true' -and $env:YDC_API_KEY -eq 'SECRET_FIXTURE') 'RESTORE_CANDIDATE_FAILURE'
    } finally {
        $script:DeliveryRepo=$candidateRunRepo
        if ($previousGitFunction) { Set-Item Function:git $previousGitFunction.ScriptBlock } else { Remove-Item Function:git }
        $script:candidateMutation=$null
    }
} finally {
    [Environment]::SetEnvironmentVariable('RAG_LIVE_TESTS',$live)
    [Environment]::SetEnvironmentVariable('YDC_API_KEY',$key)
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS',$opts)
}
$safeInput = [ordered]@{runId=$fixtureRun;head=('a'*40);sourceDigest=('A'*64);scope='All';state='FAIL';reasonCode='GATE_FAILED';
    startedAt=$fixtureStart.ToString('o');finishedAt=[datetime]::UtcNow.ToString('o');toolVersions=[ordered]@{java='21.0.12';secret='SECRET_FIXTURE'};
    exception='SECRET_FIXTURE';environment='SECRET_FIXTURE';path='SECRET_FIXTURE';stages=@(
        @{stage='Backend';exitCode=0;reasonCode='VALIDATED';command='SECRET_FIXTURE';xml='SECRET_FIXTURE';validation=$backend})}
$safeText = Get-DeliverySafe $safeInput | ConvertTo-Json -Depth 20
Check (!$safeText.Contains('SECRET_FIXTURE') -and !$safeText.Contains('command') -and !$safeText.Contains('exception')) 'SAFE_WHITELIST'
$legacySafe=Get-DeliverySafe $safeInput
Check ($legacySafe.schemaVersion -eq 1 -and $legacySafe.head -ceq ('a'*40) -and
    !$legacySafe.Contains('sourceCandidate')) 'GIT_SAFE_SCHEMA_ONE_COMPATIBLE'
$candidateSafeInput=[ordered]@{};foreach($pair in $safeInput.GetEnumerator()){$candidateSafeInput[$pair.Key]=$pair.Value}
$candidateSafeInput.schemaVersion=2;$candidateSafeInput.head=$null
$candidateSafeInput.sourceCandidate=[ordered]@{};foreach($pair in $candidateIdentity.GetEnumerator()){$candidateSafeInput.sourceCandidate[$pair.Key]=$pair.Value}
$candidateSafeInput.sourceCandidate.path='SECRET_FIXTURE';$candidateSafeInput.sourceCandidate.exception='SECRET_FIXTURE'
$candidateSafe=Get-DeliverySafe $candidateSafeInput
$candidateSafeText=$candidateSafe|ConvertTo-Json -Depth 20
Check ($candidateSafe.schemaVersion -eq 2 -and $null -eq $candidateSafe.head -and
    $candidateSafe.sourceCandidate.candidateId -ceq $candidateFixture.candidateId -and
    !$candidateSafeText.Contains('SECRET_FIXTURE') -and $candidateSafe.sourceCandidate.Count -eq 8) 'CANDIDATE_SAFE_WHITELIST'
foreach($field in @('kind','candidateId','manifestSha256','metadataSha256','zipSha256','checksumsSha256','baselineHead','fileCount')) {
    $savedValue=$candidateSafeInput.sourceCandidate[$field]
    $candidateSafeInput.sourceCandidate[$field]='SECRET_FIXTURE'
    Reject {Get-DeliverySafe $candidateSafeInput} 'SAFE_INVALID'
    $candidateSafeInput.sourceCandidate[$field]=$savedValue
}
foreach($field in @('manifestSha256','metadataSha256','zipSha256','checksumsSha256')) {
    $savedValue=$candidateSafeInput.sourceCandidate[$field]
    $candidateSafeInput.sourceCandidate[$field]=$savedValue.ToLowerInvariant()
    Reject {Get-DeliverySafe $candidateSafeInput} 'SAFE_INVALID'
    $candidateSafeInput.sourceCandidate[$field]=$savedValue
}
foreach($field in @('kind','candidateId','baselineHead','manifestSha256','metadataSha256','zipSha256','checksumsSha256')) {
    $savedValue=$candidateSafeInput.sourceCandidate[$field]
    $candidateSafeInput.sourceCandidate[$field]=@($savedValue,'SECRET_FIXTURE')
    Reject {Get-DeliverySafe $candidateSafeInput} 'SAFE_INVALID'
    $candidateSafeInput.sourceCandidate[$field]=$savedValue
}
$candidateSafeInput.schemaVersion='2'
Reject {Get-DeliverySafe $candidateSafeInput} 'SAFE_INVALID'
$candidateSafeInput.schemaVersion=2.0
Reject {Get-DeliverySafe $candidateSafeInput} 'SAFE_INVALID'
$candidateSafeInput.schemaVersion=2
$candidateSafeInput.head=('a'*40)
Reject {Get-DeliverySafe $candidateSafeInput} 'SAFE_INVALID'
$candidateSafeInput.head=$null;$candidateSafeInput.schemaVersion=1
Reject {Get-DeliverySafe $candidateSafeInput} 'SAFE_INVALID'
$candidateSafeInput.schemaVersion=2;$candidateSafeInput.sourceCandidate.fileCount=0
Reject {Get-DeliverySafe $candidateSafeInput} 'SAFE_INVALID'
$candidateSafeInput.sourceCandidate.fileCount=[string]$candidateFixture.fileCount
Reject {Get-DeliverySafe $candidateSafeInput} 'SAFE_INVALID'
$candidateSafeInput.sourceCandidate.fileCount=$candidateFixture.fileCount
foreach($field in @('runId','scope','state','reasonCode','sourceDigest','head')) {
    $savedValue=$safeInput[$field];$safeInput[$field]=@($savedValue,'SECRET_FIXTURE')
    Reject {Get-DeliverySafe $safeInput} 'SAFE_INVALID'
    $safeInput[$field]=$savedValue
}
foreach($field in @('stage','reasonCode')) {
    $savedValue=$safeInput.stages[0][$field];$safeInput.stages[0][$field]=@($savedValue,'SECRET_FIXTURE')
    Reject {Get-DeliverySafe $safeInput} 'SAFE_INVALID'
    $safeInput.stages[0][$field]=$savedValue
}
$safeInput.toolVersions.java=@('21.0.12','SECRET_FIXTURE')
Reject {Get-DeliverySafe $safeInput} 'SAFE_INVALID'
$safeInput.toolVersions.java='21.0.12'
$safeInput.stages[0].reasonCode='SECRET_FIXTURE'
Reject { Get-DeliverySafe $safeInput } 'SAFE_INVALID'
$safeInput.stages[0].reasonCode='VALIDATED'
$safeInput.stages[0].exitCode='SECRET_FIXTURE'
Reject { Get-DeliverySafe $safeInput } 'SAFE_INVALID'
# 摘要生成失败：实际finalize路径必须非零，不能留下上传目标。
function Get-DeliverySafe($Run) { throw 'SAFE_INVALID' }
$script:fakeBlocked=$false; $script:fakeFailure=$false; $script:fakeCalls=0
Check ((Invoke-Delivery Frontend $false) -eq 1) 'SAFE_FAILURE_EXIT'
$lastFixture = Get-ChildItem -LiteralPath $script:DeliveryBase -Directory |
    Where-Object {$_.Name -match '^[a-f0-9-]{36}$'} | Sort-Object LastWriteTime | Select-Object -Last 1
Check (!(Test-Path "$($lastFixture.FullName)/safe/summary.json")) 'NO_RAW_FALLBACK'
Check ((Get-Content "$($lastFixture.FullName)/run.json" -Raw | ConvertFrom-Json).state -eq 'FAIL') 'FINAL_RECORD_FAIL'
function WorkflowValid([string]$Text) {
    return $Text.Contains('contents: read') -and $Text.Contains('persist-credentials: false') -and
        !$Text.Contains('secrets.') -and !$Text.Contains('pull_request_target') -and
        ([regex]::Matches($Text,'path: bootstrap/target/delivery-gates/\*/safe/summary\.json').Count -eq 2) -and
        ([regex]::Matches($Text,'run: ./scripts/check-delivery.ps1 -Gate (Backend|Frontend)').Count -eq 2) -and
        ([regex]::Matches($Text,'uses: actions/[a-z-]+@[a-f0-9]{40}').Count -eq 6)
}
$workflow = Get-Content (Join-Path $script:DeliveryRepo '.github/workflows/incidentpilot-ci.yml') -Raw
Check (WorkflowValid $workflow) 'WORKFLOW_CONTRACT'
foreach ($bad in @($workflow.Replace('safe/summary.json','raw/**'),
    $workflow.Replace('contents: read','contents: write'),
    $workflow.Replace('persist-credentials: false','persist-credentials: true'),
    $workflow.Replace('pull_request:','pull_request_target:'),
    $workflow.Replace('-Gate Backend','-Gate Arbitrary'))) {
    Check (!(WorkflowValid $bad)) 'WORKFLOW_NEGATIVE'
}
# 公共参数校验独立进程；不会启动业务。
$parameterExit = & $pwsh -NoProfile -File $entry -Gate Invalid *> "$fixture/raw/parameter.log"; $parameterExit=$LASTEXITCODE
Check ($parameterExit -ne 0) 'INVALID_PUBLIC_PARAMETER'
$parameterExit = & $pwsh -NoProfile -File $entry -Command Arbitrary *> "$fixture/raw/unknown-parameter.log"; $parameterExit=$LASTEXITCODE
Check ($parameterExit -ne 0) 'NO_COMMAND_PASSTHROUGH'
Write-Host "CONTRACT_PASS $script:checks"

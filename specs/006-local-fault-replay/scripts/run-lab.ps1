param([switch]$List, [string]$Scenario, [switch]$All, [string]$Verify)
$ErrorActionPreference = 'Stop'
$script:LabRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$script:LabBase = Join-Path $script:LabRepo 'bootstrap/target/fault-lab'
function Write-LabJson($Path, $Value) { $Value | ConvertTo-Json -Depth 32 | Set-Content -LiteralPath $Path -Encoding utf8 }
function Read-LabJson($Path) {
    $value = Get-Content -LiteralPath $Path -Raw -Encoding utf8 | ConvertFrom-Json
    return $value
}
function Assert-Lab($Condition, [string]$Code) { if (!$Condition) { throw $Code } }
function Get-LabReason($Failure) {
    $message = $Failure.Exception.Message
    if ($message -match '^[A-Z_]{1,64}$') { return $message }
    return 'EVIDENCE_OR_ENVIRONMENT_UNAVAILABLE'
}
function Resolve-LabPath([string]$Path) {
    $absolute = if ([IO.Path]::IsPathRooted($Path)) { [IO.Path]::GetFullPath($Path) }
                else { [IO.Path]::GetFullPath((Join-Path $script:LabRepo $Path)) }
    Assert-Lab ($absolute.StartsWith($script:LabBase + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) 'PATH_OUTSIDE_LAB'
    $ancestor = $absolute
    while ($ancestor -and $ancestor.Length -ge $script:LabRepo.Length) {
        if (Test-Path -LiteralPath $ancestor) {
            Assert-Lab (((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'REPARSE_PATH'
        }
        $ancestor = Split-Path -Path $ancestor -Parent
    }
    return $absolute
}
function Get-LabScenarios {
    $items = @(Read-LabJson (Join-Path $PSScriptRoot 'scenarios.json'))
    Assert-Lab (($items.id -join ',') -eq 'ALERT-REPLAY,OUTBOX-RETRY,WORKER-RECOVERY,RECEIPT-CHANNELS,GOLDEN,DIAGNOSIS-SAFETY') 'CATALOGUE_INVALID'
    foreach ($item in $items) {
        Assert-Lab ($item.module -in @('incident','bootstrap') -and $item.testSelector -match '^[A-Za-z]+(#[A-Za-z]+)?$' -and
            $item.className -match '^[A-Za-z]+$') 'CATALOGUE_COMMAND_INVALID'
    }
    return $items
}
function Get-LabFiles([string]$Root) {
    $pending = New-Object 'System.Collections.Generic.Stack[string]'
    $pending.Push((Resolve-LabPath $Root))
    while ($pending.Count) {
        foreach ($entry in @(Get-ChildItem -LiteralPath $pending.Pop() -Force)) {
            Assert-Lab (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'REPARSE_PATH'
            if ($entry.PSIsContainer) { $pending.Push($entry.FullName) } else { $entry }
        }
    }
}
function Get-LabSources {
    $paths = @('pom.xml')
    foreach ($module in @('framework','infra-ai','system','incident','agent','rag','mcp-server','bootstrap')) {
        $paths += "$module/pom.xml"
        $paths += @(Get-ChildItem -LiteralPath (Join-Path $script:LabRepo "$module/src") -Recurse -File |
                ForEach-Object { $_.FullName.Substring($script:LabRepo.Length + 1).Replace('\','/') })
    }
    $paths += @(Get-ChildItem (Join-Path $script:LabRepo 'resources/database') -Recurse -File |
            ForEach-Object { $_.FullName.Substring($script:LabRepo.Length + 1).Replace('\','/') })
    $paths += @('specs/006-local-fault-replay/scripts/run-lab.ps1','specs/006-local-fault-replay/scripts/scenarios.json','specs/006-local-fault-replay/scripts/test-run-lab.ps1')
    return @(foreach ($path in ($paths | Sort-Object -Unique)) {
        [ordered]@{path=$path;sha256=(Get-FileHash -LiteralPath (Join-Path $script:LabRepo $path) -Algorithm SHA256).Hash}
    })
}
function Test-LabScene([string]$Directory, $Definition, [string]$RunId, [string]$SourceDigest) {
    $proof = Join-Path $Directory 'proof'
    $origin = Read-LabJson (Join-Path $proof 'proof-origin.json')
    Assert-Lab ($origin.runId -eq $RunId -and $origin.scenarioId -eq $Definition.id -and
        $origin.sourceDigest -eq $SourceDigest) 'PROOF_SOURCE_MISMATCH'
    Assert-Lab ((@($origin.tests | Sort-Object) -join ',') -eq (@($Definition.tests | Sort-Object) -join ',')) 'PROOF_TEST_MISMATCH'
    $xmlFiles = @(Get-ChildItem (Join-Path $Directory 'reports') -Recurse -Filter 'TEST-*.xml' -File)
    Assert-Lab ($xmlFiles.Count -eq 1) 'REPORT_SELECTION_MISMATCH'
    [xml]$xml = Get-Content -LiteralPath $xmlFiles[0].FullName -Raw
    $suite = $xml.testsuite
    Assert-Lab (($suite.name -split '\.')[-1] -eq $Definition.className) 'REPORT_CLASS_MISMATCH'
    Assert-Lab ([int]$suite.tests -eq $Definition.expectedTests -and [int]$suite.failures -eq 0 -and
            [int]$suite.errors -eq 0 -and [int]$suite.skipped -eq 0) 'TEST_NOT_PASSED'
    Assert-Lab ((@($suite.testcase | ForEach-Object { $_.name } | Sort-Object) -join ',') -eq
            (@($Definition.tests | Sort-Object) -join ',')) 'TEST_CASE_MISMATCH'
    foreach ($pair in @{ 'lab.runId'=$RunId; 'lab.scenarioId'=$Definition.id; 'lab.sourceDigest'=$SourceDigest }.GetEnumerator()) {
        $property = @($suite.properties.property | Where-Object { $_.name -eq $pair.Key })
        Assert-Lab ($property.Count -eq 1 -and $property[0].value -eq $pair.Value) 'REPORT_SOURCE_MISMATCH'
    }
    $facts = @(); $trace = $null
    if (!$Definition.trace) {
        foreach ($testName in $Definition.tests) { $facts += Read-LabJson (Join-Path $proof "$testName.json") }
        if ($Definition.id -eq 'ALERT-REPLAY') {
            Assert-Lab ($facts[0].status -eq 'PROCESSED' -and $facts[0].incidentCount -eq 1) 'ALERT_FACTS_INVALID'
            $retry = $facts[1]
            Assert-Lab ($retry.status -eq 'PROCESSED' -and $retry.retryCount -eq 5 -and $retry.attemptCount -eq 6 -and
                $retry.processingEpoch -eq 1 -and $retry.terminalFacts -eq 1 -and $retry.replayAccepted -eq 1 -and
                $retry.replayRejected -eq 1 -and $retry.recoveredFacts -eq 1) 'REPLAY_FACTS_INVALID'
        } else {
            Assert-Lab ($facts[0].publishedCount -eq 2 -and $facts[0].sendAttempts -eq 7 -and
                $facts[0].sameEnvelope -eq $true -and $facts[0].brokerValidated -eq $false) 'OUTBOX_FACTS_INVALID'
        }
    } else {
        $summary = Read-LabJson (Join-Path $proof 'summary.json')
        $traceProof = Read-LabJson (Join-Path $proof 'trace-proof.json')
        Assert-Lab ($summary.passed -eq $true -and $summary.simulationApplications -eq $Definition.expectedApplications -and
                $summary.secretLeaks -eq 0 -and $summary.actualSecretsScanned -gt 0) 'SUMMARY_INVALID'
        $executions = @($summary.applicationsByExecution)
        Assert-Lab ($executions.Count -eq $Definition.expectedApplications) 'EXECUTION_COUNT_INVALID'
        Assert-Lab (@($executions | Where-Object { $_.applications -ne 1 -or !$_.executionId -or !$_.dispatchRequestId }).Count -eq 0) 'DUPLICATE_OR_UNPROVEN_APPLICATION'
        Assert-Lab (@($executions.executionId | Sort-Object -Unique).Count -eq $executions.Count) 'EXECUTION_ID_DUPLICATE'
        $spans = @(foreach ($file in @('main-spans.jsonl','mcp-spans.jsonl')) {
            Get-Content -LiteralPath (Join-Path $proof $file) | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json }
        })
        Assert-Lab ($traceProof.status -eq 'PASS' -and $spans.Count -gt 0 -and
                $traceProof.actualSpans -eq $spans.Count -and
                $traceProof.simulationApplications -eq $Definition.expectedApplications) 'TRACE_PROOF_INVALID'
        $executeSpans = @($spans | Where-Object name -eq 'incident.mcp.execute')
        Assert-Lab ($executeSpans.Count -eq $Definition.expectedApplications) 'EXECUTE_SPANS_INVALID'
        foreach ($execution in $executeSpans) {
            Assert-Lab (@($executions | Where-Object executionId -eq $execution.attributes.'ragent.execution_id').Count -eq 1) 'SPAN_EXECUTION_MISMATCH'
        }
        if ($Definition.id -eq 'WORKER-RECOVERY') {
            $dispatches = @($spans | Where-Object { $_.name -in @('incident.action.dispatch','incident.action.recover') -and
                    $_.attributes.'ragent.execution_id' -eq $executions[0].executionId })
            Assert-Lab (@($dispatches.traceId | Sort-Object -Unique).Count -ge 2 -and
                @($dispatches.attributes.'ragent.execution_id' | Sort-Object -Unique).Count -eq 1 -and
                @($dispatches.attributes.'ragent.dispatch_request_id' | Sort-Object -Unique).Count -eq 1) 'RECOVERY_IDENTITY_INVALID'
            $recovery = Read-LabJson (Join-Path $proof 'recovery/summary.json')
            Assert-Lab ($recovery.workerKilled -eq $true -and $recovery.logicalExecutions -eq 1 -and
                $recovery.applicationsBeforeCrash -eq 0 -and $recovery.applicationsAfterRecovery -eq 1 -and
                $recovery.applicationsAfterReplay -eq 1) 'CRASH_PROOF_INVALID'
        }
        if ($Definition.id -eq 'RECEIPT-CHANNELS') {
            $channels = Read-LabJson (Join-Path $proof 'channels/summary.json')
            Assert-Lab (@($channels.cases).Count -eq 3) 'CHANNEL_CASES_INVALID'
            foreach ($case in $channels.cases) {
                Assert-Lab ($case.applicationsForExecution -eq 1 -and $case.toolCallsForAction -eq 1 -and
                        $case.authoritativeReceipts -eq 1 -and $case.finalState -eq 'SUCCEEDED') 'CHANNEL_FACTS_INVALID'
            }
            Assert-Lab (@($channels.cases | Where-Object stateBeforeLateReceipt -eq 'UNKNOWN').Count -eq 1) 'UNKNOWN_PROOF_INVALID'
            $facts += $channels.cases
        }
        if ($Definition.id -eq 'GOLDEN') {
            foreach ($client in @($spans | Where-Object { $_.kind -eq 'CLIENT' -and
                    $_.name -in @('incident.mcp.dispatch','incident.callback.consume','incident.callback.receipt') })) {
                Assert-Lab (@($spans | Where-Object { $_.kind -eq 'SERVER' -and $_.parentSpanId -eq $client.spanId -and
                            $_.traceId -eq $client.traceId }).Count -ge 1) 'ANCESTRY_INVALID'
            }
        }
        $facts += $executions
        $trace = [ordered]@{actualSpans=$spans.Count;actualSecretsScanned=$summary.actualSecretsScanned;secretLeaks=0;
            simulationApplications=$summary.simulationApplications;otlpBackendValidated=$false}
    }
    return [ordered]@{scenarioId=$Definition.id;version=$Definition.version;status='PASS';reasonCode='VALIDATED';
        tests=[int]$suite.tests;failures=0;errors=0;skipped=0;caseNames=@($Definition.tests);
        javaRuntime=$origin.javaRuntime;compileRelease=$origin.compileRelease;
        evidenceLevel=$Definition.evidenceLevel;facts=$facts;traceSummary=$trace;limitations=$Definition.limitations}
}
function Write-LabHashes([string]$Root) {
    $entries = @(Get-LabFiles $Root | Where-Object { $_.FullName -ne (Join-Path $Root 'SHA256SUMS.json') } |
        Sort-Object FullName | ForEach-Object {
            [ordered]@{path=$_.FullName.Substring($Root.Length+1).Replace('\','/');
                sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
        })
    Write-LabJson (Join-Path $Root 'SHA256SUMS.json') $entries
}
function Test-LabArchive([string]$Root) {
    $Root = Resolve-LabPath $Root
    $hashes = @(Read-LabJson (Join-Path $Root 'SHA256SUMS.json'))
    $actual = @(Get-LabFiles $Root | Where-Object { $_.FullName -ne (Join-Path $Root 'SHA256SUMS.json') })
    Assert-Lab ($hashes.Count -gt 0 -and $hashes.Count -eq $actual.Count -and
        @($hashes.path | Sort-Object -Unique).Count -eq $hashes.Count) 'ARTIFACT_SET_INVALID'
    foreach ($item in $hashes) {
        Assert-Lab (![IO.Path]::IsPathRooted($item.path) -and $item.path -notmatch '(^|[\\/])\.\.([\\/]|$)') 'ARTIFACT_PATH_INVALID'
        $path = Resolve-LabPath (Join-Path $Root $item.path)
        Assert-Lab ($path.StartsWith($Root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) 'ARTIFACT_PATH_INVALID'
        Assert-Lab ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -eq $item.sha256) 'ARTIFACT_HASH_MISMATCH'
    }
    $manifest = Read-LabJson (Join-Path $Root 'manifest.json')
    Assert-Lab ($manifest.status -eq 'PASS' -and $manifest.finishedAt -and $manifest.schemaVersion -eq 1) 'RUN_NOT_PASSED'
    $null = [guid]::Parse($manifest.runId)
    Assert-Lab ($manifest.sourceDigest -eq (Get-FileHash -LiteralPath (Join-Path $Root 'source-hashes.json') -Algorithm SHA256).Hash) 'SOURCE_DIGEST_INVALID'
    $definitions = @(Read-LabJson (Join-Path $Root 'scenarios.json'))
    Assert-Lab ((@($definitions.id | Sort-Object) -join ',') -eq (@($manifest.selectedScenarios | Sort-Object) -join ',')) 'SCENARIO_SET_INVALID'
    Assert-Lab (@($manifest.scenarioResults).Count -eq $definitions.Count) 'SCENARIO_RESULTS_INVALID'
    Assert-Lab (@($definitions.id | Sort-Object -Unique).Count -eq $definitions.Count) 'SCENARIO_SET_INVALID'
    $catalogue = @(Get-LabScenarios)
    foreach ($definition in $definitions) {
        $expected = @($catalogue | Where-Object id -eq $definition.id)
        Assert-Lab ($expected.Count -eq 1 -and ($definition | ConvertTo-Json -Depth 20 -Compress) -eq
            ($expected[0] | ConvertTo-Json -Depth 20 -Compress)) 'ARCHIVED_CATALOGUE_INVALID'
        $sceneRoot = Join-Path $Root $definition.id
        $stored = Read-LabJson (Join-Path $sceneRoot 'result.json')
        $declared = @($manifest.scenarioResults | Where-Object scenarioId -eq $definition.id)
        Assert-Lab ($declared.Count -eq 1 -and ($declared[0] | ConvertTo-Json -Depth 24 -Compress) -eq
            ($stored | ConvertTo-Json -Depth 24 -Compress)) 'MANIFEST_RESULT_MISMATCH'
        Assert-Lab ($stored.status -eq 'PASS' -and $stored.exitCode -eq 0) 'SCENARIO_NOT_PASSED'
        $checked = Test-LabScene $sceneRoot $definition $manifest.runId $manifest.sourceDigest
        Assert-Lab (($checked | ConvertTo-Json -Depth 24 -Compress) -eq
                ($stored.validation | ConvertTo-Json -Depth 24 -Compress)) 'RESULT_FACTS_MISMATCH'
    }
    return $manifest
}
function Invoke-Lab {
    $modeCount = [int][bool]$List + [int][bool]$Scenario + [int][bool]$All + [int][bool]$Verify
    if ($modeCount -ne 1) { Write-Host 'PARAMETER_MODE_INVALID'; return 2 }
    if ($Verify) {
        try { $manifest = Test-LabArchive $Verify; Write-Host "VERIFIED $($manifest.runId)"; return 0 }
        catch { Write-Host "VERIFY_FAILED $(Get-LabReason $_)"; return 1 }
    }
    $definitions = @(Get-LabScenarios)
    if ($List) {
        $definitions | Select-Object id,title,faultPoint,expectedTests,evidenceLevel,limitations | Format-Table -Wrap | Out-Host
        return 0
    }
    if ($Scenario) { $definitions = @($definitions | Where-Object id -eq $Scenario) }
    if ($definitions.Count -eq 0) { Write-Host 'SCENARIO_UNKNOWN'; return 2 }
    New-Item -ItemType Directory -Path $script:LabBase -Force | Out-Null
    $lockPath = Resolve-LabPath (Join-Path $script:LabBase 'run.lock')
    try { $lock = [IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None) }
    catch { Write-Host 'RUN_LOCKED'; return 2 }
    $previousLive = $env:RAG_LIVE_TESTS
    $runId = [guid]::NewGuid().ToString()
    $root = Resolve-LabPath (Join-Path $script:LabBase $runId)
    New-Item -ItemType Directory -Path $root | Out-Null
    $manifest = [ordered]@{schemaVersion=1;runId=$runId;selectedScenarios=@($definitions.id);startedAt=(Get-Date).ToString('o');
        finishedAt=$null;status='RUNNING';branch=$null;head=$null;dirty=$true;sourceDigest=$null;
        environment=@{dockerEngine=$null;paidServices=$false;developerDatabase=$false;brokerValidated=$false};scenarioResults=@()}
    $returnCode = 1
    try {
        $manifest.branch = (git -C $script:LabRepo branch --show-current)
        $manifest.head = (git -C $script:LabRepo rev-parse HEAD)
        $manifest.dirty = [bool]@(git -C $script:LabRepo status --porcelain=v1).Count
        Write-LabJson (Join-Path $root 'manifest.json') $manifest
        Write-LabJson (Join-Path $root 'scenarios.json') @($definitions)
        Write-LabJson (Join-Path $root 'source-hashes.json') @(Get-LabSources)
        $manifest.sourceDigest = (Get-FileHash -LiteralPath (Join-Path $root 'source-hashes.json') -Algorithm SHA256).Hash
        $dockerVersion = & docker info --format '{{.ServerVersion}}' 2>&1
        if ($LASTEXITCODE -ne 0) { $manifest.status = 'BLOCKED'; $returnCode = 2 }
        else {
            $manifest.environment.dockerEngine = "$dockerVersion"
            $env:RAG_LIVE_TESTS = 'false'
            foreach ($definition in $definitions) {
                $scene = Resolve-LabPath (Join-Path $root $definition.id)
                New-Item -ItemType Directory -Path $scene | Out-Null
                $mavenArguments = @('-Dspotless.apply.skip=true','-pl',$definition.module,'-am',
                    "-Dtest=$($definition.testSelector)",'-Dsurefire.failIfNoSpecifiedTests=false',
                    "-Dlab.runId=$runId","-Dlab.scenarioId=$($definition.id)","-Dlab.sourceDigest=$($manifest.sourceDigest)",
                    "-Dlab.proofRoot=$scene","-Dlab.reportRoot=$scene/reports",'test')
                $result = [ordered]@{scenarioId=$definition.id;status='RUNNING';reasonCode='STARTED';
                    arguments=$mavenArguments;exitCode=$null;startedAt=(Get-Date).ToString('o');finishedAt=$null;observedReports=@();validation=$null}
                Write-LabJson (Join-Path $scene 'result.json') $result
                Write-Host "RUN $($definition.id) $runId"
                Push-Location $script:LabRepo
                try {
                    & (Join-Path $script:LabRepo 'mvnw.cmd') @mavenArguments *> (Join-Path $scene 'maven.log')
                    $result.exitCode = $LASTEXITCODE
                } finally { Pop-Location }
                try {
                if (Test-Path -LiteralPath (Join-Path $scene 'reports')) {
                    $result.observedReports = @(foreach ($report in @(Get-ChildItem (Join-Path $scene 'reports') -Recurse -Filter 'TEST-*.xml')) {
                        [xml]$observed = Get-Content -LiteralPath $report.FullName -Raw -Encoding utf8
                        [ordered]@{className=$observed.testsuite.name;tests=[int]$observed.testsuite.tests;
                            failures=[int]$observed.testsuite.failures;errors=[int]$observed.testsuite.errors;
                            skipped=[int]$observed.testsuite.skipped}
                    })
                }
                    Assert-Lab ($result.exitCode -eq 0) 'MAVEN_FAILED'
                    Assert-Lab ((@(Get-LabSources) | ConvertTo-Json -Depth 4 -Compress) -eq
                        (@(Read-LabJson (Join-Path $root 'source-hashes.json')) | ConvertTo-Json -Depth 4 -Compress)) 'SOURCE_CHANGED_DURING_RUN'
                    $result.validation = Test-LabScene $scene $definition $runId $manifest.sourceDigest
                    $result.status = 'PASS'; $result.reasonCode = 'VALIDATED'
                } catch { $result.status = 'FAIL'; $result.reasonCode = Get-LabReason $_ }
                $result.finishedAt = (Get-Date).ToString('o')
                Write-LabJson (Join-Path $scene 'result.json') $result
                $manifest.scenarioResults += $result
                Write-LabJson (Join-Path $root 'manifest.json') $manifest
                Write-Host "$($definition.id) $($result.status)"
            }
            $manifest.status = if (@($manifest.scenarioResults | Where-Object status -ne 'PASS').Count) { 'FAIL' } else { 'PASS' }
            $returnCode = if ($manifest.status -eq 'PASS') { 0 } else { 1 }
        }
    } catch [System.Management.Automation.PipelineStoppedException] {
        $manifest.status = 'INTERRUPTED'; $returnCode = 3
    } catch { $manifest.status = 'BLOCKED'; $returnCode = 2 }
    finally {
        $manifest.finishedAt = (Get-Date).ToString('o')
        Write-LabJson (Join-Path $root 'manifest.json') $manifest
        $lines = @("# 本地故障实验 $runId","", "结果：$($manifest.status)","",
            "范围：本地临时安全模拟。无生产、真实Broker、付费模型或跨主机验收。",
            "原始Maven诊断只作本地排错，未承诺逐实际凭据扫描；安全过程日志及新证明沿用测试内扫描。","")
        foreach ($definition in $definitions) {
            $entry = @($manifest.scenarioResults | Where-Object scenarioId -eq $definition.id)
            $lines += @("## $($definition.id) $($definition.title)","",
                "注入点：$($definition.faultPoint)","", "证据层级：$($definition.evidenceLevel)","",
                "预期：原测试全部断言通过、本轮精确检查数$($definition.expectedTests)、证明来源一致。",
                "", "限制：$($definition.limitations)","")
            if ($entry.Count) {
                $lines += @("实际：$($entry[0].status)，退出码$($entry[0].exitCode)。","",
                    "[本轮结果及命令]($($definition.id)/result.json)","")
                if ($entry[0].validation) {
                    $fence = ''.PadLeft(3,[char]96)
                    $lines += @(($fence+'json'),($entry[0].validation | ConvertTo-Json -Depth 20),$fence,"")
                }
            } else { $lines += @('实际：未执行，环境受阻或运行中断。',"") }
        }
        $lines | Set-Content -LiteralPath (Join-Path $root 'report.md') -Encoding utf8
        Write-LabHashes $root
        $env:RAG_LIVE_TESTS = $previousLive
        $lock.Dispose()
    }
    Write-Host "LAB_RESULT $($manifest.status) $($root.Substring($script:LabRepo.Length+1).Replace('\','/'))"
    return $returnCode
}
# Dot sourcing supports contract checks without starting an experiment.
if ($MyInvocation.InvocationName -ne '.') {
    try { $code = Invoke-Lab; exit $code }
    catch { Write-Host 'LAB_PARAMETER_OR_CATALOGUE_ERROR'; exit 2 }
}

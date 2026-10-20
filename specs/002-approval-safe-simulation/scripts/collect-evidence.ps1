param(
    [Parameter(Mandatory)][datetime]$Since,
    [Parameter(Mandatory)][string]$Log,
    [Parameter(Mandatory)][string]$Command
)
$ErrorActionPreference = 'Stop'
# 从仓库根目录运行。只归档实际通过且属于本轮的报告，不修改历史制品。
$repoRoot = (Get-Location).Path
if ((Get-Content -LiteralPath $Log -Raw) -notmatch 'BUILD SUCCESS') { throw 'Maven 未成功，禁止生成通过清单' }
$runId = [guid]::NewGuid().ToString()
$output = "incident/target/b-catalogue/$runId"
New-Item -ItemType Directory -Path "$output/reports","$output/proofs","$output/scenarios" -Force | Out-Null
$classes = @('ActionReceiptTest','ActionOutboxCompatibilityTest','OutboxRecoveryTest','ActionUnknownConvergenceTest',
    'ActionRecoveryRaceTest','ActionApprovalRaceTest','ActionSecretLeakTest','ActionServiceAuthenticationTest',
    'ActionBindingV1Test','ActionEventWriterTest','ActionStateMachineTest','ProtectedSimulationRouteTest',
    'ProtectedSimulationE2ETest','IncidentRemediationDispatcherTest','BootstrapIsolatedStartupTest',
    'AlertIdempotencyTest','IncidentAggregationTest','AlertRecoveryTest')
$reports = @(foreach ($module in @('mcp-server','incident','agent','bootstrap')) {
    foreach ($file in Get-ChildItem "$module/target/surefire-reports/TEST-*.xml") {
        [xml]$xml = Get-Content -LiteralPath $file.FullName
        $shortName = ($xml.testsuite.name -split '\.')[-1]
        if ($shortName -notin $classes) { continue }
        if ($file.LastWriteTime -lt $Since) { throw "旧报告不能计入本轮: $shortName" }
        if ([int]$xml.testsuite.failures -or [int]$xml.testsuite.errors -or [int]$xml.testsuite.skipped) { throw "报告尚未通过: $shortName" }
        $reportPath = "reports/$module-$($file.Name)"
        Copy-Item -LiteralPath $file.FullName -Destination "$output/$reportPath"
        [pscustomobject]@{class=$shortName;module=$module;tests=[int]$xml.testsuite.tests;path=$reportPath;
            cases=@($xml.testsuite.testcase | ForEach-Object { $_.name })}
    }
})
if ($reports.Count -ne $classes.Count) { throw '选定报告不完整' }
$proofs = @(foreach ($root in @('bootstrap/target/b-catalogue-bootstrap','incident/target/b-catalogue-mcp',
        'incident/target/b-catalogue-convergence','incident/target/safety-closeout')) {
    foreach ($directory in Get-ChildItem $root -Directory) {
        $summaryFile = Join-Path $directory.FullName 'summary.json'
        if (!(Test-Path -LiteralPath $summaryFile) -or (Get-Item $summaryFile).LastWriteTime -lt $Since) { continue }
        $summary = Get-Content -LiteralPath $summaryFile -Raw | ConvertFrom-Json
        $relative = $directory.FullName.Substring($repoRoot.Length+1).Replace('\','/')
        Copy-Item -LiteralPath $directory.FullName -Destination "$output/proofs/$($directory.Name)" -Recurse
        $originalRef = Join-Path $directory.FullName 'original-proof.txt'
        if (Test-Path -LiteralPath $originalRef) {
            $original = (Get-Content -LiteralPath $originalRef -Raw).Trim()
            Copy-Item -LiteralPath "bootstrap/$original" -Destination "$output/proofs/$($directory.Name)/original" -Recurse
        }
        [pscustomobject]@{path="proofs/$($directory.Name)";source=$relative;scenario=$summary.scenario}
    }
})
$mcp = @($proofs | Where-Object source -like 'incident/target/b-catalogue-mcp/*')
if ($mcp.Count -ne 1) { throw '独立 MCP 本轮证明不唯一' }
if (@($proofs | Where-Object source -like 'bootstrap/target/b-catalogue-bootstrap/*').Count -ne 7) { throw '完整应用七个场景未齐' }
if (@($proofs | Where-Object source -like 'incident/target/b-catalogue-convergence/*').Count -ne 4) { throw '对账/人工结论四个证明未齐' }
$mapping = @(
    @{id='B01';names=@('approved');mcpFiles=@('B01-B05-success-and-replay.json');classes=@('ProtectedSimulationE2ETest','BootstrapIsolatedStartupTest');expected='真实模拟成功；重放不新增应用'},
    @{id='B02';names=@('fail-closed','approved','authorization-deadline','ticket-deadline');mcpFiles=@('B02-B05-pre-effect-refusals.json');classes=@('BootstrapIsolatedStartupTest','ActionApprovalRaceTest');expected='拒绝/截止后没有新执行或应用'},
    @{id='B03';names=@('approved','fail-closed');mcpFiles=@();classes=@('BootstrapIsolatedStartupTest','ActionApprovalRaceTest');expected='关闭先提交阻止启动；消费先提交仍收敛'},
    @{id='B04';names=@();mcpFiles=@('B04-ordinary-and-unauthenticated.json');classes=@('ProtectedSimulationRouteTest','ProtectedSimulationE2ETest');expected='普通/无认证/未知注册失败关闭'},
    @{id='B05';names=@('approved');mcpFiles=@('B02-B05-pre-effect-refusals.json','B01-B05-success-and-replay.json');classes=@('ActionBindingV1Test','ProtectedSimulationE2ETest','BootstrapIsolatedStartupTest');expected='绑定篡改与重放拒绝，应用不重复'},
    @{id='B06';names=@('worker-recovery');mcpFiles=@();classes=@('ActionRecoveryRaceTest','BootstrapIsolatedStartupTest');expected='真实杀死 Worker 后原执行安全补票；消费/补票双顺序'},
    @{id='B07';names=@();mcpFiles=@('B07-consumed-mcp-crash.json');classes=@('ProtectedSimulationE2ETest');expected='消费后 MCP 真正死亡，无再次应用；fixture 截止后 UNKNOWN'},
    @{id='B08';names=@();mcpFiles=@('B08-proven-rollback.json','B08-unproven-unknown.json');classes=@('ProtectedSimulationE2ETest');expected='真实锁超时：正面回滚证明 FAILED，否则 UNKNOWN'},
    @{id='B09';names=@('receipt-channels','receipt-first','deadline-first');mcpFiles=@('B09-fixture-deadline-unknown.json');classes=@('ActionReceiptTest','BootstrapIsolatedStartupTest');expected='三通道故障各一次应用；迟到、重复、冲突及两种锁顺序'},
    @{id='B10';names=@('B10-indeterminate-then-rollback','B10-manual-permission-replay-conflict','B10-applied-then-contrary');mcpFiles=@('B10-real-read-only-applied.json');classes=@('ActionUnknownConvergenceTest','ProtectedSimulationE2ETest');expected='证据不足保持 UNKNOWN；可信证据/有权限人工结论收敛，冲突不覆盖'},
    @{id='B11';names=@('B11-action-relay');mcpFiles=@();classes=@('ActionOutboxCompatibilityTest','OutboxRecoveryTest');expected='真实 Relay 发送/标记失败后同事件重投；Producer 替身，无 Broker'},
    @{id='B12';names=@('approved','approved-tls');mcpFiles=@();classes=@('ActionSecretLeakTest','ActionServiceAuthenticationTest','BootstrapIsolatedStartupTest');expected='重启查询不重执行；本机双向 HTTPS；逐实际票据/凭据扫描为零'}
)
foreach ($scene in $mapping) {
    $sceneProofs = @($proofs | Where-Object scenario -in $scene.names | ForEach-Object { $_.path })
    foreach ($file in $scene.mcpFiles) {
        $path = "$($mcp[0].path)/$file"
        if (!(Test-Path "$output/$path")) { throw "场景证据缺失: $path" }
        $sceneProofs += $path
    }
    if (!$sceneProofs.Count) { throw "场景无实际快照: $($scene.id)" }
    [ordered]@{schemaVersion=1;scenario=$scene.id;runId=$runId;spec='Spec B v1.1';command=$Command;
        startedAt=$Since.ToString('o');finishedAt=(Get-Date).ToString('o');result='PASS';expected=$scene.expected;
        reports=@($reports | Where-Object class -in $scene.classes);proofs=$sceneProofs;
        actual='从关联 JSON/原 summary/XML 复核；不重新计算或补造历史结果';
        boundaries=@('全为临时本地资源','MCP 精简主服务认证为替身，部分 Bootstrap/Incident 截止有 fixture SQL','B10 人工/回滚证据为替身','B11 Producer 为替身','HTTPS 为本机隔离，不代表跨主机部署')
    } | ConvertTo-Json -Depth 12 | Set-Content "$output/scenarios/$($scene.id).json" -Encoding utf8
}
[ordered]@{scenario='A-IDEM-AGG-REC-OUT';runId=$runId;result='PASS';command=$Command;
    reports=@($reports | Where-Object class -in @('AlertIdempotencyTest','IncidentAggregationTest','AlertRecoveryTest','OutboxRecoveryTest'));
    evidenceLevel='真实 PostgreSQL SQL 断言及本轮 XML；不冒充完整进程崩溃快照';
    expected='100路重复/聚合、事务故障回滚、五次重试与人工重放、Outbox 同 envelope 恢复';
    inputs='原测试源码固定输入/受控 SQL；源文件哈希见根 manifest'
} | ConvertTo-Json -Depth 10 | Set-Content "$output/scenarios/A-reliability.json" -Encoding utf8
Copy-Item -LiteralPath $Log -Destination "$output/maven.log"
[xml]$runtimeReport = Get-Content -LiteralPath "$output/$($reports[0].path)"
$javaRuntime = ($runtimeReport.testsuite.properties.property | Where-Object name -eq 'java.runtime.version').value
$dockerVersion = docker info --format '{{.ServerVersion}}'
$sourceFiles=@('specs/002-approval-safe-simulation/spec.md','specs/002-approval-safe-simulation/plan.md',
    'bootstrap/src/test/java/com/nageoffer/ai/ragent/BootstrapIsolatedStartupTest.java')
$sourceFiles += @(foreach ($module in @('incident','agent','mcp-server')) { Get-ChildItem "$module/src/test" -Recurse -Filter '*.java' | Where-Object { $_.BaseName -in $classes -or $_.BaseName -eq 'ActionDbFixture' } | ForEach-Object { $_.FullName.Substring($repoRoot.Length+1).Replace('\','/') } })
$artifacts = @(Get-ChildItem $output -File -Recurse | ForEach-Object { @{path=$_.FullName.Substring((Resolve-Path $output).Path.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
[ordered]@{schemaVersion=1;runId=$runId;startedAt=$Since.ToString('o');finishedAt=(Get-Date).ToString('o');command=$Command;
    result='PASS';exitCode=0;tests=($reports | Measure-Object tests -Sum).Sum;failures=0;errors=0;skipped=0;
    branch=(git branch --show-current);head=(git rev-parse HEAD);workingTree='uncommitted; HEAD alone does not identify dirty implementation';reports=$reports;proofs=$proofs;artifacts=$artifacts;
    sourceHashes=@($sourceFiles | ForEach-Object { @{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} });
    environment=@{javaRuntime=$javaRuntime;compileRelease=17;dockerEngine=$dockerVersion;database='isolated Testcontainers PostgreSQL 16-alpine / pgvector:pg16';other='isolated Redis/RustFS; independent Bootstrap/MCP JVM';paidServices=$false;developerDatabase=$false;broker='not required by approved Spec; fixed Producer'}
} | ConvertTo-Json -Depth 20 | Set-Content "$output/manifest.json" -Encoding utf8
Write-Output $output

# 第二台机器接手执行手册

状态：**PREPARED_AWAITING_SECOND_MACHINE**。2026-10-18用户确认当前没有第二台机器，本轮只准备交接材料。本页的异机命令和人工链路尚未在第二台机器执行，不能填为通过。本机新目录、新容器或同机虚拟运行环境不计第二台机器。

本手册对应`v0.1.0`固定源码候选`070c4b76-70db-48c0-b7d0-b701a527f9e3`，1895个源码文件。目标是让接手者用新配置、新数据库独立构建、运行并验证一次安全模拟主链，保留失败和证据。不是生产部署或真实模型、Broker验收。IncidentPilot衍生自`nageoffer/ragent`；保留Apache-2.0、NOTICE和上游署名，不把上游能力算作从零实现。

## 交接内容与校验

外层交接ZIP及独立`.sha256`文件应一同保存。ZIP内部为：

```text
README.md                 本手册副本
candidate/
  source.zip
  source-manifest.json
  candidate.json
  SHA256SUMS
provenance.json            少量白名单来源与历史验收摘要
SHA256SUMS                外层各文件摘要
```

不传作者的旧`target`、`node_modules`、Maven/npm缓存、私有配置、账号、数据库或原始服务日志；不追加原`.git`或历史附件。候选元数据的历史暂扣状态保持。`provenance.json`中的本机结果只说明输入来源，不能代替接手机器结果。SHA256用于完整性关联，不是数字签名；同一渠道的ZIP与校验文件不能单独证明发送者身份。

先将下面第一个路径换成实际交接ZIP。在正常用户的PowerShell中校验并解包外层，禁止覆盖已有目录：

```powershell
$ErrorActionPreference = 'Stop'
$bundle = (Resolve-Path -LiteralPath './<实际交接文件名>.zip').Path
$checksum = (Get-Content -LiteralPath ($bundle + '.sha256') -Raw).Trim()
if ($checksum -notmatch '^([A-Fa-f0-9]{64})(?:  [^\r\n]+)?$') { throw 'OUTER_CHECKSUM_INVALID' }
$expectedOuter = $Matches[1]
if ((Get-FileHash -LiteralPath $bundle -Algorithm SHA256).Hash -ine $expectedOuter) { throw 'OUTER_HASH_MISMATCH' }
$handoff = Join-Path (Get-Location).Path 'handoff-unpacked'
if (Test-Path -LiteralPath $handoff) { throw 'HANDOFF_DIRECTORY_ALREADY_EXISTS' }
Expand-Archive -LiteralPath $bundle -DestinationPath $handoff
Set-Location -LiteralPath $handoff
```

在新解出的交接根校验内层`SHA256SUMS`；只读取相对文件，不执行包内代码：

```powershell
$root = (Get-Location).Path
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($line in Get-Content -LiteralPath './SHA256SUMS') {
  if ($line -notmatch '^([A-Fa-f0-9]{64})  ([^\r\n]+)$') { throw 'HANDOFF_CHECKSUM_INVALID' }
  $expectedHash = $Matches[1]; $relative = $Matches[2]
  if ($relative -match '[\\:\x00-\x1f]' -or $relative.StartsWith('/') -or
      @($relative.Split('/') | Where-Object { $_ -in @('', '.', '..') }).Count -or !$seen.Add($relative)) { throw 'HANDOFF_PATH_INVALID' }
  $file = [IO.Path]::GetFullPath((Join-Path $root $relative))
  if (!$file.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
      !(Test-Path -LiteralPath $file -PathType Leaf) -or
      (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ine $expectedHash) { throw 'HANDOFF_FILE_HASH_MISMATCH' }
}
```

再核对固定候选四文件，不能仅相信包内清单自报的身份：

```powershell
$expected = @{
  'source.zip' = '541EA4659F510C9B2465E14A21D6CF185F116107EC2EEF81CFAD50BBBCF7CE87'
  'source-manifest.json' = 'D7B7181B1B8DD95CEF330F1BD104005152B4529018C798BBAC8D06172A2EA99E'
  'candidate.json' = '7A11A9A6E6A50824945E3F2BEF843C4782277DFE8D864E6AC10F6D0CB92EA3D1'
  'SHA256SUMS' = '5DB44911BB21A5F7D840DAB9FBFA6287ACF01DD9E0F087185FFFF6B139B0212F'
}
if (@(Get-ChildItem -LiteralPath './candidate' -Force).Count -ne 4) { throw 'CANDIDATE_FILE_SET_INVALID' }
foreach ($name in $expected.Keys) {
  if ((Get-FileHash -LiteralPath (Join-Path './candidate' $name) -Algorithm SHA256).Hash -ine $expected[$name]) { throw 'CANDIDATE_HASH_MISMATCH' }
}
$candidate = (Resolve-Path -LiteralPath './candidate').Path
$source = Join-Path (Get-Location).Path 'source'
if (Test-Path -LiteralPath $source) { throw 'SOURCE_DIRECTORY_ALREADY_EXISTS' }
Expand-Archive -LiteralPath (Join-Path $candidate 'source.zip') -DestinationPath $source
$manifest = Get-Content -LiteralPath (Join-Path $candidate 'source-manifest.json') -Raw | ConvertFrom-Json
$metadata = Get-Content -LiteralPath (Join-Path $candidate 'candidate.json') -Raw | ConvertFrom-Json
if ($metadata.candidateId -cne '070c4b76-70db-48c0-b7d0-b701a527f9e3' -or $metadata.fileCount -ne 1895 -or
    $manifest.files.Count -ne 1895 -or @(Get-ChildItem -LiteralPath $source -Recurse -File -Force).Count -ne 1895) { throw 'EXTRACTED_SOURCE_COUNT_INVALID' }
foreach ($entry in $manifest.files) {
  $file = Join-Path $source $entry.path
  if (!(Test-Path -LiteralPath $file -PathType Leaf) -or (Get-Item -LiteralPath $file).Length -ne $entry.bytes -or
      (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ine $entry.sha256) { throw 'EXTRACTED_SOURCE_HASH_MISMATCH' }
}
pwsh -NoProfile -File (Join-Path $source 'scripts/new-release-candidate.ps1') -Command Verify -CandidatePath $candidate
if ($LASTEXITCODE -ne 0) { throw 'CANDIDATE_VERIFY_FAILED' }
Set-Location -LiteralPath $source
```

原候选`Verify`应返回`SOURCE_VERIFIED_HISTORY_WITHHELD`、正确candidateId及1895文件。此分支不需要Git仓库。该次序先校验收到的固定ZIP摘要，再解包至新目录，最后独立复核ZIP成员与落盘文件；不要从未经校验的包先运行脚本。

## 工具、镜像与下载前提

以下是此前本机实际组合的参考，**不是接手机器已验证结果**：Windows、JDK21.0.12、Node20.19.5、npm10.8.2、pwsh7.6.5、Docker Linux Engine29.4.3、Compose5.1.3。推荐先复现此组合。POM的release17不等于Java17运行已验收；Node22、Linux及其他CPU架构同样未验证。脚本声明pwsh7.0，但调用了较新.NET API，接手时采用已验证的pwsh7.6.5，不能据声明推断全部pwsh7版本可用。

记录实际`pwsh --version`、`java -version`、`node --version`、`npm --version`、`docker version`、`docker compose version`及Docker本地context。Docker必须是本机Linux Engine，endpoint为本地npipe/unix。脚本拒绝`DOCKER_HOST`、`DOCKER_CONTEXT`、TLS重定向及JVM/Node/Maven预加载覆盖；发生拒绝时检查来源，不要为了通过而盲目清除未知配置或改连远程Engine。

Init不会自行pull镜像，接手者须预先通过其批准的网络或镜像导入方式准备以下四个标签，并记录实际image ID：

- `pgvector/pgvector:pg16`
- `postgres:16-alpine`
- `redis:latest`
- `rustfs/rustfs:1.0.0-alpha.72`

标签可能变化，不能用相同标签冒称与作者相同镜像；需在验收记录列出接手实际ID与差异。源码不含镜像、依赖缓存或私有Maven settings。Maven Wrapper指定Maven3.9.11：无Wrapper发行包缓存时需要获取发行包；无Maven/npm依赖缓存时，Build需要接手机器正常访问所需依赖仓库。默认Build可下载依赖，不称离线。`-Offline`只让Maven离线，不自动让npm离线；完全离线还需已完整准备Wrapper发行包、Maven依赖、npm缓存及四镜像，并为该次Build设置`npm_config_offline=true`。不要复制作者的私有缓存或settings来掩盖缺失前提。

Linux仅提供条件说明：使用相同pwsh实现，解包后先`chmod u+x mvnw`，再可用`sh scripts/local-demo.sh ...`。候选ZIP未记录Unix可执行位，Build会直接执行`mvnw`；这一步不改变源码字节。Linux还需可用的shell及Wrapper所需解包/下载工具。此说明不是Linux通过记录。

## 新实例初始化、构建与登录

以下Windows主命令均从新`source`根执行，统一使用新Name `handoff-check`。若该Name目录已存在，停止并核查，不覆盖、迁移或删除既有数据；改Name须全程一致并重新记录独立身份。

```powershell
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Init -Name handoff-check
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Build -Name handoff-check
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Start -Name handoff-check
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Status -Name handoff-check
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Verify -Name handoff-check
```

逐条检查退出码和结果，前条失败不执行后条。首次Init必须`reused=false`，Build为`BUILT`，Start/Status须`READY`。Build跳过业务测试，只说明构建并发布本实例JAR/dist。Init生成两个数据库、Redis、存储共四个依赖容器和五个命名卷，主业务空库、模拟应用0、目标`demo=PAUSED`。不要单独手工运行全量SQL或历史增量。

打开Start返回的本机`/login`地址。账号为`local-admin`，密码仅由接手者在本机私有编辑器打开`bootstrap/target/local-demo/handoff-check/credentials.json`读取；不要在终端打印整个文件、发送聊天、截图或上传日志。所有配置、owner与凭据都由这台机器Init新生成，不复制作者配置。页面起点应为空，空页面或Verify成功不等于主链验收。

## 两条真实HMAC告警

下面代码是人工演练用的固定小段，**尚未在异机执行**，只写本Name的私有检查点并发送两条安全模拟告警。先保存正常起点记录；整个过程中不启用PowerShell Transcript或输出私有变量。不需要登录token，Webhook严禁携带`Authorization`。

代码从`source`根运行。检查点目录只允许首次创建；任何中断都保留目录并停止，不删除检查点、生成新身份或盲重发。网络/解析结果不明确时，先只读调查原externalAlertId是否受理。若要重做，应另行明确新演练范围，保留原失败。

```powershell
$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false)
$private = Join-Path (Get-Location).Path 'bootstrap/target/local-demo/handoff-check'
$owner = Get-Content -LiteralPath (Join-Path $private 'owner.json') -Raw | ConvertFrom-Json
$credentials = Get-Content -LiteralPath (Join-Path $private 'credentials.json') -Raw | ConvertFrom-Json
if ($owner.directory -ne $private -or $owner.instanceId -notmatch '^[a-f0-9]{32}$' -or
    $owner.project -cne ('incidentpilot-local-' + $owner.instanceId) -or
    $credentials.username -cne 'local-admin' -or $credentials.alertSecret -notmatch '^[a-f0-9]{48}$' -or
    [int]$owner.ports.web -lt 1 -or [int]$owner.ports.web -gt 65535) { throw 'HANDOFF_OWNER_INVALID' }
$origin = 'http://127.0.0.1:' + [int]$owner.ports.web
$ready = Invoke-RestMethod -Uri ($origin + '/__local_demo/status') -TimeoutSec 10 -MaximumRedirection 0
if (!$ready.ready -or $ready.instanceId -cne $owner.instanceId) { throw 'HANDOFF_WEB_OWNER_MISMATCH' }
$checkpoints = Join-Path $private 'handoff-webhook-check'
if (Test-Path -LiteralPath $checkpoints) { throw 'HANDOFF_CHECKPOINT_ALREADY_EXISTS_NO_RETRY' }
$null = New-Item -ItemType Directory -Path $checkpoints
function Write-NewCheckpoint([string]$leaf, $value) {
  $stream = [IO.File]::Open((Join-Path $checkpoints $leaf), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
  try { $bytes = $utf8.GetBytes(($value | ConvertTo-Json -Depth 8 -Compress)); $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) }
  finally { $stream.Dispose() }
}
$runId = [Guid]::NewGuid().ToString()
Write-NewCheckpoint 'run.json' @{runId=$runId;instanceId=$owner.instanceId;state='STARTED'}
$accepted = @()
foreach ($n in 1,2) {
  $externalId = 'handoff-' + $runId + '-' + $n
  $body = [ordered]@{externalAlertId=$externalId;eventType='FIRING';occurredAt=[DateTimeOffset]::UtcNow.ToString('o');ruleId=('handoff-' + $runId);title='健康检查失败 探针';severity='HIGH';environment='demo';service='incident-demo';resource=@{type='sim_service';id='demo'}}
  $raw = $utf8.GetBytes(($body | ConvertTo-Json -Depth 5 -Compress))
  $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString([Globalization.CultureInfo]::InvariantCulture)
  $parts = [Collections.Generic.List[byte[]]]::new()
  foreach ($text in @('incident-hmac-v1','1','local-demo',$timestamp,'POST','/incident/v1/alerts')) { $parts.Add($utf8.GetBytes($text)) }
  $parts.Add($raw)
  $framed = [IO.MemoryStream]::new()
  try {
    foreach ($part in $parts) {
      $length = [BitConverter]::GetBytes([int]$part.Length)
      if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($length) }
      $framed.Write($length,0,4); $framed.Write($part,0,$part.Length)
    }
    $hmac = [Security.Cryptography.HMACSHA256]::new($utf8.GetBytes($credentials.alertSecret))
    try { $signature = [Convert]::ToHexString($hmac.ComputeHash($framed.ToArray())).ToLowerInvariant() }
    finally { $hmac.Dispose() }
  } finally { $framed.Dispose() }
  Write-NewCheckpoint ('alert-' + $n + '-attempt.json') @{runId=$runId;externalAlertId=$externalId;bodySha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($raw));state='ATTEMPT_STARTED'}
  $headers = @{'X-Incident-Protocol-Version'='1';'X-Incident-Source-Id'='local-demo';'X-Incident-Key-Id'='local-key';'X-Incident-Request-Timestamp'=$timestamp;'X-Incident-Signature'=('sha256=' + $signature)}
  try {
    $response = Invoke-WebRequest -Uri ($origin + '/api/ragent/incident/v1/alerts') -Method Post -Body $raw -ContentType 'application/json' -Headers $headers -TimeoutSec 30 -MaximumRedirection 0 -MaximumRetryCount 0 -SkipHttpErrorCheck
    $envelope = $response.Content | ConvertFrom-Json
  } catch {
    Write-NewCheckpoint ('alert-' + $n + '-unknown.json') @{runId=$runId;externalAlertId=$externalId;state='TRANSPORT_OR_RESPONSE_UNKNOWN';automaticRetry=$false}
    throw 'WEBHOOK_RESULT_UNKNOWN_STOP_NO_RETRY'
  }
  if ([int]$response.StatusCode -notin @(200,202) -or $envelope.code -cne '0' -or
      [string]$envelope.data.alertId -notmatch '^[0-9]{1,20}$' -or $envelope.data.duplicate -ne $false) {
    Write-NewCheckpoint ('alert-' + $n + '-rejected.json') @{runId=$runId;externalAlertId=$externalId;httpStatus=[int]$response.StatusCode;state='RESPONSE_NOT_ACCEPTED';automaticRetry=$false}
    throw 'WEBHOOK_RESPONSE_NOT_ACCEPTED_STOP'
  }
  Write-NewCheckpoint ('alert-' + $n + '-accepted.json') @{runId=$runId;externalAlertId=$externalId;alertId=[string]$envelope.data.alertId;httpStatus=[int]$response.StatusCode;state='ACCEPTED'}
  $accepted += [string]$envelope.data.alertId
}
[pscustomobject]@{state='TWO_ALERTS_ACCEPTED';alertIds=$accepted}
```

签名为7段各自“4字节大端长度＋原始字节”拼接后的HMAC-SHA256；key是`alertSecret`字符串的UTF-8字节，不做hex解码。HTTP真实URL有`/api/ragent`，签名path却是`/incident/v1/alerts`。body只序列化一次，签名与发送使用同一字节数组。两条告警外部ID不同，rule/service/environment/resource相同；机器时钟须正确，签名窗口为±5分钟。受理不等于处理完成，下一步须确认两条均`PROCESSED`、同Incident且`alertCount=2`。

## 页面业务演练与独立SQL核对

1. 打开`/admin/incidents`，找到两告警对应事件，点Incident ID进入`/admin/incidents/<incidentId>`。确认关联告警2条、同一事件、状态OPEN。
2. 在“诊断与证据”点“发起新诊断”，等待`COMPLETED / SUPPORTED`且当前适用性`VALID`。展开引用，记录原文、行范围、文档与片段摘要；可点“核对目录中的同版本手册 →”。诊断不是根因确认。
3. 找“建议 R2”，核对模拟`READY`建议，点“创建 / 重发同一待审批提案”。在“人工审批与执行结果”核对工具`simulation.set-state v1`、目标`SIM_SERVICE / demo`、冻结参数READY及绑定摘要。
4. 填“审批理由（拒绝必填）”，点“拒绝此 Action”。记录原Action ID，确认`APPROVAL_REJECTED / REJECTED`、没有Execution/回执；此时先做下方SQL，必须应用0、目标PAUSED/version0。
5. 回同一诊断R2，点“拒绝后明确提出新 Action”。确认新Action ID不同且显示原提案ID；点“人工批准此冻结 Action”。等到`SUCCEEDED`、票据CONSUMED、回执`SUCCESS / … / APPLIED`及`desiredState=READY · applied=true`。出现UNKNOWN、失败、超时或摘要不匹配即停止并只读排查，不重新批准或直接调用MCP。
6. 保持同一页面会话，再点同一R2的“拒绝后明确提出新 Action”，应仍返回已批准Action、不增加Action或应用；这是重发同一选择意图，不是再次批准。页面的原意图仅存于当前组件内存，**不要刷新/关闭浏览器再点击并声称是同意图重放**。若会话丢失，此项记待执行，保留已有Action，不用新意图替代证据。
7. 可打开`/admin/incidents/<incidentId>/review`读取复盘。执行成功不自动关闭Incident；本演练应仍OPEN。

以下只读SQL使用候选已有所有权函数，先核对本地Docker与两个命名服务。命令不打印Compose环境或凭据，也不修改数据。从`source`根执行：

```powershell
. ./scripts/local-demo.ps1 -Library -Name handoff-check
Test-DemoDocker
$sqlOwner = Get-DemoManifest
Assert-DemoContainers $sqlOwner
function Get-OwnedDbContainer([string]$service) {
  if ($service -notin @('main-db','simulation-db')) { throw 'DATABASE_SERVICE_INVALID' }
  $ids = @((& docker ps -q --filter ('label=com.docker.compose.project=' + $sqlOwner.project) --filter ('label=com.docker.compose.service=' + $service)) | Where-Object { $_ })
  if ($LASTEXITCODE -ne 0 -or $ids.Count -ne 1 -or $ids[0] -notmatch '^[a-f0-9]{12,64}$') { throw 'DATABASE_CONTAINER_NOT_UNIQUE' }
  $label = (& docker inspect --format '{{index .Config.Labels "incidentpilot.local.instance"}}' $ids[0]).Trim()
  if ($LASTEXITCODE -ne 0 -or $label -cne $sqlOwner.instanceId) { throw 'DATABASE_OWNER_MISMATCH' }
  return $ids[0]
}
$mainDb = Get-OwnedDbContainer 'main-db'
$simulationDb = Get-OwnedDbContainer 'simulation-db'
$sql = "BEGIN TRANSACTION READ ONLY; SELECT json_build_object('applications',(SELECT count(*) FROM simulation_application),'state',(SELECT state FROM simulation_target WHERE target_id='demo'),'version',(SELECT version FROM simulation_target WHERE target_id='demo')); COMMIT;"
& docker exec $simulationDb psql -X -q -t -A -v ON_ERROR_STOP=1 -U postgres -d incidentpilot_simulation -c $sql
if ($LASTEXITCODE -ne 0) { throw 'SIMULATION_READ_FAILED' }
```

首次/拒绝后的期望为`applications=0,state=PAUSED,version=0`。批准后执行下面部分，Action ID从页面取得；不要误用拒绝Action。页面只显示executionId，不显示executionKey，因此key由主库只读获取：

```powershell
$approvedActionId = Read-Host '输入已批准并成功的Action ID'
if ($approvedActionId -notmatch '^[0-9]{1,20}$') { throw 'ACTION_ID_INVALID' }
$keySql = "BEGIN TRANSACTION READ ONLY; SELECT execution_key FROM t_incident_action_execution WHERE action_id='$approvedActionId'; COMMIT;"
$executionKey = (& docker exec $mainDb psql -X -q -t -A -v ON_ERROR_STOP=1 -U postgres -d incidentpilot -c $keySql).Trim()
if ($LASTEXITCODE -ne 0 -or $executionKey -notmatch '^[a-f0-9]{32,64}$') { throw 'EXECUTION_KEY_NOT_UNIQUE_OR_INVALID' }
$sql = "BEGIN TRANSACTION READ ONLY; SELECT json_build_object('applications',(SELECT count(*) FROM simulation_application),'matching',(SELECT count(*) FROM simulation_application WHERE execution_key='$executionKey'),'state',(SELECT state FROM simulation_target WHERE target_id='demo'),'version',(SELECT version FROM simulation_target WHERE target_id='demo')); COMMIT;"
& docker exec $simulationDb psql -X -q -t -A -v ON_ERROR_STOP=1 -U postgres -d incidentpilot_simulation -c $sql
if ($LASTEXITCODE -ne 0) { throw 'APPLICATION_ONCE_READ_FAILED' }
```

批准、同意图重放及重启后三次均须`applications=1,matching=1,state=READY,version=1`。将各时点只含计数/ID/状态的结果分别保存，失败文件不覆盖。命令只读不代表断言已通过，必须检查实际返回值；不要把HTTP200或回执显示单独当作“一次应用”的证据。

## 停止、重启与留存

所有命令仍指定`handoff-check`。在私有目录分别保留Verify前后输出，避免其常规`verify.json`被下一次覆盖后丢失基线：

```powershell
$before = & pwsh -NoProfile -File scripts/local-demo.ps1 -Command Verify -Name handoff-check
if ($LASTEXITCODE -ne 0) { throw 'VERIFY_BEFORE_FAILED' }
$before | Out-File -LiteralPath './bootstrap/target/local-demo/handoff-check/handoff-verify-before.json' -Encoding utf8 -NoClobber
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Stop -Name handoff-check
if ($LASTEXITCODE -ne 0) { throw 'STOP_FAILED' }
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Start -Name handoff-check
if ($LASTEXITCODE -ne 0) { throw 'RESTART_FAILED' }
$after = & pwsh -NoProfile -File scripts/local-demo.ps1 -Command Verify -Name handoff-check
if ($LASTEXITCODE -ne 0) { throw 'VERIFY_AFTER_FAILED' }
$after | Out-File -LiteralPath './bootstrap/target/local-demo/handoff-check/handoff-verify-after.json' -Encoding utf8 -NoClobber
```

人工逐项比对account、5手册、Incident ID/OPEN/版本/alertCount2、Diagnosis ID、拒绝/批准Action ID与状态、原回执ID；两次分页`hasMore`均须false，否则先完整读取再判断。`verifiedAt`正常变化不作数据漂移。重新登录只读复核诊断resultDigest、引用、绑定摘要和回执，并重新获取两个容器ID后再跑上方SQL，应用仍只1次。

记录重启前后自有进程身份（本实例`processes.json`中的PID、创建时刻与配置标识）、两个JAR和完整dist摘要。9份私有文件为`credentials.json`、`artifacts.json`、`app.properties`、`mcp.properties`、`web.json`、`compose.yaml`、`compose.env`、`initialized.json`、`owner.json`；使用`Get-FileHash -Algorithm SHA256`只保存摘要，不能分发文件原文。五个命名卷为`main-data`、`simulation-data`、`redis-data`、`storage-data`、`storage-logs`，均带本instance标签；存储`/data`与`/logs`须对应明确命名卷。记录全局原容器/卷基线、重启集合、最后Stop后的集合，确认旧资源身份/运行状态保持且没有额外匿名卷。不要使用全局prune、删卷或清库来凑数量。

最后正常停止并保留数据：

```powershell
pwsh -NoProfile -File scripts/local-demo.ps1 -Command Stop -Name handoff-check
if ($LASTEXITCODE -ne 0) { throw 'FINAL_STOP_FAILED' }
```

必须实际得到`STOPPED,dataRetained=true`。停止后的URL不再是可用预览入口；后续继续查看可在核对同一Name所有权后正常Start，不重复Init新库或覆盖配置。

## 待接手者填写的验收记录

不要预填“通过”。至少记录接手机器/操作系统/架构、工具版本、镜像ID、是否联网下载、候选/ZIP/manifest摘要、独立runId/Name/instanceId、实际命令/退出码/时间、失败安全错误码、业务ID和只读计数。私有密码、token、票据秘密、原始请求或服务日志不作为公开附件。

| 验收项 | 当前异机状态 | 执行后填写的最小证据 |
| --- | --- | --- |
| 外层/四文件/1895源码 | 待执行 | 摘要、Verify身份与文件数 |
| 工具/镜像/下载前提 | 待执行 | 版本、实际image ID、缓存/联网说明 |
| 新Name Init与空库 | 待执行 | reused=false、独立身份、0应用/PAUSED |
| 从源码Build与真实Start | 待执行 | 退出码、JAR/dist摘要、READY、实际进程 |
| 两条签名告警聚合 | 待执行 | 私有checkpoint、2个Alert ID、同Incident/count2 |
| 诊断与引用 | 待执行 | COMPLETED/SUPPORTED/VALID、引用和摘要 |
| 拒绝零应用 | 待执行 | 原Action、无Execution、SQL0/PAUSED |
| 批准唯一应用 | 待执行 | 新Action、Execution/Receipt、SQL1/1/READY |
| 原选择意图重放 | 待执行 | 同Action、无额外应用；刷新丢意图则保留待执行 |
| Stop/Start留存与资源保护 | 待执行 | 前后快照、SQL、私有/制品摘要、5命名卷 |
| 最后Stop | 待执行 | STOPPED/dataRetained、自有进程/容器结束 |

首次失败、后续续跑及最终结果分开保留，不覆盖失败或从旧记录复制PASS。只有第二台真实机器完成上述链路，才能更新异机状态；没有机器时继续保持`PREPARED_AWAITING_SECOND_MACHINE`。

冻结候选的`check-delivery.ps1`连CheckOnly也依赖`git rev-parse HEAD`。此源码包没有`.git`，在禁止父仓库发现的独立解包根会因Git前置条件失败：内部断言为`GIT_UNAVAILABLE`，对外记录为`FAIL / GATE_FAILED`、退出1，不会得到`CHECKED`或进入测试阶段。不要擅自`git init/commit`伪造来源，也不要借父仓库HEAD冒充候选实现提交。完整All门禁的来源标识适配另列后续范围，本手册不声称异机All通过。

本机阶段11的930项测试及阶段12的持久业务结果不能累计成异机数据。旧首次静态异常根因、旧匿名卷历史归属仍TBD；新机器成功也不能反推旧问题已定位。提交、Tag、推送、正式发布、生产部署及任何旧环境变更继续另需授权。

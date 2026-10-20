param([string]$Archive)
$ErrorActionPreference = 'Stop'
trap { Write-Host 'CONTRACT_FAILED'; exit 1 }
. "$PSScriptRoot/run-lab.ps1"
$contractRoot = Resolve-LabPath (Join-Path $script:LabBase ('contract-' + [guid]::NewGuid()))
New-Item -ItemType Directory -Path $contractRoot -Force | Out-Null
$results = @()
function Check([string]$Name, [scriptblock]$Body) {
    & $Body
    $script:results += [ordered]@{name=$Name;status='PASS';evidenceLevel='SCRIPT_CONTRACT_FIXTURE'}
}
function Reject([scriptblock]$Body) {
    $rejected = $false
    try { & $Body | Out-Null } catch { $rejected = $true }
    Assert-Lab $rejected 'EXPECTED_REJECTION'
}
$definitions = @(Get-LabScenarios)
Check 'catalogue-six-and-seven-target-tests' {
    Assert-Lab ($definitions.Count -eq 6 -and ($definitions | Measure-Object expectedTests -Sum).Sum -eq 7) 'CATALOGUE_COUNTS'
    foreach ($definition in $definitions) {
        $file = if ($definition.module -eq 'bootstrap') {
            Join-Path $script:LabRepo 'bootstrap/src/test/java/com/nageoffer/ai/ragent/IncidentObservabilityIsolatedTest.java'
        } else {
            Join-Path $script:LabRepo "incident/src/test/java/com/nageoffer/ai/ragent/incident/integration/$($definition.className).java"
        }
        $source = Get-Content -LiteralPath $file -Raw
        foreach ($test in $definition.tests) { Assert-Lab ($source -match "void $test\(") 'METHOD_MAPPING' }
    }
}
Check 'parameter-conflict-and-unknown' {
    $List=$true; $All=$true; Assert-Lab ((Invoke-Lab) -eq 2) 'MODE_NOT_REJECTED'
    $List=$false; $All=$false; $Scenario='UNKNOWN'; Assert-Lab ((Invoke-Lab) -eq 2) 'ID_NOT_REJECTED'
    $Scenario=$null
}
Check 'path-traversal-and-prefix-sibling' {
    Reject { Resolve-LabPath (Join-Path $script:LabBase '../outside') }
    Reject { Resolve-LabPath ($script:LabBase + '-other/file') }
}
Check 'reparse-point' {
    $link = Join-Path $contractRoot 'linked'
    New-Item -ItemType Junction -Path $link -Target $contractRoot | Out-Null
    Reject { Resolve-LabPath (Join-Path $link 'proof.json') }
    Reject { Get-LabFiles $contractRoot }
    # Remove the junction itself, never recurse into its destination.
    [IO.Directory]::Delete($link)
}
Check 'second-run-lock' {
    $lock = [IO.File]::Open((Join-Path $script:LabBase 'run.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try { $Scenario='ALERT-REPLAY'; Assert-Lab ((Invoke-Lab) -eq 2) 'LOCK_NOT_REJECTED' }
    finally { $lock.Dispose(); $Scenario=$null }
}
Check 'blocked-environment-is-archived-and-not-passed' {
    $previousExitCode = $global:LASTEXITCODE
    function docker { $global:LASTEXITCODE=1; return 'CONTRACT_DOCKER_UNAVAILABLE' }
    $before=@(Get-ChildItem $script:LabBase -Directory | Select-Object -ExpandProperty Name)
    $Scenario='ALERT-REPLAY'
    try {
        Assert-Lab ((Invoke-Lab) -eq 2) 'ENVIRONMENT_NOT_BLOCKED'
        $created=@(Get-ChildItem $script:LabBase -Directory | Where-Object Name -notin $before)
        Assert-Lab ($created.Count -eq 1) 'BLOCKED_RUN_MISSING'
        $blocked=Read-LabJson (Join-Path $created[0].FullName 'manifest.json')
        Assert-Lab ($blocked.status -eq 'BLOCKED') 'BLOCKED_STATUS_INVALID'
        Reject { Test-LabArchive $created[0].FullName }
    } finally { $Scenario=$null; $global:LASTEXITCODE=$previousExitCode }
}
# Explicit synthetic contracts. These files do not prove any Java/runtime execution.
$definition=$definitions[0]; $runId=[guid]::NewGuid().ToString(); $digest='A'.PadLeft(64,'A')
$scene=Join-Path $contractRoot 'ALERT-REPLAY'
New-Item -ItemType Directory -Path "$scene/proof","$scene/reports/fixture" -Force | Out-Null
$origin=@{runId=$runId;scenarioId=$definition.id;sourceDigest=$digest;tests=$definition.tests;javaRuntime='CONTRACT_FIXTURE';compileRelease=17}
Write-LabJson "$scene/proof/proof-origin.json" $origin
Write-LabJson "$scene/proof/$($definition.tests[0]).json" @{status='PROCESSED';incidentCount=1}
Write-LabJson "$scene/proof/$($definition.tests[1]).json" @{status='PROCESSED';retryCount=5;attemptCount=6;processingEpoch=1;terminalFacts=1;replayAccepted=1;replayRejected=1;recoveredFacts=1}
$xmlPath="$scene/reports/fixture/TEST-fixture.xml"
$xmlText=@"
<testsuite name="fixture.AlertRecoveryTest" tests="2" failures="0" errors="0" skipped="0">
<properties><property name="lab.runId" value="$runId"/><property name="lab.scenarioId" value="ALERT-REPLAY"/><property name="lab.sourceDigest" value="$digest"/></properties>
<testcase name="$($definition.tests[0])"/><testcase name="$($definition.tests[1])"/>
</testsuite>
"@
$xmlText | Set-Content -LiteralPath $xmlPath -Encoding utf8
Check 'valid-scene-contract' { $null=Test-LabScene $scene $definition $runId $digest }
Check 'old-run-origin' { Reject { Test-LabScene $scene $definition ([guid]::NewGuid().ToString()) $digest } }
Check 'source-mismatch' { Reject { Test-LabScene $scene $definition $runId ('B'.PadLeft(64,'B')) } }
foreach ($attribute in @('failures','errors','skipped')) {
    Check "$attribute-rejected" {
        $xmlText.Replace("$attribute="+[char]34+'0'+[char]34,"$attribute="+[char]34+'1'+[char]34) | Set-Content -LiteralPath $xmlPath -Encoding utf8
        try { Reject { Test-LabScene $scene $definition $runId $digest } }
        finally { $xmlText | Set-Content -LiteralPath $xmlPath -Encoding utf8 }
    }
}
Check 'missing-proof' {
    Move-Item -LiteralPath "$scene/proof/proof-origin.json" -Destination "$scene/proof/origin.saved"
    try { Reject { Test-LabScene $scene $definition $runId $digest } }
    finally { Move-Item -LiteralPath "$scene/proof/origin.saved" -Destination "$scene/proof/proof-origin.json" }
}
Check 'wrong-case-selection' {
    $xmlText.Replace($definition.tests[0],'WrongCase') | Set-Content -LiteralPath $xmlPath -Encoding utf8
    try { Reject { Test-LabScene $scene $definition $runId $digest } }
    finally { $xmlText | Set-Content -LiteralPath $xmlPath -Encoding utf8 }
}
Check 'unfinished-run' {
    Write-LabJson "$contractRoot/manifest.json" @{status='RUNNING'}
    Write-LabHashes $contractRoot
    Reject { Test-LabArchive $contractRoot }
}
Check 'tamper-and-extra-file' {
    Add-Content -LiteralPath $xmlPath -Value 'modified'
    Reject { Test-LabArchive $contractRoot }
    $xmlText | Set-Content -LiteralPath $xmlPath -Encoding utf8
    Write-LabHashes $contractRoot
    Set-Content "$contractRoot/unlisted.txt" 'fixture'
    Reject { Test-LabArchive $contractRoot }
}
if ($Archive) {
    # Every mutation uses a fresh copy; original evidence is left intact.
    Check 'actual-archive-read-only' {
        $before=(Get-FileHash (Join-Path $Archive 'SHA256SUMS.json')).Hash
        $null=Test-LabArchive $Archive
        Assert-Lab ((Get-FileHash (Join-Path $Archive 'SHA256SUMS.json')).Hash -eq $before) 'VERIFY_MODIFIED_ARCHIVE'
    }
    foreach ($mutation in @('TAMPER','EXTRA','MISSING','OLD_ORIGIN','FAIL','SKIP','RUNNING','CHANNEL','TRACE','SOURCE','NESTED_CHECKSUM','CATALOGUE','MANIFEST_RESULT')) {
        Check "actual-$mutation-rejected" {
            $copy=Join-Path $contractRoot $mutation
            Copy-Item -LiteralPath (Resolve-LabPath $Archive) -Destination $copy -Recurse
            $manifest=Read-LabJson "$copy/manifest.json"
            $firstScene=Join-Path $copy $manifest.selectedScenarios[0]
            switch ($mutation) {
                'TAMPER' { Add-Content "$copy/report.md" 'changed' }
                'EXTRA' { Set-Content "$copy/unlisted.txt" 'changed' }
                'MISSING' { Remove-Item -LiteralPath "$firstScene/proof/proof-origin.json" }
                'OLD_ORIGIN' {
                    $origin=Read-LabJson "$firstScene/proof/proof-origin.json"
                    $origin.runId=[guid]::NewGuid().ToString()
                    Write-LabJson "$firstScene/proof/proof-origin.json" $origin
                    Write-LabHashes $copy
                }
                'FAIL' { $manifest.status='FAIL';Write-LabJson "$copy/manifest.json" $manifest;Write-LabHashes $copy }
                'SKIP' {
                    $report=@(Get-ChildItem "$firstScene/reports" -Recurse -Filter 'TEST-*.xml')[0]
                    [xml]$xml=Get-Content $report.FullName -Raw; $xml.testsuite.skipped='1'; $xml.Save($report.FullName)
                    Write-LabHashes $copy
                }
                'RUNNING' { $manifest.status='RUNNING';Write-LabJson "$copy/manifest.json" $manifest;Write-LabHashes $copy }
                'CHANNEL' {
                    if ($manifest.selectedScenarios -contains 'RECEIPT-CHANNELS') {
                        $channels=Read-LabJson "$copy/RECEIPT-CHANNELS/proof/channels/summary.json"
                        $channels.cases[0].applicationsForExecution=2
                        Write-LabJson "$copy/RECEIPT-CHANNELS/proof/channels/summary.json" $channels
                        Write-LabHashes $copy
                    } else { throw 'FULL_ARCHIVE_REQUIRED' }
                }
                'TRACE' {
                    if ($manifest.selectedScenarios -contains 'GOLDEN') {
                        Remove-Item -LiteralPath "$copy/GOLDEN/proof/trace-proof.json"
                        Write-LabHashes $copy
                    } else { throw 'FULL_ARCHIVE_REQUIRED' }
                }
                'SOURCE' { Add-Content "$copy/source-hashes.json" 'changed'; Write-LabHashes $copy }
                'NESTED_CHECKSUM' { Set-Content "$firstScene/proof/SHA256SUMS.json" 'unlisted' }
                'CATALOGUE' {
                    $catalogue=@(Read-LabJson "$copy/scenarios.json");$catalogue[0].expectedTests=999
                    Write-LabJson "$copy/scenarios.json" $catalogue;Write-LabHashes $copy
                }
                'MANIFEST_RESULT' {
                    $manifest.scenarioResults[0].status='FAIL'
                    Write-LabJson "$copy/manifest.json" $manifest;Write-LabHashes $copy
                }
            }
            Reject { Test-LabArchive $copy }
        }
    }
}
Write-LabJson (Join-Path $contractRoot 'contract-results.json') @{status='PASS';checks=$results.Count;evidenceLevel='SCRIPT_CONTRACT_FIXTURE';results=$results}
Write-Host "CONTRACT_PASS $($results.Count) $contractRoot"
exit 0

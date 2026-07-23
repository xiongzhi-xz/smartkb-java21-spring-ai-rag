[CmdletBinding()]
param(
    [int]$OpenSearchPort = 0,
    [int]$MilvusPort = 19530,
    [switch]$StopServices
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$reportPath = Join-Path $projectRoot 'target/reports/retrieval-degradation-smoke.md'
$openSearchPort = if ($OpenSearchPort -gt 0) {
    $OpenSearchPort
} elseif ($env:SMARTKB_OPENSEARCH_PORT) {
    [int]$env:SMARTKB_OPENSEARCH_PORT
} else {
    9200
}
$openSearchEndpoint = "http://localhost:$openSearchPort"
$env:SMARTKB_OPENSEARCH_PORT = [string]$openSearchPort
$runId = [guid]::NewGuid().ToString('N')
$collection = "smartkb_degradation_$runId"
$index = "smartkb-degradation-$runId"
$knowledgeBaseId = [guid]::NewGuid().ToString()
$documentId = [guid]::NewGuid().ToString()
$chunkId = [guid]::NewGuid().ToString()
$observations = [System.Collections.Generic.List[object]]::new()
$failed = $false
$cleanupFailed = $false
$seeded = $false

function Invoke-Compose([string[]]$Arguments) {
    & docker compose @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Invoke-ComposeWithRetry([string[]]$Arguments, [int]$Attempts = 3) {
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            Invoke-Compose $Arguments
            return
        } catch {
            if ($attempt -eq $Attempts) { throw }
            Write-Warning "Compose command failed (attempt $attempt/$Attempts); retrying: $($_.Exception.Message)"
            Start-Sleep -Seconds 10
        }
    }
}

function Invoke-Scenario([string]$Scenario, [int]$ScenarioMilvusPort, [string]$ScenarioOpenSearchEndpoint, [bool]$CoreScenario = $true, [int]$Attempts = 1) {
    $started = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $mavenExitCode = 1
        for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
            Write-Host "Running degradation scenario: $Scenario (attempt $attempt/$Attempts)"
            & mvn -B -Pintegration-tests `
            '-Dtest=NoUnitTestsForRetrievalSmoke' `
            '-Dsurefire.failIfNoSpecifiedTests=false' `
            '-Dit.test=RetrievalDegradationSmokeIT' `
            "-Dsmartkb.degradation.scenario=$Scenario" `
            "-Dsmartkb.degradation.milvus.host=localhost" `
            "-Dsmartkb.degradation.milvus.port=$ScenarioMilvusPort" `
            "-Dsmartkb.degradation.opensearch.endpoint=$ScenarioOpenSearchEndpoint" `
            "-Dsmartkb.degradation.collection=$collection" `
            "-Dsmartkb.degradation.index=$index" `
            "-Dsmartkb.degradation.knowledgeBaseId=$knowledgeBaseId" `
            "-Dsmartkb.degradation.documentId=$documentId" `
                "-Dsmartkb.degradation.chunkId=$chunkId" `
                verify
            $mavenExitCode = $LASTEXITCODE
            if ($mavenExitCode -eq 0) { break }
            if ($attempt -lt $Attempts) {
                Write-Warning "Scenario $Scenario failed on attempt $attempt/$Attempts; retrying after backend recovery delay."
                Start-Sleep -Seconds 10
            }
        }
        $attemptsUsed = [math]::Min($attempt, $Attempts)
        if ($mavenExitCode -ne 0) {
            throw "Maven scenario $Scenario failed after $Attempts attempt(s) with exit code $mavenExitCode"
        }
        $observations.Add([pscustomobject]@{
            Scenario = $Scenario
            Milvus = "localhost:$ScenarioMilvusPort"
            OpenSearch = $ScenarioOpenSearchEndpoint
            Status = 'PASS'
            Attempts = $attemptsUsed
            ObservedSeconds = [math]::Round($started.Elapsed.TotalSeconds, 1)
            Error = ''
        })
        if ($Scenario -eq 'seed') { $script:seeded = $true }
    } catch {
        if ($CoreScenario) {
            $script:failed = $true
        } else {
            $script:cleanupFailed = $true
        }
        $observations.Add([pscustomobject]@{
            Scenario = $Scenario
            Milvus = "localhost:$ScenarioMilvusPort"
            OpenSearch = $ScenarioOpenSearchEndpoint
            Status = if ($CoreScenario) { 'FAILED' } else { 'WARN' }
            Attempts = [math]::Min($attempt, $Attempts)
            ObservedSeconds = [math]::Round($started.Elapsed.TotalSeconds, 1)
            Error = $_.Exception.Message.Replace('|', '/')
        })
        throw
    }
}

function Write-Report {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $reportPath) | Out-Null
    $output = [System.Text.StringBuilder]::new()
    [void]$output.AppendLine('# Retrieval degradation smoke report')
    [void]$output.AppendLine()
    [void]$output.AppendLine("- Generated at: $(Get-Date -Format o)")
    [void]$output.AppendLine('- Scope: real Compose backend stop plus production adapter fallback assertions; not a load test')
    [void]$output.AppendLine('- Run ID: ' + $runId + '')
    [void]$output.AppendLine()
    [void]$output.AppendLine('| Scenario | Milvus endpoint | OpenSearch endpoint | Attempts | Observed s | Status | Error |')
    [void]$output.AppendLine('| --- | --- | --- | ---: | ---: | --- | --- |')
    foreach ($observation in $observations) {
        [void]$output.AppendLine("| $($observation.Scenario) | $($observation.Milvus) | $($observation.OpenSearch) | $($observation.Attempts) | $($observation.ObservedSeconds) | $($observation.Status) | $($observation.Error) |")
    }
    [void]$output.AppendLine()
    $result = if ($failed) { 'FAILED' } else { 'PASS' }
    [void]$output.AppendLine('Result: ' + $result + '')
    if ($cleanupFailed) {
        [void]$output.AppendLine('- Cleanup/recovery: WARN; core assertions completed, but backend restoration or temporary-data cleanup needs manual review.')
    } else {
        [void]$output.AppendLine('- Cleanup/recovery: PASS')
    }
    [void]$output.AppendLine()
    [void]$output.AppendLine('- `seed`: writes deterministic chunks to both real backends.')
    [void]$output.AppendLine('- `keyword-only`: Milvus is stopped; OpenSearch must answer and the service must report `keyword-only`.')
    [void]$output.AppendLine('- `dense-only`: OpenSearch is stopped; Milvus must answer and the service must report `dense-only`; bounded retries cover Milvus collection recovery after restart.')
    [void]$output.AppendLine('- `unavailable`: both backends are stopped; the service must return `RETRIEVAL_UNAVAILABLE`.')
    [void]$output.AppendLine('- The run records observations only; it makes no QPS, percentile, or capacity claim.')
    [System.IO.File]::WriteAllText($reportPath, $output.ToString())
    Write-Host "Report: $reportPath"
}

Push-Location $projectRoot
try {
    Write-Host 'Starting Milvus and OpenSearch with Docker Compose...'
    Invoke-ComposeWithRetry @('up', '-d', '--wait', 'milvus', 'opensearch')
    Invoke-Scenario 'seed' $MilvusPort $openSearchEndpoint

    Write-Host 'Stopping Milvus to inject a dense-backend failure...'
    Invoke-Compose @('stop', 'milvus')
    Invoke-Scenario 'keyword-only' $MilvusPort $openSearchEndpoint

    Write-Host 'Restoring Milvus and stopping OpenSearch to inject a keyword-backend failure...'
    Invoke-ComposeWithRetry @('up', '-d', '--wait', 'milvus')
    Invoke-Compose @('stop', 'opensearch')
    Invoke-Scenario 'dense-only' $MilvusPort $openSearchEndpoint $true 3

    Write-Host 'Stopping both backends to verify the unavailable contract...'
    Invoke-Compose @('stop', 'milvus', 'opensearch')
    Invoke-Scenario 'unavailable' $MilvusPort $openSearchEndpoint
} catch {
    Write-Error $_.Exception.Message
} finally {
    Write-Host 'Restoring both retrieval backends for cleanup...'
    try {
        Invoke-ComposeWithRetry @('up', '-d', '--wait', 'milvus', 'opensearch')
        if ($seeded) {
            try {
                Invoke-Scenario 'cleanup' $MilvusPort $openSearchEndpoint $false 3
            } catch {
                Write-Warning "Cleanup scenario failed: $($_.Exception.Message)"
            }
        }
    } catch {
        $cleanupFailed = $true
        Write-Warning "Unable to restore retrieval backends: $($_.Exception.Message)"
    }
    if ($StopServices) {
        try { Invoke-Compose @('stop', 'milvus', 'opensearch') } catch { Write-Warning $_.Exception.Message }
    }
    Write-Report
    Pop-Location
}

if ($failed) { exit 1 }

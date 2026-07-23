[CmdletBinding()]
param(
    [int]$OpenSearchPort = 0,
    [switch]$StopServices
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$reportPath = Join-Path $projectRoot 'target/reports/retrieval-backends-smoke.md'
$openSearchPort = if ($OpenSearchPort -gt 0) {
    $OpenSearchPort
} elseif ($env:SMARTKB_OPENSEARCH_PORT) {
    [int]$env:SMARTKB_OPENSEARCH_PORT
} else {
    9200
}
$env:SMARTKB_OPENSEARCH_PORT = [string]$openSearchPort
$endpoint = "http://localhost:$openSearchPort"

Push-Location $projectRoot
try {
    Write-Host 'Starting Milvus and OpenSearch with Docker Compose...'
    docker compose up -d --wait milvus opensearch
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed with exit code $LASTEXITCODE"
    }

    Write-Host 'Running the deterministic retrieval backend smoke test...'
    mvn -B -Pintegration-tests `
        '-Dit.test=RetrievalBackendsSmokeIT' `
        '-Dsmartkb.smoke.milvus.host=localhost' `
        '-Dsmartkb.smoke.milvus.port=19530' `
        "-Dsmartkb.smoke.opensearch.endpoint=$endpoint" `
        "-Dsmartkb.smoke.report=$reportPath" `
        verify
    $mvnExitCode = $LASTEXITCODE

    if (Test-Path -LiteralPath $reportPath) {
        $composeSnapshot = docker compose ps milvus opensearch
        Add-Content -Encoding UTF8 -LiteralPath $reportPath -Value @(
            ''
            '## Compose service snapshot'
            ''
            '```text'
            ($composeSnapshot -join "`n")
            '```'
        )
        Write-Host "Report: $reportPath"
    }

    if ($mvnExitCode -ne 0) {
        exit $mvnExitCode
    }
}
finally {
    if ($StopServices) {
        Write-Host 'Stopping only the Milvus and OpenSearch services...'
        docker compose stop milvus opensearch
    } else {
        Write-Host 'Leaving Milvus and OpenSearch running. Pass -StopServices to stop them.'
    }
    Pop-Location
}

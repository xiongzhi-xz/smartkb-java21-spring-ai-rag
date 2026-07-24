[CmdletBinding()]
param(
    [string]$Url = "http://localhost:8080/actuator/health",
    [ValidateRange(1, 1000)]
    [int]$Requests = 100,
    [ValidateRange(1, 256)]
    [int]$Concurrency = 10,
    [ValidateRange(0, 10000)]
    [int]$WarmupRequests = 10,
    [ValidateRange(100, 120000)]
    [int]$TimeoutMilliseconds = 10000,
    [string]$Scenario = "unspecified",
    [string]$ReportPath = "target/reports/http-load.md"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-HttpRequest {
    param([string]$TargetUrl, [int]$Timeout)

    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $request = [System.Net.WebRequest]::Create($TargetUrl)
        $request.Method = "GET"
        $request.Timeout = $Timeout
        $request.ReadWriteTimeout = $Timeout
        $response = [System.Net.HttpWebResponse]$request.GetResponse()
        $statusCode = [int]$response.StatusCode
        $response.Close()
        [pscustomobject]@{ DurationMs = $watch.Elapsed.TotalMilliseconds; StatusCode = $statusCode; Error = $null }
    } catch {
        $response = if ($_.Exception.PSObject.Properties.Match("Response").Count -gt 0) { $_.Exception.Response } else { $null }
        $statusCode = if ($response) { [int]$response.StatusCode } else { 0 }
        [pscustomobject]@{ DurationMs = $watch.Elapsed.TotalMilliseconds; StatusCode = $statusCode; Error = $_.Exception.Message }
    }
}

function Get-Percentile {
    param([double[]]$SortedValues, [double]$Percentile)

    if ($SortedValues.Count -eq 0) { return 0.0 }
    $index = [Math]::Ceiling($Percentile * $SortedValues.Count) - 1
    return $SortedValues[[Math]::Max(0, [Math]::Min($index, $SortedValues.Count - 1))]
}

for ($index = 0; $index -lt $WarmupRequests; $index++) {
    Invoke-HttpRequest -TargetUrl $Url -Timeout $TimeoutMilliseconds | Out-Null
}

$worker = {
    param($TargetUrl, $Timeout)
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $request = [System.Net.WebRequest]::Create($TargetUrl)
        $request.Method = "GET"
        $request.Timeout = $Timeout
        $request.ReadWriteTimeout = $Timeout
        $response = [System.Net.HttpWebResponse]$request.GetResponse()
        $statusCode = [int]$response.StatusCode
        $response.Close()
        [pscustomobject]@{ DurationMs = $watch.Elapsed.TotalMilliseconds; StatusCode = $statusCode; Error = $null }
    } catch {
        $response = if ($_.Exception.PSObject.Properties.Match("Response").Count -gt 0) { $_.Exception.Response } else { $null }
        $statusCode = if ($response) { [int]$response.StatusCode } else { 0 }
        [pscustomobject]@{ DurationMs = $watch.Elapsed.TotalMilliseconds; StatusCode = $statusCode; Error = $_.Exception.Message }
    }
}

$pool = [RunspaceFactory]::CreateRunspacePool(1, $Concurrency)
$pool.Open()
$pending = @()
$benchmarkWatch = [System.Diagnostics.Stopwatch]::StartNew()
try {
    for ($index = 0; $index -lt $Requests; $index++) {
        $powerShell = [PowerShell]::Create()
        $powerShell.RunspacePool = $pool
        [void]$powerShell.AddScript($worker).AddArgument($Url).AddArgument($TimeoutMilliseconds)
        $pending += [pscustomobject]@{ PowerShell = $powerShell; Handle = $powerShell.BeginInvoke() }
    }

    $results = foreach ($request in $pending) {
        try { $request.PowerShell.EndInvoke($request.Handle) } finally { $request.PowerShell.Dispose() }
    }
} finally {
    $pool.Close()
    $pool.Dispose()
}
$benchmarkWatch.Stop()

$successful = @($results | Where-Object { $_.StatusCode -ge 200 -and $_.StatusCode -lt 400 })
$failed = @($results | Where-Object { $_.StatusCode -lt 200 -or $_.StatusCode -ge 400 })
$durations = @($results | ForEach-Object { [double]$_.DurationMs } | Sort-Object)
$throughput = if ($benchmarkWatch.Elapsed.TotalSeconds -gt 0) { $Requests / $benchmarkWatch.Elapsed.TotalSeconds } else { 0.0 }
$reportDirectory = Split-Path -Parent $ReportPath
if ($reportDirectory) { New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null }

$report = @"
# HTTP Load Result

Generated: $(Get-Date -Format o)

| Metric | Value |
| --- | --- |
| URL | $Url |
| Scenario | $Scenario |
| Requests | $Requests |
| Concurrency | $Concurrency |
| Warmup requests | $WarmupRequests |
| Timeout | $TimeoutMilliseconds ms |
| Successful responses | $($successful.Count) |
| Failed responses | $($failed.Count) |
| Error rate | $([Math]::Round(($failed.Count / $Requests) * 100, 2))% |
| Elapsed | $([Math]::Round($benchmarkWatch.Elapsed.TotalMilliseconds, 2)) ms |
| Throughput | $([Math]::Round($throughput, 2)) req/s |
| P50 | $([Math]::Round((Get-Percentile $durations 0.50), 2)) ms |
| P95 | $([Math]::Round((Get-Percentile $durations 0.95), 2)) ms |
| P99 | $([Math]::Round((Get-Percentile $durations 0.99), 2)) ms |
| Operating system | $([Environment]::OSVersion.VersionString) |
| Logical processors | $([Environment]::ProcessorCount) |
| PowerShell | $($PSVersionTable.PSVersion) |

This is a local HTTP measurement, not a production capacity claim. Record the commit, host resources, dependency versions, data scale, model settings, and query mix before comparing runs.
"@

Set-Content -Path $ReportPath -Value $report -Encoding utf8
Write-Output "Report written to $ReportPath"
if ($failed.Count -gt 0) {
    [Console]::Error.WriteLine("$($failed.Count) of $Requests requests failed.")
    exit 1
}

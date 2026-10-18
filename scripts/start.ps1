# Weekly use: one window, one URL. Builds the dashboard when its sources changed, starts the API (which serves the
# dashboard and starts media-svc by itself) and opens http://localhost:8080. Ctrl+C stops everything.
param(
    [ValidateSet('mock', 'local')] [string] $Mode = 'local',
    [switch] $Docker,
    [switch] $Rebuild,
    [switch] $NoBrowser
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dashboard = Join-Path $root 'dashboard'
$index = Join-Path $dashboard 'public\index.html'
$profiles = if ($Docker) { $Mode } else { "$Mode,h2" }

$stale = $Rebuild -or -not (Test-Path $index)
if (-not $stale) {
    $built = (Get-Item $index).LastWriteTime
    $newest = Get-ChildItem (Join-Path $dashboard 'src') -Recurse -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $stale = $newest.LastWriteTime -gt $built
}
if ($stale) {
    Write-Host 'Building the dashboard (first time or after changes, a few minutes)...'
    Push-Location $dashboard
    try {
        if (-not (Test-Path 'node_modules')) { npm ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' } }
        npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Dashboard build failed' }
    } finally {
        Pop-Location
    }
}

if ($Docker) {
    docker compose -f (Join-Path $root 'docker-compose.yml') up -d postgres
    if ($LASTEXITCODE -ne 0) { throw 'docker compose failed: start Docker Desktop or run without -Docker' }
}

if (-not $NoBrowser) {
    # Opens the browser once the API answers (first start can take a minute).
    Start-Job -ScriptBlock {
        for ($i = 0; $i -lt 180; $i++) {
            try {
                Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 'http://localhost:8080/actuator/health' | Out-Null
                Start-Process 'http://localhost:8080'
                return
            } catch {
                Start-Sleep -Seconds 2
            }
        }
    } | Out-Null
}

Write-Host "StoryForge ($profiles) starting: http://localhost:8080  (Ctrl+C to stop)"
Push-Location (Join-Path $root 'orchestrator')
try {
    & .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.profiles=$profiles"
} finally {
    Pop-Location
    Get-Job | Remove-Job -Force -ErrorAction SilentlyContinue
}

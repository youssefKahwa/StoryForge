param(
    [ValidateSet('mock', 'local')] [string] $Mode = 'mock',
    [switch] $NoDocker
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$profiles = if ($NoDocker) { "$Mode,h2" } else { $Mode }

if (-not $NoDocker) {
    docker compose -f (Join-Path $root 'docker-compose.yml') up -d postgres
    if ($LASTEXITCODE -ne 0) { throw 'docker compose failed: start Docker Desktop or use -NoDocker' }
}

# media-svc starts by itself when the API needs it. Notification links point at the hot-reload dashboard.
$env:STORYFORGE_DASHBOARD_URL = 'http://localhost:8000'
Start-Process powershell -ArgumentList '-NoExit', '-Command', "cd '$root\dashboard'; npm run develop"
Push-Location (Join-Path $root 'orchestrator')
try {
    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=$profiles"
} finally {
    Pop-Location
}

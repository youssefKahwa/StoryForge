$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $root 'orchestrator')
try {
    & .\mvnw.cmd -B -q test "-Dtest=MockPipelineE2ETest"
    if ($LASTEXITCODE -ne 0) { throw "E2E mock pipeline failed (exit $LASTEXITCODE)" }
    $run = Join-Path (Get-Location) 'target\e2e-data\runs\1'
    Write-Host "Video:     $run\assemble\final_16x9.mp4"
    Write-Host "QA report: $run\qa\qa_report.html"
} finally {
    Pop-Location
}

param([switch] $Install)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'render-svc'
$target = Join-Path $env:USERPROFILE '.storyforge\render-svc'

New-Item -ItemType Directory -Force $target | Out-Null
Copy-Item (Join-Path $source 'package.json'), (Join-Path $source 'render.mjs') $target -Force
Copy-Item (Join-Path $source 'src') $target -Recurse -Force

if ($Install -or -not (Test-Path (Join-Path $target 'node_modules\@remotion'))) {
    Push-Location $target
    try {
        npm install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
        node -e "import('@remotion/renderer').then((m) => m.ensureBrowser())"
    } finally {
        Pop-Location
    }
}
Write-Host "Remotion renderer ready in $target"

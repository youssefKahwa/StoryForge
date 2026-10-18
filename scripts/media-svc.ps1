param([switch] $Install)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$venv = Join-Path $env:USERPROFILE '.storyforge\media-venv'
$models = Join-Path $env:USERPROFILE 'storyforge-data\models\kokoro'
$python = Join-Path $venv 'Scripts\python.exe'

if ($Install -or -not (Test-Path $python)) {
    python -m venv $venv
    & $python -m pip install -q -r (Join-Path $root 'media-svc\requirements.txt')
    New-Item -ItemType Directory -Force $models | Out-Null
    foreach ($file in 'kokoro-v1.0.onnx', 'voices-v1.0.bin') {
        $target = Join-Path $models $file
        if (-not (Test-Path $target)) {
            Invoke-WebRequest "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.1/$file" -OutFile $target
        }
    }
}

Push-Location (Join-Path $root 'media-svc')
try {
    & $python -m uvicorn app:app --host 127.0.0.1 --port 8090
} finally {
    Pop-Location
}

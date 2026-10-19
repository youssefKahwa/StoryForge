# Saves a new Cloudflare Workers AI token into .env without it ever being shown or pasted into a chat.
# Usage: .\scripts\set-cloudflare-token.ps1   (then paste the token at the hidden prompt)
$ErrorActionPreference = 'Stop'
$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) '.env'

$secure = Read-Host 'Paste the new Cloudflare API token (input hidden)' -AsSecureString
$token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)).Trim()
if (-not $token) { throw 'No token entered.' }

try {
    $check = Invoke-RestMethod -Uri 'https://api.cloudflare.com/client/v4/user/tokens/verify' `
        -Headers @{ Authorization = "Bearer $token" }
} catch {
    throw "Cloudflare rejected this token: $($_.Exception.Message). Nothing was changed."
}
if (-not $check.success -or $check.result.status -ne 'active') { throw 'Token is not active. Nothing was changed.' }

$lines = if (Test-Path $envFile) { @(Get-Content $envFile) } else { @() }
$found = $false
$lines = $lines | ForEach-Object {
    if ($_ -match '^CLOUDFLARE_API_TOKEN=') { $found = $true; "CLOUDFLARE_API_TOKEN=$token" } else { $_ }
}
if (-not $found) { $lines += "CLOUDFLARE_API_TOKEN=$token" }
[IO.File]::WriteAllLines($envFile, [string[]]$lines, (New-Object Text.UTF8Encoding $false))
Write-Host 'Token verified and saved to .env. Restart StoryForge (scripts\start.ps1) to use it.' -ForegroundColor Green

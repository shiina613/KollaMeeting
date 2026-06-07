# Creates keys/signing.p12 for local PDF digital signature testing.
# Password: kolla-signing-dev (change in production)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$keysDir = Join-Path $repoRoot "keys"
$p12Path = Join-Path $keysDir "signing.p12"
$password = "kolla-signing-dev"

if (-not (Test-Path $keysDir)) {
    New-Item -ItemType Directory -Path $keysDir | Out-Null
}

if (Test-Path $p12Path) {
    Write-Host "Keystore already exists: $p12Path"
    exit 0
}

$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if (-not $keytool) {
    Write-Error "keytool not found. Install JDK 17+ and ensure keytool is on PATH."
}

& keytool -genkeypair `
    -alias kolla-signing `
    -keyalg RSA `
    -keysize 2048 `
    -sigalg SHA256withRSA `
    -validity 3650 `
    -storetype PKCS12 `
    -keystore $p12Path `
    -storepass $password `
    -keypass $password `
    -dname "CN=Kolla Meeting Dev Signer, OU=Dev, O=KollaMeeting, L=Hanoi, C=VN"

Write-Host "Created $p12Path"
Write-Host "Set in .env:"
Write-Host "  DIGITAL_SIGNATURE_ENABLED=true"
Write-Host "  DIGITAL_SIGNATURE_KEYSTORE_PATH=/app/keys/signing.p12"
Write-Host "  DIGITAL_SIGNATURE_KEYSTORE_PASSWORD=$password"

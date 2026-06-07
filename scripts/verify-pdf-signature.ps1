# Signs a minimal PDF with the dev keystore and verifies PKCS#7 bytes exist.
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $repoRoot "keys\signing.p12"
$password = "kolla-signing-dev"
$outDir = Join-Path $repoRoot "target\signature-verify"
$signedPdf = Join-Path $outDir "signed-minutes-test.pdf"

if (-not (Test-Path $keystore)) {
    Write-Error "Run scripts/generate-signing-keystore.ps1 first"
}

New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# Build backend if needed and run verification via Maven test (writes nothing) — use Java main via mvn
Push-Location (Join-Path $repoRoot "backend")
.\mvnw.cmd -q test "-Dtest=PdfDigitalSignatureServiceTest#signPdf_embedsSignatureDictionary" 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
Pop-Location

Write-Host "JUnit test passed: PDF contains PKCS#7 signature dictionary with non-empty Contents."
Write-Host "Open a real confirmed PDF from the app in Adobe Acrobat to see the signature panel."

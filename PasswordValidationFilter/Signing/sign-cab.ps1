# sign-cab.ps1
# Run this AFTER authenticating with SimplySign Desktop (token must be active).

param(
    [string]$CertificateName = "Digital Identity Aps",
    [string]$OutputPath = (Join-Path $PSScriptRoot "Output")
)

$ErrorActionPreference = "Stop"

Write-Host "=== OS2faktor Password Filter - Sign CAB ===" -ForegroundColor Cyan
Write-Host ""

if (!(Get-Command signtool.exe -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: signtool.exe not found in PATH" -ForegroundColor Red
    Write-Host ""
    Write-Host "SignTool is part of the Windows SDK. Find it with:" -ForegroundColor Yellow
    Write-Host '  Get-ChildItem -Path "C:\Program Files (x86)\Windows Kits" -Recurse -Filter "signtool.exe" | Select-Object FullName' -ForegroundColor White
    exit 1
}

$cabFile = Join-Path $OutputPath "os2faktor-password-filter.cab"

if (!(Test-Path $cabFile)) {
    Write-Host "ERROR: CAB file not found at: $cabFile" -ForegroundColor Red
    Write-Host "Run prepare-cab.ps1 first" -ForegroundColor Yellow
    exit 1
}

Write-Host "Signing CAB file with certificate: $CertificateName" -ForegroundColor Yellow
Write-Host "Using timestamp server: http://timestamp.certum.pl" -ForegroundColor Yellow
Write-Host ""

& signtool.exe sign /n "$CertificateName" /fd SHA256 /tr http://timestamp.certum.pl /td SHA256 /v $cabFile

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: Signing failed!" -ForegroundColor Red
    Write-Host "Make sure:" -ForegroundColor Yellow
    Write-Host "  - SimplySign Desktop is running and authenticated with a valid token"
    Write-Host "  - The certificate name matches the EV certificate registered in Partner Center"
    Write-Host ""
    Write-Host "To find your certificate name, run:" -ForegroundColor Cyan
    Write-Host '  Get-ChildItem Cert:\CurrentUser\My | Format-Table Subject, Thumbprint'
    exit 1
}

Write-Host ""
Write-Host "Verifying signature..." -ForegroundColor Yellow
& signtool.exe verify /pa /v $cabFile

Write-Host ""
Write-Host "=== Signing Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "CAB file is ready for Partner Center submission:" -ForegroundColor Green
Write-Host "  $cabFile" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Go to: https://partner.microsoft.com/dashboard/hardware/filesign"
Write-Host "  2. File Signing Services > Submit New LSA"
Write-Host "  3. Upload: $cabFile"
Write-Host "  4. Wait ~10 minutes for processing"
Write-Host "  5. Download Signed_*.zip into Signing\Microsoft-Signed-Dlls\"
Write-Host "  6. Run: .\integrate-signed.ps1"
Write-Host ""

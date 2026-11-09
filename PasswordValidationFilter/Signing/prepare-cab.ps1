# prepare-cab.ps1
# Run this BEFORE getting the signing token.
# Builds the CAB that gets submitted to Microsoft for LSA signing.

param(
    [string]$SourcePath = (Split-Path -Parent $PSScriptRoot),
    [string]$OutputPath = (Join-Path $PSScriptRoot "Output")
)

$ErrorActionPreference = "Stop"
$DllName = "OS2faktorPasswordValidationFilter.dll"

Write-Host "=== OS2faktor Password Filter - CAB Preparation ===" -ForegroundColor Cyan
Write-Host ""

if (!(Test-Path $OutputPath)) {
    New-Item -ItemType Directory -Force -Path $OutputPath | Out-Null
    Write-Host "Created output directory: $OutputPath" -ForegroundColor Green
}

$FilterDll = Join-Path $SourcePath "build\x64\release\$DllName"

Write-Host "Checking source DLL..." -ForegroundColor Yellow
if (!(Test-Path $FilterDll)) {
    Write-Host "ERROR: DLL not found at: $FilterDll" -ForegroundColor Red
    Write-Host "Build Release|x64 first:" -ForegroundColor Yellow
    Write-Host "  msbuild PasswordValidationFilter.sln /p:Configuration=Release /p:Platform=x64" -ForegroundColor White
    exit 1
}

# A 32-bit or debug binary will be rejected by Partner Center
$info = (Get-Item $FilterDll).VersionInfo
Write-Host "  Found: $DllName ($((Get-Item $FilterDll).Length) bytes)" -ForegroundColor Green
Write-Host "  File version: $($info.FileVersion)" -ForegroundColor Green

Write-Host ""
Write-Host "Copying DLL to $OutputPath..." -ForegroundColor Yellow
Copy-Item $FilterDll $OutputPath -Force

Write-Host ""
Write-Host "Creating DDF file..." -ForegroundColor Yellow
Copy-Item (Join-Path $PSScriptRoot "os2faktor-password-filter.ddf") $OutputPath -Force
Write-Host "  Copied: os2faktor-password-filter.ddf" -ForegroundColor Green

Write-Host ""
Write-Host "Creating CAB file..." -ForegroundColor Yellow
Push-Location $OutputPath
& makecab.exe /F os2faktor-password-filter.ddf
Pop-Location

$cab = Join-Path $OutputPath "os2faktor-password-filter.cab"
if (Test-Path $cab) {
    Write-Host "  Created: os2faktor-password-filter.cab ($((Get-Item $cab).Length) bytes)" -ForegroundColor Green
} else {
    Write-Host "ERROR: CAB file was not created" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== CAB Preparation Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Get the signing token"
Write-Host "  2. Enter the token in SimplySign Desktop"
Write-Host "  3. Run: .\sign-cab.ps1"
Write-Host ""

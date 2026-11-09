# integrate-signed.ps1
# Run after downloading the signed zip from Partner Center.
# Place the Signed_*.zip file in Microsoft-Signed-Dlls/ before running.

param(
    [string]$SigningPath = $PSScriptRoot,
    [string]$RepoPath = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$DllName = "OS2faktorPasswordValidationFilter.dll"

Write-Host "=== Integrating Microsoft-Signed DLL ===" -ForegroundColor Cyan
Write-Host ""

$signedDllsPath = Join-Path $SigningPath "Microsoft-Signed-Dlls"

if (!(Test-Path $signedDllsPath)) {
    New-Item -ItemType Directory -Force -Path $signedDllsPath | Out-Null
    Write-Host "Created: Microsoft-Signed-Dlls/" -ForegroundColor Green
    Write-Host "Place the Signed_*.zip file from Partner Center in this folder and run again." -ForegroundColor Yellow
    exit 0
}

$zipFiles = @(Get-ChildItem -Path $signedDllsPath -Filter "Signed_*.zip" -ErrorAction SilentlyContinue)

if ($zipFiles.Count -eq 0) {
    if (Test-Path (Join-Path $signedDllsPath $DllName)) {
        Write-Host "DLL already extracted in Microsoft-Signed-Dlls/" -ForegroundColor Green
    } else {
        Write-Host "ERROR: No Signed_*.zip file found in Microsoft-Signed-Dlls/" -ForegroundColor Red
        Write-Host "Download the signed package from Partner Center and place it there." -ForegroundColor Yellow
        exit 1
    }
} elseif ($zipFiles.Count -gt 1) {
    Write-Host "ERROR: Multiple Signed_*.zip files found:" -ForegroundColor Red
    $zipFiles | ForEach-Object { Write-Host "  - $($_.Name)" -ForegroundColor Yellow }
    Write-Host "Please keep only the one you want to use." -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "Found: $($zipFiles[0].Name)" -ForegroundColor Green
    Write-Host "Extracting..." -ForegroundColor Yellow
    Expand-Archive -Path $zipFiles[0].FullName -DestinationPath $signedDllsPath -Force
    Write-Host "Extracted successfully" -ForegroundColor Green
    Write-Host ""
}

$src = Join-Path $signedDllsPath $DllName
$dstFolder = Join-Path $RepoPath "build\x64\release"

if (!(Test-Path $src)) {
    Write-Host "ERROR: $DllName not found in Microsoft-Signed-Dlls/" -ForegroundColor Red
    exit 1
}
if (!(Test-Path $dstFolder)) {
    Write-Host "ERROR: Destination folder not found: $dstFolder" -ForegroundColor Red
    Write-Host "Build Release|x64 first to create the folder." -ForegroundColor Yellow
    exit 1
}

Write-Host "Verifying Microsoft signature..." -ForegroundColor Yellow
& signtool.exe verify /pa /v $src
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Signature verification failed - do not ship this DLL" -ForegroundColor Red
    exit 1
}

Copy-Item $src $dstFolder -Force
Write-Host ""
Write-Host "Copied $DllName to x64\Release" -ForegroundColor Green
Write-Host ""
Write-Host "=== Integration Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "IMPORTANT: do NOT rebuild the C++ project now - that would overwrite" -ForegroundColor Yellow
Write-Host "the signed DLL with an unsigned one." -ForegroundColor Yellow
Write-Host ""
Write-Host "Next step:" -ForegroundColor Yellow
Write-Host "  Build the installer: iscc Installer\setup.iss"
Write-Host ""

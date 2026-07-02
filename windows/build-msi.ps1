<#
.SYNOPSIS
  Publica la app WPF (self-contained) y genera el instalador MSI con WiX.
.EXAMPLE
  pwsh windows/build-msi.ps1 -Version 0.1.0
#>
param(
    [string]$Version = "0.1.0",
    [string]$Runtime = "win-x64"
)

$ErrorActionPreference = "Stop"
$win          = $PSScriptRoot
$repo         = Split-Path -Parent $win
$proj         = Join-Path $win "src\App\CompartirArchivosRED.App.csproj"
$publishDir   = Join-Path $win "publish"
$installerDir = Join-Path $win "installer"
$dist         = Join-Path $repo "dist"

Write-Host "==> Publicando app self-contained ($Runtime), versión $Version..." -ForegroundColor Cyan
if (Test-Path $publishDir) { Remove-Item $publishDir -Recurse -Force }
dotnet publish $proj -c Release -r $Runtime --self-contained true `
    -p:Version=$Version -p:PublishSingleFile=false -o $publishDir
if ($LASTEXITCODE -ne 0) { throw "dotnet publish falló." }

New-Item -ItemType Directory -Force $dist | Out-Null
$msi = Join-Path $dist "CompartirArchivosRED-$Version.msi"

Write-Host "==> Construyendo MSI con WiX..." -ForegroundColor Cyan
Push-Location $installerDir
try {
    wix build Package.wxs -arch x64 `
        -d Version=$Version -d PublishDir=$publishDir `
        -ext WixToolset.UI.wixext `
        -ext WixToolset.Firewall.wixext `
        -o $msi
    if ($LASTEXITCODE -ne 0) { throw "wix build falló." }
}
finally { Pop-Location }

Write-Host "==> MSI generado: $msi" -ForegroundColor Green

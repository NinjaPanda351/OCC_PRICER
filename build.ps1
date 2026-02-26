# build.ps1 — One-click build: compile → jar → jpackage → zip
# Usage: .\build.ps1 [-Version "2.0.7"]
param(
    [string]$Version = "2.0.7"
)

$ErrorActionPreference = "Stop"

$ProjectDir = $PSScriptRoot
$SrcDir     = Join-Path $ProjectDir "src"
$LibDir     = Join-Path $ProjectDir "lib"
$OutDir     = Join-Path $ProjectDir "out\production\OCC_Trade_Pricer"
$DistDir    = Join-Path $ProjectDir "dist"
$JarName    = "OCC_Trade_Pricer.jar"
$JarPath    = Join-Path $ProjectDir $JarName
$AppName    = "OCC Card Pricer"
$MainClass  = "com.cardpricer.gui.MainSwingApplication"
$ManifestSrc = Join-Path $SrcDir "META-INF\MANIFEST.MF"

Write-Host "=== OCC Card Pricer Build Script ===" -ForegroundColor Cyan
Write-Host "Version : $Version"
Write-Host "Project : $ProjectDir"

# ── Step 1: Compile ───────────────────────────────────────────────────────────
Write-Host "`n[1/4] Compiling sources..." -ForegroundColor Yellow

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }

# Collect all .java files
$sources = Get-ChildItem -Path $SrcDir -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName

# Build classpath from lib/
$cp = (Get-ChildItem -Path $LibDir -Filter "*.jar" | Select-Object -ExpandProperty FullName) -join ";"

javac -encoding UTF-8 -cp $cp -d $OutDir $sources
if ($LASTEXITCODE -ne 0) { throw "Compilation failed" }
Write-Host "  Compiled $($sources.Count) source files." -ForegroundColor Green

# ── Step 2: Copy resources (non-.java files under src/) ───────────────────────
Write-Host "`n[2/4] Copying resources..." -ForegroundColor Yellow
Get-ChildItem -Path $SrcDir -Recurse -File | Where-Object { $_.Extension -ne ".java" } | ForEach-Object {
    $rel  = $_.FullName.Substring($SrcDir.Length + 1)
    $dest = Join-Path $OutDir $rel
    $destParent = Split-Path $dest
    if (-not (Test-Path $destParent)) { New-Item -ItemType Directory -Path $destParent | Out-Null }
    Copy-Item $_.FullName $dest -Force
}

# ── Step 3: Package as fat JAR ────────────────────────────────────────────────
Write-Host "`n[3/4] Creating fat JAR..." -ForegroundColor Yellow

# Extract dependency JARs into a temp dir
$TmpExtract = Join-Path $ProjectDir "out\extracted_deps"
if (Test-Path $TmpExtract) { Remove-Item $TmpExtract -Recurse -Force }
New-Item -ItemType Directory -Path $TmpExtract | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
Get-ChildItem -Path $LibDir -Filter "*.jar" | ForEach-Object {
    # $true = overwrite existing files (needed when multiple JARs share META-INF/MANIFEST.MF)
    [System.IO.Compression.ZipFile]::ExtractToDirectory($_.FullName, $TmpExtract, $true)
}

# Merge: copy extracted deps, then overlay compiled classes (so our classes win)
# Use robocopy for directory merge
robocopy $TmpExtract $OutDir /E /NFL /NDL /NJH /NJS /NP | Out-Null

# Build the JAR with our MANIFEST
if (Test-Path $JarPath) { Remove-Item $JarPath -Force }
Push-Location $OutDir
jar cfm $JarPath $ManifestSrc .
Pop-Location
if ($LASTEXITCODE -ne 0) { throw "jar command failed" }
Write-Host "  Created: $JarPath" -ForegroundColor Green

# ── Step 4: jpackage (Windows app-image) ──────────────────────────────────────
Write-Host "`n[4/4] Running jpackage..." -ForegroundColor Yellow

$AppImageDir = Join-Path $DistDir $AppName
if (Test-Path $AppImageDir) { Remove-Item $AppImageDir -Recurse -Force }
if (-not (Test-Path $DistDir)) { New-Item -ItemType Directory -Path $DistDir | Out-Null }

jpackage `
    --type app-image `
    --name $AppName `
    --app-version $Version `
    --input $ProjectDir `
    --main-jar $JarName `
    --main-class $MainClass `
    --dest $DistDir `
    --java-options "-Xmx512m"

if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }
Write-Host "  App image created: $AppImageDir" -ForegroundColor Green

# ── Step 5: Zip ───────────────────────────────────────────────────────────────
$ZipName    = "OCC_Card_Pricer_V$Version.zip"
$ZipPath    = Join-Path $DistDir $ZipName

if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
Compress-Archive -Path $AppImageDir -DestinationPath $ZipPath
Write-Host "`nDone! Release archive: $ZipPath" -ForegroundColor Cyan

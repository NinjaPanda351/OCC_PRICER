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

# jpackage requires purely numeric version (major.minor.patch).
# Strip any pre-release suffix (e.g. "2.0.8-1" → "2.0.8").
$JpackageVersion = ($Version -split '-')[0]

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

# Also copy assets/ into the output dir so they are bundled in the JAR
$AssetsDir = Join-Path $ProjectDir "assets"
if (Test-Path $AssetsDir) {
    $AssetsOutDir = Join-Path $OutDir "assets"
    if (-not (Test-Path $AssetsOutDir)) { New-Item -ItemType Directory -Path $AssetsOutDir | Out-Null }
    Get-ChildItem -Path $AssetsDir -File | ForEach-Object {
        Copy-Item $_.FullName (Join-Path $AssetsOutDir $_.Name) -Force
    }
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

# ── Step 3b: Convert icon JPG → ICO for jpackage ─────────────────────────────
$IcoPath = Join-Path $AssetsDir "OCC_Icon.ico"
$JpgPath = Join-Path $AssetsDir "OCC_Icon_400x400.jpg"
if (Test-Path $JpgPath) {
    Add-Type -AssemblyName System.Drawing
    $bitmap = New-Object System.Drawing.Bitmap($JpgPath)
    $hIcon  = $bitmap.GetHicon()
    $icon   = [System.Drawing.Icon]::FromHandle($hIcon)
    $stream = [System.IO.File]::Open($IcoPath, [System.IO.FileMode]::Create)
    $icon.Save($stream)
    $stream.Close()
    $icon.Dispose()
    $bitmap.Dispose()
    Write-Host "  Icon converted: $IcoPath" -ForegroundColor Green
} else {
    Write-Host "  Warning: icon not found at $JpgPath — skipping icon" -ForegroundColor Yellow
    $IcoPath = $null
}

# ── Step 4: jpackage (Windows app-image) ──────────────────────────────────────
Write-Host "`n[4/4] Running jpackage..." -ForegroundColor Yellow

$AppImageDir = Join-Path $DistDir $AppName
if (Test-Path $AppImageDir) { Remove-Item $AppImageDir -Recurse -Force }
if (-not (Test-Path $DistDir)) { New-Item -ItemType Directory -Path $DistDir | Out-Null }

$iconArgs = if ($IcoPath -and (Test-Path $IcoPath)) { @("--icon", $IcoPath) } else { @() }

jpackage `
    --type app-image `
    --name $AppName `
    --app-version $JpackageVersion `
    --input $ProjectDir `
    --main-jar $JarName `
    --main-class $MainClass `
    --dest $DistDir `
    --java-options "-Xmx512m" `
    @iconArgs

if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }
Write-Host "  App image created: $AppImageDir" -ForegroundColor Green

# ── Step 5: Zip ───────────────────────────────────────────────────────────────
$ZipName    = "OCC_Card_Pricer_V$Version.zip"
$ZipPath    = Join-Path $DistDir $ZipName

if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
Compress-Archive -Path $AppImageDir -DestinationPath $ZipPath
Write-Host "`nDone! Release archive: $ZipPath" -ForegroundColor Cyan

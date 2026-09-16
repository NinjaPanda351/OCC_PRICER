param([string]$Version = '')
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    [xml]$projectPom = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'pom.xml')
    $projectVersion = $projectPom.project.version
    if ($Version -and $Version -ne $projectVersion) { throw 'Release tag and pom.xml version differ' }
    if ($projectVersion -notmatch '^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$') { throw 'Invalid release version' }
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 25' }
    & (Join-Path $PSScriptRoot 'mvnw.cmd') -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Verification failed' }
    $packageRun = Join-Path $PSScriptRoot ('target\package-' + [guid]::NewGuid())
    $packageInput = Join-Path $packageRun 'input'
    $packageOutput = Join-Path $packageRun 'output'
    New-Item -ItemType Directory -Path $packageInput,$packageOutput | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'target\OCC_Trade_Pricer.jar') -Destination $packageInput
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'THIRD_PARTY_NOTICES.md') -Destination $packageInput
    & (Join-Path $env:JAVA_HOME 'bin\jpackage.exe') --type app-image --name 'OCC Card Pricer' `
        --app-version ($projectVersion -split '-')[0] --input $packageInput --main-jar 'OCC_Trade_Pricer.jar' `
        --main-class 'com.cardpricer.gui.MainSwingApplication' --dest $packageOutput `
        --icon (Join-Path $PSScriptRoot 'assets\OCC_Icon_400x400.ico') --java-options '-Xmx512m' `
        --java-options '--enable-native-access=ALL-UNNAMED'
    if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }
    # jpackage marks launcher/runtime files read-only; generated staging must remain cleanable.
    foreach ($stagedFile in Get-ChildItem -LiteralPath $packageOutput -Recurse -File -Force) {
        if ($stagedFile.IsReadOnly) { $stagedFile.IsReadOnly = $false }
    }
    $appPayload = Join-Path $packageOutput 'OCC Card Pricer\app'
    $allowedPayload = @('OCC_Trade_Pricer.jar','OCC Card Pricer.cfg','THIRD_PARTY_NOTICES.md','.jpackage.xml')
    foreach ($payloadFile in Get-ChildItem -LiteralPath $appPayload -Force) {
        if ($payloadFile.Name -notin $allowedPayload) { throw ('Unexpected package payload: ' + $payloadFile.Name) }
    }
    $releaseDir = Join-Path $PSScriptRoot 'dist'
    New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
    $releaseZip = Join-Path $releaseDir ('OCC_Card_Pricer_V' + $projectVersion + '.zip')
    Compress-Archive -LiteralPath (Join-Path $packageOutput 'OCC Card Pricer') -DestinationPath $releaseZip -Force
    Write-Output ('Release archive: ' + $releaseZip)
} finally { Pop-Location }

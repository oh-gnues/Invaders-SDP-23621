$ErrorActionPreference = 'Stop'
$projectPath = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location -LiteralPath $projectPath
try {
    # Class location controls the game's save directory. Every check gets disposable saves.
    $verificationPath = Join-Path $projectPath ('bin/connected-check-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
    $classPath = Join-Path $verificationPath 'classes'
    $tempPath = Join-Path $verificationPath 'tmp'
    $servicePath = Join-Path $classPath 'META-INF/services'
    New-Item -ItemType Directory -Force $classPath, $tempPath, $servicePath | Out-Null
    Set-Content -LiteralPath (Join-Path $servicePath 'endless.GameplayExtension') -Encoding ascii -Value 'engine.ConnectedSystemsChecks$Probe'
    $productionSources = @(Get-ChildItem -LiteralPath src -Filter '*.java' -Recurse | ForEach-Object FullName)
    $testSources = @(Get-ChildItem -LiteralPath tests/endless -Filter '*.java' | ForEach-Object FullName)
    & javac '-J-Duser.language=en' -Xlint:all -encoding UTF-8 -d $classPath $productionSources 2> (Join-Path $verificationPath 'production-compile.log')
    if ($LASTEXITCODE -ne 0) { throw 'Production compilation failed.' }
    & javac '-J-Duser.language=en' -encoding UTF-8 -cp "$classPath;res" -d $classPath $testSources 2> (Join-Path $verificationPath 'test-compile.log')
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    $suites = @('engine.ConnectedSystemsChecks', 'engine.RunTest', 'engine.RewardItemsTest',
        'engine.EndlessUiChecks', 'engine.ShopEffectsChecks')
    foreach ($suite in $suites) {
        $suiteLog = Join-Path $verificationPath ($suite + '.log')
        $suitePath = Join-Path $verificationPath ('connected-check-' + $suite.Replace('.', '-'))
        New-Item -ItemType Directory -Force $suitePath | Out-Null
        Copy-Item -LiteralPath $classPath -Destination $suitePath -Recurse
        $suiteClasses = Join-Path $suitePath 'classes'
        & java '-Duser.language=en' "-Djava.io.tmpdir=$tempPath" -cp "$suiteClasses;res" $suite (Join-Path $verificationPath 'screenshots') *> $suiteLog
        if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $suiteLog -Tail 12; throw "$suite failed." }
        Get-Content -LiteralPath $suiteLog | Select-String '^(PASS:|TOTAL )' | ForEach-Object { $_.Line }
    }
    # Reproduce a release launch: only a JAR, bundled resources and an empty cwd.
    $packagedPath = Join-Path $verificationPath 'connected-check-packaged'
    $noSourcePath = Join-Path $packagedPath 'empty-working-directory'
    New-Item -ItemType Directory -Force $noSourcePath | Out-Null
    $packagedJar = Join-Path $packagedPath 'entry-check.jar'
    & jar --create --file $packagedJar -C $classPath . -C (Join-Path $projectPath 'res') .
    if ($LASTEXITCODE -ne 0) { throw 'Entry-check packaging failed.' }
    $entryLog = Join-Path $verificationPath 'engine.EntryGateChecks.log'
    Push-Location -LiteralPath $noSourcePath
    try {
        & java '-Duser.language=en' "-Djava.io.tmpdir=$tempPath" -cp $packagedJar engine.EntryGateChecks (Join-Path $verificationPath 'screenshots') *> $entryLog
        if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $entryLog -Tail 18; throw 'Packaged entry checks failed.' }
        Get-Content -LiteralPath $entryLog | Select-String '^PASS:' | ForEach-Object { $_.Line }
    } finally { Pop-Location }
    Write-Host "Verified in: $verificationPath"
} finally { Pop-Location }

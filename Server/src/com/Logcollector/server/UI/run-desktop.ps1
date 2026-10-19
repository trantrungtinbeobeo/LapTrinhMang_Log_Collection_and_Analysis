param([switch]$Demo, [switch]$Test, [switch]$Visual)
$ErrorActionPreference = 'Stop'
$buildRoot = Join-Path $PSScriptRoot '.build'
$buildDir = Join-Path $buildRoot 'classes'
$flatLafJar = Join-Path $PSScriptRoot 'lib/flatlaf-3.7.jar'
if (!(Test-Path -LiteralPath $flatLafJar)) { throw 'Missing UI/lib/flatlaf-3.7.jar. See README-DESKTOP.md.' }
$classPath = $buildDir + [System.IO.Path]::PathSeparator + $flatLafJar
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
$serverSource = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$sourceFiles = Get-ChildItem -LiteralPath $serverSource -Recurse -Filter '*.java' |
    Where-Object { $_.FullName -notlike '*\.build\*' -and (($Test -or $Visual) -or $_.Name -ne 'DesktopSelfTest.java') } |
    Select-Object -ExpandProperty FullName
& javac --release 17 -encoding UTF-8 -d $buildDir $sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
if ($Test -or $Visual) {
    $testArguments = @('-cp', $classPath, 'com.Logcollector.server.UI.desktop.DesktopSelfTest')
    if ($Visual) { $testArguments += @('--visual', (Join-Path $buildRoot 'screenshots')) }
    & java @testArguments
} else {
    $runArguments = @('-cp', $classPath, 'com.Logcollector.server.UI.DashboardUI')
    if ($Demo) { $runArguments += '--demo' }
    & java @runArguments
}
if ($LASTEXITCODE -ne 0) { throw 'Java application/test failed' }

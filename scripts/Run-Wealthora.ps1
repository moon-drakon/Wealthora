[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot 'dist\Wealthora.jar'

if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    Write-Error "Wealthora.jar was not found. Run 'ant clean jar' first."
    exit 1
}

$javaExecutable = $null
if ($env:JAVA_HOME) {
    $javaFromHome = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $javaFromHome -PathType Leaf) {
        $javaExecutable = $javaFromHome
    }
}

if (-not $javaExecutable) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $javaExecutable = $javaCommand.Source
    }
}

if (-not $javaExecutable) {
    Write-Error 'Java was not found. Install Java 25 or set JAVA_HOME, then try again.'
    exit 1
}

$versionStartInfo = New-Object System.Diagnostics.ProcessStartInfo
$versionStartInfo.FileName = $javaExecutable
$versionStartInfo.Arguments = '-version'
$versionStartInfo.UseShellExecute = $false
$versionStartInfo.CreateNoWindow = $true
$versionStartInfo.RedirectStandardError = $true
$versionStartInfo.RedirectStandardOutput = $true

$versionProcess = New-Object System.Diagnostics.Process
$versionProcess.StartInfo = $versionStartInfo
$null = $versionProcess.Start()
$versionText = $versionProcess.StandardError.ReadToEnd()
$versionText += $versionProcess.StandardOutput.ReadToEnd()
$versionProcess.WaitForExit()

if ($versionProcess.ExitCode -ne 0) {
    Write-Error "Java could not be started:`n$versionText"
    exit 1
}

if ($versionText -notmatch 'version "25(?:\.|"|\s)') {
    Write-Error "Wealthora requires Java 25. Detected:`n$versionText"
    exit 1
}

& $javaExecutable -jar $jarPath
exit $LASTEXITCODE

[CmdletBinding()]
param(
    [Uri] $ServerUrl = 'http://127.0.0.1:18080',

    [string] $JarPath,

    [ValidateRange(96, 1024)]
    [int] $MaximumHeapMegabytes = 160,

    [switch] $ValidateOnly
)

$ErrorActionPreference = 'Stop'

function Test-AllowedServerUrl {
    param([Uri] $Url)

    if (-not $Url.IsAbsoluteUri) {
        return $false
    }
    if ($Url.Scheme -eq 'https') {
        return $true
    }
    $loopbackHost = $Url.Host -in @('localhost', '127.0.0.1', '::1')
    return $Url.Scheme -eq 'http' -and $loopbackHost
}

if (-not (Test-AllowedServerUrl -Url $ServerUrl)) {
    throw 'The server URL must use HTTPS. HTTP is allowed only for localhost.'
}

$repositoryRoot = (Resolve-Path -LiteralPath (
        Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = Join-Path $repositoryRoot 'dist\Wealthora.jar'
}
$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
$javawExecutable = (Get-Command javaw -ErrorAction Stop).Source
$baseUrl = $ServerUrl.AbsoluteUri.TrimEnd('/')

try {
    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" `
        -TimeoutSec 10
    $availability = Invoke-RestMethod -Uri "$baseUrl/api/auth/status" `
        -TimeoutSec 10
} catch {
    throw "The Wealthora server is unavailable at $baseUrl."
}

if ($health.status -ne 'UP') {
    throw "The Wealthora server is not ready at $baseUrl."
}
if (-not $availability.emailProviderAvailable) {
    throw 'The server email provider is unavailable.'
}

Write-Output 'ServerEndpoint: PASS'
Write-Output 'EmailProvider: PASS'
$googleStatus = if ($availability.googleOAuthAvailable) {
    'PASS'
} else {
    'MISSING'
}
Write-Output "GoogleOAuth: $googleStatus"
Write-Output 'DesktopJar: PASS'
Write-Output 'JavaRuntime: PASS'

if ($ValidateOnly) {
    return
}

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $javawExecutable
$startInfo.WorkingDirectory = $repositoryRoot
$startInfo.UseShellExecute = $false
$startInfo.Environment['WEALTHORA_SERVER_URL'] = $baseUrl
foreach ($argument in @(
        '-Xms32m',
        "-Xmx${MaximumHeapMegabytes}m",
        '-Xss256k',
        '-XX:MaxMetaspaceSize=128m',
        '-XX:CompressedClassSpaceSize=32m',
        '-XX:ReservedCodeCacheSize=48m',
        '-XX:ActiveProcessorCount=2',
        '-XX:+UseSerialGC',
        '-jar',
        $resolvedJar)) {
    [void] $startInfo.ArgumentList.Add($argument)
}

$desktopProcess = [System.Diagnostics.Process]::Start($startInfo)
if ($null -eq $desktopProcess) {
    throw 'The Wealthora desktop process could not be started.'
}
Start-Sleep -Seconds 5
$desktopProcess.Refresh()
if ($desktopProcess.HasExited) {
    throw 'The Wealthora desktop process exited during startup.'
}

Write-Output 'DesktopProcess: PASS'
Write-Output "DesktopProcessId: $($desktopProcess.Id)"

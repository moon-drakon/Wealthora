[CmdletBinding()]
param(
    [string]$ConfigurationRoot,
    [string]$ProjectRoot,
    [switch]$ExitAfterReady,
    [switch]$SkipUserEnvironment,
    [int]$RelayPort = 8443
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'WealthoraLauncher.psm1') -Force

if ([string]::IsNullOrWhiteSpace($ConfigurationRoot)) {
    $ConfigurationRoot = Get-WealthoraDefaultConfigurationRoot
}
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
}

$state = Test-WealthoraStoredConfiguration $ConfigurationRoot
if ($state.Status -ne 'Valid') {
    Write-Host $state.Message -ForegroundColor Yellow
    Write-Host 'Run Configure Wealthora OTP.cmd first.'
    if (-not $ExitAfterReady) {
        [void](Read-Host 'Press Enter to close')
    }
    exit 2
}

$relay = $null
try {
    $artifacts = Resolve-WealthoraProjectArtifacts $ProjectRoot
    $java = Resolve-WealthoraJava $ProjectRoot
    if (-not $SkipUserEnvironment) {
        Set-WealthoraRelayUserEnvironment
    }
    $relay = Start-WealthoraRelay -Artifacts $artifacts -Java $java `
        -ConfigurationRoot $ConfigurationRoot -RelayPort $RelayPort
    if ($relay.Owned) {
        Write-Host 'OTP relay ready for NetBeans F6.' -ForegroundColor Green
        Write-Host 'Keep this window open. Press Enter or Ctrl+C to stop this launcher-owned relay.'
    } else {
        Write-Host 'A healthy OTP relay is already running and will be reused.' `
            -ForegroundColor Green
        Write-Host 'Closing this window will not stop the existing relay.'
    }
    if ($ExitAfterReady) {
        Write-Host ('NETBEANS_RELAY_READY owned=' + $relay.Owned)
    } else {
        [void](Read-Host 'Press Enter to stop/close')
    }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    if (-not $ExitAfterReady) {
        [void](Read-Host 'Press Enter to close')
    }
    exit 1
} finally {
    Stop-WealthoraOwnedRelay $relay
}

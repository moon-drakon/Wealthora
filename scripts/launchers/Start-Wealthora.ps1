[CmdletBinding()]
param(
    [string]$ConfigurationRoot,
    [string]$ProjectRoot,
    [switch]$CheckConfigurationOnly,
    [switch]$ExitAfterRelayReady,
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
if ($CheckConfigurationOnly) {
    if ($state.Status -eq 'Valid') {
        Write-Host ('CONFIGURATION_READY sender=' + $state.SenderAddress)
        exit 0
    }
    Write-Host ('CONFIGURATION_' + $state.Status.ToUpperInvariant())
    exit 2
}

try {
    $artifacts = Resolve-WealthoraProjectArtifacts $ProjectRoot
    $java = Resolve-WealthoraJava $ProjectRoot
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    [void](Read-Host 'Press Enter to close')
    exit 1
}

$offline = $false
while ($state.Status -ne 'Valid') {
    Write-Host $state.Message -ForegroundColor Yellow
    Write-Host '[C] Configure OTP now'
    Write-Host '[O] Start Wealthora in offline mode'
    Write-Host '[E] Exit'
    $choice = (Read-Host 'Choose an option').Trim().ToUpperInvariant()
    if ($choice -eq 'C') {
        & (Join-Path $PSScriptRoot 'Configure-WealthoraOtp.ps1') `
            -ConfigurationRoot $ConfigurationRoot -NoFinalPause
        $state = Test-WealthoraStoredConfiguration $ConfigurationRoot
        continue
    }
    if ($choice -eq 'O') {
        $offline = $true
        break
    }
    if ($choice -eq 'E') {
        exit 0
    }
    Write-Host 'Choose C, O, or E.' -ForegroundColor Yellow
}

$relay = $null
try {
    if (-not $offline) {
        Write-Host 'Starting or reusing the local Wealthora OTP relay...'
        $relay = Start-WealthoraRelay -Artifacts $artifacts -Java $java `
            -ConfigurationRoot $ConfigurationRoot -RelayPort $RelayPort
        if ($relay.Owned) {
            Write-Host 'OTP relay is ready (started by this launcher).' `
                -ForegroundColor Green
        } else {
            Write-Host 'A healthy OTP relay is already running; it will be reused.' `
                -ForegroundColor Green
        }
        if ($ExitAfterRelayReady) {
            Write-Host ('LAUNCHER_RELAY_READY owned=' + $relay.Owned)
            return
        }
    } else {
        Write-Host 'Starting Wealthora in offline mode.' -ForegroundColor Yellow
    }
    $desktop = Start-WealthoraDesktop -Artifacts $artifacts -Java $java `
        -RelayUrl $(if ($null -ne $relay) { $relay.Url } else { '' }) `
        -Offline:$offline
    $desktop.WaitForExit()
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    [void](Read-Host 'Press Enter to close')
    exit 1
} finally {
    Stop-WealthoraOwnedRelay $relay
}

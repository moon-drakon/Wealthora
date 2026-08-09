[CmdletBinding()]
param(
    [string]$ConfigurationRoot,
    [switch]$NoFinalPause
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'WealthoraLauncher.psm1') -Force

if ([string]::IsNullOrWhiteSpace($ConfigurationRoot)) {
    $ConfigurationRoot = Get-WealthoraDefaultConfigurationRoot
}

function Read-ValidatedSenderAddress {
    while ($true) {
        $value = Read-Host 'Gmail sender address'
        if (Test-WealthoraEmailAddress $value) {
            return $value.Trim().ToLowerInvariant()
        }
        Write-Host 'Enter a valid Gmail or Google Workspace email address.' `
            -ForegroundColor Yellow
    }
}

function Read-ValidatedSenderName {
    while ($true) {
        $value = Read-Host 'Sender name [Wealthora Security]'
        if ([string]::IsNullOrWhiteSpace($value)) {
            $value = 'Wealthora Security'
        }
        if (Test-WealthoraSenderName $value) {
            return $value.Trim()
        }
        Write-Host 'Use 1-70 plain letters, numbers, spaces, or safe punctuation.' `
            -ForegroundColor Yellow
    }
}

function Invoke-PrivateConfiguration {
    $address = Read-ValidatedSenderAddress
    $senderName = Read-ValidatedSenderName
    Write-Host 'Enter the Google 16-character App Password privately.'
    Write-Host 'Spaces are accepted. Input is masked and is never logged.'
    $appPassword = Read-Host 'App Password' -AsSecureString
    Write-Host 'Validating Gmail authentication without sending an email...'
    $result = Save-WealthoraOtpConfiguration `
        -SenderAddress $address `
        -SenderName $senderName `
        -AppPassword $appPassword `
        -ConfigurationRoot $ConfigurationRoot
    $appPassword.Dispose()
    if ($result.Success) {
        Write-Host ''
        Write-Host ('Configured Gmail: ' + $result.SenderAddress) `
            -ForegroundColor Green
        Write-Host $result.Message -ForegroundColor Green
        Write-Host 'Restart NetBeans once after first-time configuration.'
        return $true
    }
    Write-Host $result.Message -ForegroundColor Red
    return $false
}

Write-Host 'Wealthora OTP Configuration' -ForegroundColor Cyan
Write-Host 'Credentials stay outside the repository and use Windows DPAPI.'
Write-Host ''

$state = Test-WealthoraStoredConfiguration $ConfigurationRoot
if ($state.Status -eq 'Missing') {
    [void](Invoke-PrivateConfiguration)
} else {
    if ($state.Status -eq 'Valid') {
        Write-Host 'Wealthora OTP is already configured.' -ForegroundColor Green
        Write-Host ('Configured Gmail: ' + $state.SenderAddress)
        Write-Host ''
        Write-Host '[K] Keep current configuration'
    } else {
        Write-Host $state.Message -ForegroundColor Yellow
        Write-Host ''
    }
    Write-Host '[R] Replace Gmail/App Password'
    Write-Host '[D] Remove OTP configuration'
    Write-Host '[C] Cancel'
    while ($true) {
        $choice = (Read-Host 'Choose an option').Trim().ToUpperInvariant()
        if ($choice -eq 'K' -and $state.Status -eq 'Valid') {
            Write-Host 'Current configuration was kept.' -ForegroundColor Green
            break
        }
        if ($choice -eq 'R') {
            [void](Invoke-PrivateConfiguration)
            break
        }
        if ($choice -eq 'D') {
            $confirmation = Read-Host 'Type REMOVE to delete the encrypted OTP configuration'
            if ($confirmation -ceq 'REMOVE') {
                Remove-WealthoraOtpConfiguration `
                    -ConfigurationRoot $ConfigurationRoot
                Write-Host 'Wealthora OTP configuration was removed.' `
                    -ForegroundColor Green
            } else {
                Write-Host 'Removal was cancelled.'
            }
            break
        }
        if ($choice -eq 'C') {
            Write-Host 'No configuration changes were made.'
            break
        }
        Write-Host 'Choose one of the displayed options.' -ForegroundColor Yellow
    }
}

if (-not $NoFinalPause) {
    [void](Read-Host 'Press Enter to close')
}

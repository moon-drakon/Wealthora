[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [IO.Path]::GetFullPath(
    (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)))
$modulePath = Join-Path $projectRoot `
    'scripts\launchers\WealthoraLauncher.psm1'
$startScript = Join-Path $projectRoot `
    'scripts\launchers\Start-Wealthora.ps1'
$netBeansScript = Join-Path $projectRoot `
    'scripts\launchers\Start-OtpRelayForNetBeans.ps1'
$powershell = Join-Path $env:SystemRoot `
    'System32\WindowsPowerShell\v1.0\powershell.exe'
Import-Module $modulePath -Force

function Assert-LauncherTest {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "Launcher test failed: $Message"
    }
}

function New-TestAppPassword {
    $alphabet = 'abcdefghjkmnpqrstuvwxyz23456789'.ToCharArray()
    $bytes = New-Object byte[] 16
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
        return -join ($bytes | ForEach-Object {
            $alphabet[$_ % $alphabet.Length]
        })
    } finally {
        $random.Dispose()
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function New-FreeTcpPort {
    $listener = New-Object Net.Sockets.TcpListener(
        [Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function Invoke-ChildPowerShell {
    param([Parameter(Mandatory = $true)][string]$Command)

    $encoded = [Convert]::ToBase64String(
        [Text.Encoding]::Unicode.GetBytes($Command))
    $output = & $powershell -NoLogo -NoProfile -ExecutionPolicy Bypass `
        -EncodedCommand $encoded 2>&1
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($output -join "`n")
    }
}

function Wait-RelayUnavailable {
    param([int]$Port)
    $deadline = [DateTime]::UtcNow.AddSeconds(8)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (-not (Test-WealthoraRelayHealth -RelayPort $Port `
                -TimeoutSeconds 1)) {
            return $true
        }
        Start-Sleep -Milliseconds 200
    }
    return $false
}

$testBase = 'C:\Users\Drakon\Documents\Codex\2026-08-08\n\work'
$testRoot = Join-Path $testBase `
    ('wealthora-launcher-tests-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($testRoot) | Out-Null

try {
    Write-Host 'Checking PowerShell and CMD syntax...'
    $powerShellFiles = @(Get-ChildItem -LiteralPath `
        (Join-Path $projectRoot 'scripts\launchers') -File |
        Where-Object { $_.Extension -in '.ps1', '.psm1' })
    $powerShellFiles += Get-Item -LiteralPath $PSCommandPath
    foreach ($file in $powerShellFiles) {
        $tokens = $null
        $errors = $null
        [Management.Automation.Language.Parser]::ParseFile(
            $file.FullName, [ref]$tokens, [ref]$errors) | Out-Null
        Assert-LauncherTest ($errors.Count -eq 0) `
            ("PowerShell syntax: " + $file.Name)
    }
    $cmdFiles = @(
        'Configure Wealthora OTP.cmd',
        'Start Wealthora.cmd',
        'Start OTP Relay for NetBeans.cmd')
    foreach ($name in $cmdFiles) {
        $path = Join-Path $projectRoot $name
        & $env:ComSpec /d /q /c ('call "' + $path + '" --syntax-check')
        Assert-LauncherTest ($LASTEXITCODE -eq 0) ("CMD syntax: " + $name)
        $cmdText = Get-Content -LiteralPath $path -Raw
        Assert-LauncherTest ($cmdText.Contains('%~dp0')) `
            ("relative path anchoring: " + $name)
        Assert-LauncherTest `
            (-not $cmdText.Contains('WEALTHORA_SMTP_PASSWORD')) `
            ("no SMTP secret handling in CMD: " + $name)
    }

    Write-Host 'Checking static launcher security controls...'
    $moduleText = Get-Content -LiteralPath $modulePath -Raw
    $configureText = Get-Content -LiteralPath `
        (Join-Path $projectRoot `
            'scripts\launchers\Configure-WealthoraOtp.ps1') -Raw
    Assert-LauncherTest ($moduleText.Contains('ProtectedData]::Protect')) `
        'DPAPI protection is used'
    Assert-LauncherTest ($moduleText.Contains('DataProtectionScope]::CurrentUser')) `
        'DPAPI is tied to CurrentUser'
    Assert-LauncherTest ($moduleText.Contains('[IO.File]::Replace')) `
        'replacement uses an atomic file operation'
    Assert-LauncherTest `
        ($configureText -match "Read-Host 'App Password' -AsSecureString") `
        'App Password prompt is masked'
    Assert-LauncherTest `
        (-not ($moduleText -match `
            '(?im)^\s*\$[^\r\n]*(appPassword|smtpPassword)\s*=\s*[''"][A-Za-z0-9 ]{12,}[''"]')) `
        'no hardcoded App Password'
    Assert-LauncherTest `
        (-not ($moduleText -match `
            '(?i)\.Arguments\s*=.*(password|signing|credential)')) `
        'secrets are absent from child command arguments'
    Assert-LauncherTest (Test-WealthoraEmailAddress 'fixture@example.test') `
        'valid mailbox accepted'
    Assert-LauncherTest `
        (-not (Test-WealthoraEmailAddress "fixture@example.test`r`nBcc:x")) `
        'mailbox header injection rejected'
    Assert-LauncherTest `
        (-not (Test-WealthoraSenderName "Wealthora`r`nBcc")) `
        'sender-name header injection rejected'

    Write-Host 'Checking portable distribution metadata...'
    $manifestText = Get-Content -LiteralPath `
        (Join-Path $projectRoot 'manifest.mf') -Raw
    $buildText = Get-Content -LiteralPath `
        (Join-Path $projectRoot 'build.xml') -Raw
    Assert-LauncherTest `
        ($manifestText.Contains(
            'Class-Path: lib/flatlaf-3.7.2.jar lib/jbcrypt-0.4.jar')) `
        'desktop manifest declares both runtime libraries'
    Assert-LauncherTest `
        ($buildText -match '<target name="-post-jar"[^>]*>') `
        'portable post-jar target is active'
    Assert-LauncherTest `
        ($buildText.Contains('<include name="*.jar"/>')) `
        'portable post-jar target copies runtime libraries'

    Write-Host 'Checking DPAPI first-time configuration and persistence...'
    $configurationRoot = Join-Path $testRoot 'Configuration With Spaces'
    $password = New-TestAppPassword
    $secure = ConvertTo-SecureString $password -AsPlainText -Force
    $successValidator = { param($Address, $Password) return $true }
    $failureValidator = { param($Address, $Password) return $false }
    $first = Save-WealthoraOtpConfiguration `
        -SenderAddress 'fixture@example.test' `
        -SenderName 'Wealthora Security' `
        -AppPassword $secure `
        -ConfigurationRoot $configurationRoot `
        -SmtpValidator $successValidator `
        -SkipUserEnvironment
    Assert-LauncherTest $first.Success 'first-time configuration succeeds'
    $configurationPath = Get-WealthoraConfigurationPath $configurationRoot
    Assert-LauncherTest (Test-Path -LiteralPath $configurationPath) `
        'configuration persists on disk'
    $storedText = Get-Content -LiteralPath $configurationPath -Raw
    Assert-LauncherTest (-not $storedText.Contains($password)) `
        'plaintext App Password is absent from local configuration'
    Assert-LauncherTest `
        ($storedText.Contains('Windows-DPAPI-CurrentUser')) `
        'configuration records the protection scope'
    $opened = Open-WealthoraStoredConfiguration $configurationRoot
    Assert-LauncherTest ($opened.AppPassword -eq $password) `
        'DPAPI decrypts for the same Windows user'
    $opened.AppPassword = $null
    $opened.SigningSecret = $null

    $escapedModule = $modulePath.Replace("'", "''")
    $escapedConfiguration = $configurationRoot.Replace("'", "''")
    $newSession = Invoke-ChildPowerShell @"
Import-Module '$escapedModule' -Force
`$state = Test-WealthoraStoredConfiguration '$escapedConfiguration'
if (`$state.Status -ne 'Valid') { exit 3 }
`$opened = Open-WealthoraStoredConfiguration '$escapedConfiguration'
if (`$opened.AppPassword.Length -ne 16) { exit 4 }
`$opened.AppPassword = `$null
`$opened.SigningSecret = `$null
Write-Output 'NEW_SESSION_CONFIGURATION_READY'
"@
    Assert-LauncherTest ($newSession.ExitCode -eq 0) `
        'configuration decrypts in a new terminal process'
    Assert-LauncherTest `
        ($newSession.Output.Contains('NEW_SESSION_CONFIGURATION_READY')) `
        'new terminal reports ready without prompting'

    $launcherProbe = & $powershell -NoLogo -NoProfile `
        -ExecutionPolicy Bypass -File $startScript `
        -ConfigurationRoot $configurationRoot `
        -CheckConfigurationOnly 2>&1
    Assert-LauncherTest ($LASTEXITCODE -eq 0) `
        'subsequent launcher configuration check succeeds'
    Assert-LauncherTest `
        (($launcherProbe -join "`n").Contains('CONFIGURATION_READY')) `
        'subsequent launcher does not enter a credential prompt'

    Write-Host 'Checking replacement, rollback, corruption, and removal...'
    $beforeFailedReplacement = `
        (Get-FileHash -Algorithm SHA256 -LiteralPath $configurationPath).Hash
    $replacementPassword = New-TestAppPassword
    $replacementSecure = ConvertTo-SecureString $replacementPassword `
        -AsPlainText -Force
    $failed = Save-WealthoraOtpConfiguration `
        -SenderAddress 'replacement@example.test' `
        -SenderName 'Wealthora Accounts' `
        -AppPassword $replacementSecure `
        -ConfigurationRoot $configurationRoot `
        -SmtpValidator $failureValidator `
        -SkipUserEnvironment
    Assert-LauncherTest (-not $failed.Success) `
        'failed replacement is reported'
    Assert-LauncherTest `
        ((Get-FileHash -Algorithm SHA256 -LiteralPath $configurationPath).Hash `
            -eq $beforeFailedReplacement) `
        'failed replacement preserves the previous configuration byte-for-byte'

    $replaced = Save-WealthoraOtpConfiguration `
        -SenderAddress 'replacement@example.test' `
        -SenderName 'Wealthora Accounts' `
        -AppPassword $replacementSecure `
        -ConfigurationRoot $configurationRoot `
        -SmtpValidator $successValidator `
        -SkipUserEnvironment
    Assert-LauncherTest $replaced.Success 'successful replacement'
    $replacementState = Test-WealthoraStoredConfiguration $configurationRoot
    Assert-LauncherTest `
        ($replacementState.SenderAddress -eq 'replacement@example.test') `
        'replacement metadata is current'
    $replacementOpened = Open-WealthoraStoredConfiguration $configurationRoot
    Assert-LauncherTest `
        ($replacementOpened.AppPassword -eq $replacementPassword) `
        'replacement credential decrypts'
    $replacementOpened.AppPassword = $null
    $replacementOpened.SigningSecret = $null

    $validDocument = Get-Content -LiteralPath $configurationPath -Raw
    [IO.File]::WriteAllText($configurationPath, '{corrupted')
    Assert-LauncherTest `
        ((Test-WealthoraStoredConfiguration $configurationRoot).Status `
            -eq 'Corrupt') `
        'corrupted configuration is detected'
    $corruptProbe = & $powershell -NoLogo -NoProfile `
        -ExecutionPolicy Bypass -File $startScript `
        -ConfigurationRoot $configurationRoot `
        -CheckConfigurationOnly 2>&1
    Assert-LauncherTest ($LASTEXITCODE -eq 2) `
        'launcher reports corrupted configuration without prompting for a secret'
    [IO.File]::WriteAllText($configurationPath, $validDocument,
        (New-Object Text.UTF8Encoding($false)))

    Remove-WealthoraOtpConfiguration `
        -ConfigurationRoot $configurationRoot -SkipUserEnvironment
    Assert-LauncherTest `
        ((Test-WealthoraStoredConfiguration $configurationRoot).Status `
            -eq 'Missing') `
        'configuration removal deletes encrypted and non-secret settings'
    $missingProbe = & $powershell -NoLogo -NoProfile `
        -ExecutionPolicy Bypass -File $startScript `
        -ConfigurationRoot $configurationRoot `
        -CheckConfigurationOnly 2>&1
    Assert-LauncherTest ($LASTEXITCODE -eq 2) `
        'launcher detects missing configuration without requesting credentials'
    $recreated = Save-WealthoraOtpConfiguration `
        -SenderAddress 'fixture@example.test' `
        -SenderName 'Wealthora Security' `
        -AppPassword $secure `
        -ConfigurationRoot $configurationRoot `
        -SmtpValidator $successValidator `
        -SkipUserEnvironment
    Assert-LauncherTest $recreated.Success 'configuration recreated for relay tests'

    Write-Host 'Checking Java order, path spaces, readiness, reuse, and cleanup...'
    $detectionRoot = Join-Path $testRoot 'Bundled Runtime Probe'
    [IO.Directory]::CreateDirectory(
        (Join-Path $detectionRoot 'runtime\bin')) | Out-Null
    $fakeJava = Join-Path $detectionRoot 'runtime\bin\java.exe'
    $fakeJavaw = Join-Path $detectionRoot 'runtime\bin\javaw.exe'
    [IO.File]::WriteAllBytes($fakeJava, (New-Object byte[] 1))
    [IO.File]::WriteAllBytes($fakeJavaw, (New-Object byte[] 1))
    $detected = Resolve-WealthoraJava $detectionRoot
    Assert-LauncherTest ($detected.RelayJava -eq $fakeJava) `
        'bundled java.exe has first priority'
    Assert-LauncherTest ($detected.DesktopJava -eq $fakeJavaw) `
        'bundled javaw.exe has first priority for the desktop'

    $spaceProject = Join-Path $testRoot 'Project Folder With Spaces'
    [IO.Directory]::CreateDirectory(
        (Join-Path $spaceProject 'dist\otp-relay')) | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot 'dist\Wealthora.jar') `
        -Destination (Join-Path $spaceProject 'dist\Wealthora.jar')
    Copy-Item -LiteralPath (Join-Path $projectRoot `
        'dist\otp-relay\wealthora-otp-relay.jar') `
        -Destination (Join-Path $spaceProject `
            'dist\otp-relay\wealthora-otp-relay.jar')
    $artifacts = Resolve-WealthoraProjectArtifacts $spaceProject
    $java = Resolve-WealthoraJava $spaceProject
    Assert-LauncherTest ($artifacts.RelayJar.Contains(' ')) `
        'artifact paths with spaces are preserved'

    $port = New-FreeTcpPort
    $firstRelay = Start-WealthoraRelay -Artifacts $artifacts -Java $java `
        -ConfigurationRoot $configurationRoot -RelayPort $port
    try {
        Assert-LauncherTest $firstRelay.Owned `
            'first relay is owned by the launcher'
        Assert-LauncherTest `
            (Test-WealthoraRelayHealth -RelayPort $port) `
            'relay readiness check succeeds'
        $processRecord = Get-CimInstance Win32_Process `
            -Filter ("ProcessId=" + $firstRelay.Process.Id)
        Assert-LauncherTest `
            (-not $processRecord.CommandLine.Contains($password)) `
            'plaintext App Password is absent from the relay command line'
        Assert-LauncherTest `
            (-not $processRecord.CommandLine.Contains($replacementPassword)) `
            'replacement App Password is absent from the relay command line'
        $secondRelay = Start-WealthoraRelay -Artifacts $artifacts -Java $java `
            -ConfigurationRoot $configurationRoot -RelayPort $port
        Assert-LauncherTest (-not $secondRelay.Owned) `
            'healthy existing relay is reused'
        Stop-WealthoraOwnedRelay $secondRelay
        Assert-LauncherTest `
            (Test-WealthoraRelayHealth -RelayPort $port) `
            'reused relay is preserved'
    } finally {
        Stop-WealthoraOwnedRelay $firstRelay
    }
    Assert-LauncherTest (Wait-RelayUnavailable $port) `
        'launcher-owned relay is cleaned up'

    $launcherPort = New-FreeTcpPort
    $launcherOutput = & $powershell -NoLogo -NoProfile `
        -ExecutionPolicy Bypass -File $startScript `
        -ConfigurationRoot $configurationRoot `
        -ProjectRoot $spaceProject `
        -RelayPort $launcherPort `
        -ExitAfterRelayReady 2>&1
    Assert-LauncherTest ($LASTEXITCODE -eq 0) `
        'one-click launcher reaches relay readiness'
    Assert-LauncherTest `
        (($launcherOutput -join "`n").Contains('LAUNCHER_RELAY_READY owned=True')) `
        'one-click launcher owns its new relay'
    Assert-LauncherTest (Wait-RelayUnavailable $launcherPort) `
        'one-click launcher stops only its owned relay'

    $netBeansOwnedPort = New-FreeTcpPort
    $netBeansOwnedOutput = & $powershell -NoLogo -NoProfile `
        -ExecutionPolicy Bypass -File $netBeansScript `
        -ConfigurationRoot $configurationRoot `
        -ProjectRoot $spaceProject `
        -RelayPort $netBeansOwnedPort `
        -SkipUserEnvironment `
        -ExitAfterReady 2>&1
    Assert-LauncherTest ($LASTEXITCODE -eq 0) `
        'NetBeans launcher starts a new relay'
    Assert-LauncherTest `
        (($netBeansOwnedOutput -join "`n").Contains(
            'NETBEANS_RELAY_READY owned=True')) `
        'NetBeans launcher records ownership of its relay'
    Assert-LauncherTest (Wait-RelayUnavailable $netBeansOwnedPort) `
        'NetBeans launcher cleans up its owned relay'

    $existingPort = New-FreeTcpPort
    $existingRelay = Start-WealthoraRelay -Artifacts $artifacts -Java $java `
        -ConfigurationRoot $configurationRoot -RelayPort $existingPort
    try {
        $netBeansOutput = & $powershell -NoLogo -NoProfile `
            -ExecutionPolicy Bypass -File $netBeansScript `
            -ConfigurationRoot $configurationRoot `
            -ProjectRoot $spaceProject `
            -RelayPort $existingPort `
            -SkipUserEnvironment `
            -ExitAfterReady 2>&1
        Assert-LauncherTest ($LASTEXITCODE -eq 0) `
            'NetBeans relay flow succeeds'
        Assert-LauncherTest `
            (($netBeansOutput -join "`n").Contains(
                'NETBEANS_RELAY_READY owned=False')) `
            'NetBeans flow detects an existing relay'
        Assert-LauncherTest `
            (Test-WealthoraRelayHealth -RelayPort $existingPort) `
            'NetBeans flow preserves an existing relay'
    } finally {
        Stop-WealthoraOwnedRelay $existingRelay
    }
    Assert-LauncherTest (Wait-RelayUnavailable $existingPort) `
        'final relay cleanup succeeds'

    $allTemporaryText = @(Get-ChildItem -LiteralPath $testRoot -Recurse -File |
        ForEach-Object {
            try { Get-Content -LiteralPath $_.FullName -Raw -ErrorAction Stop }
            catch { '' }
        }) -join "`n"
    Assert-LauncherTest (-not $allTemporaryText.Contains($password)) `
        'plaintext App Password is absent from fixtures and logs'
    Assert-LauncherTest (-not $allTemporaryText.Contains($replacementPassword)) `
        'replacement plaintext App Password is absent from fixtures and logs'

    $password = $null
    $replacementPassword = $null
    Write-Host 'WealthoraLauncherTest passed'
} finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $allowed = [IO.Path]::GetFullPath($testBase) + `
        [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith(
            $allowed, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing unsafe launcher-test cleanup path.'
    }
    if ([IO.Directory]::Exists($resolved)) {
        [IO.Directory]::Delete($resolved, $true)
    }
}

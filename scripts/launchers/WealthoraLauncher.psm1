Set-StrictMode -Version Latest

if ($null -eq ('System.Security.Cryptography.ProtectedData' -as [type])) {
    Add-Type -AssemblyName System.Security -ErrorAction Stop
}

$script:ConfigurationFileName = 'otp-relay-config.json'
$script:ConfigurationSchemaVersion = 1
$script:DefaultSenderName = 'Wealthora Security'
$script:RelayUrl = 'http://127.0.0.1:8443'
$script:SmtpHost = 'smtp.gmail.com'
$script:SmtpPort = 587
$script:DpapiEntropy = [Text.Encoding]::UTF8.GetBytes(
    'Wealthora.OTP.Configuration.v1')

function Get-WealthoraDefaultConfigurationRoot {
    if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        throw 'LOCALAPPDATA is unavailable for the current Windows user.'
    }
    return Join-Path $env:LOCALAPPDATA 'Wealthora'
}

function Get-WealthoraConfigurationPath {
    param([string]$ConfigurationRoot = (Get-WealthoraDefaultConfigurationRoot))

    return Join-Path ([IO.Path]::GetFullPath($ConfigurationRoot)) `
        $script:ConfigurationFileName
}

function Test-WealthoraEmailAddress {
    param([AllowNull()][string]$Address)

    if ([string]::IsNullOrWhiteSpace($Address)) {
        return $false
    }
    $candidate = $Address.Trim()
    if ($candidate.IndexOf("`r") -ge 0 -or $candidate.IndexOf("`n") -ge 0) {
        return $false
    }
    return $candidate -match `
        '^[A-Za-z0-9.!#$%&''*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
}

function Test-WealthoraSenderName {
    param([AllowNull()][string]$SenderName)

    if ([string]::IsNullOrWhiteSpace($SenderName)) {
        return $false
    }
    $candidate = $SenderName.Trim()
    if ($candidate.Length -gt 70 -or $candidate.IndexOf("`r") -ge 0 `
            -or $candidate.IndexOf("`n") -ge 0) {
        return $false
    }
    return $candidate -match '^[A-Za-z0-9 .,&()_+-]+$'
}

function ConvertFrom-WealthoraSecureString {
    param([Parameter(Mandatory = $true)][Security.SecureString]$SecureValue)

    $pointer = [IntPtr]::Zero
    try {
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
            $SecureValue)
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        if ($pointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        }
    }
}

function Protect-WealthoraBytes {
    param([Parameter(Mandatory = $true)][byte[]]$PlainBytes)

    $cipher = [Security.Cryptography.ProtectedData]::Protect(
        $PlainBytes,
        $script:DpapiEntropy,
        [Security.Cryptography.DataProtectionScope]::CurrentUser)
    try {
        return [Convert]::ToBase64String($cipher)
    } finally {
        [Array]::Clear($cipher, 0, $cipher.Length)
    }
}

function Unprotect-WealthoraBytes {
    param([Parameter(Mandatory = $true)][string]$ProtectedValue)

    $cipher = [Convert]::FromBase64String($ProtectedValue)
    try {
        return [Security.Cryptography.ProtectedData]::Unprotect(
            $cipher,
            $script:DpapiEntropy,
            [Security.Cryptography.DataProtectionScope]::CurrentUser)
    } finally {
        [Array]::Clear($cipher, 0, $cipher.Length)
    }
}

function Read-WealthoraConfigurationDocument {
    param([string]$ConfigurationRoot = (Get-WealthoraDefaultConfigurationRoot))

    $path = Get-WealthoraConfigurationPath $ConfigurationRoot
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return [pscustomobject]@{
            Status = 'Missing'
            Path = $path
            Message = 'Wealthora OTP has not been configured.'
            Document = $null
        }
    }
    try {
        $document = Get-Content -LiteralPath $path -Raw -Encoding UTF8 |
            ConvertFrom-Json -ErrorAction Stop
        if ([int]$document.schemaVersion -ne $script:ConfigurationSchemaVersion) {
            throw 'Unsupported configuration version.'
        }
        $address = [string]$document.senderAddress
        $senderName = [string]$document.senderName
        if (-not (Test-WealthoraEmailAddress $address)) {
            throw 'Stored sender address is invalid.'
        }
        if (-not (Test-WealthoraSenderName $senderName)) {
            throw 'Stored sender name is invalid.'
        }
        if ([string]::IsNullOrWhiteSpace(
                [string]$document.protectedAppPassword) `
                -or [string]::IsNullOrWhiteSpace(
                    [string]$document.protectedSigningSecret)) {
            throw 'Encrypted credential fields are missing.'
        }
        return [pscustomobject]@{
            Status = 'Valid'
            Path = $path
            Message = 'Wealthora OTP configuration is available.'
            Document = $document
        }
    } catch {
        return [pscustomobject]@{
            Status = 'Corrupt'
            Path = $path
            Message = 'The saved Wealthora OTP configuration is invalid or corrupted.'
            Document = $null
        }
    }
}

function Test-WealthoraStoredConfiguration {
    param([string]$ConfigurationRoot = (Get-WealthoraDefaultConfigurationRoot))

    $state = Read-WealthoraConfigurationDocument $ConfigurationRoot
    if ($state.Status -ne 'Valid') {
        return $state
    }
    $passwordBytes = $null
    $secretBytes = $null
    try {
        $passwordBytes = Unprotect-WealthoraBytes `
            ([string]$state.Document.protectedAppPassword)
        $secretBytes = Unprotect-WealthoraBytes `
            ([string]$state.Document.protectedSigningSecret)
        $passwordText = [Text.Encoding]::UTF8.GetString($passwordBytes)
        if ($passwordText -notmatch '^[A-Za-z0-9]{16}$' `
                -or $secretBytes.Length -lt 32) {
            throw 'Decrypted credential data is invalid.'
        }
        $passwordText = $null
        return [pscustomobject]@{
            Status = 'Valid'
            Path = $state.Path
            Message = $state.Message
            SenderAddress = ([string]$state.Document.senderAddress).ToLowerInvariant()
            SenderName = [string]$state.Document.senderName
        }
    } catch {
        return [pscustomobject]@{
            Status = 'Corrupt'
            Path = $state.Path
            Message = 'The saved configuration cannot be decrypted by this Windows user.'
            SenderAddress = $null
            SenderName = $null
        }
    } finally {
        if ($null -ne $passwordBytes) {
            [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
        }
        if ($null -ne $secretBytes) {
            [Array]::Clear($secretBytes, 0, $secretBytes.Length)
        }
    }
}

function Open-WealthoraStoredConfiguration {
    param([string]$ConfigurationRoot = (Get-WealthoraDefaultConfigurationRoot))

    $state = Read-WealthoraConfigurationDocument $ConfigurationRoot
    if ($state.Status -ne 'Valid') {
        throw $state.Message
    }
    $passwordBytes = $null
    $secretBytes = $null
    try {
        $passwordBytes = Unprotect-WealthoraBytes `
            ([string]$state.Document.protectedAppPassword)
        $secretBytes = Unprotect-WealthoraBytes `
            ([string]$state.Document.protectedSigningSecret)
        $password = [Text.Encoding]::UTF8.GetString($passwordBytes)
        if ($password -notmatch '^[A-Za-z0-9]{16}$' `
                -or $secretBytes.Length -lt 32) {
            throw 'Decrypted credential data is invalid.'
        }
        return [pscustomobject]@{
            SenderAddress = ([string]$state.Document.senderAddress).ToLowerInvariant()
            SenderName = [string]$state.Document.senderName
            AppPassword = $password
            SigningSecret = [Convert]::ToBase64String($secretBytes)
        }
    } finally {
        if ($null -ne $passwordBytes) {
            [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
        }
        if ($null -ne $secretBytes) {
            [Array]::Clear($secretBytes, 0, $secretBytes.Length)
        }
    }
}

function Read-WealthoraSmtpResponse {
    param(
        [Parameter(Mandatory = $true)][IO.StreamReader]$Reader,
        [Parameter(Mandatory = $true)][int[]]$Accepted
    )

    $line = $Reader.ReadLine()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.Length -lt 3) {
        throw 'The SMTP server closed the connection.'
    }
    $statusText = $line.Substring(0, 3)
    while ($line.Length -gt 3 -and $line[3] -eq '-') {
        $line = $Reader.ReadLine()
        if ($null -eq $line) {
            throw 'The SMTP response was incomplete.'
        }
        if ($line.StartsWith($statusText + ' ')) {
            break
        }
    }
    $status = 0
    if (-not [int]::TryParse($statusText, [ref]$status) `
            -or $Accepted -notcontains $status) {
        throw "The SMTP server rejected a validation command (status $statusText)."
    }
}

function Send-WealthoraSmtpCommand {
    param(
        [Parameter(Mandatory = $true)][IO.StreamWriter]$Writer,
        [Parameter(Mandatory = $true)][IO.StreamReader]$Reader,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][int[]]$Accepted
    )

    $Writer.WriteLine($Command)
    $Writer.Flush()
    Read-WealthoraSmtpResponse -Reader $Reader -Accepted $Accepted
}

function Test-WealthoraSmtpCredential {
    param(
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][Security.SecureString]$AppPassword
    )

    $client = $null
    $tls = $null
    $plainPassword = $null
    $encodedPassword = $null
    try {
        $client = New-Object Net.Sockets.TcpClient
        $client.ReceiveTimeout = 10000
        $client.SendTimeout = 10000
        $client.Connect($script:SmtpHost, $script:SmtpPort)
        $reader = New-Object IO.StreamReader(
            $client.GetStream(), [Text.Encoding]::ASCII, $false, 1024, $true)
        $writer = New-Object IO.StreamWriter(
            $client.GetStream(), [Text.Encoding]::ASCII, 1024, $true)
        $writer.NewLine = "`r`n"
        Read-WealthoraSmtpResponse -Reader $reader -Accepted @(220)
        Send-WealthoraSmtpCommand $writer $reader `
            'EHLO wealthora-configurator' @(250)
        Send-WealthoraSmtpCommand $writer $reader 'STARTTLS' @(220)

        $tls = New-Object Net.Security.SslStream($client.GetStream(), $false)
        $tls.AuthenticateAsClient($script:SmtpHost)
        $reader = New-Object IO.StreamReader(
            $tls, [Text.Encoding]::ASCII, $false, 1024, $true)
        $writer = New-Object IO.StreamWriter(
            $tls, [Text.Encoding]::ASCII, 1024, $true)
        $writer.NewLine = "`r`n"
        Send-WealthoraSmtpCommand $writer $reader `
            'EHLO wealthora-configurator' @(250)
        Send-WealthoraSmtpCommand $writer $reader 'AUTH LOGIN' @(334)
        $encodedUsername = [Convert]::ToBase64String(
            [Text.Encoding]::UTF8.GetBytes($Username))
        Send-WealthoraSmtpCommand $writer $reader $encodedUsername @(334)
        $plainPassword = ConvertFrom-WealthoraSecureString $AppPassword
        $encodedPassword = [Convert]::ToBase64String(
            [Text.Encoding]::UTF8.GetBytes($plainPassword))
        Send-WealthoraSmtpCommand $writer $reader $encodedPassword @(235)
        Send-WealthoraSmtpCommand $writer $reader 'QUIT' @(221)
        return $true
    } catch {
        return $false
    } finally {
        $plainPassword = $null
        $encodedPassword = $null
        if ($null -ne $tls) {
            $tls.Dispose()
        }
        if ($null -ne $client) {
            $client.Dispose()
        }
    }
}

function Write-WealthoraConfigurationAtomically {
    param(
        [Parameter(Mandatory = $true)][string]$ConfigurationRoot,
        [Parameter(Mandatory = $true)][string]$Json
    )

    $root = [IO.Path]::GetFullPath($ConfigurationRoot)
    [IO.Directory]::CreateDirectory($root) | Out-Null
    $target = Get-WealthoraConfigurationPath $root
    $temporary = Join-Path $root `
        ('.otp-relay-config-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    $backup = Join-Path $root `
        ('.otp-relay-config-' + [Guid]::NewGuid().ToString('N') + '.bak')
    try {
        $utf8 = New-Object Text.UTF8Encoding($false)
        [IO.File]::WriteAllText($temporary, $Json, $utf8)
        if (Test-Path -LiteralPath $target -PathType Leaf) {
            [IO.File]::Replace($temporary, $target, $backup, $true)
            if (Test-Path -LiteralPath $backup -PathType Leaf) {
                [IO.File]::Delete($backup)
            }
        } else {
            [IO.File]::Move($temporary, $target)
        }
    } finally {
        if (Test-Path -LiteralPath $temporary -PathType Leaf) {
            [IO.File]::Delete($temporary)
        }
        if (Test-Path -LiteralPath $backup -PathType Leaf) {
            [IO.File]::Delete($backup)
        }
    }
}

function Set-WealthoraRelayUserEnvironment {
    [Environment]::SetEnvironmentVariable(
        'WEALTHORA_OTP_RELAY_URL', $script:RelayUrl, 'User')
}

function Save-WealthoraOtpConfiguration {
    param(
        [Parameter(Mandatory = $true)][string]$SenderAddress,
        [string]$SenderName = $script:DefaultSenderName,
        [Parameter(Mandatory = $true)][Security.SecureString]$AppPassword,
        [string]$ConfigurationRoot = (Get-WealthoraDefaultConfigurationRoot),
        [scriptblock]$SmtpValidator = {
            param($Address, $Password)
            Test-WealthoraSmtpCredential -Username $Address `
                -AppPassword $Password
        },
        [switch]$SkipUserEnvironment
    )

    $address = $SenderAddress.Trim().ToLowerInvariant()
    $name = $SenderName.Trim()
    if (-not (Test-WealthoraEmailAddress $address)) {
        return [pscustomobject]@{ Success = $false; Message = 'The Gmail address is invalid.' }
    }
    if (-not (Test-WealthoraSenderName $name)) {
        return [pscustomobject]@{ Success = $false; Message = 'The sender name is invalid.' }
    }

    $plainPassword = $null
    $normalizedPassword = $null
    $passwordBytes = $null
    $signingBytes = $null
    try {
        $plainPassword = ConvertFrom-WealthoraSecureString $AppPassword
        if ($plainPassword.IndexOf("`r") -ge 0 `
                -or $plainPassword.IndexOf("`n") -ge 0) {
            return [pscustomobject]@{ Success = $false; Message = 'The App Password is invalid.' }
        }
        $normalizedPassword = $plainPassword -replace '[ \t]', ''
        if ($normalizedPassword -notmatch '^[A-Za-z0-9]{16}$') {
            return [pscustomobject]@{
                Success = $false
                Message = 'Enter the 16-character Google App Password, with or without spaces.'
            }
        }
        $normalizedSecure = ConvertTo-SecureString $normalizedPassword `
            -AsPlainText -Force
        $validated = $false
        try {
            $validated = [bool](& $SmtpValidator $address $normalizedSecure)
        } catch {
            $validated = $false
        }
        if (-not $validated) {
            return [pscustomobject]@{
                Success = $false
                Message = 'Gmail authentication validation failed. Existing configuration was preserved.'
            }
        }

        $passwordBytes = [Text.Encoding]::UTF8.GetBytes($normalizedPassword)
        $signingBytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $random.GetBytes($signingBytes)
        } finally {
            $random.Dispose()
        }
        $document = [ordered]@{
            schemaVersion = $script:ConfigurationSchemaVersion
            protection = 'Windows-DPAPI-CurrentUser'
            senderAddress = $address
            senderName = $name
            protectedAppPassword = Protect-WealthoraBytes $passwordBytes
            protectedSigningSecret = Protect-WealthoraBytes $signingBytes
            updatedUtc = [DateTime]::UtcNow.ToString('o')
        }
        if (-not $SkipUserEnvironment) {
            Set-WealthoraRelayUserEnvironment
        }
        Write-WealthoraConfigurationAtomically `
            -ConfigurationRoot $ConfigurationRoot `
            -Json ($document | ConvertTo-Json -Depth 3)
        return [pscustomobject]@{
            Success = $true
            Message = 'Wealthora OTP configuration was saved successfully.'
            SenderAddress = $address
        }
    } catch {
        return [pscustomobject]@{
            Success = $false
            Message = 'Wealthora OTP configuration could not be saved. Existing configuration was preserved.'
        }
    } finally {
        $plainPassword = $null
        $normalizedPassword = $null
        if ($null -ne $passwordBytes) {
            [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
        }
        if ($null -ne $signingBytes) {
            [Array]::Clear($signingBytes, 0, $signingBytes.Length)
        }
    }
}

function Remove-WealthoraOtpConfiguration {
    param(
        [string]$ConfigurationRoot = (Get-WealthoraDefaultConfigurationRoot),
        [switch]$SkipUserEnvironment
    )

    $path = Get-WealthoraConfigurationPath $ConfigurationRoot
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        [IO.File]::Delete($path)
    }
    if (-not $SkipUserEnvironment) {
        $current = [Environment]::GetEnvironmentVariable(
            'WEALTHORA_OTP_RELAY_URL', 'User')
        if ($current -eq $script:RelayUrl) {
            [Environment]::SetEnvironmentVariable(
                'WEALTHORA_OTP_RELAY_URL', $null, 'User')
        }
    }
}

function Resolve-WealthoraProjectArtifacts {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    $root = [IO.Path]::GetFullPath($ProjectRoot)
    $desktopJar = Join-Path $root 'dist\Wealthora.jar'
    $relayJar = Join-Path $root 'dist\otp-relay\wealthora-otp-relay.jar'
    if (-not (Test-Path -LiteralPath $desktopJar -PathType Leaf)) {
        throw 'dist\Wealthora.jar is missing. Run the full Ant build first.'
    }
    if (-not (Test-Path -LiteralPath $relayJar -PathType Leaf)) {
        throw 'dist\otp-relay\wealthora-otp-relay.jar is missing. Run the full Ant build first.'
    }
    return [pscustomobject]@{
        ProjectRoot = $root
        DesktopJar = $desktopJar
        RelayJar = $relayJar
    }
}

function Resolve-WealthoraJava {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    $roots = New-Object Collections.Generic.List[string]
    $roots.Add((Join-Path ([IO.Path]::GetFullPath($ProjectRoot)) 'runtime\bin'))
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $roots.Add((Join-Path $env:JAVA_HOME 'bin'))
    }
    foreach ($root in $roots) {
        $java = Join-Path $root 'java.exe'
        $javaw = Join-Path $root 'javaw.exe'
        if (Test-Path -LiteralPath $java -PathType Leaf) {
            return [pscustomobject]@{
                RelayJava = $java
                DesktopJava = $(if (Test-Path -LiteralPath $javaw -PathType Leaf) { $javaw } else { $java })
            }
        }
        if (Test-Path -LiteralPath $javaw -PathType Leaf) {
            return [pscustomobject]@{ RelayJava = $javaw; DesktopJava = $javaw }
        }
    }
    $pathJava = Get-Command 'java.exe' -ErrorAction SilentlyContinue
    $pathJavaw = Get-Command 'javaw.exe' -ErrorAction SilentlyContinue
    if ($null -eq $pathJava -and $null -eq $pathJavaw) {
        throw 'Java was not found in runtime\bin, JAVA_HOME, or PATH.'
    }
    $relay = $(if ($null -ne $pathJava) { $pathJava.Source } else { $pathJavaw.Source })
    $desktop = $(if ($null -ne $pathJavaw) { $pathJavaw.Source } else { $relay })
    return [pscustomobject]@{ RelayJava = $relay; DesktopJava = $desktop }
}

function Test-WealthoraRelayHealth {
    param(
        [int]$TimeoutSeconds = 2,
        [int]$RelayPort = 8443
    )

    try {
        $url = 'http://127.0.0.1:' + $RelayPort
        $response = Invoke-WebRequest -Uri ($url + '/health') `
            -UseBasicParsing -TimeoutSec $TimeoutSeconds -ErrorAction Stop
        return $response.StatusCode -eq 200 `
            -and $response.Content -match '"status"\s*:\s*"UP"'
    } catch {
        return $false
    }
}

function New-WealthoraJavaStartInfo {
    param(
        [Parameter(Mandatory = $true)][string]$JavaPath,
        [Parameter(Mandatory = $true)][string]$JarPath,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )

    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $JavaPath
    $info.Arguments = '-jar "' + $JarPath.Replace('"', '') + '"'
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    return $info
}

function Start-WealthoraRelay {
    param(
        [Parameter(Mandatory = $true)]$Artifacts,
        [Parameter(Mandatory = $true)]$Java,
        [string]$ConfigurationRoot = (Get-WealthoraDefaultConfigurationRoot),
        [int]$ReadinessSeconds = 15,
        [int]$RelayPort = 8443
    )

    $relayUrl = 'http://127.0.0.1:' + $RelayPort
    if (Test-WealthoraRelayHealth -RelayPort $RelayPort) {
        return [pscustomobject]@{
            Owned = $false
            Process = $null
            Url = $relayUrl
        }
    }

    $configuration = Open-WealthoraStoredConfiguration $ConfigurationRoot
    $info = New-WealthoraJavaStartInfo -JavaPath $Java.RelayJava `
        -JarPath $Artifacts.RelayJar `
        -WorkingDirectory $Artifacts.ProjectRoot
    $environment = $info.EnvironmentVariables
    $environment['WEALTHORA_RELAY_ALLOW_HTTP_LOOPBACK'] = 'true'
    $environment['WEALTHORA_RELAY_BIND_ADDRESS'] = '127.0.0.1'
    $environment['WEALTHORA_RELAY_PORT'] = [string]$RelayPort
    $environment['WEALTHORA_OTP_SIGNING_SECRET'] = $configuration.SigningSecret
    $environment['WEALTHORA_SMTP_HOST'] = $script:SmtpHost
    $environment['WEALTHORA_SMTP_PORT'] = [string]$script:SmtpPort
    $environment['WEALTHORA_SMTP_USERNAME'] = $configuration.SenderAddress
    $environment['WEALTHORA_SMTP_FROM'] = $configuration.SenderAddress
    $environment['WEALTHORA_SMTP_FROM_NAME'] = $configuration.SenderName
    $environment['WEALTHORA_SMTP_PASSWORD'] = $configuration.AppPassword
    try {
        $process = [Diagnostics.Process]::Start($info)
    } finally {
        foreach ($name in @('WEALTHORA_OTP_SIGNING_SECRET',
                'WEALTHORA_SMTP_PASSWORD')) {
            if ($environment.ContainsKey($name)) {
                $environment.Remove($name)
            }
        }
        $configuration.AppPassword = $null
        $configuration.SigningSecret = $null
        $configuration = $null
    }

    $deadline = [DateTime]::UtcNow.AddSeconds($ReadinessSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-WealthoraRelayHealth -TimeoutSeconds 1 `
                -RelayPort $RelayPort) {
            if ($process.HasExited) {
                return [pscustomobject]@{
                    Owned = $false
                    Process = $null
                    Url = $relayUrl
                }
            }
            return [pscustomobject]@{
                Owned = $true
                Process = $process
                Url = $relayUrl
            }
        }
        if ($process.HasExited) {
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $process.HasExited) {
        $process.Kill()
        $process.WaitForExit(5000) | Out-Null
    }
    throw 'The OTP relay did not become ready. Port 8443 may be unavailable.'
}

function Stop-WealthoraOwnedRelay {
    param([AllowNull()]$Relay)

    if ($null -eq $Relay -or -not $Relay.Owned `
            -or $null -eq $Relay.Process) {
        return
    }
    try {
        if (-not $Relay.Process.HasExited) {
            $Relay.Process.Kill()
            $Relay.Process.WaitForExit(5000) | Out-Null
        }
    } catch {
        Write-Warning 'The launcher-owned OTP relay could not be stopped automatically.'
    }
}

function Start-WealthoraDesktop {
    param(
        [Parameter(Mandatory = $true)]$Artifacts,
        [Parameter(Mandatory = $true)]$Java,
        [string]$RelayUrl = $script:RelayUrl,
        [switch]$Offline
    )

    $info = New-WealthoraJavaStartInfo -JavaPath $Java.DesktopJava `
        -JarPath $Artifacts.DesktopJar `
        -WorkingDirectory $Artifacts.ProjectRoot
    if ($Offline) {
        if ($info.EnvironmentVariables.ContainsKey('WEALTHORA_OTP_RELAY_URL')) {
            $info.EnvironmentVariables.Remove('WEALTHORA_OTP_RELAY_URL')
        }
    } else {
        $info.EnvironmentVariables['WEALTHORA_OTP_RELAY_URL'] = $RelayUrl
    }
    return [Diagnostics.Process]::Start($info)
}

Export-ModuleMember -Function Get-WealthoraDefaultConfigurationRoot,
    Get-WealthoraConfigurationPath, Test-WealthoraEmailAddress,
    Test-WealthoraSenderName, Read-WealthoraConfigurationDocument,
    Test-WealthoraStoredConfiguration, Open-WealthoraStoredConfiguration,
    Test-WealthoraSmtpCredential, Save-WealthoraOtpConfiguration,
    Remove-WealthoraOtpConfiguration, Set-WealthoraRelayUserEnvironment,
    Resolve-WealthoraProjectArtifacts, Resolve-WealthoraJava,
    Test-WealthoraRelayHealth, New-WealthoraJavaStartInfo,
    Start-WealthoraRelay, Stop-WealthoraOwnedRelay, Start-WealthoraDesktop

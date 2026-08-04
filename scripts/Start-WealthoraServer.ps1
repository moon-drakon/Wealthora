[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $EnvironmentFile,

    [Parameter(Mandatory)]
    [string] $JavaHome,

    [ValidateRange(1, 65535)]
    [int] $Port = 8080,

    [ValidateSet('prod', 'dev-mail-sink')]
    [string] $SpringProfile = 'prod',

    [string] $DevelopmentMailDirectory,

    [ValidateRange(128, 2048)]
    [int] $MaximumHeapMegabytes = 256,

    [ValidateRange(96, 512)]
    [int] $MaximumMetaspaceMegabytes = 192,

    [ValidateRange(32, 128)]
    [int] $CompressedClassSpaceMegabytes = 64,

    [ValidateRange(32, 256)]
    [int] $ReservedCodeCacheMegabytes = 96,

    [switch] $ConstrainedMemory,

    [ValidateRange(10, 600)]
    [int] $StartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

function Read-EnvironmentVariables {
    param([string] $Path)

    $variables = @{}
    $lineNumber = 0
    foreach ($rawLine in [System.IO.File]::ReadAllLines($Path)) {
        $lineNumber++
        $line = $rawLine.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith('#')) {
            continue
        }
        if ($line.StartsWith('export ')) {
            $line = $line.Substring(7).TrimStart()
        }

        $separator = $line.IndexOf('=')
        if ($separator -le 0) {
            throw "Invalid environment-file syntax at line $lineNumber."
        }

        $name = $line.Substring(0, $separator).Trim()
        if ($name -notmatch '^[A-Z][A-Z0-9_]*$') {
            throw "Invalid environment-variable name at line $lineNumber."
        }
        if ($variables.ContainsKey($name)) {
            throw "Duplicate environment-variable name: $name"
        }

        $value = $line.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and (
                ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $variables[$name] = $value
    }
    return $variables
}

function Assert-RequiredVariables {
    param(
        [hashtable] $Variables,
        [string[]] $Names
    )

    $missing = @($Names | Where-Object {
            -not $Variables.ContainsKey($_) -or
            [string]::IsNullOrWhiteSpace($Variables[$_])
        })
    if ($missing.Count -gt 0) {
        throw "Missing required environment-variable name(s): $($missing -join ', ')"
    }
}

$resolvedEnvironmentFile = (Resolve-Path -LiteralPath $EnvironmentFile).Path
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if ($resolvedEnvironmentFile.StartsWith(
        $repositoryRoot + [System.IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The environment file must remain outside the repository.'
}

$variables = Read-EnvironmentVariables -Path $resolvedEnvironmentFile
$requiredNames = @(
    'DATABASE_URL',
    'DATABASE_USERNAME',
    'DATABASE_PASSWORD',
    'TOKEN_PEPPER'
)
Assert-RequiredVariables -Variables $variables -Names $requiredNames

if ($variables['TOKEN_PEPPER'].Length -lt 32) {
    throw 'TOKEN_PEPPER must contain at least 32 characters.'
}
if (-not $variables['DATABASE_URL'].StartsWith(
        'jdbc:postgresql://', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'DATABASE_URL must be a PostgreSQL JDBC URL.'
}

$databaseUri = [Uri] $variables['DATABASE_URL'].Substring(5)
if (-not [string]::IsNullOrEmpty($databaseUri.UserInfo)) {
    throw 'DATABASE_URL must not contain embedded credentials.'
}
$queryNames = @($databaseUri.Query.TrimStart('?').Split(
        '&', [StringSplitOptions]::RemoveEmptyEntries) | ForEach-Object {
            [Uri]::UnescapeDataString($_.Split('=', 2)[0])
        })
if ($queryNames -contains 'user' -or $queryNames -contains 'password') {
    throw 'DATABASE_URL must not contain credential query parameters.'
}

$javaExecutable = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
    throw 'JavaHome must point to a JDK containing bin\java.exe.'
}
$serverJarPath = Join-Path $repositoryRoot `
    'server\target\wealthora-auth-server-1.0.0-SNAPSHOT.jar'
$serverJar = (Resolve-Path -LiteralPath $serverJarPath).Path

$childVariables = @{}
foreach ($entry in $variables.GetEnumerator()) {
    $childVariables[$entry.Key] = $entry.Value
}
$childVariables['SPRING_PROFILES_ACTIVE'] = $SpringProfile
$childVariables['LOGGING_LEVEL_ROOT'] = 'OFF'
$childVariables['SPRING_MAIN_BANNER_MODE'] = 'off'
if ($SpringProfile -eq 'dev-mail-sink') {
    if ([string]::IsNullOrWhiteSpace($DevelopmentMailDirectory)) {
        throw 'DevelopmentMailDirectory is required for dev-mail-sink.'
    }
    $mailDirectory = [System.IO.Path]::GetFullPath(
        $DevelopmentMailDirectory)
    $repositoryPrefix = $repositoryRoot `
        + [System.IO.Path]::DirectorySeparatorChar
    if ($mailDirectory.StartsWith(
            $repositoryPrefix,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Development mail must remain outside the repository.'
    }
    [System.IO.Directory]::CreateDirectory($mailDirectory) | Out-Null
    $childVariables['WEALTHORA_DEV_MAIL_DIR'] = $mailDirectory
}

$previousValues = @{}
try {
    foreach ($entry in $childVariables.GetEnumerator()) {
        $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable(
            $entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable(
            $entry.Key, $entry.Value, 'Process')
    }

    $javaArguments = @(
            '-Xms32m',
            "-Xmx${MaximumHeapMegabytes}m",
            '-Xss256k',
            "-XX:MaxMetaspaceSize=${MaximumMetaspaceMegabytes}m",
            "-XX:CompressedClassSpaceSize=${CompressedClassSpaceMegabytes}m",
            "-XX:ReservedCodeCacheSize=${ReservedCodeCacheMegabytes}m",
            '-XX:ActiveProcessorCount=2',
            '-XX:+UseSerialGC',
            '-jar',
            $serverJar,
            "--server.port=$Port"
        )
    if ($ConstrainedMemory) {
        $javaArguments = @(
            '-XX:TieredStopAtLevel=1',
            '-XX:MaxDirectMemorySize=32m'
        ) + $javaArguments
    }
    $process = Start-Process -FilePath $javaExecutable `
        -ArgumentList $javaArguments `
        -WorkingDirectory (Split-Path -Parent $serverJar) `
        -WindowStyle Hidden `
        -PassThru
} finally {
    foreach ($entry in $previousValues.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable(
            $entry.Key, $entry.Value, 'Process')
    }
}

$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$ready = $false
do {
    if ($process.HasExited) {
        break
    }
    try {
        $providerStatus = Invoke-RestMethod `
            -Uri "http://127.0.0.1:$Port/api/auth/status" `
            -TimeoutSec 10
        if ($null -ne $providerStatus.emailProviderAvailable) {
            $ready = $true
            break
        }
    } catch {
        # Startup can legitimately take time while Flyway and JPA validate.
    }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

if (-not $ready) {
    $category = if ($process.HasExited) {
        'PROCESS_EXITED'
    } else {
        Stop-Process -Id $process.Id -Force
        'STARTUP_TIMEOUT'
    }
    throw "Server startup failed. Category=$category"
}

$healthResponse = Invoke-RestMethod `
    -Uri "http://127.0.0.1:$Port/actuator/health" `
    -TimeoutSec 30
$healthStatus = $healthResponse.status

[pscustomobject]@{
    ProcessId = $process.Id
    Port = $Port
    Health = if ($healthStatus -eq 'UP') {
        'PASS'
    } else {
        'FAIL'
    }
    EmailProvider = if ($providerStatus.emailProviderAvailable) {
        'PASS'
    } else {
        'MISSING'
    }
    GoogleOAuth = if ($providerStatus.googleOAuthAvailable) {
        'PASS'
    } else {
        'MISSING'
    }
    CredentialSeparation = 'PASS'
    PersistentRawLogging = 'PASS'
}

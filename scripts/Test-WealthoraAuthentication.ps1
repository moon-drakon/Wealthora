[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $EnvironmentFile,

    [Parameter(Mandatory)]
    [string] $ServerUrl,

    [Parameter(Mandatory)]
    [string] $DevelopmentMailDirectory
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

$resolvedEnvironmentFile = (Resolve-Path -LiteralPath $EnvironmentFile).Path
$repositoryRoot = (Resolve-Path -LiteralPath (
        Join-Path $PSScriptRoot '..')).Path
$resolvedMailDirectory = [System.IO.Path]::GetFullPath(
    $DevelopmentMailDirectory)
$repositoryPrefix = $repositoryRoot `
    + [System.IO.Path]::DirectorySeparatorChar
foreach ($externalPath in @(
        $resolvedEnvironmentFile, $resolvedMailDirectory)) {
    if ($externalPath.StartsWith(
            $repositoryPrefix,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Live authentication files must remain outside the repository.'
    }
}

$variables = Read-EnvironmentVariables -Path $resolvedEnvironmentFile
foreach ($name in @(
        'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD')) {
    if (-not $variables.ContainsKey($name) -or
            [string]::IsNullOrWhiteSpace($variables[$name])) {
        throw "Missing required environment-variable name: $name"
    }
}
$variables['WEALTHORA_LIVE_AUTH_E2E'] = 'true'
$variables['WEALTHORA_SERVER_URL'] = $ServerUrl.TrimEnd('/')
$variables['WEALTHORA_DEV_MAIL_DIR'] = $resolvedMailDirectory

$previousValues = @{}
try {
    foreach ($entry in $variables.GetEnumerator()) {
        $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable(
            $entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable(
            $entry.Key, $entry.Value, 'Process')
    }

    $serverDirectory = Join-Path $repositoryRoot 'server'
    $dependencyClasspathPath = Join-Path $serverDirectory `
        'target\live-test-classpath.txt'
    $dependencyClasspath = (Get-Content -Raw -LiteralPath `
            $dependencyClasspathPath).Trim()
    if ([string]::IsNullOrWhiteSpace($dependencyClasspath)) {
        throw 'The generated live-test classpath is unavailable.'
    }
    $classpath = @(
        (Join-Path $serverDirectory 'target\test-classes'),
        (Join-Path $serverDirectory 'target\classes'),
        $dependencyClasspath
    ) -join [System.IO.Path]::PathSeparator
    $javaExecutable = (Get-Command java -ErrorAction Stop).Source

    & $javaExecutable `
        '-Xms32m' `
        '-Xmx128m' `
        '-Xss256k' `
        '-XX:ActiveProcessorCount=2' `
        '-XX:+UseSerialGC' `
        '-cp' $classpath `
        'com.wealthora.server.api.LiveAuthenticationE2ETest'
    if ($LASTEXITCODE -ne 0) {
        throw 'Live authentication E2E failed. Category=TEST_FAILURE'
    }
} finally {
    foreach ($entry in $previousValues.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable(
            $entry.Key, $entry.Value, 'Process')
    }
}

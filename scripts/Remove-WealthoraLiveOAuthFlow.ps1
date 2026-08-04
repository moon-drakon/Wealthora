[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $EnvironmentFile,

    [Parameter(Mandatory)]
    [string] $FlowIdentifierFile,

    [Parameter(Mandatory)]
    [string] $JavaHome
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

$repositoryRoot = (Resolve-Path -LiteralPath (
        Join-Path $PSScriptRoot '..')).Path
$repositoryPrefix = $repositoryRoot `
    + [System.IO.Path]::DirectorySeparatorChar
$resolvedEnvironmentFile = (Resolve-Path -LiteralPath $EnvironmentFile).Path
$resolvedIdentifierFile = (Resolve-Path -LiteralPath $FlowIdentifierFile).Path
foreach ($externalPath in @(
        $resolvedEnvironmentFile, $resolvedIdentifierFile)) {
    if ($externalPath.StartsWith(
            $repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Live OAuth cleanup inputs must remain outside the repository.'
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

$flowIdentifier = [System.IO.File]::ReadAllText(
    $resolvedIdentifierFile).Trim()
[void] [Guid]::Parse($flowIdentifier)

$javaExecutable = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
    throw 'JavaHome must point to a JDK containing bin\java.exe.'
}

$testClasses = (Resolve-Path -LiteralPath (Join-Path $repositoryRoot `
        'server\target\test-classes')).Path
$mainClasses = (Resolve-Path -LiteralPath (Join-Path $repositoryRoot `
        'server\target\classes')).Path
$mavenRepository = Join-Path $env:USERPROFILE `
    '.m2\repository\org\postgresql\postgresql'
$postgresDriver = Get-ChildItem -LiteralPath $mavenRepository -Recurse `
    -Filter 'postgresql-*.jar' -File |
    Where-Object {
        $_.Name -notlike '*sources*' -and
        $_.Name -notlike '*javadoc*'
    } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $postgresDriver) {
    throw 'The PostgreSQL JDBC driver is unavailable.'
}
$classPath = $testClasses + [System.IO.Path]::PathSeparator `
    + $mainClasses + [System.IO.Path]::PathSeparator `
    + $postgresDriver.FullName

$childVariables = @{
    DATABASE_URL = $variables['DATABASE_URL']
    DATABASE_USERNAME = $variables['DATABASE_USERNAME']
    DATABASE_PASSWORD = $variables['DATABASE_PASSWORD']
    WEALTHORA_LIVE_OAUTH_FLOW_ID = $flowIdentifier
}
$previousValues = @{}
try {
    foreach ($entry in $childVariables.GetEnumerator()) {
        $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable(
            $entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable(
            $entry.Key, $entry.Value, 'Process')
    }

    & $javaExecutable '-Xms16m' '-Xmx64m' '-Xss256k' `
        '-XX:ActiveProcessorCount=2' '-XX:+UseSerialGC' `
        '-cp' $classPath `
        'com.wealthora.server.api.LiveGoogleOAuthFlowCleanup'
    if ($LASTEXITCODE -ne 0) {
        throw "Scoped OAuth cleanup failed with exit code $LASTEXITCODE."
    }
} finally {
    foreach ($entry in $previousValues.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable(
            $entry.Key, $entry.Value, 'Process')
    }
}

Remove-Item -LiteralPath $resolvedIdentifierFile -Force
Write-Output 'CleanupIdentifierFileRemoved: PASS'

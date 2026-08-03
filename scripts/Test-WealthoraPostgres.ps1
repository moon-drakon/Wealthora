[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $EnvironmentFile
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

$resolvedEnvironmentFile = (Resolve-Path -LiteralPath $EnvironmentFile).Path
$repositoryRoot = (Resolve-Path -LiteralPath (
        Join-Path $PSScriptRoot '..')).Path
if ($resolvedEnvironmentFile.StartsWith(
        $repositoryRoot + [System.IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The environment file must remain outside the repository.'
}

$variables = Read-EnvironmentVariables -Path $resolvedEnvironmentFile
$requiredNames = @(
    'DATABASE_URL',
    'DATABASE_USERNAME',
    'DATABASE_PASSWORD'
)
foreach ($name in $requiredNames) {
    if (-not $variables.ContainsKey($name) -or
            [string]::IsNullOrWhiteSpace($variables[$name])) {
        throw "Missing required environment-variable name: $name"
    }
}

$previousValues = @{}
try {
    foreach ($entry in $variables.GetEnumerator()) {
        $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable(
            $entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable(
            $entry.Key, $entry.Value, 'Process')
    }
    $previousValues['WEALTHORA_LIVE_DATABASE_AUDIT'] =
        [Environment]::GetEnvironmentVariable(
            'WEALTHORA_LIVE_DATABASE_AUDIT', 'Process')
    [Environment]::SetEnvironmentVariable(
        'WEALTHORA_LIVE_DATABASE_AUDIT', 'true', 'Process')

    Push-Location (Join-Path $repositoryRoot 'server')
    try {
        & '.\mvnw.cmd' -q '-Dtest=LivePostgresAuditTest' test
        if ($LASTEXITCODE -ne 0) {
            throw 'Live PostgreSQL audit failed. Category=TEST_FAILURE'
        }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($entry in $previousValues.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable(
            $entry.Key, $entry.Value, 'Process')
    }
}

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $EnvironmentFile,

    [Parameter(Mandatory)]
    [string] $JavaHome,

    [ValidateRange(1, 65535)]
    [int] $Port = 18082,

    [ValidateRange(10, 600)]
    [int] $StartupTimeoutSeconds = 240
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

function Set-TemporaryEnvironmentVariable {
    param(
        [hashtable] $PreviousValues,
        [string] $Name,
        [string] $Value
    )

    if (-not $PreviousValues.ContainsKey($Name)) {
        $PreviousValues[$Name] = [Environment]::GetEnvironmentVariable(
            $Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Find-AntExecutable {
    $command = Get-Command ant.bat -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }
    $fallback = 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat'
    if (Test-Path -LiteralPath $fallback -PathType Leaf) {
        return $fallback
    }
    throw 'Apache Ant is unavailable.'
}

function Clear-TemporaryDirectory {
    param([string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        return
    }
    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    $temporaryBase = [System.IO.Path]::GetFullPath($env:TEMP)
    $temporaryPrefix = $temporaryBase `
        + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolvedPath.StartsWith(
            $temporaryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refused to remove a directory outside the temporary root.'
    }
    Remove-Item -LiteralPath $resolvedPath -Recurse -Force
}

$repositoryRoot = (Resolve-Path -LiteralPath (
        Join-Path $PSScriptRoot '..')).Path
$resolvedEnvironmentFile = (Resolve-Path -LiteralPath $EnvironmentFile).Path
if ($resolvedEnvironmentFile.StartsWith(
        $repositoryRoot + [System.IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The environment file must remain outside the repository.'
}

$javaExecutable = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
    throw 'JavaHome must point to a JDK containing bin\java.exe.'
}
$antExecutable = Find-AntExecutable
$temporaryRoot = Join-Path $env:TEMP (
    'wealthora-speech-live-' + [Guid]::NewGuid().ToString('N'))
$mailDirectory = Join-Path $temporaryRoot 'mail'
$fixtureFile = Join-Path $temporaryRoot 'fixture.txt'
[System.IO.Directory]::CreateDirectory($mailDirectory) | Out-Null

$serverInfo = $null
$previousValues = @{}
$livePassed = $false
$cleanupPassed = $false
$fixtureCreated = $false
$cleanupFailure = $null

try {
    Set-TemporaryEnvironmentVariable $previousValues 'ANT_OPTS' `
        '-Xms16m -Xmx96m -Xss256k -XX:ActiveProcessorCount=2 -XX:+UseSerialGC'
    Set-TemporaryEnvironmentVariable $previousValues 'JAVA_TOOL_OPTIONS' `
        '-Xms16m -Xshare:off -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:CompressedClassSpaceSize=32m -XX:ReservedCodeCacheSize=32m -XX:TieredStopAtLevel=1'
    & $antExecutable compile-test
    if ($LASTEXITCODE -ne 0) {
        throw 'The live speech test classes could not be compiled.'
    }
    [Environment]::SetEnvironmentVariable(
        'JAVA_TOOL_OPTIONS', $previousValues['JAVA_TOOL_OPTIONS'], 'Process')

    Set-TemporaryEnvironmentVariable $previousValues 'JAVA_TOOL_OPTIONS' `
        '-XX:TieredStopAtLevel=1 -XX:MaxDirectMemorySize=32m'
    $serverInfo = @(& (Join-Path $PSScriptRoot 'Start-WealthoraServer.ps1') `
        -EnvironmentFile $resolvedEnvironmentFile `
        -JavaHome $JavaHome `
        -Port $Port `
        -SpringProfile 'dev-mail-sink' `
        -DevelopmentMailDirectory $mailDirectory `
        -MaximumHeapMegabytes 128 `
        -MaximumMetaspaceMegabytes 112 `
        -CompressedClassSpaceMegabytes 32 `
        -ReservedCodeCacheMegabytes 32 `
        -ConstrainedMemory `
        -StartupTimeoutSeconds $StartupTimeoutSeconds) |
        Select-Object -Last 1
    [Environment]::SetEnvironmentVariable(
        'JAVA_TOOL_OPTIONS', $previousValues['JAVA_TOOL_OPTIONS'], 'Process')
    if ($null -eq $serverInfo -or $null -eq $serverInfo.ProcessId) {
        throw 'The server launcher did not return a process identifier.'
    }

    Set-TemporaryEnvironmentVariable $previousValues `
        'WEALTHORA_SERVER_URL' "http://127.0.0.1:$Port"
    Set-TemporaryEnvironmentVariable $previousValues `
        'WEALTHORA_REPOSITORY_ROOT' $repositoryRoot
    Set-TemporaryEnvironmentVariable $previousValues `
        'WEALTHORA_DEV_MAIL_DIR' $mailDirectory
    Set-TemporaryEnvironmentVariable $previousValues `
        'WEALTHORA_LIVE_FIXTURE_FILE' $fixtureFile
    [Environment]::SetEnvironmentVariable(
        'JAVA_TOOL_OPTIONS', $previousValues['JAVA_TOOL_OPTIONS'], 'Process')
    $liveClasspath = (Join-Path $repositoryRoot 'build\classes') `
        + [System.IO.Path]::PathSeparator `
        + (Join-Path $repositoryRoot 'build\test\classes') `
        + [System.IO.Path]::PathSeparator `
        + (Join-Path $repositoryRoot 'lib\*')
    & $javaExecutable `
        '-Xms16m' `
        '-Xmx96m' `
        '-Xss256k' `
        '-Xshare:off' `
        '-XX:+UseSerialGC' `
        '-XX:MaxMetaspaceSize=96m' `
        '-XX:CompressedClassSpaceSize=32m' `
        '-XX:ReservedCodeCacheSize=32m' `
        '-XX:TieredStopAtLevel=1' `
        '-cp' $liveClasspath `
        'com.spendwise.voice.LiveSpeechRecognitionTest'
    if ($LASTEXITCODE -ne 0) {
        throw 'The live speech workflow failed.'
    }
    $livePassed = $true
} finally {
    $fixtureCreated = Test-Path -LiteralPath $fixtureFile -PathType Leaf
    if ($fixtureCreated) {
        try {
            $variables = Read-EnvironmentVariables $resolvedEnvironmentFile
            foreach ($name in @(
                    'DATABASE_URL',
                    'DATABASE_USERNAME',
                    'DATABASE_PASSWORD')) {
                if (-not $variables.ContainsKey($name) -or
                        [string]::IsNullOrWhiteSpace($variables[$name])) {
                    throw "$name is required for scoped fixture cleanup."
                }
                Set-TemporaryEnvironmentVariable $previousValues `
                    $name $variables[$name]
            }
            if (-not $livePassed) {
                Set-TemporaryEnvironmentVariable $previousValues `
                    'WEALTHORA_ALLOW_ABSENT_LIVE_FIXTURE' 'true'
            }
            $driverRoot = Join-Path $env:USERPROFILE `
                '.m2\repository\org\postgresql\postgresql'
            $driver = Get-ChildItem -LiteralPath $driverRoot -Recurse `
                    -Filter 'postgresql-*.jar' |
                Where-Object {
                    $_.Name -notlike '*sources*' -and
                    $_.Name -notlike '*javadoc*'
                } |
                Sort-Object LastWriteTime -Descending |
                Select-Object -First 1
            if ($null -eq $driver) {
                throw 'PostgreSQL JDBC driver is unavailable.'
            }
            $cleanupClasspath = (Join-Path $repositoryRoot `
                    'server\target\test-classes') `
                + [System.IO.Path]::PathSeparator `
                + $driver.FullName
            & $javaExecutable `
                '-Xms16m' `
                '-Xmx64m' `
                '-Xss256k' `
                '-XX:ActiveProcessorCount=2' `
                '-cp' $cleanupClasspath `
                'com.wealthora.server.api.LiveCloudFixtureCleanup'
            if ($LASTEXITCODE -ne 0) {
                throw 'Scoped live fixture cleanup failed.'
            }
            $cleanupPassed = $true
        } catch {
            $cleanupFailure = 'Scoped live fixture cleanup failed.'
        }
    } else {
        $cleanupPassed = $true
    }

    foreach ($entry in $previousValues.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable(
            $entry.Key, $entry.Value, 'Process')
    }
    if ($null -ne $serverInfo -and $null -ne $serverInfo.ProcessId) {
        Stop-Process -Id $serverInfo.ProcessId -Force `
            -ErrorAction SilentlyContinue
    }
    if ($cleanupPassed) {
        Clear-TemporaryDirectory $temporaryRoot
    }
    if ($null -ne $cleanupFailure) {
        throw $cleanupFailure
    }
}

[pscustomobject]@{
    LiveSpeechWorkflow = if ($livePassed) { 'PASS' } else { 'FAIL' }
    ScopedCleanup = if ($cleanupPassed) { 'PASS' } else { 'FAIL' }
}

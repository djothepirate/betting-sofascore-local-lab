[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArguments
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$propertiesPath = Join-Path $PSScriptRoot 'maven-wrapper.properties'
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "Missing Maven wrapper properties: $propertiesPath"
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) {
        continue
    }
    $separator = $trimmed.IndexOf('=')
    if ($separator -lt 1) {
        continue
    }
    $key = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim()
    $properties[$key] = $value
}

$distributionUrl = $properties['distributionUrl']
$expectedSha256 = $properties['distributionSha256Sum']
if (-not $distributionUrl -or -not $expectedSha256) {
    throw 'distributionUrl and distributionSha256Sum are required'
}

$archiveName = [System.IO.Path]::GetFileName($distributionUrl)
$distributionDirectoryName = [System.IO.Path]::GetFileNameWithoutExtension($archiveName)
$distributionDirectoryName = $distributionDirectoryName -replace '-bin$', ''

$mavenUserHome = $env:MAVEN_USER_HOME
if (-not $mavenUserHome) {
    $mavenUserHome = Join-Path $HOME '.m2'
}

$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $urlBytes = [System.Text.Encoding]::UTF8.GetBytes($distributionUrl)
    $urlHash = (($sha256.ComputeHash($urlBytes) | ForEach-Object { $_.ToString('x2') }) -join '').Substring(0, 16)
}
finally {
    $sha256.Dispose()
}

$installRoot = Join-Path $mavenUserHome "wrapper\dists\$distributionDirectoryName\$urlHash"
$mavenHome = Join-Path $installRoot $distributionDirectoryName
$mavenCommand = Join-Path $mavenHome 'bin\mvn.cmd'

if (-not (Test-Path -LiteralPath $mavenCommand -PathType Leaf)) {
    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("mvnw-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
    try {
        $archivePath = Join-Path $tempRoot $archiveName
        Write-Host "Downloading Apache Maven from $distributionUrl"

        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $webClient = New-Object System.Net.WebClient
        try {
            if ($env:MVNW_USERNAME -and $env:MVNW_PASSWORD) {
                $webClient.Credentials = New-Object System.Net.NetworkCredential($env:MVNW_USERNAME, $env:MVNW_PASSWORD)
            }
            $webClient.DownloadFile($distributionUrl, $archivePath)
        }
        finally {
            $webClient.Dispose()
        }

        $actualSha256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualSha256 -ne $expectedSha256.ToLowerInvariant()) {
            throw "Maven distribution checksum mismatch. Expected $expectedSha256, actual $actualSha256"
        }

        $extractRoot = Join-Path $tempRoot 'extracted'
        Expand-Archive -LiteralPath $archivePath -DestinationPath $extractRoot -Force
        $extractedHome = Join-Path $extractRoot $distributionDirectoryName
        $extractedMavenCommand = Join-Path $extractedHome 'bin\mvn.cmd'
        if (-not (Test-Path -LiteralPath $extractedMavenCommand -PathType Leaf)) {
            throw 'Unexpected Maven archive layout'
        }

        New-Item -ItemType Directory -Path $installRoot -Force | Out-Null
        if (-not (Test-Path -LiteralPath $mavenHome -PathType Container)) {
            Move-Item -LiteralPath $extractedHome -Destination $mavenHome
        }
    }
    finally {
        if (Test-Path -LiteralPath $tempRoot) {
            Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

& $mavenCommand @MavenArguments
exit $LASTEXITCODE

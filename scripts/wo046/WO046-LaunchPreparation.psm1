Set-StrictMode -Version 3.0

# Pure preparation only. No process start, SQL, socket, certificate store or file write.
function Assert-WO046Reference {
    param([string]$GoId, [string]$DocumentSha256, [string]$CertificateSha256 = '')
    if ($GoId -cnotmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\z' -or
            $DocumentSha256 -cnotmatch '^[0-9a-f]{64}\z' -or
            ($CertificateSha256.Length -gt 0 -and $CertificateSha256 -cnotmatch '^[0-9a-f]{64}\z')) {
        throw 'WO046_INVALID_EXACT_REFERENCE'
    }
}

function Assert-WO046AbsolutePath {
    param([string]$Path, [string]$Extension = '')
    try {
        if ([string]::IsNullOrWhiteSpace($Path) -or $Path -match '[\x00-\x1f\x7f"]' -or
                -not [IO.Path]::IsPathFullyQualified($Path) -or
                -not [string]::Equals([IO.Path]::GetFullPath($Path), $Path,
                    [StringComparison]::OrdinalIgnoreCase) -or
                ($Extension.Length -gt 0 -and [IO.Path]::GetExtension($Path) -ine $Extension)) {
            throw 'INVALID_PATH'
        }
    }
    catch { throw 'WO046_INVALID_ABSOLUTE_PATH' }
}

function Get-WO046FixedLaunchEnvironment {
    return [ordered]@{
        JAVA_TOOL_OPTIONS = ''; JDK_JAVA_OPTIONS = ''; _JAVA_OPTIONS = ''; JAVA_OPTS = ''
        HTTP_PROXY = ''; HTTPS_PROXY = ''; ALL_PROXY = ''; NO_PROXY = '127.0.0.1,localhost'
        SSLKEYLOGFILE = ''; NSS_SSLKEYLOGFILE = ''; JDK_TLS_KEYLOGGER = ''; JAVAX_NET_DEBUG = ''
        JDK_HTTPCLIENT_HTTPCLIENT_LOG = ''
        SPRING_APPLICATION_JSON = ''; SPRING_CONFIG_IMPORT = ''
        SPRING_CONFIG_LOCATION = 'classpath:/'; SPRING_CONFIG_ADDITIONAL_LOCATION = ''
        SPRING_PROFILES_ACTIVE = 'local'; SPRING_MAIN_BANNER_MODE = 'off'
        SERVER_ADDRESS = '127.0.0.1'; SERVER_PORT = '8087'; SERVER_SHUTDOWN = 'graceful'
        SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE = '20s'
        MANAGEMENT_ENDPOINT_SHUTDOWN_ENABLED = 'true'
        MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = 'health,info,shutdown'
        MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS = 'never'
        LOGGING_LEVEL_ROOT = 'WARN'; LOGGING_LEVEL_COM_BETTINGPROJECT = 'WARN'
        LOGGING_LEVEL_ORG_FLYWAYDB = 'WARN'; LOGGING_LEVEL_ORG_HIBERNATE_SQL = 'OFF'
        DEBUG = 'false'; TRACE = 'false'; POSTGRES_PORT = '5432'
        SOFASCORE_ENABLED = 'false'; SOFASCORE_PLAYWRIGHT_ENABLED = 'false'
        SOFASCORE_J3_QUALIFICATION_ENABLED = 'false'
        SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED = 'false'
        SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED = 'false'
        SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED = 'false'
        SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED = 'false'
        SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION = 'false'
        SOFASCORE_PLAYWRIGHT_LOOPBACK_ORIGIN = ''
        SOFASCORE_BASE_URL = ''; SOFASCORE_ALLOWED_ENDPOINTS = ''
        OPTIONAL_INTEGRATION_ENABLED = 'true'
        OPTIONAL_INTEGRATION_EXECUTION_MODE = 'PROVIDER_DERIVED'
        OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED = 'true'
        OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS = 'NOT_EVIDENCED'
        OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION = 'PASS'
        OPTIONAL_INTEGRATION_SENDER_QUALIFICATION = 'PASS'
        OPTIONAL_INTEGRATION_RECEIVER_ORIGIN = 'https://127.0.0.1:8444'
        OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION = 'false'
        OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN = ''
        OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED = 'false'
        OPTIONAL_INTEGRATION_CONNECT_TIMEOUT = '5s'
        OPTIONAL_INTEGRATION_REQUEST_TIMEOUT = '10s'
        OPTIONAL_INTEGRATION_MTLS_KEY_STORE_TYPE = 'Windows-MY'
        OPTIONAL_INTEGRATION_MTLS_REQUIRED = 'true'
    }
}

function New-WO046LocalLabLaunchEnvironment {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$GoId,
        [Parameter(Mandatory)][string]$OwnerGoDocumentSha256,
        [Parameter(Mandatory)][string]$ClientCertificateSha256,
        [Parameter(Mandatory)][string]$DatabaseName,
        [Parameter(Mandatory)][string]$DatabaseUser,
        [Parameter(Mandatory)][string]$DatabasePassword,
        [Parameter(Mandatory)][string]$ExportDirectory,
        [string]$PermissionAuditStatus = 'NOT_EVIDENCED'
    )
    Assert-WO046Reference $GoId $OwnerGoDocumentSha256 $ClientCertificateSha256
    if ($ClientCertificateSha256.Length -ne 64 -or
            $DatabaseName -cnotmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}\z' -or
            $DatabaseUser -cnotmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}\z' -or
            [string]::IsNullOrWhiteSpace($DatabasePassword) -or $DatabasePassword.Length -gt 512 -or
            $DatabasePassword -match '[\x00-\x1f\x7f]' -or
            @('NOT_EVIDENCED', 'EVIDENCED_COMPATIBLE') -cnotcontains $PermissionAuditStatus) {
        throw 'WO046_INVALID_LAUNCH_INPUT'
    }
    Assert-WO046AbsolutePath $ExportDirectory
    $result = Get-WO046FixedLaunchEnvironment
    $result['OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS'] = $PermissionAuditStatus
    $result['POSTGRES_DB'] = $DatabaseName
    $result['POSTGRES_USER'] = $DatabaseUser
    $result['POSTGRES_PASSWORD'] = $DatabasePassword
    $result['SOFASCORE_EXPORT_DIR'] = $ExportDirectory
    $result['OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256'] = $ClientCertificateSha256
    $result['OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID'] = $GoId
    $result['OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256'] = $OwnerGoDocumentSha256
    return ,$result
}

function Assert-WO046LocalLabLaunchEnvironment {
    [CmdletBinding()]
    param([Parameter(Mandatory)][Collections.IDictionary]$Environment)
    try {
        $expected = New-WO046LocalLabLaunchEnvironment `
            -GoId $Environment['OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID'] `
            -OwnerGoDocumentSha256 $Environment['OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256'] `
            -ClientCertificateSha256 $Environment['OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256'] `
            -DatabaseName $Environment['POSTGRES_DB'] -DatabaseUser $Environment['POSTGRES_USER'] `
            -DatabasePassword $Environment['POSTGRES_PASSWORD'] -ExportDirectory $Environment['SOFASCORE_EXPORT_DIR'] `
            -PermissionAuditStatus $Environment['OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS']
        if ($Environment.Count -ne $expected.Count) { throw 'KEY_COUNT' }
        foreach ($key in $expected.Keys) {
            if (@($Environment.Keys) -cnotcontains $key -or
                    $Environment[$key] -isnot [string] -or $Environment[$key] -cne $expected[$key]) {
                throw 'KEY_OR_VALUE'
            }
        }
    }
    catch { throw 'WO046_LAUNCH_ENVIRONMENT_REFUSED' }
}

function New-WO046LocalLabStartInfo {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$JavaPath,
        [Parameter(Mandatory)][string]$JarPath,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [Parameter(Mandatory)][string]$InstanceId,
        [Parameter(Mandatory)][Collections.IDictionary]$Environment,
        [Parameter(Mandatory)][Collections.IDictionary]$HostEnvironment
    )
    Assert-WO046LocalLabLaunchEnvironment $Environment
    Assert-WO046AbsolutePath $JavaPath '.exe'
    if ([IO.Path]::GetFileName($JavaPath) -ine 'java.exe') { throw 'WO046_INVALID_JAVA_PATH' }
    Assert-WO046AbsolutePath $JarPath '.jar'
    Assert-WO046AbsolutePath $WorkingDirectory
    Assert-WO046Reference $InstanceId ('0' * 64)
    $hostKeys = @('SystemRoot', 'WINDIR', 'TEMP', 'TMP', 'USERPROFILE', 'APPDATA', 'LOCALAPPDATA',
        'PATH', 'ComSpec', 'PATHEXT', 'PROCESSOR_ARCHITECTURE', 'NUMBER_OF_PROCESSORS', 'OS')
    if (@($HostEnvironment.Keys) -cnotcontains 'SystemRoot' -or
            [string]::IsNullOrWhiteSpace($HostEnvironment['SystemRoot'])) {
        throw 'WO046_HOST_ENVIRONMENT_REFUSED'
    }
    foreach ($key in $HostEnvironment.Keys) {
        if ($hostKeys -cnotcontains $key -or $HostEnvironment[$key] -isnot [string] -or
                $HostEnvironment[$key] -match '[\x00-\x1f\x7f]') {
            throw 'WO046_HOST_ENVIRONMENT_REFUSED'
        }
    }
    $startInfo = [Diagnostics.ProcessStartInfo]::new($JavaPath)
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Environment.Clear()
    foreach ($key in $HostEnvironment.Keys) { $startInfo.Environment.Add($key, $HostEnvironment[$key]) }
    foreach ($key in $Environment.Keys) { $startInfo.Environment.Add($key, $Environment[$key]) }
    foreach ($argument in @(
            ('-Dwo046.instance={0}' -f $InstanceId),
            '-Djdk.httpclient.disableRetryConnect=true',
            '-Djdk.httpclient.redirects.retrylimit=1',
            '-Djdk.httpclient.enableAllMethodRetry=false',
            '-jar', $JarPath)) {
        $startInfo.ArgumentList.Add($argument)
    }
    # This method deliberately does NOT call Process.Start(). Hash/authority/readiness and
    # exact process ownership must still be checked by a separately authorized campaign.
    return $startInfo
}

function New-WO046TechnicalStopBytes {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$GoId,
        [Parameter(Mandatory)][string]$OwnerGoDocumentSha256,
        [Parameter(Mandatory)][DateTimeOffset]$StoppedAtUtc
    )
    Assert-WO046Reference $GoId $OwnerGoDocumentSha256
    if ($StoppedAtUtc.Offset -ne [TimeSpan]::Zero -or $StoppedAtUtc.Ticks % 10 -ne 0) {
        throw 'WO046_STOP_TIMESTAMP_REQUIRES_EXACT_UTC_MICROSECONDS'
    }
    $fields = [ordered]@{
        FORMAT = 'WO046_FAIL_CLOSED_TECHNICAL_STOP_V1'
        ACTOR = 'CODEX_LOCAL_AGENT'
        AUTHORITY = 'FROZEN_MANIFEST_STOP_AND_ONE_TIME_EXECUTION_BOUNDARY'
        GO_ID = $GoId
        OWNER_GO_DOCUMENT_SHA256 = $OwnerGoDocumentSha256
        STOPPED_AT_UTC = $StoppedAtUtc.ToString("yyyy-MM-dd'T'HH:mm:ss.ffffff'Z'", [Globalization.CultureInfo]::InvariantCulture)
        REASON = 'LOCAL_LAB_START_CONFIGURATION_BINDING_REFUSED'
        ACTION = 'REVOKE_UNUSED_GO_AND_STOP_EXACT_OWNED_RESOURCES'
        PROVIDER_DERIVED_IMPORT_POSTS = '0'
        NEW_GO_OR_RETRY_AUTHORIZED = 'NO'
    }
    $text = [Text.StringBuilder]::new()
    foreach ($key in $fields.Keys) {
        [void]$text.Append($key).Append('=').Append([string]$fields[$key]).Append("`n")
    }
    return ,([Text.UTF8Encoding]::new($false, $true).GetBytes($text.ToString()))
}

function Test-WO046TechnicalStopBytes {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][byte[]]$Bytes,
        [Parameter(Mandatory)][string]$ExpectedGoId,
        [Parameter(Mandatory)][string]$ExpectedOwnerGoDocumentSha256
    )
    try {
        if ($Bytes.Length -gt 4096 -or $Bytes.Length -eq 0) { throw 'SIZE' }
        Assert-WO046Reference $ExpectedGoId $ExpectedOwnerGoDocumentSha256
        $text = [Text.UTF8Encoding]::new($false, $true).GetString($Bytes)
        if ($text -match '[\r\x00-\x09\x0b-\x1f\x7f\uFEFF]' -or -not $text.EndsWith("`n")) { throw 'ENCODING' }
        $lines = $text.Split("`n")
        if ($lines.Count -ne 11 -or $lines[10] -cne '') { throw 'LINES' }
        $values = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
        foreach ($line in $lines[0..9]) {
            if ($line -cnotmatch '^([A-Z][A-Z0-9_]+)=([^=\r\n]+)$') { throw 'FIELD' }
            $values.Add($Matches[1], $Matches[2])
        }
        if ($values['GO_ID'] -cne $ExpectedGoId -or
                $values['OWNER_GO_DOCUMENT_SHA256'] -cne $ExpectedOwnerGoDocumentSha256 -or
                $values['STOPPED_AT_UTC'] -cnotmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$') { throw 'REFERENCE' }
        $timestamp = [DateTimeOffset]::ParseExact($values['STOPPED_AT_UTC'],
            "yyyy-MM-dd'T'HH:mm:ss.ffffff'Z'", [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::AssumeUniversal)
        $canonical = New-WO046TechnicalStopBytes -GoId $ExpectedGoId `
            -OwnerGoDocumentSha256 $ExpectedOwnerGoDocumentSha256 -StoppedAtUtc $timestamp
        if ($text -cne [Text.Encoding]::UTF8.GetString($canonical)) { throw 'NONCANONICAL' }
        return [pscustomobject]@{
            Format = 'WO046_FAIL_CLOSED_TECHNICAL_STOP_V1'
            ByteCount = $Bytes.Length
            Sha256 = [Convert]::ToHexStringLower([Security.Cryptography.SHA256]::HashData($Bytes))
            Canonical = $true
        }
    }
    catch { throw 'WO046_TECHNICAL_STOP_REFUSED' }
}

Export-ModuleMember -Function New-WO046LocalLabLaunchEnvironment, Assert-WO046LocalLabLaunchEnvironment,
    New-WO046LocalLabStartInfo, New-WO046TechnicalStopBytes, Test-WO046TechnicalStopBytes

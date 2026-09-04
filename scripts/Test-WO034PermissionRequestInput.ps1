[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($PSVersionTable.PSVersion -lt [version]'7.4') {
    throw 'PowerShell 7.4 or newer is required for the WO-034 input preflight.'
}

$expectedLauncherSha256 = '2398de4e1a9179f6bd79913876c64256de014c48ed0615a0c3709851f3e37094'
$expectedRendererSha256 = 'b722709fd0d8988718fcfefb8506803daba4cd241bb3dc00a88874dd708f28b6'
$expectedTemplateSha256 = 'bfb9195b78aa663c2e7bb36e896b813087afb1d4e1609346bc9cb0ff21ad8356'
$expectedBettingRelatedUseDescription =
    'Betting Project will receive an immutable, minimized, normalized and human-reviewed ' +
    'J7 export as a durable input for future versioned enrichment and betting-analysis ' +
    'processing; it will not call SofaScore or trigger an acquisition.'
$utf8 = [Text.UTF8Encoding]::new($false, $true)

$expectedKeys = @(
    'OUTBOUND_CONTENT_MODE',
    'OUTBOUND_CONTENT_MODE_OWNER_CONFIRMED',
    'REQUESTOR_FULL_LEGAL_NAME',
    'REQUESTOR_ROLE',
    'ORGANIZATION_OR_PROJECT_OWNER',
    'LEGAL_ENTITY_TYPE',
    'LEGAL_ENTITY_TYPE_DETAILS',
    'COUNTRY_AND_JURISDICTION',
    'REPLY_EMAIL',
    'FORM_COMPANY_FIELD_MODE',
    'FORM_COMPANY_VALUE',
    'PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE',
    'PROJECT_WEBSITE_OR_REPOSITORY',
    'INTENDED_USE_CLASSIFICATION',
    'INTENDED_USE_CLASSIFICATION_DETAILS',
    'BETTING_RELATED_USE_DESCRIPTION',
    'END_USER_ACCESS',
    'END_USER_ACCESS_DETAILS',
    'RECEIVER_CONTROL_RELATION',
    'RECEIVER_CONTROL_RELATION_DETAILS',
    'RECEIVER_OPERATOR_IDENTITY',
    'RECEIVER_FUTURE_HOSTING_PROVIDER',
    'RECEIVER_FUTURE_HOSTING_COUNTRY',
    'RECEIVER_FUTURE_HOSTING_REGION',
    'POSSIBLE_FUTURE_PROVIDER_AND_RECEIVER_COLOCATION',
    'PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST',
    'MODEL_TRAINING',
    'MODEL_TRAINING_DETAILS',
    'DATA_RESALE',
    'DATA_RESALE_DETAILS',
    'PUBLIC_REDISTRIBUTION',
    'PUBLIC_REDISTRIBUTION_DETAILS',
    'DATA_USE_DECLARATIONS_SCOPE',
    'DATA_USE_DECLARATIONS_SCOPE_DETAILS',
    'REQUESTED_PERMISSION_DURATION',
    'EXPECTED_MANUAL_ACQUISITIONS_PER_DAY',
    'EXPECTED_MANUAL_ACQUISITIONS_PER_WEEK',
    'EXPECTED_DIRECT_ATTEMPTS_PER_DAY',
    'EXPECTED_DIRECT_ATTEMPTS_PER_MONTH',
    'LAB_ITSELF_PLACES_WAGERS',
    'LAB_ITSELF_PLACES_WAGERS_EXPLANATION'
)

$unconditionalKeys = @(
    'OUTBOUND_CONTENT_MODE_OWNER_CONFIRMED',
    'REQUESTOR_FULL_LEGAL_NAME',
    'REQUESTOR_ROLE',
    'ORGANIZATION_OR_PROJECT_OWNER',
    'LEGAL_ENTITY_TYPE',
    'COUNTRY_AND_JURISDICTION',
    'REPLY_EMAIL',
    'FORM_COMPANY_FIELD_MODE',
    'PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE',
    'INTENDED_USE_CLASSIFICATION',
    'BETTING_RELATED_USE_DESCRIPTION',
    'END_USER_ACCESS',
    'RECEIVER_CONTROL_RELATION',
    'RECEIVER_OPERATOR_IDENTITY',
    'RECEIVER_FUTURE_HOSTING_PROVIDER',
    'RECEIVER_FUTURE_HOSTING_COUNTRY',
    'RECEIVER_FUTURE_HOSTING_REGION',
    'POSSIBLE_FUTURE_PROVIDER_AND_RECEIVER_COLOCATION',
    'PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST',
    'MODEL_TRAINING',
    'DATA_RESALE',
    'PUBLIC_REDISTRIBUTION',
    'DATA_USE_DECLARATIONS_SCOPE',
    'REQUESTED_PERMISSION_DURATION',
    'EXPECTED_MANUAL_ACQUISITIONS_PER_DAY',
    'EXPECTED_MANUAL_ACQUISITIONS_PER_WEEK',
    'EXPECTED_DIRECT_ATTEMPTS_PER_DAY',
    'EXPECTED_DIRECT_ATTEMPTS_PER_MONTH',
    'LAB_ITSELF_PLACES_WAGERS',
    'LAB_ITSELF_PLACES_WAGERS_EXPLANATION'
)

function Get-WO034PreflightNormalizedValue {
    param(
        [Parameter(Mandatory = $true)][string]$Key,
        [AllowEmptyString()][string]$Value
    )

    if ($Value -cne $Value.Trim()) {
        throw 'invalid owner value'
    }
    for ($index = 0; $index -lt $Value.Length; $index++) {
        $unicodeCategory = [Globalization.CharUnicodeInfo]::GetUnicodeCategory($Value, $index)
        if ($unicodeCategory -in @(
                [Globalization.UnicodeCategory]::Control,
                [Globalization.UnicodeCategory]::Format,
                [Globalization.UnicodeCategory]::LineSeparator,
                [Globalization.UnicodeCategory]::ParagraphSeparator)) {
            throw 'invalid owner value'
        }
        if ([char]::IsHighSurrogate($Value[$index])) {
            $index++
        }
    }
    if ($Value.IndexOfAny([char[]]'[]{}<>') -ge 0) {
        throw 'invalid owner value'
    }
    $secretLikePatterns = @(
        '(?i)-----BEGIN\s+(?:RSA\s+|EC\s+|OPENSSH\s+)?PRIVATE\s+KEY-----',
        '(?i)\b(?:github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16})\b',
        ('(?i)\b(?:api[-_ ]?key|access[-_ ]?token|client[-_ ]?secret|password|passwd|' +
            'private[-_ ]?key)\s*[:=]\s*\S+'),
        '\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b',
        '(?i)\bAuthorization\s*:\s*(?:Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{8,}',
        '(?i)\bBearer\s+[A-Za-z0-9._~+/=-]{16,}',
        '(?i)\b(?:Cookie|Set-Cookie)\s*:\s*[^\s=;]+=[^\s;]{8,}',
        ('(?i)\b(?:sk-(?:proj|live|test)-[A-Za-z0-9_-]{10,}|' +
            'xox[baprs]-[A-Za-z0-9-]{10,}|glpat-[A-Za-z0-9_-]{10,})\b'),
        '(?i)\b(?:session(?:id)?|auth(?:entication)?[-_ ]?token)\s*[:=]\s*\S{8,}'
    )
    foreach ($secretLikePattern in $secretLikePatterns) {
        if ([regex]::IsMatch($Value, $secretLikePattern)) {
            throw 'invalid owner value'
        }
    }
    if ($utf8.GetByteCount($Value) -gt 4096) {
        throw 'invalid owner value'
    }
    return $Value.Normalize([Text.NormalizationForm]::FormC)
}

function Test-WO034PreflightEnum {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Values,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()]
        [Collections.Generic.HashSet[string]]$InvalidValueKeys,
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string[]]$Allowed
    )

    if (-not $Values.ContainsKey($Key) -or $InvalidValueKeys.Contains($Key)) {
        return $false
    }
    return $Allowed -ccontains $Values[$Key]
}

function Test-WO034PreflightConditionalDetails {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Values,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()]
        [Collections.Generic.HashSet[string]]$InvalidValueKeys,
        [Parameter(Mandatory = $true)][string]$SelectorKey,
        [Parameter(Mandatory = $true)][string]$DetailKey,
        [Parameter(Mandatory = $true)][string]$DetailRequiredValue
    )

    if (-not $Values.ContainsKey($SelectorKey) -or
        -not $Values.ContainsKey($DetailKey) -or
        $InvalidValueKeys.Contains($SelectorKey) -or
        $InvalidValueKeys.Contains($DetailKey)) {
        return $false
    }
    $detailsPresent = -not [string]::IsNullOrWhiteSpace($Values[$DetailKey])
    return $detailsPresent -eq ($Values[$SelectorKey] -ceq $DetailRequiredValue)
}

function Test-WO034SemanticCoverage {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Description)

    if ([string]::IsNullOrWhiteSpace($Description)) {
        return $false
    }
    return $Description.Normalize([Text.NormalizationForm]::FormC) -ceq
        $expectedBettingRelatedUseDescription.Normalize([Text.NormalizationForm]::FormC)
}

$launcherSha256Match = $false
$rendererSha256Match = $false
$templateSha256Match = $false
$inputReadable = $false
$fileFormatValid = $false
$malformedLineCount = 0
$unknownKeyCount = 0
$duplicateKeyCount = 0
$formatErrorCount = 0
$enumErrorCount = 0
$conditionalErrorCount = 0
$values = @{}
$invalidValueKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$duplicateExpectedKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$invalidEnumKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$invalidConditionalKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$invalidFormatKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$expectedKeySet = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($expectedKey in $expectedKeys) {
    [void]$expectedKeySet.Add($expectedKey)
}

try {
    $launcherPath = Join-Path $PSScriptRoot 'Invoke-WO034PermissionRequest.ps1'
    $rendererPath = Join-Path $PSScriptRoot 'Render-WO034PermissionRequest.ps1'
    $templatePath = Join-Path ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))) `
        'docs\validation\J9-WO034-PERMISSION-REQUEST-PRIMARY-TEMPLATE-20260901.txt'
    $launcherBytes = [IO.File]::ReadAllBytes($launcherPath)
    $rendererBytes = [IO.File]::ReadAllBytes($rendererPath)
    $templateBytes = [IO.File]::ReadAllBytes($templatePath)
    $launcherSha256 = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData($launcherBytes)).ToLowerInvariant()
    $rendererSha256 = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData($rendererBytes)).ToLowerInvariant()
    $templateSha256 = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData($templateBytes)).ToLowerInvariant()
    $launcherSha256Match = $launcherSha256 -ceq $expectedLauncherSha256
    $rendererSha256Match = $rendererSha256 -ceq $expectedRendererSha256
    $templateSha256Match = $templateSha256 -ceq $expectedTemplateSha256
} catch {
    $launcherSha256Match = $false
    $rendererSha256Match = $false
    $templateSha256Match = $false
}

try {
    $inputBytes = [IO.File]::ReadAllBytes([IO.Path]::GetFullPath($InputPath))
    if ($inputBytes.Length -ge 3 -and $inputBytes[0] -eq 0xEF -and
        $inputBytes[1] -eq 0xBB -and $inputBytes[2] -eq 0xBF) {
        throw 'invalid encoding'
    }
    $inputText = $utf8.GetString($inputBytes)
    $inputReadable = $true
    $fileFormatValid = $true
    if ($inputText.Contains("`r", [StringComparison]::Ordinal)) {
        $fileFormatValid = $false
        $formatErrorCount++
    }
    if (-not $inputText.EndsWith("`n", [StringComparison]::Ordinal)) {
        $fileFormatValid = $false
        $formatErrorCount++
    }
} catch {
    $formatErrorCount++
    $inputText = ''
}

if ($inputReadable) {
    foreach ($rawLine in $inputText -split "`n") {
        $line = $rawLine.TrimEnd("`r")
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
            continue
        }
        $separatorIndex = $line.IndexOf('=')
        if ($separatorIndex -lt 1) {
            $malformedLineCount++
            $formatErrorCount++
            continue
        }
        $key = $line.Substring(0, $separatorIndex)
        $value = $line.Substring($separatorIndex + 1)
        if (-not $expectedKeySet.Contains($key)) {
            $unknownKeyCount++
            continue
        }
        if ($values.ContainsKey($key)) {
            $duplicateKeyCount++
            [void]$duplicateExpectedKeys.Add($key)
            continue
        }
        try {
            $values[$key] = Get-WO034PreflightNormalizedValue -Key $key -Value $value
        } catch {
            $values[$key] = $value
            [void]$invalidValueKeys.Add($key)
            [void]$invalidFormatKeys.Add($key)
            $formatErrorCount++
        }
    }
}

$missingKeyCount = @($expectedKeys | Where-Object { -not $values.ContainsKey($_) }).Count
$emptyUnconditionalKeyCount = @($unconditionalKeys | Where-Object {
        $values.ContainsKey($_) -and [string]::IsNullOrWhiteSpace($values[$_])
    }).Count
$unconditionalCompleteCount = @($unconditionalKeys | Where-Object {
        $values.ContainsKey($_) -and -not $invalidValueKeys.Contains($_) -and
        -not [string]::IsNullOrWhiteSpace($values[$_])
    }).Count

$enumRules = [ordered]@{
    OUTBOUND_CONTENT_MODE = @('PRIMARY_ONLY')
    OUTBOUND_CONTENT_MODE_OWNER_CONFIRMED = @('YES')
    LEGAL_ENTITY_TYPE = @('INDIVIDUAL', 'ASSOCIATION', 'COMPANY', 'OTHER_WITH_DETAILS')
    FORM_COMPANY_FIELD_MODE = @('OMIT', 'USE_ORGANIZATION_OR_PROJECT_OWNER', 'EXPLICIT_VALUE')
    PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE = @('OMIT', 'INCLUDE')
    INTENDED_USE_CLASSIFICATION = @('PERSONAL_RESEARCH', 'NON_COMMERCIAL', 'COMMERCIAL',
        'OTHER_WITH_DETAILS')
    END_USER_ACCESS = @('INTERNAL_ONLY', 'EXTERNAL_USERS', 'OTHER_WITH_DETAILS')
    RECEIVER_CONTROL_RELATION = @('SAME_CONTROLLER', 'THIRD_PARTY', 'OTHER_WITH_DETAILS')
    POSSIBLE_FUTURE_PROVIDER_AND_RECEIVER_COLOCATION = @('INCLUDE', 'EXCLUDE')
    PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST = @('NO', 'YES')
    MODEL_TRAINING = @('NO', 'YES_WITH_DETAILS')
    DATA_RESALE = @('NO', 'YES_WITH_DETAILS')
    PUBLIC_REDISTRIBUTION = @('NO', 'YES_WITH_DETAILS')
    DATA_USE_DECLARATIONS_SCOPE = @('RAW_ONLY', 'RAW_NORMALIZED_AND_DERIVED_DATA',
        'OTHER_WITH_DETAILS')
    LAB_ITSELF_PLACES_WAGERS = @('NO', 'YES')
}
foreach ($enumRule in $enumRules.GetEnumerator()) {
    if ($values.ContainsKey($enumRule.Key) -and
        -not $invalidValueKeys.Contains($enumRule.Key)) {
        $enumValueIsEmpty = [string]::IsNullOrWhiteSpace($values[$enumRule.Key])
        $emptyFixedContentMode = $enumRule.Key -ceq 'OUTBOUND_CONTENT_MODE' -and
            $enumValueIsEmpty
        $nonEmptyValueIsInvalid = -not $enumValueIsEmpty -and
            -not (Test-WO034PreflightEnum -Values $values `
                -InvalidValueKeys $invalidValueKeys -Key $enumRule.Key `
                -Allowed $enumRule.Value)
        if ($emptyFixedContentMode -or $nonEmptyValueIsInvalid) {
            $enumErrorCount++
            [void]$invalidEnumKeys.Add($enumRule.Key)
        }
    }
}

$conditionalRules = @(
    @('LEGAL_ENTITY_TYPE', 'LEGAL_ENTITY_TYPE_DETAILS', 'OTHER_WITH_DETAILS'),
    @('INTENDED_USE_CLASSIFICATION', 'INTENDED_USE_CLASSIFICATION_DETAILS',
        'OTHER_WITH_DETAILS'),
    @('END_USER_ACCESS', 'END_USER_ACCESS_DETAILS', 'OTHER_WITH_DETAILS'),
    @('RECEIVER_CONTROL_RELATION', 'RECEIVER_CONTROL_RELATION_DETAILS',
        'OTHER_WITH_DETAILS'),
    @('MODEL_TRAINING', 'MODEL_TRAINING_DETAILS', 'YES_WITH_DETAILS'),
    @('DATA_RESALE', 'DATA_RESALE_DETAILS', 'YES_WITH_DETAILS'),
    @('PUBLIC_REDISTRIBUTION', 'PUBLIC_REDISTRIBUTION_DETAILS', 'YES_WITH_DETAILS'),
    @('DATA_USE_DECLARATIONS_SCOPE', 'DATA_USE_DECLARATIONS_SCOPE_DETAILS',
        'OTHER_WITH_DETAILS')
)
foreach ($conditionalRule in $conditionalRules) {
    if ($values.ContainsKey($conditionalRule[0]) -and
        $values.ContainsKey($conditionalRule[1]) -and
        -not $invalidValueKeys.Contains($conditionalRule[0]) -and
        -not $invalidValueKeys.Contains($conditionalRule[1]) -and
        (Test-WO034PreflightEnum -Values $values -InvalidValueKeys $invalidValueKeys `
            -Key $conditionalRule[0] -Allowed $enumRules[$conditionalRule[0]]) -and
        -not (Test-WO034PreflightConditionalDetails -Values $values `
            -InvalidValueKeys $invalidValueKeys -SelectorKey $conditionalRule[0] `
            -DetailKey $conditionalRule[1] -DetailRequiredValue $conditionalRule[2])) {
        $conditionalErrorCount++
        [void]$invalidConditionalKeys.Add($conditionalRule[1])
    }
}

if ($values.ContainsKey('FORM_COMPANY_FIELD_MODE') -and
    $values.ContainsKey('FORM_COMPANY_VALUE') -and
    -not $invalidValueKeys.Contains('FORM_COMPANY_FIELD_MODE') -and
    -not $invalidValueKeys.Contains('FORM_COMPANY_VALUE') -and
    (Test-WO034PreflightEnum -Values $values -InvalidValueKeys $invalidValueKeys `
        -Key 'FORM_COMPANY_FIELD_MODE' -Allowed $enumRules['FORM_COMPANY_FIELD_MODE'])) {
    $companyMode = $values['FORM_COMPANY_FIELD_MODE']
    $companyValuePresent = -not [string]::IsNullOrWhiteSpace($values['FORM_COMPANY_VALUE'])
    $companyConditionValid = if ($companyMode -ceq 'EXPLICIT_VALUE') {
        $companyValuePresent
    } elseif ($companyMode -in @('OMIT', 'USE_ORGANIZATION_OR_PROJECT_OWNER')) {
        -not $companyValuePresent
    } else {
        $true
    }
    if (-not $companyConditionValid) {
        $conditionalErrorCount++
        [void]$invalidConditionalKeys.Add('FORM_COMPANY_VALUE')
    }
}

if ($values.ContainsKey('PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE') -and
    $values.ContainsKey('PROJECT_WEBSITE_OR_REPOSITORY') -and
    -not $invalidValueKeys.Contains('PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE') -and
    -not $invalidValueKeys.Contains('PROJECT_WEBSITE_OR_REPOSITORY') -and
    (Test-WO034PreflightEnum -Values $values -InvalidValueKeys $invalidValueKeys `
        -Key 'PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE' `
        -Allowed $enumRules['PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE'])) {
    $disclosure = $values['PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE']
    $repositoryValue = $values['PROJECT_WEBSITE_OR_REPOSITORY']
    $repositoryPresent = -not [string]::IsNullOrWhiteSpace($repositoryValue)
    if (($disclosure -ceq 'INCLUDE' -and -not $repositoryPresent) -or
        ($disclosure -ceq 'OMIT' -and $repositoryPresent)) {
        $conditionalErrorCount++
        [void]$invalidConditionalKeys.Add('PROJECT_WEBSITE_OR_REPOSITORY')
    } elseif ($disclosure -ceq 'INCLUDE' -and $repositoryPresent) {
        $repositoryUri = $null
        if (-not [Uri]::TryCreate($repositoryValue, [UriKind]::Absolute,
                [ref]$repositoryUri) -or $repositoryUri.Scheme -cne 'https' -or
            -not [string]::IsNullOrEmpty($repositoryUri.UserInfo) -or
            -not [string]::IsNullOrEmpty($repositoryUri.Query) -or
            -not [string]::IsNullOrEmpty($repositoryUri.Fragment)) {
            $formatErrorCount++
            [void]$invalidFormatKeys.Add('PROJECT_WEBSITE_OR_REPOSITORY')
        }
    }
}

if ($values.ContainsKey('REPLY_EMAIL') -and
    -not $invalidValueKeys.Contains('REPLY_EMAIL') -and
    -not [string]::IsNullOrWhiteSpace($values['REPLY_EMAIL'])) {
    try {
        $mailAddress = [Net.Mail.MailAddress]::new($values['REPLY_EMAIL'])
        if ($mailAddress.Address -cne $values['REPLY_EMAIL']) {
            throw 'invalid mailbox'
        }
    } catch {
        $formatErrorCount++
        [void]$invalidFormatKeys.Add('REPLY_EMAIL')
    }
}

$semanticCoverage = $false
if ($values.ContainsKey('BETTING_RELATED_USE_DESCRIPTION') -and
    -not $invalidValueKeys.Contains('BETTING_RELATED_USE_DESCRIPTION')) {
    $semanticCoverage = Test-WO034SemanticCoverage `
        -Description $values['BETTING_RELATED_USE_DESCRIPTION']
}

$preflightPass = $launcherSha256Match -and $rendererSha256Match -and
    $templateSha256Match -and $inputReadable -and $fileFormatValid -and
    $values.Count -eq $expectedKeys.Count -and $missingKeyCount -eq 0 -and
    $unknownKeyCount -eq 0 -and $duplicateKeyCount -eq 0 -and
    $malformedLineCount -eq 0 -and $unconditionalCompleteCount -eq $unconditionalKeys.Count -and
    $emptyUnconditionalKeyCount -eq 0 -and $formatErrorCount -eq 0 -and
    $enumErrorCount -eq 0 -and $conditionalErrorCount -eq 0 -and $semanticCoverage

Write-Output "WO034_INPUT_PREFLIGHT_STATUS=$(if ($preflightPass) { 'PASS' } else { 'FAIL' })"
Write-Output "FROZEN_LAUNCHER_SHA256_MATCH=$(if ($launcherSha256Match) { 'YES' } else { 'NO' })"
Write-Output "FROZEN_RENDERER_SHA256_MATCH=$(if ($rendererSha256Match) { 'YES' } else { 'NO' })"
Write-Output "FROZEN_TEMPLATE_SHA256_MATCH=$(if ($templateSha256Match) { 'YES' } else { 'NO' })"
Write-Output "INPUT_READABLE=$(if ($inputReadable) { 'YES' } else { 'NO' })"
Write-Output "INPUT_STRICT_UTF8_NO_BOM_LF_FINAL_NEWLINE=$(if ($fileFormatValid) { 'YES' } else { 'NO' })"
Write-Output "EXPECTED_KEY_COUNT=$($expectedKeys.Count)"
Write-Output "PRESENT_EXPECTED_KEY_COUNT=$($values.Count)"
Write-Output "MISSING_KEY_COUNT=$missingKeyCount"
Write-Output "MISSING_KEYS=$(@($expectedKeys | Where-Object { -not $values.ContainsKey($_) }) -join ',')"
Write-Output "UNKNOWN_KEY_COUNT=$unknownKeyCount"
Write-Output "DUPLICATE_KEY_COUNT=$duplicateKeyCount"
Write-Output "DUPLICATE_EXPECTED_KEYS=$(@($duplicateExpectedKeys | Sort-Object) -join ',')"
Write-Output "MALFORMED_LINE_COUNT=$malformedLineCount"
Write-Output "UNCONDITIONAL_KEY_COUNT=$($unconditionalKeys.Count)"
Write-Output "UNCONDITIONAL_COMPLETE_COUNT=$unconditionalCompleteCount/$($unconditionalKeys.Count)"
Write-Output "EMPTY_UNCONDITIONAL_KEY_COUNT=$emptyUnconditionalKeyCount"
Write-Output "EMPTY_UNCONDITIONAL_KEYS=$(@($unconditionalKeys | Where-Object {
            $values.ContainsKey($_) -and [string]::IsNullOrWhiteSpace($values[$_])
        }) -join ',')"
Write-Output "FORMAT_ERROR_COUNT=$formatErrorCount"
Write-Output "INVALID_FORMAT_KEYS=$(@($invalidFormatKeys | Sort-Object) -join ',')"
Write-Output "ENUM_ERROR_COUNT=$enumErrorCount"
Write-Output "INVALID_ENUM_KEYS=$(@($invalidEnumKeys | Sort-Object) -join ',')"
Write-Output "CONDITIONAL_ERROR_COUNT=$conditionalErrorCount"
Write-Output "INVALID_CONDITIONAL_KEYS=$(@($invalidConditionalKeys | Sort-Object) -join ',')"
Write-Output ("SEMANTIC_COVERAGE_EVOLVING_BETTING_PROJECT_ANALYTICS=" +
    $(if ($semanticCoverage) { 'YES' } else { 'NO' }))
Write-Output ("INVALID_SEMANTIC_KEYS=" +
    $(if ($semanticCoverage) { '' } else { 'BETTING_RELATED_USE_DESCRIPTION' }))
Write-Output 'OWNER_INPUT_VALUES_EMITTED=NO'
Write-Output 'RENDERER_EXECUTED=NO'
Write-Output 'EXTERNAL_MESSAGE_SEND_AUTHORIZED=NO'
Write-Output 'PROVIDER_NETWORK_AUTHORIZED=NO'

if (-not $preflightPass) {
    throw 'WO-034 permission input preflight failed.'
}

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$modulePath = Join-Path $repositoryRoot 'scripts\wo036\WO036-CampaignTools.psm1'
$qualificationPath = Join-Path $repositoryRoot `
    'scripts\wo044\Invoke-WO044JavaJarArgumentBoundaryQualification.ps1'
$capturePath = Join-Path $repositoryRoot `
    'scripts\wo044\Capture-WO044NativeArguments.ps1'
$moduleText = Get-Content -LiteralPath $modulePath -Raw -Encoding utf8

Import-Module $modulePath -Force

Describe 'WO-044 Java JAR native argument boundary' {
    It 'parses the module and both host qualification scripts' {
        foreach ($path in @($modulePath, $qualificationPath, $capturePath)) {
            (Test-Path -LiteralPath $path -PathType Leaf) | Should Be $true
            $tokens = $null
            $errors = $null
            [void][Management.Automation.Language.Parser]::ParseFile(
                $path, [ref]$tokens, [ref]$errors)
            $errors.Count | Should Be 0
        }
    }

    It 'quotes only the canonical JAR value passed after the exact Java guards' {
        $moduleText | Should Match 'function ConvertTo-WO036JavaJarStartProcessArgument'
        $moduleText | Should Match ([regex]::Escape(
            "'-jar', (ConvertTo-WO036JavaJarStartProcessArgument -Path `$jar)"))
        $moduleText | Should Not Match ([regex]::Escape("'-jar', `$jar)"))
        ([regex]::Matches(
                $moduleText,
                '\$script:JdkHttpClientRetryGuards\[[0-2]\]').Count) |
            Should Be 3
    }

    InModuleScope WO036-CampaignTools {
        It 'serializes canonical absolute JAR paths with and without spaces explicitly' {
            $withSpaces = [IO.Path]::GetFullPath((Join-Path `
                ([IO.Path]::GetTempPath()) 'qualification root/synthetic application.jar'))
            $withoutSpaces = [IO.Path]::GetFullPath((Join-Path `
                ([IO.Path]::GetTempPath()) 'qualification/application.jar'))
            (ConvertTo-WO036JavaJarStartProcessArgument -Path $withSpaces) |
                Should Be ('"{0}"' -f $withSpaces)
            (ConvertTo-WO036JavaJarStartProcessArgument -Path $withoutSpaces) |
                Should Be ('"{0}"' -f $withoutSpaces)
        }

        It 'rejects relative, non-JAR, quoted and control-character values fail closed' {
            $absoluteRoot = [IO.Path]::GetFullPath((Join-Path `
                ([IO.Path]::GetTempPath()) 'qualification'))
            foreach ($candidate in @(
                    'relative.jar',
                    (Join-Path $absoluteRoot 'not-a-jar.txt'),
                    (Join-Path $absoluteRoot "quoted`"value.jar"),
                    (Join-Path $absoluteRoot "control`rvalue.jar"),
                    (Join-Path $absoluteRoot "control`nvalue.jar"))) {
                $failedClosed = $false
                try {
                    [void](ConvertTo-WO036JavaJarStartProcessArgument -Path $candidate)
                }
                catch {
                    $failedClosed = $_.Exception.Message -eq
                        'WO-036 Java JAR path cannot be serialized as one exact native argument.'
                }
                $failedClosed | Should Be $true
            }
        }
    }

    It 'qualifies the exact boundary through real Windows child processes' {
        if (-not $IsWindows) {
            return
        }
        $output = @(& $qualificationPath -Iterations 2)
        ($output -contains 'WO044_HOST_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED') |
            Should Be $true
        ($output -contains 'WO044_PRE_FIX_SPLIT_ARGUMENT_REPRODUCED=YES') |
            Should Be $true
        ($output -contains
            'WO044_WINDOWS_PATH_WITH_SPACES_RECEIVED_AS_ONE_ARGUMENT=YES') |
            Should Be $true
        ($output -contains 'WO044_QUALIFIED_ITERATIONS=2') | Should Be $true
        ($output -contains 'WO044_NO_SPACE_PATH_UNCHANGED=YES') | Should Be $true
        ($output -contains 'WO044_JDK_RETRY_GUARDS=3_OF_3_PASS') | Should Be $true
        ($output -contains 'WO044_AMBIGUOUS_PATH_CASES_REJECTED=4_OF_4') |
            Should Be $true
        ($output -contains 'WO044_RESIDUAL_PROCESS_COUNT=0') | Should Be $true
        ($output -contains 'WO044_RESIDUAL_TEMP_ROOT_COUNT=0') | Should Be $true
        ($output -contains 'WO044_DATABASES_STARTED=NO') | Should Be $true
        ($output -contains 'WO044_PROVIDER_CALLS=0') | Should Be $true
        ($output -contains 'WO044_RECEIVER_HTTP_CALLS=0') | Should Be $true
        ($output -contains 'WO044_REMOTE_NETWORK_OPENED=NO') | Should Be $true
    }
}

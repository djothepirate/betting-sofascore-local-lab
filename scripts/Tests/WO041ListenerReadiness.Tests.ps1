$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$modulePath = Join-Path $repositoryRoot 'scripts\wo036\WO036-CampaignTools.psm1'

Import-Module $modulePath -Force

Describe 'WO-041 LocalLabB readiness listener gate' {
    InModuleScope WO036-CampaignTools {
        It 'reproduces the former false conflict when the listener appears between two snapshots' {
            $script:listenerReadCount = 0
            Mock Get-NetTCPConnection {
                $script:listenerReadCount++
                if ($script:listenerReadCount -eq 1) { return @() }
                [pscustomobject]@{
                    LocalAddress = '127.0.0.1'
                    LocalPort = 8087
                    OwningProcess = 41000
                }
            }

            $legacyAccepted = if (Test-WO036ExactLoopbackListener `
                    -Port 8087 -ProcessId 41000) {
                $true
            }
            else {
                $secondSnapshot = @(Get-NetTCPConnection -State Listen `
                    -LocalPort 8087 -ErrorAction SilentlyContinue)
                if ($secondSnapshot.Count -gt 0) { $false } else { $null }
            }

            $legacyAccepted | Should Be $false
            $script:listenerReadCount | Should Be 2
            Assert-MockCalled Get-NetTCPConnection 2 -Exactly -Scope It
        }

        It 'waits for the next coherent snapshot when the exact listener appears between observations' {
            $script:listenerReadCount = 0
            Mock Get-Process { [pscustomobject]@{ Id = 41001 } }
            Mock Get-NetTCPConnection {
                $script:listenerReadCount++
                if ($script:listenerReadCount -eq 1) { return @() }
                [pscustomobject]@{
                    LocalAddress = '127.0.0.1'
                    LocalPort = 8087
                    OwningProcess = 41001
                }
            }
            Mock Start-Sleep { }

            (Wait-WO036LoopbackListener -Port 8087 -ProcessId 41001) |
                Should Be $true
            $script:listenerReadCount | Should Be 2
            Assert-MockCalled Get-NetTCPConnection 2 -Exactly -Scope It
            Assert-MockCalled Start-Sleep 1 -Exactly -Scope It
        }

        It 'accepts one exact listener from the first coherent snapshot' {
            Mock Get-Process { [pscustomobject]@{ Id = 41002 } }
            Mock Get-NetTCPConnection {
                [pscustomobject]@{
                    LocalAddress = '127.0.0.1'
                    LocalPort = 8087
                    OwningProcess = 41002
                }
            }
            Mock Start-Sleep { }

            (Wait-WO036LoopbackListener -Port 8087 -ProcessId 41002) |
                Should Be $true
            Assert-MockCalled Get-NetTCPConnection 1 -Exactly -Scope It
            Assert-MockCalled Start-Sleep 0 -Exactly -Scope It
        }

        It 'rejects an exact-address listener owned by another process' {
            Mock Get-Process { [pscustomobject]@{ Id = 41003 } }
            Mock Get-NetTCPConnection {
                [pscustomobject]@{
                    LocalAddress = '127.0.0.1'
                    LocalPort = 8087
                    OwningProcess = 41999
                }
            }
            Mock Start-Sleep { }

            (Wait-WO036LoopbackListener -Port 8087 -ProcessId 41003) |
                Should Be $false
            Assert-MockCalled Get-NetTCPConnection 1 -Exactly -Scope It
            Assert-MockCalled Start-Sleep 0 -Exactly -Scope It
        }

        It 'rejects a non-loopback listener without waiting' {
            Mock Get-Process { [pscustomobject]@{ Id = 41004 } }
            Mock Get-NetTCPConnection {
                [pscustomobject]@{
                    LocalAddress = '0.0.0.0'
                    LocalPort = 8087
                    OwningProcess = 41004
                }
            }
            Mock Start-Sleep { }

            (Wait-WO036LoopbackListener -Port 8087 -ProcessId 41004) |
                Should Be $false
            Assert-MockCalled Get-NetTCPConnection 1 -Exactly -Scope It
            Assert-MockCalled Start-Sleep 0 -Exactly -Scope It
        }

        It 'rejects multiple listeners from one coherent snapshot' {
            Mock Get-Process { [pscustomobject]@{ Id = 41005 } }
            Mock Get-NetTCPConnection {
                @(
                    [pscustomobject]@{
                        LocalAddress = '127.0.0.1'
                        LocalPort = 8087
                        OwningProcess = 41005
                    },
                    [pscustomobject]@{
                        LocalAddress = '::1'
                        LocalPort = 8087
                        OwningProcess = 41005
                    }
                )
            }
            Mock Start-Sleep { }

            (Wait-WO036LoopbackListener -Port 8087 -ProcessId 41005) |
                Should Be $false
            Assert-MockCalled Get-NetTCPConnection 1 -Exactly -Scope It
            Assert-MockCalled Start-Sleep 0 -Exactly -Scope It
        }

        It 'fails before observing listeners when the registered process disappeared' {
            Mock Get-Process { $null }
            Mock Get-NetTCPConnection { throw 'must not be called' }
            Mock Start-Sleep { }

            (Wait-WO036LoopbackListener -Port 8087 -ProcessId 41006) |
                Should Be $false
            Assert-MockCalled Get-NetTCPConnection 0 -Exactly -Scope It
            Assert-MockCalled Start-Sleep 0 -Exactly -Scope It
        }

        It 'expires after the exact bounded number of empty snapshots' {
            Mock Get-Process { [pscustomobject]@{ Id = 41007 } }
            Mock Get-NetTCPConnection { @() }
            Mock Start-Sleep { }

            (Wait-WO036LoopbackListener -Port 8087 -ProcessId 41007) |
                Should Be $false
            Assert-MockCalled Get-NetTCPConnection 60 -Exactly -Scope It
            Assert-MockCalled Start-Sleep 60 -Exactly -Scope It
        }
    }
}

Describe 'WO-041 host qualification surface' {
    $repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $qualificationPath = Join-Path $repositoryRoot `
        'scripts\wo041\Invoke-WO041ListenerGateQualification.ps1'
    $listenerPath = Join-Path $repositoryRoot `
        'scripts\wo041\Open-WO041DelayedLoopbackListener.ps1'
    $qualificationText = Get-Content -LiteralPath $qualificationPath -Raw
    $listenerText = Get-Content -LiteralPath $listenerPath -Raw

    It 'parses the two host qualification scripts without errors' {
        foreach ($path in @($qualificationPath, $listenerPath)) {
            $tokens = $null
            $errors = $null
            [void][Management.Automation.Language.Parser]::ParseFile(
                $path, [ref]$tokens, [ref]$errors)
            $errors.Count | Should Be 0
        }
    }

    It 'uses a hidden owned child and refuses PID-only cleanup' {
        $qualificationText | Should Match '-WindowStyle Hidden'
        $qualificationText | Should Match 'Get-CimInstance Win32_Process'
        $qualificationText | Should Match 'StartTimeUtc'
        $qualificationText | Should Match 'InstanceToken'
        $qualificationText | Should Match 'refused to terminate a process without exact ownership proof'
        $listenerText | Should Match "Parse\('127\.0\.0\.1'\)"
    }

    It 'contains no provider, receiver, remote or non-loopback target' {
        ($qualificationText + $listenerText) | Should Not Match '(?i)api\.sofascore\.com'
        ($qualificationText + $listenerText) | Should Not Match '51\.255\.167\.32'
        ($qualificationText + $listenerText) | Should Not Match '0\.0\.0\.0'
        ($qualificationText + $listenerText) | Should Not Match '8444'
        ($qualificationText + $listenerText) | Should Match '127\.0\.0\.1'
    }
}

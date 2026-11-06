<#
.SYNOPSIS
    Запускает тесты TestNG и Playwright.

.EXAMPLE
    .\test.ps1                  # API-тесты (TestNG)
    .\test.ps1 -Suite e2e       # Playwright Java
    .\test.ps1 -Suite all       # всё сразу
    .\test.ps1 -Suite e2e -Headed
#>
[CmdletBinding()]
param(
    [ValidateSet('api', 'e2e', 'all')]
    [string]$Suite = 'api',

    [switch]$Headed
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Push-Location $root
try {
    . (Join-Path $root 'scripts\use-java.ps1') | Out-Null

    $mavenArguments = @('test')
    switch ($Suite) {
        'e2e' { $mavenArguments += '-Pe2e' }
        'all' { $mavenArguments += '-Pall-tests' }
    }
    if ($Headed) {
        $mavenArguments += '-Dplaywright.headed=true'
    }

    # Maven пишет предупреждения JVM в stderr; при ErrorActionPreference=Stop
    # PowerShell 5.1 считает это ошибкой, поэтому на время вызова ослабляем режим.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & (Join-Path $root 'mvnw.cmd') @mavenArguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
}
finally {
    Pop-Location
}

exit $exitCode

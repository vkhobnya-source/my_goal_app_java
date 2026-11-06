<#
.SYNOPSIS
    Скачивает браузер Chromium, нужный Playwright Java.

.EXAMPLE
    .\scripts\install-playwright.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

Push-Location $root
try {
    . (Join-Path $root 'scripts\use-java.ps1') | Out-Null

    $mavenArguments = @(
        'exec:java',
        '-Dexec.mainClass=com.microsoft.playwright.CLI',
        '-Dexec.classpathScope=test',
        '-Dexec.args=install chromium'
    )

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

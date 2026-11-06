<#
.SYNOPSIS
    Запускает приложение без Docker.

.EXAMPLE
    .\run.ps1
    .\run.ps1 -Port 8081
    .\run.ps1 -Database .\data\goals.db
#>
[CmdletBinding()]
param(
    [int]$Port = 8080,
    [string]$Database
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Push-Location $root
try {
    . (Join-Path $root 'scripts\use-java.ps1') | Out-Null

    $applicationArguments = "--server.port=$Port"
    if ($Database) {
        $applicationArguments += " --app.database.path=$Database"
    }

    # Maven пишет предупреждения JVM в stderr; при ErrorActionPreference=Stop
    # PowerShell 5.1 считает это ошибкой, поэтому на время вызова ослабляем режим.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & (Join-Path $root 'mvnw.cmd') spring-boot:run "-Dspring-boot.run.arguments=$applicationArguments"
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

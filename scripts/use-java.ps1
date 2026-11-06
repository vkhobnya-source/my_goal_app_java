<#
.SYNOPSIS
    Находит JDK 25+ и выставляет JAVA_HOME/PATH для текущего процесса.

.DESCRIPTION
    Можно подключить к своей сессии:
        . .\scripts\use-java.ps1

    Или просто запустить, чтобы узнать найденный JDK:
        .\scripts\use-java.ps1

    Порядок поиска: параметр -JavaHome, переменная JAVA_HOME, каталоги
    C:\Program Files\Java, JBR из IntelliJ IDEA (обычная установка и Toolbox).
#>
[CmdletBinding()]
param(
    [string]$JavaHome
)

function Get-JavaMajorVersion {
    param([string]$JavaHomePath)

    $java = Join-Path $JavaHomePath 'bin\java.exe'
    if (-not (Test-Path $java)) {
        return $null
    }

    # java -version пишет в stderr; при ErrorActionPreference=Stop это
    # превратилось бы в исключение, поэтому читаем вывод в режиме Continue.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $firstLine = & $java -version 2>&1 | Select-Object -First 1
    }
    finally {
        $ErrorActionPreference = $previous
    }

    if ($firstLine -match '"(\d+)') {
        return [int]$Matches[1]
    }
    return $null
}

function Resolve-Jdk {
    param(
        [string]$RequestedHome,
        [int]$MinimumVersion
    )

    $candidates = [System.Collections.Generic.List[string]]::new()

    if ($RequestedHome) {
        $candidates.Add($RequestedHome)
    }
    if ($env:JAVA_HOME) {
        $candidates.Add($env:JAVA_HOME)
    }

    $searchRoots = @(
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $env:ProgramFiles 'JetBrains'),
        (Join-Path $env:LOCALAPPDATA 'Programs')
    )

    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) {
            continue
        }
        Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $candidates.Add($_.FullName)
            $candidates.Add((Join-Path $_.FullName 'jbr'))
        }
    }

    $toolbox = Join-Path $env:LOCALAPPDATA 'JetBrains\Toolbox\apps'
    if (Test-Path $toolbox) {
        Get-ChildItem -Path $toolbox -Directory -Filter 'IDEA-*' -ErrorAction SilentlyContinue |
            ForEach-Object {
                Get-ChildItem -Path $_.FullName -Directory -Recurse -Filter 'jbr' -Depth 2 -ErrorAction SilentlyContinue |
                    ForEach-Object { $candidates.Add($_.FullName) }
            }
    }

    foreach ($candidate in $candidates) {
        $version = Get-JavaMajorVersion -JavaHomePath $candidate
        if ($version -ne $null -and $version -ge $MinimumVersion) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

$resolvedHome = Resolve-Jdk -RequestedHome $JavaHome -MinimumVersion 25
if (-not $resolvedHome) {
    throw "JDK 25+ не найден. Установите JDK или укажите путь: .\scripts\use-java.ps1 -JavaHome 'C:\path\to\jdk'"
}

$env:JAVA_HOME = $resolvedHome
$env:PATH = "$resolvedHome\bin;$env:PATH"
Write-Output "JAVA_HOME=$resolvedHome"

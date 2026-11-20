$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$base = Join-Path $env:USERPROFILE '.gradle\local-agent-bridge'
$target = Join-Path $base 'gradle-8.9'
if (-not (Test-Path (Join-Path $target 'bin\gradle.bat'))) {
    New-Item -ItemType Directory -Path $base -Force | Out-Null
    $archive = Join-Path $base 'gradle-8.9-bin.zip'
    Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $base -Force
    Remove-Item $archive -Force
}

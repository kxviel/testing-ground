$version = "4.0.0"
$zip = ".\releases\GameBox-$version-windows.zip"
$runtime = ".\runtime"

if (Test-Path $runtime) {
    Remove-Item -LiteralPath $runtime -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $runtime | Out-Null

Expand-Archive -LiteralPath $zip -DestinationPath $runtime -Force

Set-Location $runtime

.\bin\GameBox.bat

$version = "2.5.0"
$zip = ".\releases\GameBox-$version-windows.zip"

New-Item -ItemType Directory -Force .\releases | Out-Null

if (Test-Path $zip) {
  Remove-Item -LiteralPath $zip -Force
}

Compress-Archive -Path ".\target\gamebox\*" -DestinationPath $zip -CompressionLevel Optimal

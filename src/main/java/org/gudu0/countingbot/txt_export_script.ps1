$source = Get-Location
$dest = Join-Path $source "txt_export"

New-Item -ItemType Directory -Path $dest -Force | Out-Null

Get-ChildItem -Recurse -Filter *.java -File | ForEach-Object {
    $relative = $_.FullName.Substring($source.Path.Length).TrimStart('\')
    $target = Join-Path $dest $relative
    $target = [System.IO.Path]::ChangeExtension($target, ".txt")

    New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
    Copy-Item $_.FullName $target
}

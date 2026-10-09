param(
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [string]$Device = "emulator-5554"
)
$ErrorActionPreference = "Stop"
android screen capture --device $Device -o $OutputPath
if ($LASTEXITCODE -ne 0) { throw "Android CLI screen capture failed" }
$resolvedPath = (Resolve-Path -LiteralPath $OutputPath).Path
$bytes = [IO.File]::ReadAllBytes($resolvedPath)
# Resizable emulator warnings can precede the PNG on stdout in Android CLI 1.0.
# Preserve the actual PNG bytes; this does not alter the image.
for ($offset = 0; $offset -lt $bytes.Length - 8; $offset++) {
    if ($bytes[$offset] -eq 137 -and $bytes[$offset+1] -eq 80 -and $bytes[$offset+2] -eq 78 -and $bytes[$offset+3] -eq 71) {
        if ($offset -gt 0) { [IO.File]::WriteAllBytes($resolvedPath, $bytes[$offset..($bytes.Length-1)]) }
        return
    }
}
throw "Android CLI did not return a PNG"

param(
    [switch]$Json
)

$ErrorActionPreference = "Stop"

function Test-File {
    param(
        [string]$Path,
        [string]$Label,
        [string]$RequiredFor
    )

    $exists = Test-Path -LiteralPath $Path -PathType Leaf
    [pscustomobject]@{
        label = $Label
        path = $Path
        requiredFor = $RequiredFor
        status = if ($exists) { "pass" } else { "missing" }
    }
}

function Test-Directory {
    param(
        [string]$Path,
        [string]$Label,
        [string]$RequiredFor
    )

    $exists = Test-Path -LiteralPath $Path -PathType Container
    [pscustomobject]@{
        label = $Label
        path = $Path
        requiredFor = $RequiredFor
        status = if ($exists) { "pass" } else { "missing" }
    }
}

function Test-KeystoreProperties {
    $path = "keystore.properties"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return [pscustomobject]@{
            label = "Release signing config"
            path = $path
            requiredFor = "Play upload"
            status = "missing"
            detail = "Copy keystore.properties.example to keystore.properties and fill local secrets."
        }
    }

    $content = Get-Content -LiteralPath $path
    $required = @(
        "STACK_RELEASE_STORE_FILE",
        "STACK_RELEASE_STORE_PASSWORD",
        "STACK_RELEASE_KEY_ALIAS",
        "STACK_RELEASE_KEY_PASSWORD"
    )
    $missing = @()
    foreach ($key in $required) {
        if (-not ($content | Select-String -Pattern "^$key=" -Quiet)) {
            $missing += $key
        }
    }

    [pscustomobject]@{
        label = "Release signing config"
        path = $path
        requiredFor = "Play upload"
        status = if ($missing.Count -eq 0) { "pass" } else { "incomplete" }
        detail = if ($missing.Count -eq 0) { "All expected keys are present." } else { "Missing: $($missing -join ', ')" }
    }
}

$checks = @(
    Test-File "app\google-services.json" "Firebase Android config" "Live Firebase/Analytics/Firestore"
    Test-KeystoreProperties
    Test-File "app\build\outputs\bundle\release\app-release.aab" "Release AAB" "Play upload"
    Test-File "app\build\outputs\apk\debug\app-debug.apk" "Debug APK" "Local testing"
    Test-File "firebase.json" "Firebase project config" "Firebase deploy"
    Test-File "firebase\firestore.rules" "Firestore rules" "Firebase deploy"
    Test-File "firebase\firestore.indexes.json" "Firestore indexes" "Firebase deploy"
    Test-File "firebase\functions\src\index.ts" "Firebase functions source" "Firebase deploy"
    Test-Directory "app\schemas" "Room schema exports" "Migration review"
    Test-File "firebase\hosting\public\privacy.html" "Hosted privacy policy source" "Play app content"
    Test-File "firebase\remoteconfig.template.json" "Remote defaults" "Feature availability"
    Test-File "app\build\outputs\apk\release\app-release.apk" "Release APK" "Physical device testing"
    Test-File "media\screenshots\home-phone.png" "Home screenshot" "Product presentation"
    Test-File "media\screenshots\play-phone.png" "Gameplay screenshot" "Product presentation"
    Test-File "media\screenshots\play-unfolded.png" "Unfolded screenshot" "Adaptive layout review"
)

if ($Json) {
    $checks | ConvertTo-Json -Depth 4
    exit 0
}

$failed = $checks | Where-Object { $_.status -ne "pass" }

Write-Host "Stack release readiness"
Write-Host "======================="
foreach ($check in $checks) {
    $marker = if ($check.status -eq "pass") { "[OK]" } elseif ($check.status -eq "incomplete") { "[!!]" } else { "[--]" }
    Write-Host "$marker $($check.label) - $($check.status)"
    Write-Host "     $($check.requiredFor): $($check.path)"
    if ($check.PSObject.Properties.Name -contains "detail" -and $check.detail) {
        Write-Host "     $($check.detail)"
    }
}

Write-Host ""
if ($failed.Count -eq 0) {
    Write-Host "Ready for Play upload checklist review."
    exit 0
}

Write-Host "Missing/incomplete items: $($failed.Count)"
foreach ($item in $failed) {
    Write-Host "- $($item.label): $($item.status)"
}
exit 1

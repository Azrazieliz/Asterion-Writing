<#
Persistent Windows signing for the ORIGINAL Asterion Writer package.
Never overwrite the installed app with a different-signature APK.
The original v0.4 CI debug key was lost; one-time verified backup/restore
is required to move v0.4 data before installing this new signing lineage.
#>
param(
    [Parameter(Mandatory=$true)][string]$UnsignedApk,
    [Parameter(Mandatory=$true)][string]$OutputApk,
    [string]$Keystore = "$HOME\.android\debug.keystore"
)
$ErrorActionPreference = 'Stop'
$src = (Resolve-Path -LiteralPath $UnsignedApk).Path
if (-not (Test-Path -LiteralPath $Keystore -PathType Leaf)) { throw 'Persistent local debug keystore missing' }
if (Test-Path -LiteralPath $OutputApk) { throw 'Refusing to overwrite previously signed APK' }
$tools = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools'
$signer = Get-ChildItem -Path $tools -Recurse -File -Filter 'apksigner.bat' -ErrorAction Stop |
    Sort-Object FullName -Descending | Select-Object -First 1
if ($null -eq $signer) { throw 'Android apksigner was not found' }
$dstDir = Split-Path $OutputApk -Parent
if ($dstDir) { New-Item -Path $dstDir -ItemType Directory -Force | Out-Null }
$aapt = Join-Path $signer.Directory.FullName 'aapt.exe'
if (Test-Path $aapt) {
    $badge = & $aapt dump badging $src 2>&1 | Select-String '^package:'
    if (-not ($badge -match "name='com.asterion.writing.bench'")) {
        throw 'Unexpected application ID; refusing to sign'
    }
}
& $signer.FullName sign --ks $Keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $OutputApk $src
if ($LASTEXITCODE -ne 0) { throw 'Signing failed' }
$verify = & $signer.FullName verify --verbose --print-certs $OutputApk 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Android signature verification failed' }
$certificate = $verify | Where-Object { $_ -match 'Signer #1 certificate SHA-256 digest:' } | Select-Object -First 1
$checksum = (Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash.ToLowerInvariant()
[ordered]@{
    output = $OutputApk
    sha256 = $checksum
    certificate = "$certificate"
    signing_key = 'persistent-local-debug-keystore'
    package = 'com.asterion.writing.bench'
    installation_authorized = $false
    reason = 'Existing v0.4 signature differs; preserve and back up user data first'
} | ConvertTo-Json -Depth 3 | Set-Content ($OutputApk + '.receipt.json') -Encoding UTF8
Write-Output 'Signed and verified. INSTALLATION BLOCKED until private app data is backed up and migration approved.'

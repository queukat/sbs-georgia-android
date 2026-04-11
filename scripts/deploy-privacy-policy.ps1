param(
    [string]$ProjectName = "sbs-georgia-privacy",
    [string]$SitePath = "play-privacy-site",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Path $PSCommandPath -Parent
$repoRoot = Split-Path -Path $scriptRoot -Parent

if (-not [System.IO.Path]::IsPathRooted($SitePath)) {
    $SitePath = Join-Path $repoRoot $SitePath
}

if (-not (Test-Path $SitePath)) {
    throw "Site path '$SitePath' does not exist."
}

Write-Host "Deploying privacy policy site from '$SitePath' to Cloudflare Pages project '$ProjectName'..."

if ($DryRun) {
    Write-Host "Dry run only. Command not executed."
    exit 0
}

wrangler pages deploy $SitePath --project-name $ProjectName

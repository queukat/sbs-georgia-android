param(
    [Parameter(Mandatory = $true)]
    [string]$SourcePath
)

Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"
$workspace = Split-Path -Parent $PSScriptRoot
$drawableDir = Join-Path $workspace "app\src\main\res\drawable-nodpi"

New-Item -ItemType Directory -Force -Path $drawableDir | Out-Null

function Save-Crop {
    param(
        [System.Drawing.Image] $Bitmap,
        [int] $X,
        [int] $Y,
        [int] $Width,
        [int] $Height,
        [string] $TargetPath
    )

    $cropRect = New-Object System.Drawing.Rectangle($X, $Y, $Width, $Height)
    $target = New-Object System.Drawing.Bitmap($Width, $Height)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($target)
        try {
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.DrawImage(
                $Bitmap,
                (New-Object System.Drawing.Rectangle(0, 0, $Width, $Height)),
                $cropRect,
                [System.Drawing.GraphicsUnit]::Pixel
            )
        } finally {
            $graphics.Dispose()
        }
        $target.Save($TargetPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $target.Dispose()
    }
}

$source = [System.Drawing.Image]::FromFile($SourcePath)
try {
    Save-Crop -Bitmap $source -X 80 -Y 335 -Width 690 -Height 260 -TargetPath (Join-Path $drawableDir 'brand_wordmark.png')
    Save-Crop -Bitmap $source -X 1030 -Y 290 -Width 320 -Height 320 -TargetPath (Join-Path $drawableDir 'brand_mark.png')
} finally {
    $source.Dispose()
}

& (Join-Path $PSScriptRoot "generate_launcher_assets.ps1")

param(
    [string]$OutputPath = "app/src/main/ic_launcher-playstore.png"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$sourcePath = (Resolve-Path "app/src/main/res/drawable-nodpi/brand_mark.png").Path
$outputFile = Join-Path (Get-Location) $OutputPath
$outputDir = Split-Path -Parent $outputFile
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$source = [System.Drawing.Bitmap]::FromFile($sourcePath)
$size = 512
$bitmap = New-Object System.Drawing.Bitmap($size, $size)

try {
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.Clear([System.Drawing.Color]::White)

        $drawSize = 408
        $offset = [int](($size - $drawSize) / 2)
        $graphics.DrawImage($source, $offset, $offset, $drawSize, $drawSize)
    } finally {
        $graphics.Dispose()
    }

    for ($x = 0; $x -lt $bitmap.Width; $x++) {
        for ($y = 0; $y -lt $bitmap.Height; $y++) {
            $pixel = $bitmap.GetPixel($x, $y)
            $maxChannel = [Math]::Max($pixel.R, [Math]::Max($pixel.G, $pixel.B))
            $minChannel = [Math]::Min($pixel.R, [Math]::Min($pixel.G, $pixel.B))
            $avg = ($pixel.R + $pixel.G + $pixel.B) / 3
            $isNearNeutralLight = (($maxChannel - $minChannel) -le 36 -and $avg -ge 120)
            $isVeryLight = ($pixel.R -ge 215 -and $pixel.G -ge 215 -and $pixel.B -ge 215)
            if ($pixel.A -lt 255 -or $isVeryLight -or $isNearNeutralLight) {
                $bitmap.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, 255, 255, 255))
            }
        }
    }

    $bitmap.Save($outputFile, [System.Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $source.Dispose()
    $bitmap.Dispose()
}

$image = [System.Drawing.Image]::FromFile($outputFile)
try {
    [pscustomobject]@{
        FullName = $outputFile
        Width = $image.Width
        Height = $image.Height
        Length = (Get-Item $outputFile).Length
    }
} finally {
    $image.Dispose()
}

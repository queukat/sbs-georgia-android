Add-Type -AssemblyName System.Drawing

$sourcePath = 'C:/Users/User/Downloads/ChatGPT Image 3 апр. 2026 г., 01_41_49.png'
$drawableDir = 'C:/Users/User/Desktop/sbsgeorgiaandroid/app/src/main/res/drawable-nodpi'
$resDir = 'C:/Users/User/Desktop/sbsgeorgiaandroid/app/src/main/res'

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

function Save-Scaled {
    param(
        [System.Drawing.Image] $Bitmap,
        [int] $Size,
        [string] $TargetPath
    )

    $target = New-Object System.Drawing.Bitmap($Size, $Size)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($target)
        try {
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.DrawImage($Bitmap, 0, 0, $Size, $Size)
        } finally {
            $graphics.Dispose()
        }
        $target.Save($TargetPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $target.Dispose()
    }
}

$source = [System.Drawing.Image]::FromFile($sourcePath)
try {
    Save-Crop -Bitmap $source -X 80 -Y 335 -Width 690 -Height 260 -TargetPath (Join-Path $drawableDir 'brand_wordmark.png')
    Save-Crop -Bitmap $source -X 1030 -Y 290 -Width 320 -Height 320 -TargetPath (Join-Path $drawableDir 'brand_mark.png')
} finally {
    $source.Dispose()
}

$mark = [System.Drawing.Image]::FromFile((Join-Path $drawableDir 'brand_mark.png'))
try {
    $sizes = @{
        'mipmap-mdpi' = 48
        'mipmap-hdpi' = 72
        'mipmap-xhdpi' = 96
        'mipmap-xxhdpi' = 144
        'mipmap-xxxhdpi' = 192
    }
    foreach ($entry in $sizes.GetEnumerator()) {
        $dir = Join-Path $resDir $entry.Key
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        Save-Scaled -Bitmap $mark -Size $entry.Value -TargetPath (Join-Path $dir 'ic_launcher.png')
        Save-Scaled -Bitmap $mark -Size $entry.Value -TargetPath (Join-Path $dir 'ic_launcher_round.png')
    }
} finally {
    $mark.Dispose()
}

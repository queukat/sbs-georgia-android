Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"
$workspace = Split-Path -Parent $PSScriptRoot
$resDir = Join-Path $workspace "app\src\main\res"
$densities = [ordered]@{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

function New-RoundedRectanglePath {
    param(
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height,
        [float]$Radius
    )

    $diameter = $Radius * 2
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
    $path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
    $path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-RightGlyphPath {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $path.StartFigure()
    $path.AddLine(178, 86, 243, 86)
    $path.AddLine(243, 86, 243, 199)
    $path.AddBezier(243, 199, 243, 222, 225, 240, 202, 240)
    $path.AddLine(202, 240, 178, 240)
    $path.AddLine(178, 240, 178, 86)
    $path.CloseFigure()
    return $path
}

function Save-LauncherIcon {
    param(
        [int]$Size,
        [bool]$Round,
        [string]$TargetPath
    )

    $renderSize = $Size * 4
    $render = [System.Drawing.Bitmap]::new($renderSize, $renderSize)
    $output = [System.Drawing.Bitmap]::new($Size, $Size)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($render)
        try {
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.ScaleTransform($renderSize / 320.0, $renderSize / 320.0)

            $surface = [System.Drawing.ColorTranslator]::FromHtml("#F5F7F7")
            $accent = [System.Drawing.ColorTranslator]::FromHtml("#168678")
            $surfaceBrush = [System.Drawing.SolidBrush]::new($surface)
            $accentBrush = [System.Drawing.SolidBrush]::new($accent)
            try {
                if ($Round) {
                    $graphics.FillEllipse($surfaceBrush, 22, 22, 276, 276)
                } else {
                    $plate = New-RoundedRectanglePath -X 22 -Y 22 -Width 276 -Height 276 -Radius 58
                    try {
                        $graphics.FillPath($surfaceBrush, $plate)
                    } finally {
                        $plate.Dispose()
                    }
                }

                $graphics.FillRectangle($accentBrush, 70, 86, 95, 41)
                $graphics.FillRectangle($accentBrush, 70, 140, 43, 19)
                $graphics.FillRectangle($accentBrush, 70, 170, 43, 19)
                $graphics.FillRectangle($accentBrush, 70, 214, 43, 26)
                $graphics.FillRectangle($accentBrush, 124, 140, 41, 19)
                $graphics.FillRectangle($accentBrush, 124, 170, 41, 70)

                $rightGlyph = New-RightGlyphPath
                try {
                    $graphics.FillPath($accentBrush, $rightGlyph)
                } finally {
                    $rightGlyph.Dispose()
                }
                $graphics.FillRectangle($surfaceBrush, 201, 107, 22, 23)
                $graphics.FillRectangle($surfaceBrush, 194, 194, 23, 23)
            } finally {
                $surfaceBrush.Dispose()
                $accentBrush.Dispose()
            }
        } finally {
            $graphics.Dispose()
        }

        $outputGraphics = [System.Drawing.Graphics]::FromImage($output)
        try {
            $outputGraphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $outputGraphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $outputGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $outputGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $outputGraphics.DrawImage($render, 0, 0, $Size, $Size)
        } finally {
            $outputGraphics.Dispose()
        }
        $output.Save($TargetPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $render.Dispose()
        $output.Dispose()
    }
}

foreach ($entry in $densities.GetEnumerator()) {
    $targetDir = Join-Path $resDir $entry.Key
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    Save-LauncherIcon -Size $entry.Value -Round $false -TargetPath (Join-Path $targetDir "ic_launcher.png")
    Save-LauncherIcon -Size $entry.Value -Round $true -TargetPath (Join-Path $targetDir "ic_launcher_round.png")
}

Write-Host "Generated legacy launcher icons in $resDir"

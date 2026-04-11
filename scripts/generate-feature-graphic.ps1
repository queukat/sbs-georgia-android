param(
    [string]$OutputPath = "artifacts/play/feature-graphic-en-US.png"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function New-RoundedRectPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $diameter = $r * 2
    $path.AddArc($x, $y, $diameter, $diameter, 180, 90)
    $path.AddArc($x + $w - $diameter, $y, $diameter, $diameter, 270, 90)
    $path.AddArc($x + $w - $diameter, $y + $h - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($x, $y + $h - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function Draw-RoundedImage($graphics, $image, [float]$x, [float]$y, [float]$w, [float]$h, [float]$radius) {
    $path = New-RoundedRectPath $x $y $w $h $radius
    $state = $graphics.Save()
    $graphics.SetClip($path)
    $graphics.DrawImage($image, [System.Drawing.RectangleF]::new($x, $y, $w, $h))
    $graphics.Restore($state)
    $borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(40, 255, 255, 255), 1.4)
    $graphics.DrawPath($borderPen, $path)
    $borderPen.Dispose()
    $path.Dispose()
}

function New-FittedFont(
    $graphics,
    [string]$fontFamily,
    [float]$startingSize,
    [System.Drawing.FontStyle]$style,
    [string]$text,
    [float]$maxWidth,
    [float]$maxHeight
) {
    $size = $startingSize
    while ($size -gt 10) {
        $font = New-Object System.Drawing.Font($fontFamily, $size, $style)
        $measured = $graphics.MeasureString($text, $font, [int][Math]::Ceiling($maxWidth))
        if ($measured.Width -le $maxWidth -and $measured.Height -le $maxHeight) {
            return $font
        }
        $font.Dispose()
        $size -= 1
    }
    return New-Object System.Drawing.Font($fontFamily, 10, $style)
}

function Draw-PhoneFrame($graphics, $image, [float]$x, [float]$y, [float]$w, [float]$h, [float]$angle) {
    $state = $graphics.Save()
    $graphics.TranslateTransform($x + ($w / 2), $y + ($h / 2))
    $graphics.RotateTransform($angle)

    $shadowPath = New-RoundedRectPath (-$w / 2 + 6) (-$h / 2 + 10) $w $h 28
    $shadowBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(72, 0, 0, 0))
    $graphics.FillPath($shadowBrush, $shadowPath)
    $shadowBrush.Dispose()
    $shadowPath.Dispose()

    $outerPath = New-RoundedRectPath (-$w / 2) (-$h / 2) $w $h 28
    $outerBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 18, 26, 33))
    $graphics.FillPath($outerBrush, $outerPath)
    $outerBrush.Dispose()

    $screenX = -$w / 2 + 10
    $screenY = -$h / 2 + 10
    $screenW = $w - 20
    $screenH = $h - 20
    Draw-RoundedImage $graphics $image $screenX $screenY $screenW $screenH 20

    $notchBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 18, 26, 33))
    $graphics.FillRectangle($notchBrush, -28, -$h / 2 + 8, 56, 8)
    $notchBrush.Dispose()

    $framePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(54, 255, 255, 255), 1.2)
    $graphics.DrawPath($framePen, $outerPath)
    $framePen.Dispose()
    $outerPath.Dispose()

    $graphics.Restore($state)
}

function Get-AppName() {
    [xml]$stringsXml = Get-Content (Resolve-Path "app/src/main/res/values/strings.xml")
    $appName = $stringsXml.resources.string | Where-Object { $_.name -eq "app_name" } | Select-Object -First 1
    if ($null -eq $appName -or [string]::IsNullOrWhiteSpace($appName.'#text')) {
        throw "Unable to resolve app_name from app/src/main/res/values/strings.xml"
    }
    return $appName.'#text'
}

$outputFile = Join-Path (Get-Location) $OutputPath
$outputDir = Split-Path -Parent $outputFile
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$canvasWidth = 1024
$canvasHeight = 500
$bitmap = New-Object System.Drawing.Bitmap($canvasWidth, $canvasHeight)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$backgroundRect = New-Object System.Drawing.Rectangle(0, 0, $canvasWidth, $canvasHeight)
$backgroundBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $backgroundRect,
    [System.Drawing.Color]::FromArgb(255, 10, 24, 31),
    [System.Drawing.Color]::FromArgb(255, 18, 74, 79),
    18
)
$graphics.FillRectangle($backgroundBrush, $backgroundRect)
$backgroundBrush.Dispose()

foreach ($shape in @(
    @{ X = -80; Y = -50; W = 300; H = 300; Color = [System.Drawing.Color]::FromArgb(34, 87, 211, 189) },
    @{ X = 120; Y = 300; W = 260; H = 260; Color = [System.Drawing.Color]::FromArgb(26, 255, 255, 255) },
    @{ X = 760; Y = -40; W = 320; H = 320; Color = [System.Drawing.Color]::FromArgb(24, 145, 235, 224) }
)) {
    $shapeBrush = New-Object System.Drawing.SolidBrush($shape.Color)
    $graphics.FillEllipse($shapeBrush, $shape.X, $shape.Y, $shape.W, $shape.H)
    $shapeBrush.Dispose()
}

$panelX = 34
$panelY = 34
$panelWidth = 438
$panelHeight = 432
$panelPath = New-RoundedRectPath $panelX $panelY $panelWidth $panelHeight 32
$panelBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(34, 255, 255, 255))
$panelPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(46, 255, 255, 255), 1.3)
$graphics.FillPath($panelBrush, $panelPath)
$graphics.DrawPath($panelPen, $panelPath)
$panelBrush.Dispose()
$panelPen.Dispose()
$panelPath.Dispose()

$brandMarkPath = (Resolve-Path "app/src/main/res/drawable-nodpi/brand_mark.png").Path
$brandMark = [System.Drawing.Image]::FromFile($brandMarkPath)
$iconShadowBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(60, 0, 0, 0))
$graphics.FillEllipse($iconShadowBrush, 76, 88, 116, 116)
$iconShadowBrush.Dispose()
$graphics.DrawImage($brandMark, 68, 78, 116, 116)

$whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(245, 255, 255, 255))
$mutedBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(214, 228, 236, 241))
$accentBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 161, 238, 227))
$footerBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(168, 228, 240, 245))

$textFormat = New-Object System.Drawing.StringFormat
$textFormat.Alignment = [System.Drawing.StringAlignment]::Near
$textFormat.LineAlignment = [System.Drawing.StringAlignment]::Near
$textFormat.Trimming = [System.Drawing.StringTrimming]::Word
$textFormat.FormatFlags = [System.Drawing.StringFormatFlags]::LineLimit

$appName = Get-AppName
$bodyText = "Track income, official FX to GEL, monthly declaration values, payment details, and reminders in one offline-first Android app."
$accentText = "Built for Georgian small business tax workflow"
$leftInset = 58
$textWidth = 380

$titleFont = New-FittedFont $graphics "Segoe UI Semibold" 38 ([System.Drawing.FontStyle]::Bold) $appName $textWidth 60
$bodyFont = New-FittedFont $graphics "Segoe UI" 18 ([System.Drawing.FontStyle]::Regular) $bodyText $textWidth 126
$accentFont = New-FittedFont $graphics "Segoe UI Semibold" 16 ([System.Drawing.FontStyle]::Bold) $accentText $textWidth 42
$footerFont = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Regular)

$graphics.DrawString($appName, $titleFont, $whiteBrush, [System.Drawing.RectangleF]::new($leftInset, 208, $textWidth, 60), $textFormat)
$graphics.DrawString(
    $bodyText,
    $bodyFont,
    $mutedBrush,
    [System.Drawing.RectangleF]::new($leftInset, 266, $textWidth, 126),
    $textFormat
)
$graphics.DrawString(
    $accentText,
    $accentFont,
    $accentBrush,
    [System.Drawing.RectangleF]::new($leftInset, 396, $textWidth, 42),
    $textFormat
)

$shot1 = [System.Drawing.Image]::FromFile((Resolve-Path "artifacts/play-screenshots/en-US/02-home-dashboard.png").Path)
$shot2 = [System.Drawing.Image]::FromFile((Resolve-Path "artifacts/play-screenshots/en-US/04-month-detail-copy-tools.png").Path)
$shot3 = [System.Drawing.Image]::FromFile((Resolve-Path "artifacts/play-screenshots/en-US/05-import-preview.png").Path)

$phoneWidth = 176
$phoneHeight = 352
Draw-PhoneFrame $graphics $shot1 548 96 $phoneWidth $phoneHeight -8
Draw-PhoneFrame $graphics $shot2 700 66 $phoneWidth $phoneHeight 0
Draw-PhoneFrame $graphics $shot3 852 96 $phoneWidth $phoneHeight 8

$graphics.DrawString(
    "Real app screens from the current en-US build",
    $footerFont,
    $footerBrush,
    [System.Drawing.RectangleF]::new(594, 446, 320, 20),
    $textFormat
)

$bitmap.Save($outputFile, [System.Drawing.Imaging.ImageFormat]::Png)

$textFormat.Dispose()
$brandMark.Dispose()
$shot1.Dispose()
$shot2.Dispose()
$shot3.Dispose()
$titleFont.Dispose()
$bodyFont.Dispose()
$accentFont.Dispose()
$footerFont.Dispose()
$whiteBrush.Dispose()
$mutedBrush.Dispose()
$accentBrush.Dispose()
$footerBrush.Dispose()
$graphics.Dispose()
$bitmap.Dispose()

$image = [System.Drawing.Image]::FromFile($outputFile)
[pscustomobject]@{
    FullName = $outputFile
    Width = $image.Width
    Height = $image.Height
    Length = (Get-Item $outputFile).Length
}
$image.Dispose()

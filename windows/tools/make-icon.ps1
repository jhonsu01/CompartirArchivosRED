<#
.SYNOPSIS
  Genera windows/src/App/app.ico (multi-tamaño) con el glifo de "compartir".
  Ejecuta este script si quieres regenerar el icono.
.NOTES
  Requiere Windows (System.Drawing).
#>
Add-Type -AssemblyName System.Drawing
$out = Join-Path (Split-Path -Parent $PSScriptRoot) "src\App\app.ico"

function New-IconPng([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)
    $blue = [System.Drawing.Color]::FromArgb(255, 79, 156, 249)
    $brush = New-Object System.Drawing.SolidBrush($blue)
    $r = [int]($size * 0.18); $d = $r * 2
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(0, 0, $d, $d, 180, 90)
    $path.AddArc($size - $d, 0, $d, $d, 270, 90)
    $path.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
    $path.AddArc(0, $size - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $g.FillPath($brush, $path)
    $wb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, [single]($size * 0.055))
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round; $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $cr = $size * 0.095
    $x1 = $size * 0.34; $y1 = $size * 0.50; $x2 = $size * 0.66; $y2 = $size * 0.29; $x3 = $size * 0.66; $y3 = $size * 0.71
    $g.DrawLine($pen, [single]$x1, [single]$y1, [single]$x2, [single]$y2)
    $g.DrawLine($pen, [single]$x1, [single]$y1, [single]$x3, [single]$y3)
    foreach ($p in @(@($x1, $y1), @($x2, $y2), @($x3, $y3))) {
        $g.FillEllipse($wb, [single]($p[0] - $cr), [single]($p[1] - $cr), [single]($cr * 2), [single]($cr * 2))
    }
    $g.Dispose()
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    return , $ms.ToArray()
}

$sizes = @(16, 32, 48, 64, 128, 256)
$pngs = @(); foreach ($s in $sizes) { $pngs += , (New-IconPng $s) }
$ico = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter($ico)
$bw.Write([uint16]0); $bw.Write([uint16]1); $bw.Write([uint16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $s = $sizes[$i]; $len = $pngs[$i].Length
    $wh = if ($s -ge 256) { 0 } else { $s }
    $bw.Write([byte]$wh); $bw.Write([byte]$wh); $bw.Write([byte]0); $bw.Write([byte]0)
    $bw.Write([uint16]1); $bw.Write([uint16]32); $bw.Write([uint32]$len); $bw.Write([uint32]$offset)
    $offset += $len
}
foreach ($d in $pngs) { $bw.Write($d) }
$bw.Flush()
[System.IO.File]::WriteAllBytes($out, $ico.ToArray())
Write-Host "Generado: $out"

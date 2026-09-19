# Generates the "creative purple" textures of Extension Addon Type III.
#
# Sources are the Oritech "machine extender" textures (CC0-1.0, by rearth_neosample). The frame/plate
# is recoloured along a purple ramp that is built from the colours of Oritech's creative fluid tank /
# creative energy storage (#642285 -> #8833B3 -> #B53FF0 -> #CE6BFF), so the block keeps the shape and
# shading of type I but uses the "creative" purple palette.
#
# The connector port itself keeps Oritech's original colours: blue (#3597AD/#3EC9D5/#9CFFF5) while the
# block is connected and red/pink (#FFA7A4/#F48686/#EC736F) while it is not, so type III still shows
# the same "blue = active, red = inactive" port state as type I / the vanilla machine extender.
#
# Produces:
#   extension_addon_3_port.png      (big faces with the connector port)
#   extension_addon_3_port_off.png  (same, disconnected)
#   extension_addon_3_side.png      (narrow faces, connector port removed)
#
# Run with: pwsh -File tools/generate-plugin3-textures.ps1
param(
    [string]$OritechJar = (Join-Path $PSScriptRoot '..\libs\oritech-2.0.0-exp6.jar'),
    [string]$OutDir = (Join-Path $PSScriptRoot '..\src\main\resources\assets\oritechaddonsone\textures\block')
)

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

# Connector port colours of the extender texture, removed for the plain side panel.
$portColors = @(
    '3597AD', '3EC9D5', '9CFFF5',   # connected: blue / cyan port
    'FFA7A4', 'F48686', 'EC736F',   # disconnected: pink port
    '29323D', '414758', '565C6D'    # dark grey slots inside the port
)
$plainColor = [System.Drawing.Color]::FromArgb(255, 0xC3, 0x6C, 0x2B)   # frame base colour

# Creative purple ramp (anchors taken from Oritech's creative tank / creative storage textures).
$rampAnchors = @(
    [System.Drawing.Color]::FromArgb(255, 0x2B, 0x0B, 0x38),
    [System.Drawing.Color]::FromArgb(255, 0x4A, 0x14, 0x5E),
    [System.Drawing.Color]::FromArgb(255, 0x64, 0x22, 0x85),
    [System.Drawing.Color]::FromArgb(255, 0x88, 0x33, 0xB3),
    [System.Drawing.Color]::FromArgb(255, 0xB5, 0x3F, 0xF0)
)

# The connector port keeps Oritech's original blue / red so the active and inactive states stay
# readable; only the dark grey slots inside the port are nudged towards the purple palette.
$portMap = @{
    '29323D' = [System.Drawing.Color]::FromArgb(255, 0x20, 0x28, 0x34)
    '414758' = [System.Drawing.Color]::FromArgb(255, 0x32, 0x3C, 0x46)
    '565C6D' = [System.Drawing.Color]::FromArgb(255, 0x41, 0x47, 0x58)
}

function Get-Luminance([System.Drawing.Color]$c) {
    return (0.2126 * $c.R + 0.7152 * $c.G + 0.0722 * $c.B)
}

function New-PurpleRamp([int]$steps) {
    $result = @()
    for ($i = 0; $i -lt $steps; $i++) {
        $t = if ($steps -le 1) { 0.0 } else { $i / ($steps - 1.0) }
        $pos = $t * ($rampAnchors.Count - 1)
        $idx = [Math]::Min([Math]::Floor($pos), $rampAnchors.Count - 2)
        $frac = $pos - $idx
        $a = $rampAnchors[$idx]
        $b = $rampAnchors[$idx + 1]
        $result += [System.Drawing.Color]::FromArgb(255,
            [int][Math]::Round($a.R + ($b.R - $a.R) * $frac),
            [int][Math]::Round($a.G + ($b.G - $a.G) * $frac),
            [int][Math]::Round($a.B + ($b.B - $a.B) * $frac))
    }
    return $result
}

function Read-JarBitmap($zip, [string]$entryName) {
    $entry = $zip.Entries | Where-Object { $_.FullName -eq $entryName } | Select-Object -First 1
    if (-not $entry) { return $null }
    $ms = New-Object System.IO.MemoryStream
    $stream = $entry.Open(); $stream.CopyTo($ms); $stream.Close(); $ms.Position = 0
    return [System.Drawing.Bitmap]::FromStream($ms)
}

function New-PlainSide([System.Drawing.Bitmap]$src) {
    $dst = New-Object System.Drawing.Bitmap($src.Width, $src.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            $key = '{0:X2}{1:X2}{2:X2}' -f $c.R, $c.G, $c.B
            $dst.SetPixel($x, $y, $(if ($portColors -contains $key) { $plainColor } else { $c }))
        }
    }
    return $dst
}

function New-Recoloured([System.Drawing.Bitmap]$src, $map) {
    $dst = New-Object System.Drawing.Bitmap($src.Width, $src.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            $key = '{0:X2}{1:X2}{2:X2}' -f $c.R, $c.G, $c.B
            $dst.SetPixel($x, $y, $(if ($map.ContainsKey($key)) { $map[$key] } else { $c }))
        }
    }
    return $dst
}

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }

$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $OritechJar))
$portOn = Read-JarBitmap $zip 'assets/oritech/textures/block/machine_extender.png'
$portOff = Read-JarBitmap $zip 'assets/oritech/textures/block/machine_extender_off.png'
if (-not $portOn -or -not $portOff) { Write-Error "extender textures not found in $OritechJar"; exit 1 }
$side = New-PlainSide $portOn

# Build one shared recolour map so all textures stay consistent: port colours use the explicit map,
# every other (frame) colour gets the purple shade of the same brightness.
$colors = @{}
foreach ($bmp in @($portOn, $portOff, $side)) {
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $key = '{0:X2}{1:X2}{2:X2}' -f $c.R, $c.G, $c.B
            if ($portColors -contains $key) { continue }
            $colors[$key] = $c
        }
    }
}

$ordered = $colors.GetEnumerator() | Sort-Object { Get-Luminance $_.Value }
$ramp = New-PurpleRamp $ordered.Count

$map = @{}
for ($i = 0; $i -lt $ordered.Count; $i++) { $map[$ordered[$i].Key] = $ramp[$i] }
foreach ($key in $portMap.Keys) { $map[$key] = $portMap[$key] }

$outputs = @(
    @{ Name = 'extension_addon_3_port.png'; Src = $portOn },
    @{ Name = 'extension_addon_3_port_off.png'; Src = $portOff },
    @{ Name = 'extension_addon_3_side.png'; Src = $side }
)

foreach ($job in $outputs) {
    $bmp = New-Recoloured $job.Src $map
    $out = Join-Path $OutDir $job.Name
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "wrote $out"
}

# Comparison sheet (preview only, written OUTSIDE the mod resources so it never ships):
#   row 0: original extender (port = blue, connected)   | type III port (blue kept)
#   row 1: original extender_off (port = red)           | type III port_off (red kept)
#   row 2: type I plain side                            | type III plain side (purple frame)
$previewDir = Join-Path $PSScriptRoot '..\build\ref-textures'
if (-not (Test-Path $previewDir)) { New-Item -ItemType Directory -Force -Path $previewDir | Out-Null }
$cellSize = 160
$pad = 10
$rows = @(
    @($portOn, (Join-Path $OutDir 'extension_addon_3_port.png')),
    @($portOff, (Join-Path $OutDir 'extension_addon_3_port_off.png')),
    @((Join-Path $OutDir 'extension_addon_1_side.png'), (Join-Path $OutDir 'extension_addon_3_side.png'))
)
$previewWidth = ($cellSize + $pad) * 2 + $pad
$previewHeight = ($cellSize + $pad) * $rows.Count + $pad
$preview = New-Object System.Drawing.Bitmap -ArgumentList $previewWidth, $previewHeight
$g = [System.Drawing.Graphics]::FromImage($preview)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.Clear([System.Drawing.Color]::FromArgb(255, 24, 24, 28))
for ($row = 0; $row -lt $rows.Count; $row++) {
    for ($col = 0; $col -lt 2; $col++) {
        $item = $rows[$row][$col]
        $src = if ($item -is [System.Drawing.Bitmap]) { $item } elseif (Test-Path $item) { [System.Drawing.Bitmap]::FromFile($item) } else { $null }
        if ($src) {
            $g.DrawImage($src, ($pad + $col * ($cellSize + $pad)), ($pad + $row * ($cellSize + $pad)), $cellSize, $cellSize)
            if ($item -isnot [System.Drawing.Bitmap]) { $src.Dispose() }
        }
    }
}
$g.Dispose()
$previewPath = Join-Path $previewDir 'plugin3_port_states.png'
$preview.Save($previewPath, [System.Drawing.Imaging.ImageFormat]::Png)
$preview.Dispose()
Write-Host "wrote $previewPath (preview only, not part of the mod)"

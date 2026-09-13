# Generates the side textures of the Extension Plugins.
#
# Base: the Oritech "machine extender" texture (CC0-1.0, by rearth_neosample). It has a big connector
# graphic in the middle which our half blocks would show cut in half, so every port pixel
# (blue/cyan when connected, pink when not, plus the dark grey slots) is replaced by the base frame
# colour. On top of that, type II gets bright energy stripes: blue while it is connected to a machine,
# red while it is not - both using the port colours of the Oritech extender textures.
#
# Run with: pwsh -File tools/generate-side-texture.ps1
param(
    [string]$OritechJar = (Join-Path $PSScriptRoot '..\libs\oritech-2.0.0-exp6.jar'),
    [string]$OutDir = (Join-Path $PSScriptRoot '..\src\main\resources\assets\oritechaddonsone\textures\block')
)

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

# Port colours used by machine_extender.png / machine_extender_off.png
$portColors = @(
    '3597AD', '3EC9D5', '9CFFF5',   # connected: blue / cyan port
    'FFA7A4', 'F48686', 'EC736F',   # disconnected: pink port
    '29323D', '414758', '565C6D'    # dark grey slots inside the port
)
$baseColor = [System.Drawing.Color]::FromArgb(255, 0xC3, 0x6C, 0x2B)   # main frame colour

# Energy stripes of type II, drawn into the inner panel (columns 4..11, rows 4..11).
# Column -> palette index (0 = outer glow, 1 = mid tone, 2 = bright core)
$stripeColumns = @{ 4 = 0; 5 = 1; 6 = 2; 9 = 2; 10 = 1; 11 = 0 }
$stripeRows = 4..11
$activeStripes = @(
    [System.Drawing.Color]::FromArgb(255, 0x35, 0x97, 0xAD),   # #3597AD
    [System.Drawing.Color]::FromArgb(255, 0x3E, 0xC9, 0xD5),   # #3EC9D5
    [System.Drawing.Color]::FromArgb(255, 0x9C, 0xFF, 0xF5)    # #9CFFF5
)
$inactiveStripes = @(
    [System.Drawing.Color]::FromArgb(255, 0xEC, 0x73, 0x6F),   # #EC736F
    [System.Drawing.Color]::FromArgb(255, 0xF4, 0x86, 0x86),   # #F48686
    [System.Drawing.Color]::FromArgb(255, 0xFF, 0xA7, 0xA4)    # #FFA7A4
)

function New-PlainSide([System.Drawing.Bitmap]$src) {
    # Oritech ships indexed (palette) PNGs, which do not support SetPixel - draw into a fresh ARGB bitmap.
    $dst = New-Object System.Drawing.Bitmap($src.Width, $src.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            $key = '{0:X2}{1:X2}{2:X2}' -f $c.R, $c.G, $c.B
            if ($portColors -contains $key) { $dst.SetPixel($x, $y, $baseColor) } else { $dst.SetPixel($x, $y, $c) }
        }
    }
    return $dst
}

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }

$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $OritechJar))
$entry = $zip.Entries | Where-Object { $_.FullName -eq 'assets/oritech/textures/block/machine_extender.png' } | Select-Object -First 1
if (-not $entry) { Write-Error "machine_extender.png not found in $OritechJar"; exit 1 }
$ms = New-Object System.IO.MemoryStream
$stream = $entry.Open(); $stream.CopyTo($ms); $stream.Close(); $ms.Position = 0
$source = [System.Drawing.Bitmap]::FromStream($ms)

# --- type I: plain side, no port ---
$plain = New-PlainSide $source
$plain.Save((Join-Path $OutDir 'extension_plugin_1_side.png'), [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "wrote extension_plugin_1_side.png (plain, port removed)"

# --- type II: plain side + energy stripes (blue when active, red when inactive) ---
foreach ($variant in @(
    @{ Name = 'extension_plugin_2_side.png'; Colors = $activeStripes },
    @{ Name = 'extension_plugin_2_side_off.png'; Colors = $inactiveStripes }
)) {
    $bmp = New-Object System.Drawing.Bitmap($source.Width, $source.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $plain.Height; $y++) {
        for ($x = 0; $x -lt $plain.Width; $x++) { $bmp.SetPixel($x, $y, $plain.GetPixel($x, $y)) }
    }
    foreach ($x in $stripeColumns.Keys) {
        $color = $variant.Colors[$stripeColumns[$x]]
        foreach ($y in $stripeRows) { $bmp.SetPixel($x, $y, $color) }
    }
    $bmp.Save((Join-Path $OutDir $variant.Name), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "wrote $($variant.Name) (energy stripes)"
}

$plain.Dispose()
$source.Dispose()
$ms.Dispose()
$zip.Dispose()

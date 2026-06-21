param(
    [string]$ImagesDir = (Join-Path $PSScriptRoot "..\src\main\resources\images")
)

$ErrorActionPreference = "Stop"

function Convert-JsxAttributes {
    param([string]$Markup)

    $result = $Markup
    $result = $result -replace '\{/\*.*?\*/\}', ''
    $result = $result -replace '^\s*\(', ''
    $result = $result -replace '\)\s*,?\s*$', ''
    $result = $result -replace '^\s*\[', ''
    $result = $result -replace '\]\s*,?\s*$', ''
    $result = $result -replace '\skey="[^"]*"', ''
    $result = $result -replace '\s\{\.\.\.[^}]+\}', ''
    $result = $result -replace ',\s*(?=<)', ''
    $result = $result -replace '(</[A-Za-z][A-Za-z0-9:_-]*>)\s*,', '$1'
    $result = $result -replace 'fill=\{colorMode === "light" \? "([^"]+)" : "([^"]+)"\}', 'fill="$1"'
    $result = $result -replace 'fill=\{colorMode === "dark" \? "([^"]+)" : "([^"]+)"\}', 'fill="$2"'
    $result = $result -replace 'fill=\{circleFillColor\}', 'fill="#EEF2F6"'
    $result = $result -replace 'style=\{\{\s*clipPath:\s*"([^"]+)"\s*\}\}', 'style="clip-path:$1"'
    $result = $result -replace 'style=\{\{\s*([^:}]+):\s*"([^"]+)"\s*\}\}', 'style="$1:$2"'
    $result = $result -replace '([A-Za-z_:][-A-Za-z0-9_:.]*)=\{([0-9.]+)\}', '$1="$2"'

    $attributeMap = [ordered]@{
        'fillRule' = 'fill-rule'
        'clipRule' = 'clip-rule'
        'clipPath' = 'clip-path'
        'strokeWidth' = 'stroke-width'
        'strokeLinecap' = 'stroke-linecap'
        'strokeLinejoin' = 'stroke-linejoin'
        'strokeMiterlimit' = 'stroke-miterlimit'
        'strokeDasharray' = 'stroke-dasharray'
        'strokeDashoffset' = 'stroke-dashoffset'
        'stopColor' = 'stop-color'
        'stopOpacity' = 'stop-opacity'
        'floodOpacity' = 'flood-opacity'
        'colorInterpolationFilters' = 'color-interpolation-filters'
        'dominantBaseline' = 'dominant-baseline'
        'fontSize' = 'font-size'
        'fontWeight' = 'font-weight'
        'className' = 'class'
    }

    foreach ($entry in $attributeMap.GetEnumerator()) {
        $result = $result -replace $entry.Key, $entry.Value
    }

    $result = $result -replace '<>', ''
    $result = $result -replace '</>', ''
    return $result.Trim()
}

function Get-IconBody {
    param([string]$Source)

    $viewBox = $null
    $body = $null

    $createIconViewBox = [regex]::Match($Source, 'viewBox:\s*"([^"]+)"')
    $iconViewBox = [regex]::Match($Source, '<Icon\s+[^>]*viewBox="([^"]+)"')

    if ($createIconViewBox.Success) {
        $viewBox = $createIconViewBox.Groups[1].Value
        $pathStart = $Source.IndexOf('path:')
        if ($pathStart -ge 0) {
            $afterPath = $Source.Substring($pathStart + 5).Trim()
            $lastClose = $afterPath.LastIndexOf("})")
            if ($lastClose -ge 0) {
                $body = $afterPath.Substring(0, $lastClose).Trim()
            }
        }
    } elseif ($iconViewBox.Success) {
        $viewBox = $iconViewBox.Groups[1].Value
        $iconMatch = [regex]::Match($Source, '(?s)<Icon\b[^>]*>(.*)</Icon>')
        if ($iconMatch.Success) {
            $body = $iconMatch.Groups[1].Value.Trim()
        }
    }

    if (-not $viewBox -or -not $body) {
        return $null
    }

    if ($body.StartsWith("(") -and $body.EndsWith(")")) {
        $body = $body.Substring(1, $body.Length - 2).Trim()
    }
    if ($body.StartsWith("[") -and $body.EndsWith("]")) {
        $body = $body.Substring(1, $body.Length - 2).Trim()
    }

    return @{
        ViewBox = $viewBox
        Body = (Convert-JsxAttributes $body)
    }
}

$converted = 0
$failed = @()

Get-ChildItem -LiteralPath $ImagesDir -Filter *.tsx |
    Where-Object { $_.Name -notin @("defaultProps.tsx", "index.tsx") } |
    ForEach-Object {
        $source = Get-Content -LiteralPath $_.FullName -Raw
        $icon = Get-IconBody $source

        if (-not $icon) {
            $failed += "$($_.Name): could not find SVG body"
            return
        }

        $svg = @"
<svg xmlns="http://www.w3.org/2000/svg" viewBox="$($icon.ViewBox)" fill="currentColor">
$($icon.Body)
</svg>
"@

        try {
            [xml]$null = $svg
            $target = Join-Path $ImagesDir ($_.BaseName + ".svg")
            Set-Content -LiteralPath $target -Value $svg -Encoding UTF8
            $converted++
        } catch {
            $failed += "$($_.Name): $($_.Exception.Message)"
        }
    }

Write-Host "Converted $converted TSX files to SVG."
if ($failed.Count -gt 0) {
    Write-Host "Failed conversions:"
    $failed | ForEach-Object { Write-Host " - $_" }
    exit 1
}

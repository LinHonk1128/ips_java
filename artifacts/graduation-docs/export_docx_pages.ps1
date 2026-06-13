param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputDir
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$app = $null
$doc = $null
try {
    $app = New-Object -ComObject Word.Application
    $app.Visible = $false
    $app.DisplayAlerts = 0
    $doc = $app.Documents.Open($InputPath, $false, $true)
    $doc.Repaginate()
    $pageCount = $doc.ComputeStatistics(2)

    for ($page = 1; $page -le $pageCount; $page++) {
        $startRange = $doc.GoTo(1, 1, $page)
        $start = $startRange.Start
        if ($page -lt $pageCount) {
            $nextRange = $doc.GoTo(1, 1, $page + 1)
            $end = [Math]::Max($start, $nextRange.Start - 1)
        } else {
            $end = $doc.Content.End
        }
        $range = $doc.Range($start, $end)
        $range.CopyAsPicture()
        Start-Sleep -Milliseconds 250
        $image = [System.Windows.Forms.Clipboard]::GetImage()
        if ($null -eq $image) {
            throw "Failed to obtain clipboard image for page $page"
        }
        $path = Join-Path $OutputDir ("page-{0}.png" -f $page)
        $image.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        $image.Dispose()
        [System.Windows.Forms.Clipboard]::Clear()
        Write-Output $path
    }
} finally {
    if ($doc) {
        $doc.Close($false)
        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($doc) | Out-Null
    }
    if ($app) {
        $app.Quit()
        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($app) | Out-Null
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}

param(
    [Parameter(Mandatory = $true)]
    [string]$DocxPath
)

$word = New-Object -ComObject Word.Application
$word.Visible = $false
try {
    $document = $word.Documents.Open((Resolve-Path -LiteralPath $DocxPath).Path, $false, $true)
    try {
        $document.Repaginate()
        Write-Output $document.ComputeStatistics(2)
    }
    finally {
        $document.Close($false)
    }
}
finally {
    $word.Quit()
}

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try {
    $from = [datetime]::ParseExact('__DAY__', 'yyyy-MM-dd', $null)
    $to = $from.AddDays(1)
    $outlook = New-Object -ComObject Outlook.Application
    $items = $outlook.GetNamespace('MAPI').GetDefaultFolder(9).Items
    $items.IncludeRecurrences = $true
    $items.Sort('[Start]')
    $filter = "[Start] < '" + $to.ToString('g') + "' AND [End] > '" + $from.ToString('g') + "'"
    $entries = @()
    foreach ($item in $items.Restrict($filter)) {
        $entries += [pscustomobject]@{
            Subject        = [string]$item.Subject
            Start          = $item.Start.ToUniversalTime().ToString('o')
            End            = $item.End.ToUniversalTime().ToString('o')
            AllDay         = [bool]$item.AllDayEvent
            ResponseStatus = [int]$item.ResponseStatus
            BusyStatus     = [int]$item.BusyStatus
        }
    }
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    Write-Output (ConvertTo-Json -InputObject @($entries) -Compress -Depth 3)
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}

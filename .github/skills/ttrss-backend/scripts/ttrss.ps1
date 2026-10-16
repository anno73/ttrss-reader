#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Profile,
    [string]$Command = 'Check',
    [string]$ArticleIds,
    [string]$FeedUrl,
    [int]$CategoryId = -3,
    [int]$FeedId = -4,
    [switch]$IsCategory,
    [string]$ViewMode = 'all_articles',
    [string]$Search,
    [int]$Limit = 50,
    [int]$Offset = 0,
    [switch]$AllowMutation
)

$ErrorActionPreference = 'Stop'
try {
    Import-Module (Join-Path $PSScriptRoot 'TtrssClient.psm1') -Force -ErrorAction Stop
    $result = Invoke-TtrssCommand -ProjectRoot (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path -Options $PSBoundParameters
    [Console]::Out.WriteLine($result.Json)
    exit $result.ExitCode
}
catch {
    [Console]::Out.WriteLine('{"ok":false,"data":null,"error":{"code":"CLIENT_ERROR","message":"Client initialization failed. Check installation and PowerShell requirements."}}')
    exit 1
}

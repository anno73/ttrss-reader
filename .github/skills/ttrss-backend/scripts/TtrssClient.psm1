#requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

function Stop-Ttrss {
    param([string]$Code, [string]$Message)
    $exception = New-Object System.InvalidOperationException($Message)
    $exception.Data['TtrssCode'] = $Code
    throw $exception
}

function Get-TtrssError {
    param($Record)
    if ($Record.Exception.Data.Contains('TtrssCode')) {
        return @{ code = $Record.Exception.Data['TtrssCode']; message = $Record.Exception.Message }
    }
    return @{ code = 'CLIENT_ERROR'; message = 'Unexpected client failure. Check configuration and installation; no request is retried.' }
}

function Read-TtrssText {
    param([string]$Path, [string]$Label)
    try {
        $encoding = New-Object System.Text.UTF8Encoding($false, $true)
        return [IO.File]::ReadAllText($Path, $encoding)
    }
    catch {
        Stop-Ttrss 'CONFIG_ERROR' "Cannot read $Label as UTF-8. Check that it exists and is accessible."
    }
}

function Assert-TtrssProperties {
    param($Value, [string[]]$Allowed, [string[]]$Required)
    if ($null -eq $Value -or $Value -isnot [pscustomobject]) {
        Stop-Ttrss 'CONFIG_ERROR' 'Configuration objects must be JSON objects.'
    }
    $names = @($Value.PSObject.Properties | ForEach-Object { $_.Name })
    foreach ($name in $names) {
        if ($Allowed -cnotcontains $name) {
            Stop-Ttrss 'CONFIG_ERROR' 'Configuration contains an unknown property. Check the documented schema.'
        }
    }
    foreach ($name in $Required) {
        if ($names -cnotcontains $name) {
            Stop-Ttrss 'CONFIG_ERROR' 'Configuration is missing a required property.'
        }
    }
}

function Assert-TtrssJsonKeys {
    param([string]$Text)
    # JSON syntax is validated first; tokenize strings atomically to ignore braces in values.
    $tokens = [regex]::Matches($Text, '"(?:[^"\\]|\\.)*"|[{}:]')
    $stack = New-Object System.Collections.Stack
    for ($i = 0; $i -lt $tokens.Count; $i++) {
        $token = $tokens[$i].Value
        if ($token -eq '{') {
            $stack.Push(@{})
        }
        elseif ($token -eq '}') {
            $null = $stack.Pop()
        }
        elseif ($token.StartsWith('"') -and $i + 1 -lt $tokens.Count -and $tokens[$i + 1].Value -eq ':') {
            $key = ConvertFrom-Json -InputObject $token
            $keys = $stack.Peek()
            if ($keys.ContainsKey($key)) {
                Stop-Ttrss 'CONFIG_ERROR' 'Configuration contains duplicate JSON keys (case-insensitive).'
            }
            $keys[$key] = $true
        }
    }
}

function Read-TtrssConfiguration {
    param([string]$ProjectRoot, [string]$Profile)
    $text = Read-TtrssText (Join-Path $ProjectRoot 'ttrss.config.json') 'ttrss.config.json'
    try { $config = ConvertFrom-Json -InputObject $text -ErrorAction Stop }
    catch { Stop-Ttrss 'CONFIG_ERROR' 'ttrss.config.json must contain valid JSON with unambiguous property names.' }
    Assert-TtrssJsonKeys $text
    Assert-TtrssProperties $config @('defaultProfile', 'profiles') @('defaultProfile', 'profiles')
    if ($config.defaultProfile -isnot [string] -or $config.defaultProfile -cnotmatch '^[A-Za-z][A-Za-z0-9]*$') {
        Stop-Ttrss 'CONFIG_ERROR' 'defaultProfile must be a valid profile name.'
    }
    if ($null -eq $config.profiles -or $config.profiles -isnot [pscustomobject]) {
        Stop-Ttrss 'CONFIG_ERROR' 'profiles must be a JSON object.'
    }
    $profiles = @{}
    foreach ($property in $config.profiles.PSObject.Properties) {
        if ($property.Name -cnotmatch '^[A-Za-z][A-Za-z0-9]*$') {
            Stop-Ttrss 'CONFIG_ERROR' 'Profile names must start with a letter and contain only ASCII letters and digits.'
        }
        $value = $property.Value
        Assert-TtrssProperties $value @('apiUrl', 'timeoutSeconds') @('apiUrl', 'timeoutSeconds')
        $uri = $null
        if ($value.apiUrl -isnot [string] -or
            -not [Uri]::TryCreate($value.apiUrl, [UriKind]::Absolute, [ref]$uri) -or
            $uri.Scheme -ne 'https' -or -not $uri.Host -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
            Stop-Ttrss 'CONFIG_ERROR' 'apiUrl must be an absolute HTTPS URL without credentials, query or fragment.'
        }
        if (($value.timeoutSeconds -isnot [int] -and $value.timeoutSeconds -isnot [long]) -or
            $value.timeoutSeconds -lt 1 -or $value.timeoutSeconds -gt 300) {
            Stop-Ttrss 'CONFIG_ERROR' 'timeoutSeconds must be an integer from 1 through 300.'
        }
        $profiles[$property.Name] = @{ Name = $property.Name; Uri = $uri; Timeout = [int]$value.timeoutSeconds }
    }
    if (-not $profiles.ContainsKey($config.defaultProfile)) {
        Stop-Ttrss 'CONFIG_ERROR' 'defaultProfile does not reference a configured profile.'
    }
    if ([string]::IsNullOrEmpty($Profile)) { $Profile = $config.defaultProfile }
    if (-not $profiles.ContainsKey($Profile)) {
        Stop-Ttrss 'CONFIG_ERROR' 'The selected profile is not configured.'
    }
    return $profiles[$Profile]
}

function Read-TtrssDotEnv {
    param([string]$ProjectRoot)
    $text = Read-TtrssText (Join-Path $ProjectRoot '.env') '.env'
    $values = @{}
    $lineNumber = 0
    foreach ($line in ($text -split '\r?\n')) {
        $lineNumber++
        $entry = $line.Trim()
        if (-not $entry -or $entry.StartsWith('#')) { continue }
        if ($entry -cnotmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            Stop-Ttrss 'CONFIG_ERROR' "Malformed .env entry at line $lineNumber."
        }
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($values.ContainsKey($key)) {
            Stop-Ttrss 'CONFIG_ERROR' "Duplicate .env key at line $lineNumber."
        }
        if ($value.StartsWith('"') -or $value.StartsWith("'")) {
            if ($value.Length -lt 2 -or $value[$value.Length - 1] -ne $value[0]) {
                Stop-Ttrss 'CONFIG_ERROR' "Unmatched .env quotes at line $lineNumber."
            }
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$key] = $value
    }
    return $values
}

function New-TtrssRequest {
    param([System.Collections.IDictionary]$Options)
    $defaults = @{
        Command = 'Check'; Profile = ''; ArticleIds = ''; FeedUrl = ''; CategoryId = -3; FeedId = -4
        IsCategory = $false; ViewMode = 'all_articles'; Search = ''; Limit = 50; Offset = 0; AllowMutation = $false
    }
    $common = @('Verbose', 'Debug', 'ErrorAction', 'WarningAction', 'InformationAction', 'ErrorVariable',
        'WarningVariable', 'InformationVariable', 'OutVariable', 'OutBuffer', 'PipelineVariable')
    foreach ($key in $Options.Keys) {
        if ($common -contains $key) {
            Stop-Ttrss 'ARGUMENT_ERROR' 'Diagnostic/common parameters are not supported; invoke the client without tracing.'
        }
        if (-not $defaults.ContainsKey($key)) { Stop-Ttrss 'ARGUMENT_ERROR' 'Unknown client parameter.' }
        $defaults[$key] = $Options[$key]
    }
    $command = [string]$defaults.Command
    $allowed = @{
        Check = @(); GetFeeds = @('CategoryId', 'Limit', 'Offset'); GetCategories = @(); GetUnread = @()
        GetHeadlines = @('FeedId', 'IsCategory', 'ViewMode', 'Search', 'Limit', 'Offset')
        GetArticle = @('ArticleIds')
        SubscribeFeed = @('FeedUrl', 'CategoryId', 'AllowMutation')
        SetRead = @('ArticleIds', 'AllowMutation'); SetUnread = @('ArticleIds', 'AllowMutation')
        SetStarred = @('ArticleIds', 'AllowMutation'); SetUnstarred = @('ArticleIds', 'AllowMutation')
    }
    if (-not $allowed.ContainsKey($command)) { Stop-Ttrss 'ARGUMENT_ERROR' 'Unsupported client command.' }
    foreach ($key in $Options.Keys) {
        if ($key -notin @('Command', 'Profile') -and $allowed[$command] -notcontains $key) {
            Stop-Ttrss 'ARGUMENT_ERROR' 'A supplied parameter does not apply to this command.'
        }
    }
    if ($Options.ContainsKey('Profile') -and ([string]$defaults.Profile) -cnotmatch '^[A-Za-z][A-Za-z0-9]*$') {
        Stop-Ttrss 'ARGUMENT_ERROR' 'Profile must start with a letter and contain only ASCII letters and digits.'
    }
    if ($defaults.Limit -lt 1 -or $defaults.Limit -gt 200 -or $defaults.Offset -lt 0 -or $defaults.Offset -gt 1000000) {
        Stop-Ttrss 'ARGUMENT_ERROR' 'Limit must be 1..200 and Offset must be 0..1000000.'
    }
    if ($defaults.ViewMode -notin @('all_articles', 'unread', 'marked', 'updated', 'adaptive')) {
        Stop-Ttrss 'ARGUMENT_ERROR' 'Unsupported headline view mode.'
    }
    $payload = @{}
    switch ($command) {
        Check { $payload.op = 'getVersion' }
        GetFeeds {
            $payload = @{ op = 'getFeeds'; cat_id = $defaults.CategoryId; limit = $defaults.Limit; offset = $defaults.Offset }
        }
        GetCategories { $payload = @{ op = 'getCategories'; enable_nested = $true; include_empty = $true } }
        GetUnread { $payload.op = 'getUnread' }
        SubscribeFeed {
            if (-not $defaults.AllowMutation) {
                Stop-Ttrss 'ARGUMENT_ERROR' 'Subscription requires explicit user intent and -AllowMutation.'
            }
            $feedUri = $null
            if (-not [Uri]::TryCreate([string]$defaults.FeedUrl, [UriKind]::Absolute, [ref]$feedUri) -or
                $feedUri.Scheme -ne 'https' -or -not $feedUri.Host -or $feedUri.UserInfo -or $feedUri.Fragment) {
                Stop-Ttrss 'ARGUMENT_ERROR' 'FeedUrl must be an absolute HTTPS URL without credentials or fragment.'
            }
            $category = 0
            if ($Options.ContainsKey('CategoryId')) { $category = $defaults.CategoryId }
            if ($category -lt 0) { Stop-Ttrss 'ARGUMENT_ERROR' 'Subscription CategoryId must be zero (uncategorized) or positive.' }
            $payload = @{ op = 'subscribeToFeed'; feed_url = $feedUri.AbsoluteUri; category_id = $category }
        }
        GetHeadlines {
            $payload = @{
                op = 'getHeadlines'; feed_id = $defaults.FeedId; is_cat = [bool]$defaults.IsCategory
                view_mode = $defaults.ViewMode; search = $defaults.Search; limit = $defaults.Limit
                skip = $defaults.Offset; show_content = $false; show_excerpt = $true
                sanitize = $true; force_update = $false; mark_read = $false; include_header = $false
            }
        }
    }
    if ($command -eq 'GetArticle' -or $command -like 'Set*') {
        $ids = @(([string]$defaults.ArticleIds).Split(','))
        if ($ids.Count -gt 100) { Stop-Ttrss 'ARGUMENT_ERROR' 'At most 100 article IDs are allowed.' }
        $seen = @{}
        foreach ($id in $ids) {
            $number = 0
            if ($id -cnotmatch '^[1-9][0-9]*$' -or -not [int]::TryParse($id, [ref]$number)) {
                Stop-Ttrss 'ARGUMENT_ERROR' 'ArticleIds must be comma-separated positive 32-bit integers without spaces.'
            }
            if ($seen.ContainsKey($id)) { Stop-Ttrss 'ARGUMENT_ERROR' 'ArticleIds must be unique.' }
            $seen[$id] = $true
        }
        if ($command -eq 'GetArticle') {
            $payload = @{ op = 'getArticle'; article_id = $ids -join ','; sanitize = $true }
        }
        else {
            if (-not $defaults.AllowMutation) {
                Stop-Ttrss 'ARGUMENT_ERROR' 'State changes require explicit user intent and -AllowMutation.'
            }
            $field = 2
            if ($command -in @('SetStarred', 'SetUnstarred')) { $field = 0 }
            $mode = 0
            if ($command -in @('SetUnread', 'SetStarred')) { $mode = 1 }
            $payload = @{ op = 'updateArticle'; article_ids = $ids -join ','; field = $field; mode = $mode }
        }
    }
    return @{ Command = $command; Profile = [string]$defaults.Profile; Payload = $payload }
}

function New-TtrssHttpClient {
    param([int]$Timeout)
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false
    $handler.UseCookies = $false
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($Timeout)
    $client.MaxResponseContentBufferSize = 4 * 1024 * 1024
    return $client
}

function Invoke-TtrssHttp {
    param($Client, [Uri]$Uri, [hashtable]$Payload)
    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, $Uri)
    $response = $null
    try {
        $request.Content = New-Object System.Net.Http.StringContent(
            (ConvertTo-Json -InputObject $Payload -Depth 10 -Compress), [Text.Encoding]::UTF8, 'application/json')
        try { $response = $Client.SendAsync($request).GetAwaiter().GetResult() }
        catch { Stop-Ttrss 'TRANSPORT_ERROR' 'Connection, TLS, timeout or response-size failure. Check connectivity, certificate trust and timeout; requests are not retried.' }
        if ([int]$response.StatusCode -ge 300 -and [int]$response.StatusCode -lt 400) {
            Stop-Ttrss 'REDIRECT_REJECTED' 'The API endpoint redirected. Configure its final HTTPS URL; redirects are not followed.'
        }
        if (-not $response.IsSuccessStatusCode) {
            Stop-Ttrss 'HTTP_ERROR' ("Backend returned HTTP {0}. Check API availability and access." -f [int]$response.StatusCode)
        }
        try {
            $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $body = ConvertFrom-Json -InputObject $text -ErrorAction Stop
        }
        catch { Stop-Ttrss 'INVALID_RESPONSE' 'Backend did not return valid JSON.' }
        if ($null -eq $body -or $body -isnot [pscustomobject] -or
            -not (Test-TtrssProperty $body 'status') -or
            -not (Test-TtrssProperty $body 'content') -or
            ($body.status -isnot [int] -and $body.status -isnot [long]) -or $body.status -notin @(0, 1)) {
            Stop-Ttrss 'INVALID_RESPONSE' 'Backend response lacks a valid API status/content envelope.'
        }
        if ($body.status -eq 1) {
            $code = 'API_ERROR'
            if ((Test-TtrssProperty $body.content 'error') -and
                $body.content.error -cin @('API_DISABLED', 'LOGIN_ERROR', 'NOT_LOGGED_IN', 'INCORRECT_USAGE',
                    'UNKNOWN_METHOD', 'E_OPERATION_FAILED', 'E_NOT_FOUND')) {
                $code = $body.content.error
            }
            Stop-Ttrss $code 'Backend rejected the API operation. Check API access, credentials, session validity and server capabilities.'
        }
        return ,$body.content
    }
    finally {
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose()
    }
}

function Test-TtrssProperty {
    param($Value, [string]$Name)
    return ($null -ne $Value -and $null -ne $Value.PSObject.Properties[$Name])
}

function Invoke-TtrssCommand {
    [CmdletBinding()]
    param([string]$ProjectRoot, [System.Collections.IDictionary]$Options)
    $result = [ordered]@{ ok = $false; profile = $null; command = $null; data = $null; error = $null }
    $client = $null
    $sid = $null
    $secrets = New-Object 'System.Collections.Generic.List[string]'
    try {
        $request = New-TtrssRequest -Options $Options
        $result.command = $request.Command
        $profile = Read-TtrssConfiguration $ProjectRoot $request.Profile
        $result.profile = $profile.Name
        $envValues = Read-TtrssDotEnv $ProjectRoot
        $prefix = 'TTRSS_' + $profile.Name.ToUpperInvariant()
        foreach ($suffix in @('_USERNAME', '_PASSWORD')) {
            $key = $prefix + $suffix
            if (-not $envValues.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($envValues[$key])) {
                Stop-Ttrss 'CONFIG_ERROR' "Missing or empty credential: $key in .env."
            }
            $secrets.Add($envValues[$key])
        }
        $username = $envValues[$prefix + '_USERNAME']
        $password = $envValues[$prefix + '_PASSWORD']
        $envValues.Clear()
        $client = New-TtrssHttpClient $profile.Timeout
        $login = Invoke-TtrssHttp $client $profile.Uri @{ op = 'login'; user = $username; password = $password }
        if (-not (Test-TtrssProperty $login 'session_id') -or
            $login.session_id -isnot [string] -or [string]::IsNullOrWhiteSpace($login.session_id)) {
            Stop-Ttrss 'INVALID_RESPONSE' 'Login succeeded without a valid session ID.'
        }
        $sid = $login.session_id
        $secrets.Add($sid)
        $request.Payload.sid = $sid
        $data = Invoke-TtrssHttp $client $profile.Uri $request.Payload
        if ($request.Command -eq 'Check') {
            $level = Invoke-TtrssHttp $client $profile.Uri @{ op = 'getApiLevel'; sid = $sid }
            if (-not (Test-TtrssProperty $data 'version') -or -not (Test-TtrssProperty $level 'level')) {
                Stop-Ttrss 'INVALID_RESPONSE' 'Version/API-level response is missing required fields.'
            }
            $data = @{ version = $data.version; apiLevel = $level.level }
        }
        elseif ($request.Command -eq 'SubscribeFeed') {
            if (-not (Test-TtrssProperty $data 'status')) {
                Stop-Ttrss 'INVALID_RESPONSE' 'Subscription response lacks a status. Inspect subscriptions before retrying.'
            }
            $status = $data.status
            $feedId = $null
            if (Test-TtrssProperty $status 'code') {
                if (Test-TtrssProperty $status 'feed_id') { $feedId = $status.feed_id }
                $status = $status.code
            }
            if (($status -isnot [int] -and $status -isnot [long]) -or $status -lt 0 -or $status -gt 8) {
                Stop-Ttrss 'INVALID_RESPONSE' 'Subscription response contains an unknown status. Inspect subscriptions before retrying.'
            }
            $messages = @{
                2 = 'Backend rejected the feed URL.'
                3 = 'The URL returned HTML without a discoverable feed.'
                4 = 'The URL returned multiple feeds. Select an explicit feed URL; no automatic selection is performed.'
                5 = 'Backend could not download the feed. Check backend connectivity or feed access restrictions.'
                6 = 'Backend could not parse the feed content.'
                7 = 'Backend could not create the subscription database entry.'
                8 = 'Backend account does not have permission to subscribe.'
            }
            if ($status -ge 2) { Stop-Ttrss 'SUBSCRIPTION_FAILED' ("Subscription status {0}: {1}" -f $status, $messages[[int]$status]) }
            $outcome = 'subscribed'
            if ($status -eq 0) { $outcome = 'alreadySubscribed' }
            $data = @{ outcome = $outcome; statusCode = $status; feedId = $feedId; feedUrl = $request.Payload.feed_url }
        }
        elseif ($request.Payload.op -eq 'updateArticle') {
            if (-not (Test-TtrssProperty $data 'updated') -or
                ($data.updated -isnot [int] -and $data.updated -isnot [long]) -or
                $data.updated -lt 0 -or $data.updated -gt $request.Payload.article_ids.Split(',').Count) {
                Stop-Ttrss 'INVALID_RESPONSE' 'Mutation response is missing a valid updated count; inspect state before retrying.'
            }
        }
        elseif ($request.Command -in @('GetFeeds', 'GetCategories', 'GetHeadlines', 'GetArticle')) {
            if ($data -isnot [array]) { Stop-Ttrss 'INVALID_RESPONSE' 'Backend returned an invalid list result.' }
        }
        elseif ($request.Command -eq 'GetUnread') {
            if (-not (Test-TtrssProperty $data 'unread')) {
                Stop-Ttrss 'INVALID_RESPONSE' 'Backend returned an invalid unread result.'
            }
        }
        $result.data = $data
        $result.ok = $true
    }
    catch { $result.error = Get-TtrssError $_ }
    finally {
        if ($null -ne $client) {
            if ($sid) {
                try {
                    $logout = Invoke-TtrssHttp $client $profile.Uri @{ op = 'logout'; sid = $sid }
                    if (-not (Test-TtrssProperty $logout 'status') -or $logout.status -cne 'OK') {
                        Stop-Ttrss 'INVALID_RESPONSE' 'Backend did not confirm logout.'
                    }
                }
                catch {
                    $cleanup = Get-TtrssError $_
                    if ($result.ok) {
                        $result.operationSucceeded = $true
                        $result.error = $cleanup
                    }
                    else { $result.cleanupError = $cleanup }
                    $result.ok = $false
                }
            }
            $client.Dispose()
        }
    }
    $orderedSecrets = @($secrets | Sort-Object Length -Descending)
    try {
        foreach ($key in @('data', 'profile', 'command')) {
            $result[$key] = Protect-TtrssValue $result[$key] $orderedSecrets
        }
        foreach ($key in @('error', 'cleanupError')) {
            if ($result.Contains($key) -and $null -ne $result[$key]) {
                $result[$key].message = Protect-TtrssValue $result[$key].message $orderedSecrets
            }
        }
    }
    catch {
        $result.data = $null
        $result.operationSucceeded = $result.ok
        $result.ok = $false
        $result.error = Get-TtrssError $_
    }
    $json = ConvertTo-Json -InputObject $result -Depth 50 -Compress
    $secrets.Clear()
    $exitCode = 1
    if ($result.ok) { $exitCode = 0 }
    return @{ Json = $json; ExitCode = $exitCode }
}

function Protect-TtrssValue {
    param($Value, [string[]]$Secrets, [int]$Depth = 0)
    if ($Depth -gt 40) {
        Stop-Ttrss 'INVALID_RESPONSE' 'Response nesting exceeds the client limit. Inspect state before retrying any operation.'
    }
    if ($null -eq $Value) { return $null }
    if ($Value -is [string]) {
        foreach ($secret in $Secrets) { $Value = $Value.Replace($secret, '[REDACTED]') }
        return $Value
    }
    if ($Value -is [System.Collections.IDictionary] -or $Value -is [pscustomobject]) {
        $safe = [ordered]@{}
        $properties = @()
        if ($Value -is [System.Collections.IDictionary]) {
            $properties = @($Value.Keys | ForEach-Object { @{ Name = [string]$_; Value = $Value[$_] } })
        }
        else { $properties = @($Value.PSObject.Properties) }
        foreach ($property in $properties) {
            $name = Protect-TtrssValue ([string]$property.Name) $Secrets ($Depth + 1)
            $safe[$name] = Protect-TtrssValue $property.Value $Secrets ($Depth + 1)
        }
        return $safe
    }
    if ($Value -is [array]) {
        $safe = @(
            foreach ($item in $Value) { Protect-TtrssValue $item $Secrets ($Depth + 1) }
        )
        return ,$safe
    }
    return $Value
}

Export-ModuleMember -Function Invoke-TtrssCommand

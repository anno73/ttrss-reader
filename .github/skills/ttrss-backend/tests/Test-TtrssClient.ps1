#requires -Version 5.1
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '..\scripts\TtrssClient.psm1') -Force
$module = Get-Module TtrssClient

Add-Type -ReferencedAssemblies 'System.Net.Http', 'System.Core', 'System' -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;

public sealed class TtrssFixture {
    public int Status = 200;
    public string Json;
    public bool Fail;
}

public sealed class TtrssFakeHandler : HttpMessageHandler {
    public static readonly Queue<TtrssFixture> Responses = new Queue<TtrssFixture>();
    public static readonly List<string> Bodies = new List<string>();
    public static readonly List<string> Uris = new List<string>();
    public static readonly List<string> ContentTypes = new List<string>();
    public static void Reset() {
        Responses.Clear(); Bodies.Clear(); Uris.Clear(); ContentTypes.Clear();
    }
    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken) {
        Bodies.Add(await request.Content.ReadAsStringAsync());
        Uris.Add(request.RequestUri.AbsoluteUri);
        ContentTypes.Add(request.Content.Headers.ContentType.ToString());
        if (request.Method != HttpMethod.Post) throw new Exception("Expected POST.");
        if (Responses.Count == 0) throw new Exception("Unexpected request.");
        TtrssFixture fixture = Responses.Dequeue();
        if (fixture.Fail) throw new HttpRequestException("Private transport details: fixture-password.");
        var response = new HttpResponseMessage((HttpStatusCode)fixture.Status);
        response.Content = new StringContent(fixture.Json ?? "");
        response.Headers.Location = new Uri("https://redirect.invalid/api/");
        return response;
    }
}
'@

$root = Join-Path ([IO.Path]::GetTempPath()) ('ttrss-skill-tests-' + [Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $root
$script:passed = 0
$script:failed = 0
$script:baseConfig = '{"defaultProfile":"personal","profiles":{"personal":{"apiUrl":"https://rss.example.net/tt-rss/api/","timeoutSeconds":30},"test":{"apiUrl":"https://rss-test.example.net/api/","timeoutSeconds":15}}}'
$script:baseEnv = "TTRSS_PERSONAL_USERNAME=fixture-user`r`nTTRSS_PERSONAL_PASSWORD=fixture-password`r`nTTRSS_TEST_USERNAME=fixture-test-user`r`nTTRSS_TEST_PASSWORD=fixture-test-password`r`n"

function Assert {
    param([bool]$Condition, [string]$Message = 'Assertion failed.')
    if (-not $Condition) { throw $Message }
}

function Write-Fixture {
    param([string]$Name, [string]$Text, [bool]$Bom = $false)
    [IO.File]::WriteAllText((Join-Path $root $Name), $Text, (New-Object Text.UTF8Encoding($Bom)))
}

function Reset-Fixture {
    [TtrssFakeHandler]::Reset()
    Write-Fixture 'ttrss.config.json' $script:baseConfig
    Write-Fixture '.env' $script:baseEnv
}

function Queue-Reply {
    param($Content, [int]$ApiStatus = 0, [int]$HttpStatus = 200)
    $fixture = New-Object TtrssFixture
    $fixture.Status = $HttpStatus
    $fixture.Json = ConvertTo-Json -InputObject @{ seq = 0; status = $ApiStatus; content = $Content } -Depth 20 -Compress
    [TtrssFakeHandler]::Responses.Enqueue($fixture)
}

function Queue-Login { Queue-Reply @{ session_id = 'fixture-session'; api_level = 23 } }
function Queue-Logout { Queue-Reply @{ status = 'OK' } }

function Invoke-Client {
    param([hashtable]$Options = @{})
    $result = Invoke-TtrssCommand -ProjectRoot $root -Options $Options
    $body = ConvertFrom-Json -InputObject $result.Json
    Assert ($body.ok -eq ($result.ExitCode -eq 0)) 'Exit code and JSON success disagree.'
    Assert (-not $result.Json.Contains('fixture-password')) 'Password leaked.'
    Assert (-not $result.Json.Contains('fixture-session')) 'Session leaked.'
    return $body
}

function Assert-Code {
    param($Body, [string]$Code)
    Assert (-not $Body.ok) 'Expected failure.'
    Assert ($Body.error.code -ceq $Code) ("Expected error {0}, got {1}." -f $Code, $Body.error.code)
}

function Test {
    param([string]$Name, [scriptblock]$Body)
    Reset-Fixture
    try {
        & $Body
        $script:passed++
        Write-Output "PASS $Name"
    }
    catch {
        $script:failed++
        Write-Output "FAIL $Name"
        Write-Output $_.Exception.Message
        Write-Output $_.ScriptStackTrace
    }
}

try {
    Test 'production client timeout and bounded response settings' {
        $client = & $module { New-TtrssHttpClient 17 }
        try {
            Assert ($client.Timeout.TotalSeconds -eq 17)
            Assert ($client.MaxResponseContentBufferSize -eq 4194304)
        }
        finally { $client.Dispose() }
    }

    & $module {
        function script:New-TtrssHttpClient {
            param([int]$Timeout)
            $client = New-Object System.Net.Http.HttpClient((New-Object TtrssFakeHandler))
            $client.Timeout = [TimeSpan]::FromSeconds($Timeout)
            $client.MaxResponseContentBufferSize = 4194304
            return $client
        }
    }

    Test 'check logs in, reports version/level and logs out without exposing session' {
        Queue-Login
        Queue-Reply @{ version = 'test-version' }
        Queue-Reply @{ level = 23 }
        Queue-Logout
        $body = Invoke-Client
        Assert $body.ok
        Assert ($body.profile -eq 'personal' -and $body.command -eq 'Check')
        Assert ($body.data.version -eq 'test-version' -and $body.data.apiLevel -eq 23)
        Assert ([TtrssFakeHandler]::Bodies.Count -eq 4)
        $login = ConvertFrom-Json ([TtrssFakeHandler]::Bodies[0])
        Assert ($login.op -eq 'login' -and $login.user -eq 'fixture-user' -and $login.password -eq 'fixture-password')
        Assert ([TtrssFakeHandler]::ContentTypes[0] -eq 'application/json; charset=utf-8')
        Assert ([TtrssFakeHandler]::Uris[0] -eq 'https://rss.example.net/tt-rss/api/')
        Assert ((ConvertFrom-Json ([TtrssFakeHandler]::Bodies[3])).op -eq 'logout')
    }

    Test 'explicit case-insensitive profile selects its own endpoint and dotenv keys' {
        Queue-Login; Queue-Reply @(); Queue-Logout
        $body = Invoke-Client @{ Command = 'GetFeeds'; Profile = 'TEST' }
        Assert $body.ok
        Assert ($body.profile -eq 'test')
        $login = ConvertFrom-Json ([TtrssFakeHandler]::Bodies[0])
        Assert ($login.user -eq 'fixture-test-user' -and $login.password -eq 'fixture-test-password')
        Assert ([TtrssFakeHandler]::Uris[0] -eq 'https://rss-test.example.net/api/')
        Assert ($body.data -is [array] -and $body.data.Count -eq 0)
    }

    Test 'dotenv BOM, CRLF, first equals, literal special characters and quoted spaces' {
        $envText = @'
# comment
 TTRSS_PERSONAL_USERNAME = ' fixture-user '
TTRSS_PERSONAL_PASSWORD="p#=$literal\path=more"
UNRELATED=value # literal inline content
'@
        Write-Fixture '.env' $envText $true
        Queue-Login; Queue-Reply @(); Queue-Logout
        $body = Invoke-Client @{ Command = 'GetFeeds' }
        Assert $body.ok
        $login = ConvertFrom-Json ([TtrssFakeHandler]::Bodies[0])
        Assert ($login.user -ceq ' fixture-user ')
        Assert ($login.password -ceq 'p#=$literal\path=more')
    }

    Test 'dotenv double quote inside single quotes and LF' {
        Write-Fixture '.env' "TTRSS_PERSONAL_USERNAME=fixture-user`nTTRSS_PERSONAL_PASSWORD='a`"b\c#=d'`n"
        Queue-Login; Queue-Reply @(); Queue-Logout
        Assert (Invoke-Client @{ Command = 'GetFeeds' }).ok
        Assert ((ConvertFrom-Json ([TtrssFakeHandler]::Bodies[0])).password -ceq 'a"b\c#=d')
    }

    foreach ($case in @(
        @{ Name = 'malformed dotenv'; Text = 'not an entry' },
        @{ Name = 'dotenv export rejected'; Text = 'export PASSWORD=secret' },
        @{ Name = 'duplicate dotenv'; Text = "KEY=a`nkey=b" },
        @{ Name = 'unmatched quotes'; Text = 'TTRSS_PERSONAL_PASSWORD="secret' },
        @{ Name = 'empty credentials'; Text = "TTRSS_PERSONAL_USERNAME=x`nTTRSS_PERSONAL_PASSWORD=" },
        @{ Name = 'whitespace credentials'; Text = "TTRSS_PERSONAL_USERNAME=x`nTTRSS_PERSONAL_PASSWORD='   '" }
    )) {
        Test $case.Name {
            Write-Fixture '.env' $case.Text
            Assert-Code (Invoke-Client) 'CONFIG_ERROR'
            Assert ([TtrssFakeHandler]::Bodies.Count -eq 0)
        }
    }

    Test 'missing dotenv does not fall back to inherited environment' {
        Remove-Item -LiteralPath (Join-Path $root '.env')
        $old = [Environment]::GetEnvironmentVariable('TTRSS_PERSONAL_PASSWORD', 'Process')
        try {
            [Environment]::SetEnvironmentVariable('TTRSS_PERSONAL_PASSWORD', 'inherited', 'Process')
            Assert-Code (Invoke-Client) 'CONFIG_ERROR'
            Assert ([TtrssFakeHandler]::Bodies.Count -eq 0)
        }
        finally { [Environment]::SetEnvironmentVariable('TTRSS_PERSONAL_PASSWORD', $old, 'Process') }
    }

    Test 'missing credential names reported without values' {
        Write-Fixture '.env' 'TTRSS_PERSONAL_USERNAME=fixture-user'
        $body = Invoke-Client
        Assert-Code $body 'CONFIG_ERROR'
        Assert ($body.error.message.Contains('TTRSS_PERSONAL_PASSWORD'))
    }

    Test 'missing JSON configuration' {
        Remove-Item -LiteralPath (Join-Path $root 'ttrss.config.json')
        Assert-Code (Invoke-Client) 'CONFIG_ERROR'
    }

    foreach ($case in @(
        @{ Name = 'malformed JSON'; Text = '{' },
        @{ Name = 'null JSON'; Text = 'null' },
        @{ Name = 'unknown property'; Text = '{"defaultProfile":"personal","profiles":{},"typo":1}' },
        @{ Name = 'empty profiles'; Text = '{"defaultProfile":"personal","profiles":{}}' },
        @{ Name = 'invalid profile name'; Text = '{"defaultProfile":"per-sonal","profiles":{}}' },
        @{ Name = 'case duplicate profiles'; Text = '{"defaultProfile":"a","profiles":{"a":{},"A":{}}}' },
        @{ Name = 'exact duplicate profiles'; Text = '{"defaultProfile":"a","profiles":{"a":{},"a":{}}}' },
        @{ Name = 'escaped duplicate keys'; Text = '{"defaultProfile":"a","profiles":{},"\u0070rofiles":{}}' }
    )) {
        Test $case.Name {
            Write-Fixture 'ttrss.config.json' $case.Text
            Assert-Code (Invoke-Client) 'CONFIG_ERROR'
            Assert ([TtrssFakeHandler]::Bodies.Count -eq 0)
        }
    }

    foreach ($badUrl in @('http://rss.example.net/api/', 'https://user:pass@rss.example.net/api/',
        'https://rss.example.net/api/?password=secret', 'https://rss.example.net/api/#fragment', 'relative/api/')) {
        Test "reject URL $badUrl" {
            Write-Fixture 'ttrss.config.json' ($script:baseConfig.Replace('https://rss.example.net/tt-rss/api/', $badUrl))
            Assert-Code (Invoke-Client) 'CONFIG_ERROR'
            Assert ([TtrssFakeHandler]::Bodies.Count -eq 0)
        }
    }

    foreach ($badTimeout in @('0', '301', '1.5', '"30"', 'true')) {
        Test "reject timeout $badTimeout" {
            Write-Fixture 'ttrss.config.json' ($script:baseConfig.Replace('"timeoutSeconds":30', ('"timeoutSeconds":' + $badTimeout)))
            Assert-Code (Invoke-Client) 'CONFIG_ERROR'
        }
    }

    foreach ($case in @(
        @{ Name = 'unknown command'; Options = @{ Command = 'DeleteFeed' } },
        @{ Name = 'invalid profile'; Options = @{ Profile = '../personal' } },
        @{ Name = 'empty explicit profile'; Options = @{ Profile = '' } },
        @{ Name = 'wrong command parameter'; Options = @{ Command = 'Check'; ArticleIds = '1' } },
        @{ Name = 'missing mutation guard'; Options = @{ Command = 'SetRead'; ArticleIds = '1' } },
        @{ Name = 'missing IDs'; Options = @{ Command = 'GetArticle' } },
        @{ Name = 'negative ID'; Options = @{ Command = 'GetArticle'; ArticleIds = '-1' } },
        @{ Name = 'zero ID'; Options = @{ Command = 'GetArticle'; ArticleIds = '0' } },
        @{ Name = 'overflow ID'; Options = @{ Command = 'GetArticle'; ArticleIds = '2147483648' } },
        @{ Name = 'duplicate ID'; Options = @{ Command = 'GetArticle'; ArticleIds = '1,1' } },
        @{ Name = 'too many IDs'; Options = @{ Command = 'GetArticle'; ArticleIds = ((1..101) -join ',') } },
        @{ Name = 'zero page size'; Options = @{ Command = 'GetHeadlines'; Limit = 0 } },
        @{ Name = 'page size over 200'; Options = @{ Command = 'GetHeadlines'; Limit = 201 } },
        @{ Name = 'negative offset'; Options = @{ Command = 'GetHeadlines'; Offset = -1 } },
        @{ Name = 'offset too large'; Options = @{ Command = 'GetHeadlines'; Offset = 1000001 } },
        @{ Name = 'invalid view mode'; Options = @{ Command = 'GetHeadlines'; ViewMode = 'invalid' } },
        @{ Name = 'diagnostics rejected'; Options = @{ Verbose = $true } }
    )) {
        Test $case.Name {
            Assert-Code (Invoke-Client $case.Options) 'ARGUMENT_ERROR'
            Assert ([TtrssFakeHandler]::Bodies.Count -eq 0)
        }
    }

    Test 'unknown profile never falls back' {
        Assert-Code (Invoke-Client @{ Profile = 'missing' }) 'CONFIG_ERROR'
        Assert ([TtrssFakeHandler]::Bodies.Count -eq 0)
    }

    Test 'feed pagination uses offset and category' {
        Queue-Login; Queue-Reply @(@{ id = 5 }); Queue-Logout
        Assert (Invoke-Client @{ Command = 'GetFeeds'; CategoryId = -3; Limit = 200; Offset = 1000000 }).ok
        $payload = ConvertFrom-Json ([TtrssFakeHandler]::Bodies[1])
        Assert ($payload.cat_id -eq -3 -and $payload.limit -eq 200 -and $payload.offset -eq 1000000)
        Assert ($payload.sid -eq 'fixture-session')
    }

    Test 'headline search uses skip and cannot mark read or force update' {
        Queue-Login; Queue-Reply @(@{ id = 123; unread = $true }); Queue-Logout
        $body = Invoke-Client @{ Command = 'GetHeadlines'; FeedId = -4; Search = 'release'; ViewMode = 'unread'; IsCategory = $true; Limit = 20; Offset = 40 }
        Assert $body.ok
        Assert $body.data[0].unread
        $payload = ConvertFrom-Json ([TtrssFakeHandler]::Bodies[1])
        Assert ($payload.op -eq 'getHeadlines' -and $payload.skip -eq 40 -and $payload.limit -eq 20)
        Assert ($payload.feed_id -eq -4 -and $payload.is_cat -and $payload.search -eq 'release')
        Assert (-not $payload.mark_read -and -not $payload.force_update -and $payload.sanitize)
        Assert ([TtrssFakeHandler]::Bodies.Count -eq 3)
    }

    Test 'article retrieval requests sanitization with explicit IDs' {
        Queue-Login; Queue-Reply @(@{ id = 1 }, @{ id = 2 }); Queue-Logout
        $body = Invoke-Client @{ Command = 'GetArticle'; ArticleIds = '1,2' }
        Assert ($body.ok -and $body.data.Count -eq 2)
        $payload = ConvertFrom-Json ([TtrssFakeHandler]::Bodies[1])
        Assert ($payload.op -eq 'getArticle' -and $payload.article_id -eq '1,2' -and $payload.sanitize)
    }

    Test 'categories include nested and empty' {
        Queue-Login; Queue-Reply @(); Queue-Logout
        Assert (Invoke-Client @{ Command = 'GetCategories' }).ok
        $payload = ConvertFrom-Json ([TtrssFakeHandler]::Bodies[1])
        Assert ($payload.enable_nested -and $payload.include_empty)
    }

    Test 'unread result preserves backend count' {
        Queue-Login; Queue-Reply @{ unread = '42' }; Queue-Logout
        $body = Invoke-Client @{ Command = 'GetUnread' }
        Assert ($body.ok -and $body.data.unread -eq '42')
    }

    foreach ($status in @(0, 1)) {
        foreach ($legacy in @($false, $true)) {
            Test "subscription status $status legacy=$legacy" {
                Queue-Login
                $reply = @{ code = $status; feed_id = 42 }
                if ($legacy) { $reply = $status }
                Queue-Reply @{ status = $reply }
                Queue-Logout
                $body = Invoke-Client @{ Command = 'SubscribeFeed'; FeedUrl = 'https://petapixel.com/feed/'; AllowMutation = $true }
                Assert $body.ok
                Assert ($body.data.statusCode -eq $status)
                if ($status -eq 0) { Assert ($body.data.outcome -eq 'alreadySubscribed') }
                else { Assert ($body.data.outcome -eq 'subscribed') }
                if (-not $legacy) { Assert ($body.data.feedId -eq 42) }
                $payload = ConvertFrom-Json ([TtrssFakeHandler]::Bodies[1])
                Assert ($payload.op -eq 'subscribeToFeed' -and $payload.category_id -eq 0)
                Assert ($payload.feed_url -eq 'https://petapixel.com/feed/')
                Assert ($payload.PSObject.Properties.Name -notcontains 'password')
                Assert ([TtrssFakeHandler]::Bodies.Count -eq 3)
            }
        }
    }

    Test 'subscription honors explicit category' {
        Queue-Login; Queue-Reply @{ status = @{ code = 1; feed_id = 42 } }; Queue-Logout
        Assert (Invoke-Client @{ Command = 'SubscribeFeed'; FeedUrl = 'https://petapixel.com/feed/'; CategoryId = 9; AllowMutation = $true }).ok
        Assert ((ConvertFrom-Json ([TtrssFakeHandler]::Bodies[1])).category_id -eq 9)
    }

    foreach ($code in 2..8) {
        foreach ($legacy in @($false, $true)) {
            Test "subscription failure $code legacy=$legacy is not API success" {
                Queue-Login
                $reply = @{ code = $code; message = 'private backend diagnostics' }
                if ($legacy) { $reply = $code }
                Queue-Reply @{ status = $reply }
                Queue-Logout
                $body = Invoke-Client @{ Command = 'SubscribeFeed'; FeedUrl = 'https://petapixel.com/feed/'; AllowMutation = $true }
                Assert-Code $body 'SUBSCRIPTION_FAILED'
                Assert (-not $body.error.message.Contains('private backend diagnostics'))
                Assert ([TtrssFakeHandler]::Bodies.Count -eq 3)
            }
        }
    }

    foreach ($reply in @(@{}, @{ status = '1' }, @{ status = 9 }, @{ status = @{ unexpected = 1 } })) {
        Test 'malformed subscription result fails' {
            Queue-Login; Queue-Reply $reply; Queue-Logout
            Assert-Code (Invoke-Client @{ Command = 'SubscribeFeed'; FeedUrl = 'https://petapixel.com/feed/'; AllowMutation = $true }) 'INVALID_RESPONSE'
        }
    }

    foreach ($options in @(
        @{ Command = 'SubscribeFeed'; FeedUrl = 'https://petapixel.com/feed/' },
        @{ Command = 'SubscribeFeed'; AllowMutation = $true },
        @{ Command = 'SubscribeFeed'; FeedUrl = 'http://example.net/feed'; AllowMutation = $true },
        @{ Command = 'SubscribeFeed'; FeedUrl = 'https://user:pass@example.net/feed'; AllowMutation = $true },
        @{ Command = 'SubscribeFeed'; FeedUrl = 'https://example.net/feed#fragment'; AllowMutation = $true },
        @{ Command = 'SubscribeFeed'; FeedUrl = 'https://example.net/feed'; CategoryId = -3; AllowMutation = $true },
        @{ Command = 'Check'; FeedUrl = 'https://example.net/feed' }
    )) {
        Test 'invalid subscription arguments fail before login' {
            Assert-Code (Invoke-Client $options) 'ARGUMENT_ERROR'
            Assert ([TtrssFakeHandler]::Bodies.Count -eq 0)
        }
    }

    Test 'subscription timeout never replays request and still logs out' {
        Queue-Login
        $fixture = New-Object TtrssFixture
        $fixture.Fail = $true
        [TtrssFakeHandler]::Responses.Enqueue($fixture)
        Queue-Logout
        Assert-Code (Invoke-Client @{ Command = 'SubscribeFeed'; FeedUrl = 'https://petapixel.com/feed/'; AllowMutation = $true }) 'TRANSPORT_ERROR'
        Assert ([TtrssFakeHandler]::Bodies.Count -eq 3)
        Assert ((ConvertFrom-Json ([TtrssFakeHandler]::Bodies[2])).op -eq 'logout')
    }

    foreach ($case in @(
        @{ Command = 'SetRead'; Field = 2; Mode = 0 },
        @{ Command = 'SetUnread'; Field = 2; Mode = 1 },
        @{ Command = 'SetStarred'; Field = 0; Mode = 1 },
        @{ Command = 'SetUnstarred'; Field = 0; Mode = 0 }
    )) {
        Test ("explicit mutation " + $case.Command) {
            Queue-Login; Queue-Reply @{ status = 'OK'; updated = 1 }; Queue-Logout
            $body = Invoke-Client @{ Command = $case.Command; ArticleIds = '123,124'; AllowMutation = $true }
            Assert ($body.ok -and $body.data.updated -eq 1)
            $payload = ConvertFrom-Json ([TtrssFakeHandler]::Bodies[1])
            Assert ($payload.op -eq 'updateArticle' -and $payload.article_ids -eq '123,124')
            Assert ($payload.field -eq $case.Field -and $payload.mode -eq $case.Mode)
        }
    }

    foreach ($code in @('API_DISABLED', 'LOGIN_ERROR', 'NOT_LOGGED_IN', 'UNKNOWN_METHOD',
        'INCORRECT_USAGE', 'E_OPERATION_FAILED', 'E_NOT_FOUND')) {
        Test "API error $code" {
            Queue-Reply @{ error = $code } 1
            Assert-Code (Invoke-Client) $code
            Assert ([TtrssFakeHandler]::Bodies.Count -eq 1)
        }
    }

    Test 'unknown server error is not echoed' {
        Queue-Reply @{ error = 'fixture-password fixture-session private details' } 1
        Assert-Code (Invoke-Client) 'API_ERROR'
    }

    Test 'expired session is not reauthenticated or replayed' {
        Queue-Login; Queue-Reply @{ error = 'NOT_LOGGED_IN' } 1; Queue-Logout
        Assert-Code (Invoke-Client @{ Command = 'GetFeeds' }) 'NOT_LOGGED_IN'
        Assert ([TtrssFakeHandler]::Bodies.Count -eq 3)
    }

    foreach ($http in @(301, 302, 307, 308, 401, 500)) {
        Test "HTTP status $http not retried" {
            Queue-Reply @{ private = 'fixture-password' } 0 $http
            $expected = 'HTTP_ERROR'
            if ($http -lt 400) { $expected = 'REDIRECT_REJECTED' }
            Assert-Code (Invoke-Client) $expected
            Assert ([TtrssFakeHandler]::Bodies.Count -eq 1)
        }
    }

    foreach ($badJson in @('<html>fixture-password</html>', '{}', 'null', '[]',
        '{"status":"0","content":[]}', '{"status":0}', '{"status":2,"content":{}}')) {
        Test "invalid response $badJson" {
            $fixture = New-Object TtrssFixture
            $fixture.Json = $badJson
            [TtrssFakeHandler]::Responses.Enqueue($fixture)
            Assert-Code (Invoke-Client) 'INVALID_RESPONSE'
        }
    }

    Test 'login without session fails without logout' {
        Queue-Reply @{ api_level = 23 }
        Assert-Code (Invoke-Client) 'INVALID_RESPONSE'
        Assert ([TtrssFakeHandler]::Bodies.Count -eq 1)
    }

    Test 'invalid list response fails and logs out' {
        Queue-Login; Queue-Reply @{ unexpected = 'object' }; Queue-Logout
        Assert-Code (Invoke-Client @{ Command = 'GetFeeds' }) 'INVALID_RESPONSE'
        Assert ([TtrssFakeHandler]::Bodies.Count -eq 3)
    }

    Test 'invalid mutation count fails and logs out' {
        Queue-Login; Queue-Reply @{ updated = 2 }; Queue-Logout
        Assert-Code (Invoke-Client @{ Command = 'SetRead'; ArticleIds = '1'; AllowMutation = $true }) 'INVALID_RESPONSE'
    }

    Test 'transport failure does not disclose exception details or retry' {
        $fixture = New-Object TtrssFixture
        $fixture.Fail = $true
        [TtrssFakeHandler]::Responses.Enqueue($fixture)
        $body = Invoke-Client
        Assert-Code $body 'TRANSPORT_ERROR'
        Assert (-not $body.error.message.Contains('Private transport details'))
        Assert ([TtrssFakeHandler]::Bodies.Count -eq 1)
    }

    Test 'oversized response is rejected by HTTP buffering limit' {
        $fixture = New-Object TtrssFixture
        $fixture.Json = 'x' * 4194305
        [TtrssFakeHandler]::Responses.Enqueue($fixture)
        Assert-Code (Invoke-Client) 'TRANSPORT_ERROR'
        Assert ([TtrssFakeHandler]::Bodies.Count -eq 1)
    }

    Test 'ambiguous mutation transport failure still logs out without replay' {
        Queue-Login
        $fixture = New-Object TtrssFixture
        $fixture.Fail = $true
        [TtrssFakeHandler]::Responses.Enqueue($fixture)
        Queue-Logout
        Assert-Code (Invoke-Client @{ Command = 'SetRead'; ArticleIds = '123'; AllowMutation = $true }) 'TRANSPORT_ERROR'
        Assert ([TtrssFakeHandler]::Bodies.Count -eq 3)
        Assert ((ConvertFrom-Json ([TtrssFakeHandler]::Bodies[2])).op -eq 'logout')
    }

    Test 'logout error preserves completed operation, never success shaped' {
        Queue-Login; Queue-Reply @{ updated = 1 }; Queue-Reply @{ error = 'NOT_LOGGED_IN' } 1
        $body = Invoke-Client @{ Command = 'SetStarred'; ArticleIds = '123'; AllowMutation = $true }
        Assert-Code $body 'NOT_LOGGED_IN'
        Assert ($body.operationSucceeded -and $body.data.updated -eq 1)
    }

    Test 'operation and cleanup failures are both visible' {
        Queue-Login; Queue-Reply @{ error = 'INCORRECT_USAGE' } 1; Queue-Reply @{ error = 'NOT_LOGGED_IN' } 1
        $body = Invoke-Client @{ Command = 'GetFeeds' }
        Assert-Code $body 'INCORRECT_USAGE'
        Assert ($body.cleanupError.code -eq 'NOT_LOGGED_IN')
    }

    Test 'redaction handles equal-length secrets, escaped values, nested data and JSON booleans' {
        Write-Fixture '.env' "TTRSS_PERSONAL_USERNAME=true`nTTRSS_PERSONAL_PASSWORD='a`"b\'`n"
        Queue-Login
        Queue-Reply @(@{ title = 'true and a"b\ and fixture-session'; unread = $true; nested = @('true') })
        Queue-Logout
        $result = Invoke-TtrssCommand -ProjectRoot $root -Options @{ Command = 'GetFeeds' }
        $body = ConvertFrom-Json $result.Json
        Assert $body.ok
        Assert $body.data[0].unread
        Assert ($body.data[0].title -eq '[REDACTED] and [REDACTED] and [REDACTED]')
        Assert ($body.data[0].nested[0] -eq '[REDACTED]')
    }

    Test 'credentials matching protocol words cannot corrupt the error schema' {
        Write-Fixture '.env' "TTRSS_PERSONAL_USERNAME=code`nTTRSS_PERSONAL_PASSWORD=LOGIN_ERROR`n"
        Queue-Reply @{ error = 'LOGIN_ERROR' } 1
        Assert-Code (Invoke-Client) 'LOGIN_ERROR'
    }

    Test 'script resolves configuration from its location outside working directory and exits nonzero' {
        $scriptPath = Join-Path $PSScriptRoot '..\scripts\ttrss.ps1'
        Push-Location $root
        try {
            $output = & powershell.exe -NoProfile -File $scriptPath -Profile MissingProfile -Command Check
            Assert ($LASTEXITCODE -eq 1)
            $body = ConvertFrom-Json ($output -join "`n")
            Assert-Code $body 'CONFIG_ERROR'
        }
        finally { Pop-Location }
    }

    Write-Output ("Tests: {0} passed, {1} failed." -f $script:passed, $script:failed)
    if ($script:failed -gt 0) { exit 1 }
}
finally {
    Remove-Item -LiteralPath (Join-Path $root 'ttrss.config.json') -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $root '.env') -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $root -Force
}
exit 0

# =============================================================================
# scripts/tests/Test-StartPs1.ps1
# Property tests cho PowerShell: URL extraction và .env update
# Validates: Requirements 2.1, 2.2, 3.1, 3.2, 7.2, 7.3
# =============================================================================
# Property 1: URL extraction từ log
#   For any log string, Extract-TunnelUrl returns a URL if and only if
#   a valid trycloudflare.com URL is present in the log.
#
# Property 2: .env update không phá vỡ các biến khác
#   For any valid .env file, after updating VITE_API_BASE_URL, VITE_WS_URL,
#   and CORS_ALLOWED_ORIGINS, all other variables retain their original values.
#
# Property 3: Derived URLs có scheme đúng
#   For any valid tunnel URL https://xxx.trycloudflare.com:
#   - VITE_API_BASE_URL starts with https:// and ends with /api/v1
#   - VITE_WS_URL starts with wss:// and ends with /ws
#   - CORS_ALLOWED_ORIGINS equals exactly the tunnel URL
#
# Self-contained PowerShell property test — no external dependencies.
# Runs at least 50 iterations per property.
# =============================================================================

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# =============================================================================
# Inline helpers (mirrors logic in scripts/start.ps1)
# =============================================================================

function Extract-TunnelUrl {
    param([string]$LogContent)
    $match = [regex]::Match($LogContent, 'https://[a-z0-9-]+\.trycloudflare\.com')
    if ($match.Success) { return $match.Value }
    return $null
}

function Apply-EnvUpdate {
    param(
        [string]$EnvFile,
        [string]$TunnelUrl
    )
    $apiUrl  = "$TunnelUrl/api/v1"
    $wsUrl   = ($TunnelUrl -replace '^https://', 'wss://') + "/ws"
    $corsUrl = $TunnelUrl

    $content = Get-Content $EnvFile -Raw
    $content = $content -replace '(?m)^VITE_API_BASE_URL=.*$',    "VITE_API_BASE_URL=$apiUrl"
    $content = $content -replace '(?m)^VITE_WS_URL=.*$',          "VITE_WS_URL=$wsUrl"
    $content = $content -replace '(?m)^CORS_ALLOWED_ORIGINS=.*$', "CORS_ALLOWED_ORIGINS=$corsUrl"
    [System.IO.File]::WriteAllText((Resolve-Path $EnvFile).Path, $content, [System.Text.UTF8Encoding]::new($false))
}

# =============================================================================
# Generators
# =============================================================================

function New-RandomWord {
    $chars = 'abcdefghijklmnopqrstuvwxyz'
    $len = Get-Random -Minimum 3 -Maximum 9
    -join (1..$len | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })
}

function New-ValidTunnelUrl {
    $w1 = New-RandomWord
    $w2 = New-RandomWord
    $w3 = New-RandomWord
    return "https://$w1-$w2-$w3.trycloudflare.com"
}

function New-LogNoiseLine {
    $lines = @(
        "2024/01/15 10:23:45 INF Starting tunnel",
        "2024/01/15 10:23:46 INF Registered tunnel connection",
        "INFO: Tunnel established",
        "[cloudflared] connection ready",
        "Starting tunnel daemon",
        "Connecting to Cloudflare edge",
        "Tunnel ID: abc123def456"
    )
    return $lines[(Get-Random -Maximum $lines.Count)]
}

function New-LogWithUrl {
    param([string]$Url)
    $noise1 = New-LogNoiseLine
    $noise2 = New-LogNoiseLine
    return "$noise1`n$(New-LogNoiseLine) $Url`n$noise2"
}

function New-LogWithoutUrl {
    $noise1 = New-LogNoiseLine
    $noise2 = New-LogNoiseLine
    $noise3 = New-LogNoiseLine
    return "$noise1`n$noise2`n$noise3"
}

function New-RandomEnvKey {
    $keys = @("DB_HOST","DB_PORT","APP_NAME","LOG_LEVEL","DEBUG_MODE","REDIS_HOST","REDIS_PORT","JWT_SECRET","APP_PORT","FEATURE_FLAG")
    return $keys[(Get-Random -Maximum $keys.Count)]
}

function New-RandomEnvValue {
    $values = @("hello","world","foo","bar","baz","qux","test","value","example","data","config","secret","key","token")
    return $values[(Get-Random -Maximum $values.Count)]
}

function New-RandomEnvFile {
    param([string]$Path)
    $targetKeys = @("VITE_API_BASE_URL","VITE_WS_URL","CORS_ALLOWED_ORIGINS")
    $lines = @(
        "VITE_API_BASE_URL=https://old-placeholder.trycloudflare.com/api/v1",
        "VITE_WS_URL=wss://old-placeholder.trycloudflare.com/ws",
        "CORS_ALLOWED_ORIGINS=https://old-placeholder.trycloudflare.com"
    )
    $extraCount = Get-Random -Minimum 2 -Maximum 7
    $usedKeys = [System.Collections.Generic.HashSet[string]]::new()
    for ($i = 0; $i -lt $extraCount; $i++) {
        $key = New-RandomEnvKey
        if (-not $usedKeys.Contains($key) -and $key -notin $targetKeys) {
            $val = New-RandomEnvValue
            $lines += "$key=$val"
            [void]$usedKeys.Add($key)
        }
    }
    $lines -join "`n" | Set-Content -Path $Path -NoNewline -Encoding UTF8
}

# =============================================================================
# Test framework
# =============================================================================
$script:Pass   = 0
$script:Fail   = 0
$script:Errors = [System.Collections.Generic.List[string]]::new()

function Assert-Equals {
    param([string]$Expected, [string]$Actual, [string]$Msg)
    if ($Expected -eq $Actual) {
        $script:Pass++
    } else {
        $script:Fail++
        $script:Errors.Add("  FAIL: $Msg`n    expected: '$Expected'`n    actual:   '$Actual'")
    }
}

function Assert-Empty {
    param([string]$Actual, [string]$Msg)
    if ([string]::IsNullOrEmpty($Actual)) {
        $script:Pass++
    } else {
        $script:Fail++
        $script:Errors.Add("  FAIL: $Msg`n    expected empty, got: '$Actual'")
    }
}

function Assert-NotEmpty {
    param([string]$Actual, [string]$Msg)
    if (-not [string]::IsNullOrEmpty($Actual)) {
        $script:Pass++
    } else {
        $script:Fail++
        $script:Errors.Add("  FAIL: $Msg`n    expected non-empty, got empty")
    }
}

function Assert-StartsWith {
    param([string]$Prefix, [string]$Actual, [string]$Msg)
    if ($Actual.StartsWith($Prefix)) {
        $script:Pass++
    } else {
        $script:Fail++
        $script:Errors.Add("  FAIL: $Msg`n    expected prefix: '$Prefix'`n    actual: '$Actual'")
    }
}

function Assert-EndsWith {
    param([string]$Suffix, [string]$Actual, [string]$Msg)
    if ($Actual.EndsWith($Suffix)) {
        $script:Pass++
    } else {
        $script:Fail++
        $script:Errors.Add("  FAIL: $Msg`n    expected suffix: '$Suffix'`n    actual: '$Actual'")
    }
}

# =============================================================================
# Unit Tests (deterministic)
# =============================================================================
Write-Host "=== Unit Tests ==="

# --- Property 1: URL extraction ---

# Exact valid URL
$r = Extract-TunnelUrl -LogContent "https://abc-def-ghi.trycloudflare.com"
Assert-Equals "https://abc-def-ghi.trycloudflare.com" "$r" "exact valid URL"

# URL embedded in log line
$r = Extract-TunnelUrl -LogContent "2024/01/15 10:23:46 INF +config=https://abc-def-ghi.trycloudflare.com tunnel=quick"
Assert-Equals "https://abc-def-ghi.trycloudflare.com" "$r" "URL embedded in log line"

# URL with numbers in subdomain
$r = Extract-TunnelUrl -LogContent "tunnel url: https://abc123-def456-ghi789.trycloudflare.com"
Assert-Equals "https://abc123-def456-ghi789.trycloudflare.com" "$r" "URL with numbers in subdomain"

# Empty string → null
$r = Extract-TunnelUrl -LogContent ""
Assert-Empty "$r" "empty string returns null/empty"

# Log without URL → null
$r = Extract-TunnelUrl -LogContent "2024/01/15 10:23:45 INF Starting tunnel daemon"
Assert-Empty "$r" "log without URL returns null/empty"

# Wrong domain → null
$r = Extract-TunnelUrl -LogContent "https://abc-def-ghi.cloudflare.com"
Assert-Empty "$r" "wrong domain returns null/empty"

# http:// (not https://) → null
$r = Extract-TunnelUrl -LogContent "http://abc-def-ghi.trycloudflare.com"
Assert-Empty "$r" "http scheme returns null/empty"

# Uppercase URL → null (pattern requires lowercase)
$r = Extract-TunnelUrl -LogContent "https://ABC-DEF-GHI.trycloudflare.com"
Assert-Empty "$r" "uppercase URL returns null/empty"

# Multiple URLs → returns first
$multiLog = "https://first-url-one.trycloudflare.com`nhttps://second-url-two.trycloudflare.com"
$r = Extract-TunnelUrl -LogContent $multiLog
Assert-Equals "https://first-url-one.trycloudflare.com" "$r" "multiple URLs returns first"

# --- Property 2 & 3: .env update ---
$tmpDir = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "kolla-ps1-tests-$([System.Guid]::NewGuid().ToString('N'))")
New-Item -ItemType Directory -Path $tmpDir | Out-Null

try {
    # Basic update of all three target variables
    $envFile = Join-Path $tmpDir "test_basic.env"
    "VITE_API_BASE_URL=https://old.trycloudflare.com/api/v1`nVITE_WS_URL=wss://old.trycloudflare.com/ws`nCORS_ALLOWED_ORIGINS=https://old.trycloudflare.com`nOTHER_VAR=unchanged" | Set-Content $envFile -NoNewline -Encoding UTF8
    Apply-EnvUpdate -EnvFile $envFile -TunnelUrl "https://new-abc-def.trycloudflare.com"
    $content = Get-Content $envFile -Raw
    $apiLine  = ($content -split "`n" | Where-Object { $_ -match '^VITE_API_BASE_URL=' }) -replace '^VITE_API_BASE_URL=', ''
    $wsLine   = ($content -split "`n" | Where-Object { $_ -match '^VITE_WS_URL=' }) -replace '^VITE_WS_URL=', ''
    $corsLine = ($content -split "`n" | Where-Object { $_ -match '^CORS_ALLOWED_ORIGINS=' }) -replace '^CORS_ALLOWED_ORIGINS=', ''
    $otherLine= ($content -split "`n" | Where-Object { $_ -match '^OTHER_VAR=' }) -replace '^OTHER_VAR=', ''
    Assert-Equals "https://new-abc-def.trycloudflare.com/api/v1" $apiLine.Trim()  "basic: VITE_API_BASE_URL updated"
    Assert-Equals "wss://new-abc-def.trycloudflare.com/ws"       $wsLine.Trim()   "basic: VITE_WS_URL updated"
    Assert-Equals "https://new-abc-def.trycloudflare.com"        $corsLine.Trim() "basic: CORS_ALLOWED_ORIGINS updated"
    Assert-Equals "unchanged"                                     $otherLine.Trim() "basic: OTHER_VAR not changed"

    # VITE_WS_URL scheme conversion https → wss
    $envFile2 = Join-Path $tmpDir "test_scheme.env"
    "VITE_API_BASE_URL=placeholder`nVITE_WS_URL=placeholder`nCORS_ALLOWED_ORIGINS=placeholder" | Set-Content $envFile2 -NoNewline -Encoding UTF8
    Apply-EnvUpdate -EnvFile $envFile2 -TunnelUrl "https://abc-def-ghi.trycloudflare.com"
    $wsLine2 = ((Get-Content $envFile2 -Raw) -split "`n" | Where-Object { $_ -match '^VITE_WS_URL=' }) -replace '^VITE_WS_URL=', ''
    Assert-StartsWith "wss://" $wsLine2.Trim() "scheme: VITE_WS_URL starts with wss://"
    Assert-EndsWith   "/ws"    $wsLine2.Trim() "scheme: VITE_WS_URL ends with /ws"

    # CORS_ALLOWED_ORIGINS has no trailing slash or path
    $envFile3 = Join-Path $tmpDir "test_cors.env"
    "VITE_API_BASE_URL=placeholder`nVITE_WS_URL=placeholder`nCORS_ALLOWED_ORIGINS=placeholder" | Set-Content $envFile3 -NoNewline -Encoding UTF8
    Apply-EnvUpdate -EnvFile $envFile3 -TunnelUrl "https://xyz-abc-def.trycloudflare.com"
    $corsLine3 = ((Get-Content $envFile3 -Raw) -split "`n" | Where-Object { $_ -match '^CORS_ALLOWED_ORIGINS=' }) -replace '^CORS_ALLOWED_ORIGINS=', ''
    Assert-Equals "https://xyz-abc-def.trycloudflare.com" $corsLine3.Trim() "cors: no trailing slash or path"

    # Comment lines preserved
    $envFile4 = Join-Path $tmpDir "test_comments.env"
    "# This is a comment`nVITE_API_BASE_URL=old`nVITE_WS_URL=old`nCORS_ALLOWED_ORIGINS=old`n# Another comment`nDB_HOST=localhost" | Set-Content $envFile4 -NoNewline -Encoding UTF8
    Apply-EnvUpdate -EnvFile $envFile4 -TunnelUrl "https://test-url-one.trycloudflare.com"
    $commentCount = (Get-Content $envFile4 | Where-Object { $_ -match '^#' }).Count
    Assert-Equals "2" "$commentCount" "comments: comment lines preserved"
    $dbHost = ((Get-Content $envFile4 -Raw) -split "`n" | Where-Object { $_ -match '^DB_HOST=' }) -replace '^DB_HOST=', ''
    Assert-Equals "localhost" $dbHost.Trim() "comments: DB_HOST unchanged"

    Write-Host "Unit tests: $($script:Pass) passed, $($script:Fail) failed"

    # =============================================================================
    # Property Tests (randomized, 50 iterations each)
    # =============================================================================
    Write-Host ""
    Write-Host "=== Property Tests (50 iterations each) ==="

    $propPass   = 0
    $propFail   = 0
    $propErrors = [System.Collections.Generic.List[string]]::new()

    # Property 1a: If log contains a valid URL, Extract-TunnelUrl returns that URL
    Write-Host "Property 1a: extract returns URL when URL is present..."
    for ($i = 0; $i -lt 50; $i++) {
        $url = New-ValidTunnelUrl
        $log = New-LogWithUrl -Url $url
        $result = Extract-TunnelUrl -LogContent $log
        if ($result -eq $url) {
            $propPass++
        } else {
            $propFail++
            $propErrors.Add("  FAIL P1a iter $i`: url='$url', got='$result'")
        }
    }

    # Property 1b: If log does NOT contain a valid URL, Extract-TunnelUrl returns null/empty
    Write-Host "Property 1b: extract returns empty when no URL is present..."
    for ($i = 0; $i -lt 50; $i++) {
        $log = New-LogWithoutUrl
        $result = Extract-TunnelUrl -LogContent $log
        if ([string]::IsNullOrEmpty($result)) {
            $propPass++
        } else {
            $propFail++
            $propErrors.Add("  FAIL P1b iter $i`: expected empty, got='$result'")
        }
    }

    # Property 1c: Result (when non-empty) always starts with https:// and ends with .trycloudflare.com
    Write-Host "Property 1c: extracted URL always has correct format..."
    for ($i = 0; $i -lt 50; $i++) {
        $url = New-ValidTunnelUrl
        $log = New-LogWithUrl -Url $url
        $result = Extract-TunnelUrl -LogContent $log
        if (-not [string]::IsNullOrEmpty($result)) {
            if ($result.StartsWith("https://") -and $result.EndsWith(".trycloudflare.com")) {
                $propPass++
            } else {
                $propFail++
                $propErrors.Add("  FAIL P1c iter $i`: result='$result' has wrong format")
            }
        } else {
            $propFail++
            $propErrors.Add("  FAIL P1c iter $i`: expected URL, got empty (url='$url')")
        }
    }

    # Property 2: .env update không phá vỡ các biến khác
    Write-Host "Property 2: non-target variables retain original values..."
    for ($i = 0; $i -lt 50; $i++) {
        $envFileP2 = Join-Path $tmpDir "p2_iter_$i.env"
        New-RandomEnvFile -Path $envFileP2

        # Capture non-target lines before update
        $before = (Get-Content $envFileP2 | Where-Object { $_ -notmatch '^VITE_API_BASE_URL=|^VITE_WS_URL=|^CORS_ALLOWED_ORIGINS=' }) -join "`n"

        $tunnelUrl = New-ValidTunnelUrl
        Apply-EnvUpdate -EnvFile $envFileP2 -TunnelUrl $tunnelUrl

        # Capture non-target lines after update
        $after = (Get-Content $envFileP2 | Where-Object { $_ -notmatch '^VITE_API_BASE_URL=|^VITE_WS_URL=|^CORS_ALLOWED_ORIGINS=' }) -join "`n"

        if ($before -eq $after) {
            $propPass++
        } else {
            $propFail++
            $propErrors.Add("  FAIL P2 iter $i`: non-target vars changed`n    before: $before`n    after:  $after")
        }
    }

    # Property 3: Derived URLs có scheme đúng
    Write-Host "Property 3: derived URLs have correct schemes and paths..."
    for ($i = 0; $i -lt 50; $i++) {
        $envFileP3 = Join-Path $tmpDir "p3_iter_$i.env"
        "VITE_API_BASE_URL=placeholder`nVITE_WS_URL=placeholder`nCORS_ALLOWED_ORIGINS=placeholder" | Set-Content $envFileP3 -NoNewline -Encoding UTF8

        $tunnelUrl = New-ValidTunnelUrl
        Apply-EnvUpdate -EnvFile $envFileP3 -TunnelUrl $tunnelUrl

        $lines   = Get-Content $envFileP3
        $apiUrl  = ($lines | Where-Object { $_ -match '^VITE_API_BASE_URL=' }) -replace '^VITE_API_BASE_URL=', ''
        $wsUrl   = ($lines | Where-Object { $_ -match '^VITE_WS_URL=' }) -replace '^VITE_WS_URL=', ''
        $corsUrl = ($lines | Where-Object { $_ -match '^CORS_ALLOWED_ORIGINS=' }) -replace '^CORS_ALLOWED_ORIGINS=', ''

        $iterPass = $true

        # P3a: VITE_API_BASE_URL starts with https://
        if (-not $apiUrl.Trim().StartsWith("https://")) {
            $iterPass = $false
            $propErrors.Add("  FAIL P3a iter $i`: VITE_API_BASE_URL='$apiUrl' does not start with https://")
        }
        # P3b: VITE_API_BASE_URL ends with /api/v1
        if (-not $apiUrl.Trim().EndsWith("/api/v1")) {
            $iterPass = $false
            $propErrors.Add("  FAIL P3b iter $i`: VITE_API_BASE_URL='$apiUrl' does not end with /api/v1")
        }
        # P3c: VITE_WS_URL starts with wss://
        if (-not $wsUrl.Trim().StartsWith("wss://")) {
            $iterPass = $false
            $propErrors.Add("  FAIL P3c iter $i`: VITE_WS_URL='$wsUrl' does not start with wss://")
        }
        # P3d: VITE_WS_URL ends with /ws
        if (-not $wsUrl.Trim().EndsWith("/ws")) {
            $iterPass = $false
            $propErrors.Add("  FAIL P3d iter $i`: VITE_WS_URL='$wsUrl' does not end with /ws")
        }
        # P3e: CORS_ALLOWED_ORIGINS equals exactly the tunnel URL
        if ($corsUrl.Trim() -ne $tunnelUrl) {
            $iterPass = $false
            $propErrors.Add("  FAIL P3e iter $i`: CORS_ALLOWED_ORIGINS='$corsUrl' != tunnel_url='$tunnelUrl'")
        }

        if ($iterPass) { $propPass++ } else { $propFail++ }
    }

    Write-Host "Property tests: $propPass passed, $propFail failed"

    # =============================================================================
    # Summary
    # =============================================================================
    Write-Host ""
    Write-Host "=== Summary ==="
    $totalPass = $script:Pass + $propPass
    $totalFail = $script:Fail + $propFail
    Write-Host "Total: $totalPass passed, $totalFail failed"

    if ($script:Errors.Count -gt 0) {
        Write-Host ""
        Write-Host "Unit test failures:"
        $script:Errors | ForEach-Object { Write-Host $_ }
    }

    if ($propErrors.Count -gt 0) {
        Write-Host ""
        Write-Host "Property test failures:"
        $propErrors | ForEach-Object { Write-Host $_ }
    }

    if ($totalFail -gt 0) {
        Write-Host ""
        Write-Host "❌ Tests FAILED"
        exit 1
    } else {
        Write-Host ""
        Write-Host "✅ All tests PASSED"
        exit 0
    }

} finally {
    Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
}

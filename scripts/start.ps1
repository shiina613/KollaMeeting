# =============================================================================
# scripts/start.ps1 - Kolla Meeting startup script (Windows PowerShell)
# =============================================================================
# Khoi dong toan bo stack Kolla Meeting voi Cloudflare Tunnel.
# Chay tu thu muc goc cua project (noi co docker-compose.yml).
# KHONG yeu cau quyen Administrator.
#
# Usage:
#   .\scripts\start.ps1
# =============================================================================

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# =============================================================================
# Helper: Extract-TunnelUrl
# =============================================================================
function Extract-TunnelUrl {
    param(
        [Parameter(Mandatory = $true)]
        [string]$LogContent
    )
    $match = [regex]::Match($LogContent, 'https://[a-z0-9-]+\.trycloudflare\.com')
    if ($match.Success) {
        return $match.Value
    }
    return $null
}

# =============================================================================
# Check-DockerRunning - Kiem tra Docker Desktop dang chay
# =============================================================================
function Check-DockerRunning {
    Write-Host "[*] Kiem tra Docker Desktop..."
    $dockerProcess = Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue
    if ($null -eq $dockerProcess) {
        Write-Host "[!] Docker Desktop chua chay. Dang khoi dong Docker Desktop..."
        $dockerDesktopPaths = @(
            "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
            "$env:LOCALAPPDATA\Programs\Docker\Docker\Docker Desktop.exe"
        )
        $launched = $false
        foreach ($path in $dockerDesktopPaths) {
            if (Test-Path $path) {
                Start-Process -FilePath $path
                $launched = $true
                Write-Host "    Docker Desktop dang khoi dong tu: $path"
                break
            }
        }
        if (-not $launched) {
            Write-Host "[ERROR] Khong tim thay Docker Desktop. Hay cai dat tu https://www.docker.com/products/docker-desktop"
            exit 1
        }
    } else {
        Write-Host "[OK] Docker Desktop dang chay."
    }
}

# =============================================================================
# Wait-DockerDaemon - Cho Docker daemon san sang (timeout 60s)
# =============================================================================
function Wait-DockerDaemon {
    Write-Host "[*] Cho Docker daemon san sang (timeout 60s)..."
    $timeout = 60
    $elapsed = 0
    $interval = 5

    while ($elapsed -lt $timeout) {
        cmd /c "docker info > nul 2>&1"
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[OK] Docker daemon san sang."
            return
        }
        Write-Host "    Cho Docker daemon... ($elapsed/$timeout giay)"
        Start-Sleep -Seconds $interval
        $elapsed += $interval
    }

    Write-Host "[ERROR] Docker daemon khong san sang sau 60s. Hay kiem tra Docker Desktop."
    exit 1
}

# =============================================================================
# Start-Services - Khoi dong tat ca services tru frontend
# =============================================================================
function Start-Services {
    Write-Host "[*] Khoi dong services (tru frontend)..."
    # Dung cmd /c de tranh PowerShell bat stderr cua docker thanh exception
    cmd /c "docker compose up -d --scale frontend=0"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Khong the khoi dong services. Chay thu: docker compose up -d --scale frontend=0"
        exit 1
    }
    Write-Host "[OK] Services da khoi dong."
}

# =============================================================================
# Get-TunnelUrl - Poll log cloudflared, extract URL (timeout 30s)
# =============================================================================
function Get-TunnelUrl {
    Write-Host "[*] Dang cho cloudflared sinh URL (timeout 30s)..."
    $timeout = 30
    $elapsed = 0
    $interval = 2

    while ($elapsed -lt $timeout) {
        # Dung cmd /c de gop ca stdout va stderr ma khong trigger PowerShell exception
        $logs = cmd /c "docker logs kolla-cloudflared 2>&1"
        $logsStr = $logs -join "`n"
        $url = Extract-TunnelUrl -LogContent $logsStr
        if ($null -ne $url -and $url -ne "") {
            return $url
        }
        Start-Sleep -Seconds $interval
        $elapsed += $interval
    }

    Write-Host "[ERROR] cloudflared khong sinh URL sau 30s. Kiem tra: docker logs kolla-cloudflared"
    exit 1
}

# =============================================================================
# Update-EnvFile - Cap nhat .env voi tunnel URL moi
# =============================================================================
function Update-EnvFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TunnelUrl
    )

    $envFile = ".env"
    if (-not (Test-Path $envFile)) {
        Write-Host "[ERROR] .env khong tim thay. Chay: Copy-Item .env.example .env"
        exit 1
    }

    Write-Host "[*] Cap nhat .env voi tunnel URL: $TunnelUrl"

    $apiUrl  = "$TunnelUrl/api/v1"
    $wsUrl   = ($TunnelUrl -replace '^https://', 'wss://') + "/ws"
    $corsUrl = $TunnelUrl

    $content = Get-Content $envFile -Raw

    $content = $content -replace '(?m)^VITE_API_BASE_URL=.*', "VITE_API_BASE_URL=$apiUrl"
    $content = $content -replace '(?m)^VITE_WS_URL=.*',       "VITE_WS_URL=$wsUrl"
    $content = $content -replace '(?m)^CORS_ALLOWED_ORIGINS=.*', "CORS_ALLOWED_ORIGINS=$corsUrl"

    [System.IO.File]::WriteAllText((Resolve-Path $envFile).Path, $content, [System.Text.UTF8Encoding]::new($false))

    Write-Host "[OK] .env da duoc cap nhat."
}

# =============================================================================
# Build-Frontend - Rebuild va khoi dong frontend container
# =============================================================================
function Build-Frontend {
    Write-Host "[*] Rebuild frontend container..."
    cmd /c "docker compose up -d --build frontend"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Khong the build frontend. Chay thu: docker compose up -d --build frontend"
        exit 1
    }
    Write-Host "[OK] Frontend da duoc build va khoi dong."
}

# =============================================================================
# Print-Success - In thong bao thanh cong
# =============================================================================
function Print-Success {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TunnelUrl
    )
    Write-Host ""
    Write-Host ">>> Kolla dang chay tai: $TunnelUrl"
    Write-Host ""
}

# =============================================================================
# Main
# =============================================================================
function Main {
    Check-DockerRunning
    Wait-DockerDaemon
    Start-Services

    $tunnelUrl = Get-TunnelUrl

    Update-EnvFile -TunnelUrl $tunnelUrl
    Build-Frontend
    Print-Success -TunnelUrl $tunnelUrl
}

Main

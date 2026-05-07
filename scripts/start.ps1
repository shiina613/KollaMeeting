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
# NOTE: Do NOT set ErrorActionPreference=Stop globally — Docker writes build progress
# to stderr which PowerShell would treat as a terminating error. Each critical
# step checks $LASTEXITCODE explicitly via cmd /c instead.
$ErrorActionPreference = "Continue"

# =============================================================================
# Helper: Extract-TunnelUrl
# =============================================================================
function Extract-TunnelUrl {
    param(
        [Parameter(Mandatory = $false)]
        [AllowEmptyString()]
        [string]$LogContent = ""
    )
    if ([string]::IsNullOrEmpty($LogContent)) { return $null }
    # Lay tat ca matches, tra ve cai cuoi cung (URL moi nhat trong log)
    $matches = [regex]::Matches($LogContent, 'https://[a-z0-9-]+\.trycloudflare\.com')
    if ($matches.Count -gt 0) {
        return $matches[$matches.Count - 1].Value
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
    Write-Host "[*] Build backend (apply migrations + code moi)..."
    # Dung cmd /c de absorb docker stderr (build progress) tranh PowerShell coi la loi
    cmd /c "docker compose build backend 2>&1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Khong the build backend. Chay thu: docker compose build backend"
        exit 1
    }
    Write-Host "[OK] Backend image da duoc build."

    Write-Host "[*] Khoi dong services (tru frontend)..."
    # Restart cloudflared truoc de dam bao lay duoc URL moi (xoa log cu)
    cmd /c "docker compose rm -sf cloudflared > nul 2>&1"
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

    # Dung ReadAllText thay vi Get-Content -Raw de tranh OutOfMemoryException
    # khi file .env bi corrupt/phinh to
    $content = [System.IO.File]::ReadAllText((Resolve-Path $envFile).Path, [System.Text.UTF8Encoding]::new($false))

    # Kiem tra kich thuoc file hop ly (< 1MB)
    $fileSize = (Get-Item $envFile).Length
    if ($fileSize -gt 1MB) {
        Write-Host "[ERROR] .env bi corrupt (kich thuoc: $([math]::Round($fileSize/1MB, 1)) MB). Xoa va tao lai tu .env.example"
        exit 1
    }

    $content = $content -replace '(?m)^VITE_API_BASE_URL=.*', "VITE_API_BASE_URL=$apiUrl"
    $content = $content -replace '(?m)^VITE_WS_URL=.*',       "VITE_WS_URL=$wsUrl"
    # NOTE: CORS_ALLOWED_ORIGINS is intentionally NOT updated here.
    # It is set to "*" in .env so it works regardless of Cloudflare tunnel URL rotation.
    # JWT token validation on every request provides the actual security boundary.

    [System.IO.File]::WriteAllText((Resolve-Path $envFile).Path, $content, [System.Text.UTF8Encoding]::new($false))

    Write-Host "[OK] .env da duoc cap nhat."
}

# =============================================================================
# Wait-Backend - Cho backend Spring Boot san sang (timeout 60s)
# =============================================================================
function Wait-Backend {
    Write-Host "[*] Cho backend khoi dong (timeout 120s)..."
    $timeout = 120
    $elapsed = 0
    $interval = 3

    while ($elapsed -lt $timeout) {
        $status = cmd /c "docker inspect kolla-backend --format={{.State.Health.Status}} 2>&1"
        if ($status -eq "healthy") {
            Write-Host "[OK] Backend san sang."
            return
        }
        Write-Host "    Backend dang khoi dong... ($elapsed/$timeout giay) [$status]"
        Start-Sleep -Seconds $interval
        $elapsed += $interval
    }

    Write-Host "[WARN] Backend chua healthy sau 120s, tiep tuc anyway..."
}
function Build-Frontend {
    Write-Host "[*] Rebuild frontend container (no cache)..."
    # --no-cache dam bao VITE_* env vars (VITE_JAAS_APP_ID, VITE_API_BASE_URL, v.v.)
    # luon duoc bake vao Vite bundle moi, khong bi dung lai tu cache cu
    cmd /c "docker compose build --no-cache frontend 2>&1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Khong the build frontend. Chay thu: docker compose build --no-cache frontend"
        exit 1
    }
    cmd /c "docker compose up -d frontend 2>&1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Khong the khoi dong frontend."
        exit 1
    }
    Write-Host "[OK] Frontend da duoc build va khoi dong."

    # Restart nginx de resolve lai DNS sau khi frontend container duoc tao moi
    # (nginx cache IP cu cua container truoc, gay ra 502 Bad Gateway)
    Write-Host "[*] Restart nginx de cap nhat DNS..."
    cmd /c "docker compose restart nginx > nul 2>&1"
    Write-Host "[OK] Nginx da duoc restart."
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
    Wait-Backend
    Build-Frontend
    Print-Success -TunnelUrl $tunnelUrl
}

Main

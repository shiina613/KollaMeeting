# =============================================================================
# scripts/start.ps1 — Kolla Meeting startup script (Windows PowerShell)
# =============================================================================
# Khởi động toàn bộ stack Kolla Meeting với Cloudflare Tunnel.
# Chạy từ thư mục gốc của project (nơi có docker-compose.yml).
# KHÔNG yêu cầu quyền Administrator.
#
# Usage:
#   .\scripts\start.ps1
#
# Steps:
#   1. Check-DockerRunning()   — Kiểm tra Docker Desktop process đang chạy
#   2. Wait-DockerDaemon()     — Chờ Docker daemon sẵn sàng (timeout 60s)
#   3. Start-Services()        — docker compose up -d (trừ frontend)
#   4. Get-TunnelUrl()         — Poll log cloudflared, extract URL (timeout 30s)
#   5. Update-EnvFile()        — Cập nhật .env với tunnel URL mới
#   6. Build-Frontend()        — docker compose up -d --build frontend
#   7. Print-Success()         — In tunnel URL ra màn hình
# =============================================================================

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# =============================================================================
# Helper: Extract-TunnelUrl
# Extract URL từ một chuỗi log (dùng cho testing và Get-TunnelUrl).
# Input:  $LogContent = chuỗi log (có thể nhiều dòng)
# Output: URL string, hoặc $null nếu không tìm thấy
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
# Subtask 5.1 — Check-DockerRunning
# Kiểm tra process Docker Desktop đang chạy.
# Requirements: 11.1, 11.4, 11.5
# =============================================================================
function Check-DockerRunning {
    Write-Host "🔍 Kiểm tra Docker Desktop..."
    $dockerProcess = Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue
    if ($null -eq $dockerProcess) {
        Write-Host "⚠️  Docker Desktop chưa chạy. Đang khởi động Docker Desktop..."
        # Tìm Docker Desktop executable
        $dockerDesktopPaths = @(
            "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
            "$env:LOCALAPPDATA\Programs\Docker\Docker\Docker Desktop.exe"
        )
        $launched = $false
        foreach ($path in $dockerDesktopPaths) {
            if (Test-Path $path) {
                Start-Process -FilePath $path
                $launched = $true
                Write-Host "   Docker Desktop đang khởi động từ: $path"
                break
            }
        }
        if (-not $launched) {
            Write-Host "❌ Không tìm thấy Docker Desktop. Hãy cài đặt Docker Desktop từ https://www.docker.com/products/docker-desktop"
            exit 1
        }
    } else {
        Write-Host "✅ Docker Desktop đang chạy."
    }
}

# =============================================================================
# Subtask 5.1 — Wait-DockerDaemon
# Poll docker info với timeout 60 giây, mỗi 5 giây.
# Requirements: 11.1, 11.4, 11.5
# =============================================================================
function Wait-DockerDaemon {
    Write-Host "⏳ Chờ Docker daemon sẵn sàng (timeout 60s)..."
    $timeout = 60
    $elapsed = 0
    $interval = 5

    while ($elapsed -lt $timeout) {
        $result = & docker info 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ Docker daemon đang sẵn sàng."
            return
        }
        Write-Host "   Chờ Docker daemon... ($elapsed/$timeout giây)"
        Start-Sleep -Seconds $interval
        $elapsed += $interval
    }

    Write-Host "❌ Docker daemon không sẵn sàng sau 60s. Hãy kiểm tra Docker Desktop."
    exit 1
}

# =============================================================================
# Subtask 5.2 — Start-Services
# Khởi động tất cả services trừ frontend.
# Requirements: 11.1
# =============================================================================
function Start-Services {
    Write-Host "🚀 Khởi động services (trừ frontend)..."
    $output = & docker compose up -d --scale frontend=0 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Không thể khởi động services. Output:"
        Write-Host $output
        exit 1
    }
    Write-Host "✅ Services đã khởi động."
}

# =============================================================================
# Subtask 5.3 — Get-TunnelUrl
# Poll log cloudflared để lấy tunnel URL, timeout 30 giây.
# Requirements: 2.1, 2.2, 2.3, 2.4
# =============================================================================
function Get-TunnelUrl {
    Write-Host "⏳ Đang chờ cloudflared sinh URL (timeout 30s)..."
    $timeout = 30
    $elapsed = 0
    $interval = 2

    while ($elapsed -lt $timeout) {
        $logs = & docker logs kolla-cloudflared 2>&1 | Out-String
        $url = Extract-TunnelUrl -LogContent $logs
        if ($null -ne $url -and $url -ne "") {
            return $url
        }
        Start-Sleep -Seconds $interval
        $elapsed += $interval
    }

    Write-Host "❌ cloudflared không sinh URL sau 30s. Kiểm tra: docker logs kolla-cloudflared"
    exit 1
}

# =============================================================================
# Subtask 5.4 — Update-EnvFile
# Cập nhật .env với tunnel URL mới.
# Requirements: 3.1, 3.2, 7.1, 7.2, 7.3
# =============================================================================
function Update-EnvFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TunnelUrl
    )

    $envFile = ".env"
    if (-not (Test-Path $envFile)) {
        Write-Host "❌ .env không tìm thấy. Chạy: Copy-Item .env.example .env"
        exit 1
    }

    Write-Host "📝 Cập nhật .env với tunnel URL: $TunnelUrl"

    # Derive URLs từ tunnel URL
    $apiUrl  = "$TunnelUrl/api/v1"
    $wsUrl   = ($TunnelUrl -replace '^https://', 'wss://') + "/ws"
    $corsUrl = $TunnelUrl

    # Đọc toàn bộ nội dung file
    $content = Get-Content $envFile -Raw

    # Dùng regex replace để cập nhật từng biến (không xóa các biến khác)
    $content = $content -replace '(?m)^VITE_API_BASE_URL=.*$', "VITE_API_BASE_URL=$apiUrl"
    $content = $content -replace '(?m)^VITE_WS_URL=.*$',       "VITE_WS_URL=$wsUrl"
    $content = $content -replace '(?m)^CORS_ALLOWED_ORIGINS=.*$', "CORS_ALLOWED_ORIGINS=$corsUrl"

    # Ghi lại file (giữ nguyên encoding, không thêm BOM)
    [System.IO.File]::WriteAllText((Resolve-Path $envFile).Path, $content, [System.Text.UTF8Encoding]::new($false))

    Write-Host "✅ .env đã được cập nhật."
}

# =============================================================================
# Subtask 5.6 — Build-Frontend
# Rebuild và khởi động frontend container.
# Requirements: 3.3
# =============================================================================
function Build-Frontend {
    Write-Host "🔨 Rebuild frontend container..."
    $output = & docker compose up -d --build frontend 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Không thể build frontend. Output:"
        Write-Host $output
        exit 1
    }
    Write-Host "✅ Frontend đã được build và khởi động."
}

# =============================================================================
# Subtask 5.6 — Print-Success
# In thông báo thành công kèm tunnel URL.
# Requirements: 3.4, 11.3
# =============================================================================
function Print-Success {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TunnelUrl
    )
    Write-Host ""
    Write-Host "✅ Kolla đang chạy tại: $TunnelUrl"
    Write-Host ""
}

# =============================================================================
# Main — Wire toàn bộ script
# Gọi các hàm theo thứ tự với error handling ở mỗi bước.
# Requirements: 3.4, 11.1, 11.3
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

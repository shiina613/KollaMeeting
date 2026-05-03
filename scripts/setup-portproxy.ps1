# setup-portproxy.ps1
# Tự động cập nhật portproxy khi WSL2 IP thay đổi sau restart
# Chạy script này với quyền Administrator

$wslIp = (wsl ip addr show eth0 2>&1 | Select-String "inet " | Select-Object -First 1).ToString().Trim()
$wslIp = ($wslIp -split '\s+')[1] -split '/' | Select-Object -First 1

Write-Host "WSL2 IP: $wslIp"

# Xóa rule cũ
netsh interface portproxy delete v4tov4 listenport=8443 listenaddress=0.0.0.0 2>$null
netsh interface portproxy delete v4tov4 listenport=8888 listenaddress=0.0.0.0 2>$null

# Thêm rule mới với IP hiện tại
netsh interface portproxy add v4tov4 listenport=8443 listenaddress=0.0.0.0 connectport=8443 connectaddress=$wslIp
netsh interface portproxy add v4tov4 listenport=8888 listenaddress=0.0.0.0 connectport=8888 connectaddress=$wslIp

Write-Host "Portproxy updated:"
netsh interface portproxy show all

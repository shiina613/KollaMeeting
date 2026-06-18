## Di chuyển đến thư mục gốc:


```powershell
cd KollaMeeting
```

## Chạy demo bằng một lệnh

Trước khi chạy lần đầu, copy `.env.example` sang `.env`. GitHub có thể chặn push `.env`, nên repo chỉ track `.env.example`; hai file nên giống nhau ở trạng thái nộp.

Windows PowerShell:

```powershell
Copy-Item .env.example .env -Force
.\scripts\start.ps1
```

WSL2/Linux:

```bash
cp .env.example .env
./scripts/start.sh
```

Script có nhiệm vụ:

1. Kiểm tra Docker đang chạy.
2. Đọc/cập nhật `.env`, sinh secret demo hoặc `keys/signing.p12` nếu thiếu.
3. Build/start backend, mysql, redis, asr-service, nginx và cloudflared.
4. Lấy Quick Tunnel URL từ log `cloudflared`.
5. Cập nhật URL browser cần dùng trong `.env`.
6. Rebuild/start frontend để Vite bake đúng URL mới.
7. In URL cuối cùng dạng `https://xxx.trycloudflare.com`.
# Cấu hình ký số PDF biên bản

Biên bản PDF được ký ở bước host/chủ trì xác nhận:

```text
POST /api/v1/meetings/{meetingId}/minutes/confirm
```

Code dùng iText signatures và BouncyCastle để nhúng chữ ký PDF dạng detached CAdES/PAdES-compatible. PDF được render bằng iText layout; DOCX được render bằng OOXML nội bộ. Chữ ký có vùng hiển thị ở cuối trang đầu tiên của PDF.

## Biến môi trường

```dotenv
DIGITAL_SIGNATURE_ENABLED=true
DIGITAL_SIGNATURE_KEYSTORE_PATH=/app/keys/signing.p12
DIGITAL_SIGNATURE_KEYSTORE_PASSWORD=kolla-signing-dev
DIGITAL_SIGNATURE_KEYSTORE_TYPE=PKCS12
DIGITAL_SIGNATURE_KEY_ALIAS=
DIGITAL_SIGNATURE_REASON=Xác nhận biên bản cuộc họp
DIGITAL_SIGNATURE_LOCATION=KollaMeeting
```

`keys/` chỉ dùng cho keystore local và bị ignore khỏi git với các đuôi khóa riêng tư. Với Docker Compose, thư mục local `./keys` được mount read-only vào `/app/keys`.

## Tạo keystore demo

Windows PowerShell:

```powershell
.\scripts\generate-signing-keystore.ps1
```

WSL2/Linux:

```bash
./scripts/generate-signing-keystore.sh
```

Keystore demo chỉ dùng để nghiệm thu kỹ thuật. Vận hành thật cần chứng thư số hợp lệ từ CA hoặc hạ tầng ký số được đơn vị chấp nhận.

## Kiểm tra chữ ký

Windows PowerShell:

```powershell
.\scripts\verify-pdf-signature.ps1 -PdfPath <path-to-signed.pdf>
```

Backend test liên quan:

- `PdfDigitalSignatureServiceTest`
- `PdfDigitalSignatureDisabledTest`
- `PdfDigitalSignatureDevKeystoreTest`
- `MinutesSignedPdfDigestPropertyTest`

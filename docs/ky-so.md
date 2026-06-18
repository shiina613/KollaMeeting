# Ký số trong KollaMeeting

## 1. Bản chất

Ký số là cơ chế dùng khóa bí mật để tạo chữ ký mật mã cho tài liệu, cho phép người khác dùng chứng thư/khóa công khai để kiểm tra:

- Tài liệu có đúng do chủ thể ký không.
- Tài liệu có bị sửa sau khi ký không.
- Người ký khó phủ nhận việc đã ký nếu khóa bí mật do họ kiểm soát.

Trong KollaMeeting, ký số được áp dụng cho PDF biên bản cuộc họp khi host xác nhận biên bản nháp.

## 2. File liên quan

Backend:

- `backend/src/main/java/com/example/kolla/services/PdfDigitalSignatureService.java`
- `backend/src/main/java/com/example/kolla/services/impl/MinutesServiceImpl.java`
- `backend/src/main/java/com/example/kolla/config/DigitalSignatureProperties.java`
- `backend/src/main/java/com/example/kolla/config/DigitalSignatureConfig.java`
- `backend/src/main/java/com/example/kolla/controllers/MinutesController.java`

Script/dev key:

- `scripts/generate-signing-keystore.ps1`
- `scripts/generate-signing-keystore.sh`
- `scripts/verify-pdf-signature.ps1`
- `keys/signing.p12`

Config:

- `.env`
- `.env.example`
- `docker-compose.yml`

## 3. Ký số nằm ở bước nào trong nghiệp vụ?

Luồng biên bản trong KollaMeeting:

1. Cuộc họp kết thúc.
2. Backend gom các `TranscriptionSegment`.
3. `MinutesServiceImpl.compileDraftMinutes()` tạo biên bản nháp PDF/DOCX.
4. Host mở biên bản nháp để kiểm tra.
5. Host gọi API confirm.
6. `MinutesServiceImpl.confirmMinutes()` kiểm tra người gọi có phải host không.
7. Backend đọc draft PDF.
8. `PdfDigitalSignatureService.signPdf()` ký PDF.
9. Backend lưu file `confirmed_{minutesId}.pdf`.
10. Backend lưu SHA-256 của PDF đã ký vào `hostConfirmationHash`.
11. Trạng thái minutes chuyển sang `HOST_CONFIRMED`.
12. Backend gửi notification và WebSocket event `MINUTES_CONFIRMED`.

Ký số không diễn ra khi AI tạo bản nháp. Nó chỉ diễn ra khi host xác nhận.

## 4. API liên quan

Theo README:

```text
POST /meetings/{id}/minutes/confirm
```

Controller gọi xuống service, service thực thi logic trong:

```text
MinutesServiceImpl.confirmMinutes(...)
```

Trong method này có các bước chính:

- Kiểm tra host.
- Kiểm tra minutes đang ở trạng thái `DRAFT`.
- Load draft PDF từ storage.
- Gọi `pdfDigitalSignatureService.signPdf(draftBytes, requester.getFullName())`.
- Tính SHA-256 của PDF đã ký.
- Lưu file signed PDF.
- Cập nhật status và metadata.

## 5. iText, Bouncy Castle và PAdES/CAdES

`PdfDigitalSignatureService` dùng:

- iText PDF Signatures.
- Bouncy Castle provider.
- Keystore PKCS#12 hoặc JKS.
- SHA-256.
- `PdfSigner.CryptoStandard.CADES`.

Trong code:

```java
IExternalDigest digest = new BouncyCastleDigest();
IExternalSignature signature = new PrivateKeySignature(
    material.privateKey(),
    DigestAlgorithms.SHA256,
    BouncyCastleProvider.PROVIDER_NAME);
```

Sau đó gọi:

```java
signer.signDetached(..., PdfSigner.CryptoStandard.CADES);
```

Về bản chất, PDF được nhúng chữ ký dạng detached signature. Nội dung PDF vẫn là tài liệu PDF, chữ ký nằm trong cấu trúc PDF để các phần mềm đọc PDF có thể kiểm tra.

## 6. Keystore hoạt động như thế nào?

Khi app start, `PdfDigitalSignatureService.loadKeystoreIfEnabled()` chạy sau khi bean được tạo.

Nếu `digital-signature.enabled=false`, service không load key.

Nếu bật, service:

1. Đọc `digital-signature.keystore-path`.
2. Load `KeyStore` theo type cấu hình, ví dụ `PKCS12`.
3. Dùng password để mở keystore.
4. Tìm alias chứa private key.
5. Lấy `PrivateKey`.
6. Lấy certificate chain.
7. Lưu vào biến `signingMaterial`.

Khi ký, service dùng private key và certificate chain này để tạo chữ ký PDF.

## 7. Script tạo key demo

Repo có script:

```text
scripts/generate-signing-keystore.ps1
scripts/generate-signing-keystore.sh
```

Script tạo:

```text
keys/signing.p12
```

Với cấu hình demo:

```text
DIGITAL_SIGNATURE_KEYSTORE_PATH=/app/keys/signing.p12
DIGITAL_SIGNATURE_KEYSTORE_PASSWORD=kolla-signing-dev
```

Đây là key phục vụ demo/đồ án, không phải cách triển khai production an toàn tuyệt đối.

## 8. Watermark và chữ ký số khác nhau

Trong `signPdf()`, service trước tiên gọi:

```java
addSignatureWatermark(pdfBytes, signerName)
```

Watermark thêm dòng mờ ở góc dưới PDF, ví dụ:

```text
Sign By: <tên host>
<thời điểm ký>
```

Đây chỉ là dấu hiển thị để người đọc nhìn thấy. Nó không phải bằng chứng mật mã.

Bằng chứng mật mã thật nằm ở chữ ký PDF được tạo bởi `signDetached()` bằng private key.

Nói ngắn gọn:

- Watermark: cho người dùng nhìn.
- Digital signature: cho phần mềm và hệ thống xác minh.

## 9. SHA-256 trong hệ thống dùng để làm gì?

Sau khi ký xong, `MinutesServiceImpl.confirmMinutes()` gọi:

```java
computeSha256(confirmedPdfBytes)
```

Hash này được lưu vào `hostConfirmationHash`.

Ý nghĩa:

- Là fingerprint của file PDF đã ký.
- Dùng cho audit/API để biết file hiện tại có đúng với bản đã xác nhận không.
- Nếu file signed PDF bị thay đổi, SHA-256 sẽ khác.

Lưu ý: SHA-256 hash không thay thế chữ ký số. Hash chỉ chứng minh toàn vẹn khi có giá trị hash tin cậy để đối chiếu. Chữ ký số mới gắn được danh tính/chứng thư của người ký với tài liệu.

## 10. Quyền ký

Trong `confirmMinutes()`:

```java
if (!isHost(meeting, requester)) {
    throw new ForbiddenException("Only the meeting Host may confirm the minutes");
}
```

Chỉ host cuộc họp được xác nhận và ký biên bản nháp. Nếu minutes không ở `DRAFT`, backend cũng từ chối:

```text
Minutes must be in DRAFT status to confirm
```

Điều này tránh việc ký lại tùy tiện hoặc ký khi biên bản đã qua bước xử lý khác.

## 11. Trạng thái biên bản

Ký số làm chuyển trạng thái:

```text
DRAFT -> HOST_CONFIRMED
```

Sau đó secretary có thể chỉnh sửa/xuất bản bản chính thức:

```text
HOST_CONFIRMED -> SECRETARY_CONFIRMED
```

Trong thiết kế hiện tại, PDF được host ký là bản xác nhận của host. Secretary edit hiện tạo DOCX đã chỉnh sửa, và code hiện tại không ký lại bản secretary DOCX/PDF.

## 12. Rủi ro bảo mật hiện tại

Trong đồ án, keystore demo nằm trên server. Cách này giúp backend ký tự động, dễ demo và dễ tích hợp, nhưng có rủi ro:

- Nếu server bị chiếm quyền, kẻ tấn công có thể lấy keystore và password.
- Người ký có thể tranh luận rằng hệ thống/admin đã dùng key thay họ.
- Tính chống chối bỏ chưa mạnh như khi private key nằm trong USB Token/Smart Card/HSM.

Hướng production tốt hơn:

- USB Token hoặc Smart Card: private key không rời thiết bị của người ký.
- HSM/KMS: khóa được quản lý bởi hạ tầng chuyên dụng.
- Ký phía client hoặc ký qua dịch vụ ký số được chứng thực.
- Ghi audit log đầy đủ: ai ký, lúc nào, IP nào, file hash nào.

## 13. Điểm cần nói khi bảo vệ

Ký số trong KollaMeeting là ký PDF biên bản bằng iText và Bouncy Castle, dùng private key trong PKCS#12/JKS keystore, thuật toán SHA-256 và chữ ký nhúng vào PDF. Host là người kích hoạt ký khi xác nhận biên bản nháp. Hệ thống lưu file PDF đã ký, lưu SHA-256 để audit, gửi notification/WebSocket event sau khi ký. Bản hiện tại phù hợp prototype/đồ án; triển khai thật nên chuyển private key sang USB Token, Smart Card, HSM hoặc dịch vụ ký số chuyên dụng.

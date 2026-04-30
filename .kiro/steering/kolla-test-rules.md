---
inclusion: auto
description: Test task rules — always write AND run tests, ask user immediately when blocked, report pass/fail results.
---

# Kolla — Test Task Rules

Các quy tắc bắt buộc khi thực hiện bất kỳ task nào liên quan đến **viết test** trong project này.

## Quy tắc 1: Viết test VÀ chạy test

Với mọi task có từ khóa: *"viết test"*, *"unit test"*, *"integration test"*, *"property test"*, *"E2E test"*, *"component test"* — bạn phải:

1. **Viết code test** đầy đủ
2. **Chạy test** ngay sau khi viết xong
3. **Báo cáo kết quả** rõ ràng: pass/fail, số lượng test, output

Không được chỉ viết test rồi dừng lại. Phải chạy và xác nhận kết quả.

## Quy tắc 2: Gặp vấn đề → Hỏi user ngay

Nếu gặp bất kỳ vấn đề nào khi chạy test, **DỪNG LẠI và hỏi user** trước khi làm bất cứ điều gì khác. Không được tự tìm cách thay thế mà không hỏi.

Các tình huống phải hỏi:

| Vấn đề | Ví dụ | Phải hỏi |
|--------|-------|----------|
| Build tool không có trong PATH | `mvn` không nhận ra, `npm` không tìm thấy | ✅ Hỏi ngay |
| External service không chạy | MySQL, Redis, Docker chưa start | ✅ Hỏi ngay |
| Thiếu dependency / cấu hình | Testcontainers cần Docker, GPU cho Gipformer | ✅ Hỏi ngay |
| Test fail không rõ nguyên nhân | Sau 2 lần thử vẫn fail | ✅ Hỏi ngay |
| Không biết cách chạy test trong môi trường này | Không tìm thấy mvnw, gradlew, v.v. | ✅ Hỏi ngay |

**Mẫu câu hỏi khi gặp vấn đề:**
> "Tôi gặp vấn đề khi chạy test: [mô tả vấn đề]. Bạn muốn tôi xử lý như thế nào?"

## Quy tắc 3: Cách chạy test trong project này

### Backend (Spring Boot / Java)
```bash
# Chạy tất cả tests
./mvnw test

# Chạy một test class cụ thể
./mvnw test -Dtest=ClassName

# Chạy với profile test
./mvnw test -Ptest
```
> Nếu `mvnw` không có hoặc `mvn` không trong PATH → **hỏi user ngay**

### Frontend (React / TypeScript)
```bash
# Chạy một lần (không watch mode)
npm run test -- --run
# hoặc
npx vitest run
```
> Không dùng `npm test` hay `vitest` không có `--run` (sẽ block)

### Gipformer (Python)
```bash
# Chạy hypothesis property tests
pytest gipformer/tests/ -v
```
> Nếu môi trường Python/pytest chưa setup → **hỏi user ngay**

## Quy tắc 4: Báo cáo kết quả test

Sau khi chạy test, luôn báo cáo:
- ✅ Số test pass
- ❌ Số test fail (kèm error message nếu có)
- ⏭️ Số test skip
- Thời gian chạy (nếu có)

Nếu có test fail: phân tích nguyên nhân và fix trước khi mark task là completed.

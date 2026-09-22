# 🛡️ Kế Hoạch Hoàn Tác Rủi Ro (Rollback Plan)
## Dành Cho Tái Cấu Trúc 3 Microservices

> **Mục tiêu:** Đảm bảo hệ thống có thể hoàn tác ngay lập tức về trạng thái ổn định với `employee-service` nếu quá trình phân tách phát sinh lỗi không thể khắc phục.

---

## 1. Các Tình Huống Kích Hoạt Rollback

- Lỗi biên dịch Gradle giữa 3 subprojects không thể giải quyết.
- Có ít nhất 1 container bị crash liên tục không thể kết nối Eureka hoặc MySQL.
- API Gateway không định tuyến được hoặc phát sinh lỗi 502/504 hàng loạt.

---

## 2. Quy Trình Hoàn Tác Chi Tiết (Rollback Steps)

### Bước 1: Dừng toàn bộ containers
```powershell
docker compose down
```

### Bước 2: Khôi phục cấu hình hạ tầng Git
```bash
git checkout develop -- settings.gradle docker-compose.yml config-repo-local/
```

### Bước 3: Khôi phục lại thư mục `employee-service`
```powershell
# Nếu thư mục identity-and-access-service đang tồn tại
Rename-Item -Path "identity-and-access-service" -NewName "employee-service"
```

### Bước 4: Xóa 2 thư mục services mới tạo
```powershell
Remove-Item -Recurse -Force "intern-and-program-service"
Remove-Item -Recurse -Force "reporting-and-integration-service"
```

### Bước 5: Build và khởi động lại
```powershell
.\gradlew :employee-service:compileJava
docker compose up -d --build
```

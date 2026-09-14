## 📌 Mô tả thay đổi (Description)
<!-- Tóm tắt ngắn gọn tính năng mới, bug đã fix hoặc thay đổi trong PR này -->

## 🏢 Các dịch vụ bị ảnh hưởng (Affected Services)
- [ ] `config-server`
- [ ] `discovery-server`
- [ ] `api-gateway`
- [ ] `employee-service`
- [ ] `docker-compose.yml` / DevOps
- [ ] Khác: 

## 🔍 Checklist kiểm thử (Verification)
- [ ] Đã chạy `./gradlew bootJar -x test` (hoặc `build-all.bat`) thành công tại máy local.
- [ ] Đã chạy `docker compose up --build` và kiểm tra tất cả các container đều `(healthy)`.
- [ ] Đã test thử API qua Gateway (`http://localhost:8080/api/...`).
- [ ] Đã tuân thủ quy ước commit và không commit các file rác, file `.env`, file cấu hình cá nhân.

## 📷 Ảnh chụp màn hình / Log kết quả (nếu có)
<!-- Đính kèm ảnh chụp Swagger, Postman hoặc log thành công -->

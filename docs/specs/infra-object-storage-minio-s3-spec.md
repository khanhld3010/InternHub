# Technical Specification: Object Storage Infrastructure (MinIO & AWS S3) with Pre-signed URLs

- **Feature / Subsystem:** Infrastructure Layer - Distributed Object Storage
- **Branch:** `infra/object-storage-minio-s3`
- **Target Services:**
  - `docker-compose.yml` (Hạ tầng MinIO + MinIO Init Container)
  - `file-service` (Microservice mới - Internal Storage Utility Service)
  - `intern-and-program-service` (Service nghiệp vụ quản lý tài liệu TTS)
  - `InternHub-Frontend` (Client upload/download trực tiếp qua Pre-signed URL)

---

## 1. Bối cảnh & Mục tiêu Kiến trúc (Context & Objectives)

### 1.1. Hiện trạng & Thách thức
- Hệ thống InternHub hiện tại lưu trữ tài liệu (CV, hợp đồng, tài liệu thực tập) trực tiếp trên ổ đĩa cục bộ (Local Disk) hoặc truyền qua Backend stream.
- **Hạn chế:**
  - Không thể mở rộng (Scale Horizontal) khi chạy nhiều container replica của backend service.
  - Tải trọng mạng đè nặng lên Backend khi client upload các file dung lượng lớn (CV PDF, ảnh, scan hợp đồng).
  - Nguy cơ cạn kiệt dung lượng đĩa cứng cục bộ của server và khó khăn trong việc sao lưu dữ liệu.

### 1.2. Mục tiêu kỹ thuật
- Chuyển đổi toàn bộ việc lưu trữ sang chuẩn **Object Storage (S3 API)**: Sử dụng **MinIO** cho môi trường Local/Development/Staging và **AWS S3** cho môi trường Production mà không cần thay đổi logic code.
- Triển khai mô hình **Pre-signed URL (Direct-to-S3 Upload/Download)**:
  - Client tương tác thẳng với MinIO/S3 để upload và download dữ liệu.
  - Backend hoàn toàn giải phóng khỏi việc xử lý IO/Stream dung lượng file.
- Đảm bảo **Phân định rõ ranh giới nghiệp vụ (Domain Boundaries)**:
  - `file-service`: Hoàn toàn **vô tri về mặt nghiệp vụ** (Business-Agnostic). Chỉ quản lý file vật lý, bucket, key, sinh Pre-signed URL và thao tác copy/move S3.
  - `intern-and-program-service`: Quản lý 100% logic nghiệp vụ (Quyền truy cập, trạng thái duyệt, bảng `intern_documents`).
  - `file-service` là service nội bộ (Internal Service), **không expose** ra ngoài Internet qua API Gateway.

---

## 2. Kiến trúc Tổng thể & Luồng Dữ liệu (Architecture & Data Flow)

```
                       ┌──────────────────────────────────────────────┐
                       │                   Frontend                   │
                       │           (React Vite Application)           │
                       └───────────┬──────────────────────┬───────────┘
                                   │ 1. POST upload-url   │ 3. PUT file (Binary)
                                   │ 4. POST confirm      │    (Presigned PUT)
                                   ▼                      ▼
┌──────────────────────────────────────┐       ┌──────────────────────────────────────┐
│      intern-and-program-service      │       │             MinIO / S3               │
│  (Business Logic & Authorization)    │       │        (Object Storage Server)       │
└──────────────────┬───────────────────┘       └──────────────────▲───────────────────┘
                   │                                              │
                   │ 2. Feign: /internal/files/presigned-upload   │
                   │ 5. Feign: /internal/files/promote            │
                   ▼                                              │ Thao tác vật lý S3
┌──────────────────────────────────────┐                          │ (Put/Copy/Delete)
│             file-service             │──────────────────────────┘
│  (Internal Storage Engine Utility)   │
└──────────────────────────────────────┘
```

### 2.1. Quy trình Upload File Chuẩn (Zero-Orphan Architecture)

1. **Bước 1 (Xin URL Upload):**
   - Frontend gửi yêu cầu: `POST /api/interns/{id}/documents/upload-url` kèm metadata: `fileName`, `contentType`, `size`, `documentType`.
2. **Bước 2 (Ủy quyền & Khởi tạo):**
   - `intern-and-program-service` xác thực quyền (User sở hữu hồ sơ hoặc HR).
   - Gọi Feign sang `file-service`: `POST /internal/files/presigned-upload`.
   - `file-service` sinh một `tempKey` (ví dụ: `temp/{uuid}.pdf`) và cấp `presignedPutUrl` với thời hạn hiệu lực **15 phút**.
   - `intern-service` trả `presignedPutUrl` và `tempKey` về cho Frontend. **Lưu ý: Chưa ghi bất kỳ dòng nào vào cơ sở dữ liệu!**
3. **Bước 3 (Upload Trực tiếp):**
   - Frontend sử dụng `fetch` hoặc `axios` thực hiện HTTP `PUT` gửi trực tiếp file binary lên MinIO/S3 theo `presignedPutUrl`.
4. **Bước 4 (Xác nhận Hoàn tất - Confirm Upload):**
   - Sau khi upload lên S3 thành công (HTTP 200), Frontend gọi: `POST /api/interns/{id}/documents/confirm-upload` gửi `tempKey`, `documentType`, `fileName`.
5. **Bước 5 (Chuyển vùng Vĩnh viễn & Lưu Trữ Nghiệp Vụ - Promote):**
   - `intern-and-program-service` gọi Feign sang `file-service`: `POST /internal/files/promote` để copy/move file từ `temp/{uuid}.pdf` sang đường dẫn chính thức `documents/{internId}/{documentType}_{timestamp}_{uuid}.pdf`.
   - `file-service` xóa file tạm và trả về `finalKey`, `fileSize`, `checksum/etag`.
   - `intern-and-program-service` thực hiện **INSERT** bản ghi vào bảng `intern_documents`.

### 2.2. Quy trình Xem / Tải File (Secure Download & Preview)

1. Frontend gửi yêu cầu: `GET /api/interns/{id}/documents/{docId}/view-url`.
2. `intern-and-program-service` kiểm tra quyền (Chỉ HR, Mentor phụ trách hoặc chính Intern đó mới được xem).
3. Lấy `fileKey` từ bảng `intern_documents`, gọi Feign sang `file-service`: `POST /internal/files/presigned-view`.
4. `file-service` dùng AWS S3 SDK sinh `presignedGetUrl` với thời gian sống ngắn (**30 - 60 phút**).
5. Frontend nhận URL và hiển thị trên PDF viewer hoặc thẻ download an toàn.

---

## 3. Hạ Tầng Tự Động Hóa (Docker Compose & Lifecycle Automation)

Tuân thủ nghiêm ngặt 3 nguyên tắc: **Idempotency**, **Healthcheck-driven Dependency**, và **Least-Privilege Security**.

### 3.1. Cấu hình `docker-compose.yml`

```yaml
version: '3.8'

services:
  # -------------------------------------------------------------
  # MinIO Object Storage Server
  # -------------------------------------------------------------
  minio:
    image: minio/minio:RELEASE.2024-03-03T17-50-39Z
    container_name: internhub-minio
    restart: unless-stopped
    ports:
      - "9000:9000"   # API S3 Endpoint
      - "9001:9001"   # MinIO Web Console UI
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-internhub_admin}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-InternHub@2026AdminSecret}
      MINIO_SERVER_URL: "http://localhost:9000" # Đảm bảo Presigned URL tương thích với Browser ngoài host
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 5s
      timeout: 5s
      retries: 5

  # -------------------------------------------------------------
  # MinIO Initialization (Ephemeral Container - Chạy 1 lần rồi thoát)
  # -------------------------------------------------------------
  minio-init:
    image: minio/mc:latest
    container_name: internhub-minio-init
    depends_on:
      minio:
        condition: service_healthy
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-internhub_admin}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-InternHub@2026AdminSecret}
      APP_ACCESS_KEY: ${STORAGE_APP_ACCESS_KEY:-internhub_app_client}
      APP_SECRET_KEY: ${STORAGE_APP_SECRET_KEY:-InternHubAppSecret2026Secure}
      BUCKET_NAME: "internhub-documents"
    entrypoint: >
      /bin/sh -c "
        echo '=== [1/4] Configuring MinIO Client Admin Alias... ===';
        mc alias set myminio http://minio:9000 $${MINIO_ROOT_USER} $${MINIO_ROOT_PASSWORD};

        echo '=== [2/4] Ensuring Bucket Exists (Idempotent)... ===';
        mc mb --ignore-existing myminio/$${BUCKET_NAME};

        echo '=== [3/4] Configuring Lifecycle Policy for temp/ directory... ===';
        if ! mc ilm rule list myminio/$${BUCKET_NAME} | grep -q 'temp/'; then
          mc ilm rule add myminio/$${BUCKET_NAME} --prefix 'temp/' --expire-days 1;
          echo 'Successfully added 24-hour expiration lifecycle rule for temp/.';
        else
          echo 'Lifecycle rule for temp/ already exists. Skipping.';
        fi;

        echo '=== [4/4] Creating App Service Account (Least-Privilege)... ===';
        mc admin user add myminio $${APP_ACCESS_KEY} $${APP_SECRET_KEY} || true;
        mc admin policy attach myminio readwrite --user $${APP_ACCESS_KEY} || true;

        echo '=== MinIO Infrastructure Initialization Complete! ===';
        exit 0;
      "

volumes:
  minio_data:
    driver: local
```

---

## 4. Đặc Tả Giao Diện API & Hợp Đồng Dữ Liệu (API Contracts)

### 4.1. Nội bộ: `file-service` (Internal APIs)

> **Lưu ý:** Chỉ lắng nghe trên mạng nội bộ hoặc qua xác thực JWT / `client_credentials`, không định tuyến qua Gateway ra ngoài.

#### A. Sinh URL Upload tạm thời
- **Endpoint:** `POST /internal/files/presigned-upload`
- **Request Body:**
```json
{
  "prefix": "temp",
  "fileName": "cv_nguyen_van_a.pdf",
  "contentType": "application/pdf",
  "sizeLimitBytes": 10485760
}
```
- **Response Body (200 OK):**
```json
{
  "tempKey": "temp/e4a1b021-39c4-4b57-a3f2-1f4a9b5f9281.pdf",
  "presignedUrl": "http://localhost:9000/internhub-documents/temp/e4a1b021-39c4-4b57-a3f2-1f4a9b5f9281.pdf?X-Amz-Algorithm=...",
  "expiresInSeconds": 900
}
```

#### B. Chuyển File từ Thư Mục Tạm sang Chính Thức (Promote)
- **Endpoint:** `POST /internal/files/promote`
- **Request Body:**
```json
{
  "tempKey": "temp/e4a1b021-39c4-4b57-a3f2-1f4a9b5f9281.pdf",
  "destinationKey": "documents/INT-2026-001/CV_1727500000_e4a1b021.pdf"
}
```
- **Response Body (200 OK):**
```json
{
  "finalKey": "documents/INT-2026-001/CV_1727500000_e4a1b021.pdf",
  "fileSize": 1542100,
  "contentType": "application/pdf",
  "etag": "\"5d41402abc4b2a76b9719d911017c592\""
}
```

#### C. Sinh URL Xem / Tải File (Presigned View)
- **Endpoint:** `POST /internal/files/presigned-view`
- **Request Body:**
```json
{
  "fileKey": "documents/INT-2026-001/CV_1727500000_e4a1b021.pdf",
  "expiresInMinutes": 30
}
```
- **Response Body (200 OK):**
```json
{
  "presignedUrl": "http://localhost:9000/internhub-documents/documents/INT-2026-001/CV_1727500000_e4a1b021.pdf?X-Amz-Algorithm=...",
  "expiresInSeconds": 1800
}
```

---

### 4.2. Ngoại vi: `intern-and-program-service` (Client Business APIs)

- `POST /api/interns/{id}/documents/upload-url` (Yêu cầu cấp link upload tài liệu cho Intern)
- `POST /api/interns/{id}/documents/confirm-upload` (Xác nhận sau khi client upload S3 thành công)
- `GET /api/interns/{id}/documents/{docId}/view-url` (Lấy link xem file bảo mật)

---

## 5. Rủi Ro, Điểm Cần Chú Ý & Biện Pháp Giảm Thiểu (Edge Cases & Mitigations)

1. **Rủi ro File Mồ Côi (Orphaned Files):**
   - *Tình huống:* User bấm tải lên nhưng tắt máy giữa chừng hoặc lỗi mạng trước khi gọi `confirm-upload`.
   - *Biện pháp:* File nằm tại `temp/` và tự động bị MinIO/S3 xóa sau 24h nhờ **S3 Lifecycle Rule** đã thiết lập tự động trong `minio-init`. Cơ sở dữ liệu nghiệp vụ hoàn toàn không lưu rác.
2. **Lỗi Hostname giữa Docker Network và Browser:**
   - *Tình huống:* Container backend giao tiếp qua `http://minio:9000`, trong khi trình duyệt trên máy dev gọi `http://localhost:9000`.
   - *Biện pháp:* Cấu hình biến môi trường `MINIO_SERVER_URL="http://localhost:9000"` và trong file cấu hình Spring cấu hình rõ `storage.s3.public-endpoint=http://localhost:9000` để presigned URL sinh ra trỏ đúng địa chỉ máy chủ client có thể truy cập được.
3. **Bảo mật Quyền Hạn (Least Privilege):**
   - Tài khoản `file-service` chỉ có quyền đọc/ghi trên bucket chỉ định, không được phép can thiệp vào quản trị hệ thống MinIO (Không dùng Root Credentials).
4. **Giới Hạn Kích Thước File (Enforced Upload Policy):**
   - Khống chế dung lượng tối đa ngay trong `PutObjectPresignRequest` hoặc kiểm tra `Content-Length` để tránh tấn công DoS lưu trữ.

---

## 6. Kế Hoạch Triển Khai (Phase Checklist)

- [ ] **Phase 1 (Hạ tầng Docker):** Bổ sung dịch vụ `minio` và `minio-init` vào `docker-compose.yml`, kiểm thử khởi động và kiểm tra tính năng `mc ilm rule list`.
- [ ] **Phase 2 (File Service Core):** Khởi tạo module Spring Boot `file-service`, tích hợp `software.amazon.awssdk:s3`, triển khai các endpoint nội bộ `/internal/files/*`.
- [ ] **Phase 3 (Tích hợp Nghiệp vụ `intern-and-program-service`):** Cấu hình OpenFeign client kết nối `file-service`, tạo bảng `intern_documents`, triển khai API cấp presigned URL và confirm upload.
- [ ] **Phase 4 (Tích hợp Frontend):** Xây dựng component upload file hỗ trợ HTTP PUT thẳng lên MinIO S3 với thanh tiến trình (Progress bar), hoàn tất flow upload CV và xem tài liệu.

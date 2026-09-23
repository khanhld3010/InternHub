# Spec-Driven Development Guidelines (InternHub Backend)

> [!IMPORTANT]
> **TÀI LIỆU ĐẶC TẢ ĐÃ ĐƯỢC TÍCH HỢP ĐỒNG BỘ VÀO BỘ HƯỚNG DẪN CHÍNH THỨC:**
> Chi tiết đầy đủ về 13 phần của tài liệu Spec (`spec.md`), 4 cấp độ thay đổi L1 - L4 và quy trình triển khai Package-by-Feature 7 bước đã được tích hợp tập trung tại:
> 👉 [InternHub/.agents/04-development-guide.md](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/04-development-guide.md)

---

## 🧭 Tóm Tắt Triết Lý Spec-Driven Development

1. **Spec (Cần đạt điều gì):** Góc nhìn người dùng & nghiệp vụ. Xác định rõ phạm vi In-Scope, Out-of-Scope, và điều kiện biên.
2. **Plan (Làm như thế nào):** Bản đồ kỹ thuật kết nối Spec vào microservice hiện có. Xác định danh sách file tạo/sửa, luồng transaction, và rủi ro.
3. **Tasks (Từng bước cụ thể):** Chia nhỏ thành các đầu mục có thể quan sát, kiểm thử độc lập và review trong một lần diff.
4. **Implement & Converge (Thực thi & Đối chiếu):** Sau khi viết code, **bắt buộc đối chiếu từng tiêu chí chấp nhận (Acceptance Criteria)** với bằng chứng kiểm thử thực tế.

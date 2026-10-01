package org.example.internservice.intern.entity.enums;

public enum ContractStatus {
    PENDING_SIGNATURE,        // Chờ thực tập sinh ký/xác nhận
    PENDING_INTERN_FEEDBACK,  // Thực tập sinh gửi thắc mắc (bản scan mờ, sai điều khoản)
    SIGNED,                   // Đã ký kết hợp lệ
    ACTIVE,                   // Đang có hiệu lực
    EXPIRED,                  // Hết hạn hợp đồng
    SUPERSEDED,               // Đã được thay thế bởi hợp đồng gia hạn (phụ lục mới)
    TERMINATED,               // Chấm dứt trước thời hạn
    REJECTED_BY_INTERN        // Bị thực tập sinh từ chối ký
}

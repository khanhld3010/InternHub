package org.example.internservice.intern.entity.enums;

public enum ContractStatus {
    PENDING_SIGNATURE,  // Chờ thực tập sinh ký/xác nhận
    SIGNED,             // Đã ký kết hợp lệ
    ACTIVE,             // Đang có hiệu lực
    EXPIRED,            // Hết hạn hợp đồng
    TERMINATED,         // Chấm dứt trước thời hạn
    REJECTED_BY_INTERN  // Bị thực tập sinh từ chối ký
}

package org.example.employeeservice.intern.service;

import org.example.employeeservice.intern.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface InternDocumentService {

    /**
     * Tải lên tài liệu (CV hoặc Đơn xin thực tập) cho thực tập sinh theo mã internCode.
     *
     * @param internCode     Mã thực tập sinh (ví dụ: INT-202609-0001)
     * @param file           Tệp tin tài liệu
     * @param documentTypeStr Loại tài liệu (CV, APPLICATION_LETTER)
     * @return Thông tin metadata của tài liệu đã lưu
     */
    DocumentResponse uploadDocument(String internCode, MultipartFile file, String documentTypeStr);
}

package org.example.employeeservice.common.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    /**
     * Lưu trữ tệp tin vào thư mục con chỉ định.
     *
     * @param file         Tệp tin tải lên
     * @param subDirectory Thư mục con tương đối (ví dụ: "interns/INT-202609-0001")
     * @return Tên tệp tin duy nhất được sinh ra trên đĩa (UUID + extension)
     */
    String storeFile(MultipartFile file, String subDirectory);

    /**
     * Xóa tệp tin vật lý khỏi đĩa nếu xảy ra lỗi rollback.
     *
     * @param relativeFilePath Đường dẫn tương đối của tệp tin
     */
    void deleteFile(String relativeFilePath);

    /**
     * Tải tệp tin dưới dạng Resource (streaming) phục vụ download/preview.
     *
     * @param relativeFilePath Đường dẫn tương đối của tệp tin trên đĩa
     * @return Resource của tệp tin
     */
    Resource loadFileAsResource(String relativeFilePath);
}

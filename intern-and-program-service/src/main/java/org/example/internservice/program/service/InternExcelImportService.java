package org.example.internservice.program.service;

import org.example.internservice.program.dto.response.ExcelImportPreviewResponse;
import org.example.internservice.program.dto.response.ExcelImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

public interface InternExcelImportService {

    /**
     * Sinh tệp Excel mẫu (.xlsx) chuẩn hóa cho HR tải về
     *
     * @return mảng byte của tệp Excel mẫu
     */
    byte[] generateExcelTemplate();

    /**
     * Phân tích và kiểm tra tính hợp lệ của tệp Excel trước khi commit (không ghi CSDL)
     *
     * @param programId ID của chương trình thực tập tiếp nhận
     * @param file      Tệp Excel tải lên
     * @return Kết quả thống kê, số slot còn trống, danh sách preview và chi tiết lỗi nếu có
     */
    ExcelImportPreviewResponse previewExcelImport(Long programId, MultipartFile file);

    /**
     * Thực thi nhập danh sách thực tập sinh từ Excel vào chương trình (All-or-Nothing, Pessimistic Lock)
     *
     * @param programId    ID của chương trình thực tập tiếp nhận
     * @param file         Tệp Excel tải lên
     * @param targetStatus Trạng thái đích ("APPROVED" hoặc "PENDING", mặc định "APPROVED")
     * @param importedBy   Username của HR/Admin thực hiện
     * @return Kết quả nhập dữ liệu gồm số lượng và danh sách mã TTS đã sinh
     */
    ExcelImportResultResponse importInternsFromExcel(Long programId, MultipartFile file, String targetStatus, String importedBy);
}

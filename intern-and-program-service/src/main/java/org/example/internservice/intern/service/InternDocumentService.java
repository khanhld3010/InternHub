package org.example.internservice.intern.service;

import org.example.internservice.intern.dto.request.ReviewDocumentRequest;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
import org.example.internservice.intern.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface InternDocumentService {

    /**
     * Tải lên tài liệu mới (CV hoặc đơn xin thực tập) cho hồ sơ thực tập sinh.
     *
     * @param internCode      Mã thực tập sinh (ví dụ: INT-202609-0001)
     * @param file            Tệp tin đính kèm
     * @param documentTypeStr Loại tài liệu ("CV" hoặc "INTERNSHIP_APPLICATION")
     * @return Thông tin metadata của tài liệu đã lưu
     */
    DocumentResponse uploadDocument(String internCode, MultipartFile file, String documentTypeStr);

    /**
     * Lấy danh sách toàn bộ tài liệu của một thực tập sinh theo internCode.
     *
     * @param internCode Mã thực tập sinh
     * @return Danh sách metadata tài liệu xếp theo ngày nộp mới nhất
     */
    List<DocumentResponse> getDocumentsByInternCode(String internCode);

    /**
     * Tải tệp tin tài liệu để download hoặc preview inline.
     *
     * @param documentId ID của tài liệu
     * @return DTO chứa Resource nhị phân và tên file gốc
     */
    DocumentDownloadDto loadDocumentForDownload(Long documentId);

    /**
     * Cập nhật trạng thái xét duyệt tài liệu (Approve / Reject kèm lý do).
     *
     * @param documentId ID của tài liệu
     * @param request    DTO chứa status (APPROVED, REJECTED) và rejectionReason
     * @return Metadata tài liệu sau khi cập nhật
     */
    DocumentResponse reviewDocument(Long documentId, ReviewDocumentRequest request);
}

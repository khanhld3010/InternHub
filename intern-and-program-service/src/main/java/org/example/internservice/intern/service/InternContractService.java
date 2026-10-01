package org.example.internservice.intern.service;

import org.example.internservice.intern.dto.request.ConfirmContractRequest;
import org.example.internservice.intern.dto.request.ContractFeedbackRequest;
import org.example.internservice.intern.dto.request.ContractStorageDtos;
import org.example.internservice.intern.dto.request.RejectContractRequest;
import org.example.internservice.intern.dto.request.StorageBusinessDtos;
import org.example.internservice.intern.dto.request.TerminateContractRequest;
import org.example.internservice.intern.dto.request.UploadContractRequest;
import org.example.internservice.intern.dto.response.ContractResponse;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface InternContractService {

    /**
     * Tải lên tệp hợp đồng mới kèm metadata cho thực tập sinh đủ điều kiện (APPROVED hoặc INTERNING).
     */
    ContractResponse uploadContract(String internCode, MultipartFile file, UploadContractRequest request, String uploadedBy);

    /**
     * Xin cấp Presigned Upload URL trực tiếp lên S3/MinIO cho phân hệ Hợp đồng.
     */
    StorageBusinessDtos.RequestUploadUrlResponse requestContractUploadUrl(String internCode, ContractStorageDtos.RequestContractUploadUrlRequest request);

    /**
     * Xác nhận upload tệp hợp đồng thành công lên S3, di chuyển sang đường dẫn chính thức và lưu DB.
     */
    ContractResponse confirmContractUpload(String internCode, ContractStorageDtos.ConfirmContractUploadRequest request, String uploadedBy);

    /**
     * Lấy Presigned View URL để xem hợp đồng an toàn trực tiếp từ S3 (không bị lỗi 403 Forbidden).
     */
    ContractStorageDtos.ViewContractUrlResponse getContractViewUrl(Long contractId, CustomUserDetails userDetails);

    /**
     * Lấy danh sách toàn bộ hợp đồng của công ty dành cho HR (Contract Management Hub).
     */
    List<ContractResponse> getAllContracts();

    /**
     * Lấy danh sách toàn bộ hợp đồng của một thực tập sinh theo internCode.
     */
    List<ContractResponse> getContractsByInternCode(String internCode);

    /**
     * Tải tệp tin hợp đồng để phục vụ download hoặc preview inline (fallback local).
     */
    DocumentDownloadDto loadContractForDownload(Long contractId);

    /**
     * Lấy danh sách hợp đồng cá nhân của thực tập sinh đang đăng nhập.
     */
    List<ContractResponse> getMyContracts(CustomUserDetails userDetails);

    /**
     * Lấy hợp đồng đang chờ ký hoặc hiệu lực gần nhất của thực tập sinh đang đăng nhập.
     */
    ContractResponse getMyActiveContract(CustomUserDetails userDetails);

    /**
     * Xem chi tiết hợp đồng theo ID kèm kiểm tra quyền sở hữu bảo mật chống IDOR.
     */
    ContractResponse getContractById(Long contractId, CustomUserDetails userDetails);

    /**
     * Xác nhận ký hợp đồng thực tập điện tử, chuyển hợp đồng sang ACTIVE (hoặc SIGNED).
     * Nếu là hợp đồng gia hạn (EXTENSION_APPENDIX), chuyển hợp đồng cha sang SUPERSEDED.
     */
    ContractResponse confirmContract(Long contractId, ConfirmContractRequest request, CustomUserDetails userDetails);

    /**
     * Thực tập sinh gửi thắc mắc liên hệ HR (scan mờ, sai điều khoản...), chuyển sang PENDING_INTERN_FEEDBACK.
     */
    ContractResponse submitFeedback(Long contractId, ContractFeedbackRequest request, CustomUserDetails userDetails);

    /**
     * Chấm dứt hợp đồng trước hạn (Dành cho HR / Quản trị viên), chuyển sang TERMINATED.
     */
    ContractResponse terminateContract(Long contractId, TerminateContractRequest request, String terminatedBy);

    /**
     * Gửi email nhắc nhở ký hợp đồng tới thực tập sinh.
     */
    void sendContractReminder(Long contractId, String sentBy);

    /**
     * Quét các hợp đồng PENDING_SIGNATURE quá hạn để tự động gửi email nhắc nhở (Cron Job)
     */
    int scanAndSendPendingContractReminders(int overdueDays, int cooldownDays);

    /**
     * Từ chối hợp đồng thực tập kèm lý do giải trình bắt buộc, chuyển hợp đồng sang REJECTED_BY_INTERN.
     */
    ContractResponse rejectContract(Long contractId, RejectContractRequest request, CustomUserDetails userDetails);
}

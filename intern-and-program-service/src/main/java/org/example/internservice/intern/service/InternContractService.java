package org.example.internservice.intern.service;

import org.example.internservice.intern.dto.request.ConfirmContractRequest;
import org.example.internservice.intern.dto.request.RejectContractRequest;
import org.example.internservice.intern.dto.request.UploadContractRequest;
import org.example.internservice.intern.dto.response.ContractResponse;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface InternContractService {

    /**
     * Tải lên tệp hợp đồng mới kèm metadata cho thực tập sinh đủ điều kiện (APPROVED hoặc INTERNING).
     *
     * @param internCode  Mã thực tập sinh
     * @param file        Tệp tin hợp đồng (.pdf, .docx, .doc)
     * @param request     DTO chứa thông tin hợp đồng
     * @param uploadedBy  Username của HR thực hiện
     * @return ContractResponse DTO
     */
    ContractResponse uploadContract(String internCode, MultipartFile file, UploadContractRequest request, String uploadedBy);

    /**
     * Lấy danh sách toàn bộ hợp đồng của một thực tập sinh theo internCode.
     *
     * @param internCode Mã thực tập sinh
     * @return Danh sách hợp đồng sắp xếp giảm dần theo ngày tạo
     */
    List<ContractResponse> getContractsByInternCode(String internCode);

    /**
     * Tải tệp tin hợp đồng để phục vụ download hoặc preview inline.
     *
     * @param contractId ID của hợp đồng
     * @return DTO chứa Resource nhị phân và metadata tệp
     */
    DocumentDownloadDto loadContractForDownload(Long contractId);

    /**
     * Lấy danh sách hợp đồng cá nhân của thực tập sinh đang đăng nhập.
     *
     * @param userDetails Thông tin tài khoản người dùng đăng nhập
     * @return Danh sách hợp đồng cá nhân sắp xếp mới nhất lên đầu
     */
    List<ContractResponse> getMyContracts(CustomUserDetails userDetails);

    /**
     * Lấy hợp đồng đang chờ ký hoặc hiệu lực gần nhất của thực tập sinh đang đăng nhập.
     *
     * @param userDetails Thông tin tài khoản người dùng đăng nhập
     * @return Hợp đồng active hoặc pending gần nhất
     */
    ContractResponse getMyActiveContract(CustomUserDetails userDetails);

    /**
     * Xem chi tiết hợp đồng theo ID kèm kiểm tra quyền sở hữu bảo mật chống IDOR.
     *
     * @param contractId  ID của hợp đồng
     * @param userDetails Thông tin tài khoản người dùng đăng nhập
     * @return ContractResponse DTO
     */
    ContractResponse getContractById(Long contractId, CustomUserDetails userDetails);

    /**
     * Xác nhận ký hợp đồng thực tập điện tử, chuyển hợp đồng sang SIGNED và hồ sơ sang INTERNING.
     *
     * @param contractId  ID của hợp đồng
     * @param request     Dữ liệu xác nhận ký
     * @param userDetails Thông tin tài khoản người dùng đăng nhập
     * @return ContractResponse DTO sau khi ký thành công
     */
    ContractResponse confirmContract(Long contractId, ConfirmContractRequest request, CustomUserDetails userDetails);

    /**
     * Từ chối hợp đồng thực tập kèm lý do giải trình bắt buộc, chuyển hợp đồng sang REJECTED_BY_INTERN.
     *
     * @param contractId  ID của hợp đồng
     * @param request     Dữ liệu từ chối hợp đồng
     * @param userDetails Thông tin tài khoản người dùng đăng nhập
     * @return ContractResponse DTO sau khi từ chối thành công
     */
    ContractResponse rejectContract(Long contractId, RejectContractRequest request, CustomUserDetails userDetails);
}

package org.example.internservice.intern.service;

import org.example.internservice.intern.dto.request.UploadContractRequest;
import org.example.internservice.intern.dto.response.ContractResponse;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
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
}

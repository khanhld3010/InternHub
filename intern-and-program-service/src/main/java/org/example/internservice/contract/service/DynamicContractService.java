package org.example.internservice.contract.service;

import org.example.internservice.contract.dto.request.ChangeRequestDto;
import org.example.internservice.contract.dto.request.ConfirmRevisionRequest;
import org.example.internservice.contract.dto.request.CreateContractDraftRequest;
import org.example.internservice.contract.dto.request.SignContractRequest;
import org.example.internservice.contract.dto.response.DynamicContractResponse;

import java.util.Map;

public interface DynamicContractService {
    DynamicContractResponse createDraft(CreateContractDraftRequest request, String createdBy);
    Map<String, String> previewDraft(Long contractId);
    DynamicContractResponse sendContract(Long contractId, String hrUsername);
    DynamicContractResponse getMyContract(Long internUserId);
    Map<String, String> previewRevision(Long contractId, Long revisionId, Long internUserId);
    void requestChanges(Long contractId, Long revisionId, ChangeRequestDto request, Long internUserId);
    void confirmRevision(Long contractId, Long revisionId, ConfirmRevisionRequest request, Long internUserId);
    void signRevision(Long contractId, Long revisionId, SignContractRequest request, Long internUserId, String ipAddress, String userAgent);
    DynamicContractResponse createRevision(Long contractId, CreateContractDraftRequest request, String hrUsername);
    java.util.List<DynamicContractResponse> getAllContracts();
    byte[] getContractPdfBytes(Long contractId, Long revisionId, Long internUserId);
    byte[] getMyContractPdfBytes(Long internUserId);
    String getContractPdfViewUrl(Long contractId, Long revisionId, Long internUserId);
}

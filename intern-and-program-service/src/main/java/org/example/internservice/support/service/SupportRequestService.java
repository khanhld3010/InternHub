package org.example.internservice.support.service;

import org.example.internservice.support.dto.request.CreateSupportRequestDto;
import org.example.internservice.support.dto.request.RespondSupportRequestDto;
import org.example.internservice.support.dto.response.SupportRequestResponse;
import org.example.internservice.support.entity.enums.SupportRequestStatus;
import org.example.internservice.support.entity.enums.SupportRequestType;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SupportRequestService {

    SupportRequestResponse createRequest(CreateSupportRequestDto dto, MultipartFile attachment, Long internUserId);

    List<SupportRequestResponse> getMyRequests(Long internUserId);

    List<SupportRequestResponse> getAllRequests(SupportRequestStatus status, SupportRequestType requestType);

    SupportRequestResponse cancelRequest(Long id, Long internUserId);

    SupportRequestResponse processRequest(Long id, Long hrUserId, String hrUserName);

    SupportRequestResponse respondRequest(Long id, RespondSupportRequestDto dto, MultipartFile responseAttachment, Long hrUserId, String hrUserName);

    Resource loadAttachmentFile(Long id, Long requestingUserId, String requestingRole, boolean isResponseAttachment);

    String getAttachmentFilename(Long id, boolean isResponseAttachment);

    String getAttachmentFilename(Long id, boolean isResponseAttachment, Long requestingUserId, String requestingRole);
}

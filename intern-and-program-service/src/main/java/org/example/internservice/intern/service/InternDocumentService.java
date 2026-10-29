package org.example.internservice.intern.service;

import org.example.internservice.intern.dto.request.ReviewDocumentRequest;
import org.example.internservice.intern.dto.request.StorageBusinessDtos.ConfirmUploadRequest;
import org.example.internservice.intern.dto.request.StorageBusinessDtos.RequestUploadUrlRequest;
import org.example.internservice.intern.dto.request.StorageBusinessDtos.RequestUploadUrlResponse;
import org.example.internservice.intern.dto.request.StorageBusinessDtos.ViewDocumentUrlResponse;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
import org.example.internservice.intern.dto.response.DocumentResponse;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface InternDocumentService {

    DocumentResponse uploadDocument(String internCode, MultipartFile file, String documentTypeStr);

    List<DocumentResponse> getDocumentsByInternCode(String internCode);

    List<DocumentResponse> getDocumentsByInternCode(String internCode, CustomUserDetails userDetails);

    DocumentDownloadDto loadDocumentForDownload(Long documentId);

    DocumentDownloadDto loadDocumentForDownload(Long documentId, CustomUserDetails userDetails);

    DocumentResponse reviewDocument(Long documentId, ReviewDocumentRequest request);

    RequestUploadUrlResponse createPresignedUploadUrl(String internCode, RequestUploadUrlRequest request);

    DocumentResponse confirmUpload(String internCode, ConfirmUploadRequest request);

    ViewDocumentUrlResponse getDocumentViewUrl(Long documentId);

    ViewDocumentUrlResponse getDocumentViewUrl(Long documentId, CustomUserDetails userDetails);
}

package org.example.fileservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.fileservice.dto.StorageDtos.*;
import org.example.fileservice.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/files")
@RequiredArgsConstructor
public class StorageInternalController {

    private final StorageService storageService;

    @PostMapping("/presigned-upload")
    public ResponseEntity<PresignedUploadResponse> createPresignedUpload(@RequestBody PresignedUploadRequest request) {
        return ResponseEntity.ok(storageService.createPresignedUpload(request));
    }

    @PostMapping("/promote")
    public ResponseEntity<PromoteFileResponse> promoteFile(@RequestBody PromoteFileRequest request) {
        return ResponseEntity.ok(storageService.promoteFile(request));
    }

    @PostMapping("/presigned-view")
    public ResponseEntity<PresignedViewResponse> createPresignedView(@RequestBody PresignedViewRequest request) {
        return ResponseEntity.ok(storageService.createPresignedView(request));
    }
}

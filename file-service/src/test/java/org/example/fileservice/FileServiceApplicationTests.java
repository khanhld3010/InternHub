package org.example.fileservice;

import org.example.fileservice.dto.StorageDtos.*;
import org.example.fileservice.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileServiceApplicationTests {

    @Autowired
    private StorageService storageService;

    @Test
    void testGeneratePresignedUploadAndPromoteFlow() {
        // 1. Sinh URL Presigned Upload
        PresignedUploadRequest uploadRequest = PresignedUploadRequest.builder()
                .fileName("cv_candidate.pdf")
                .contentType("application/pdf")
                .prefix("temp")
                .build();

        PresignedUploadResponse uploadResponse = storageService.createPresignedUpload(uploadRequest);

        assertNotNull(uploadResponse);
        assertNotNull(uploadResponse.getPresignedUrl());
        assertNotNull(uploadResponse.getTempKey());
        assertTrue(uploadResponse.getTempKey().startsWith("temp/"));
        assertTrue(uploadResponse.getPresignedUrl().contains("temp/"));

        // 2. Sinh URL Presigned View
        PresignedViewRequest viewRequest = PresignedViewRequest.builder()
                .fileKey(uploadResponse.getTempKey())
                .expiresInMinutes(15)
                .build();

        PresignedViewResponse viewResponse = storageService.createPresignedView(viewRequest);
        assertNotNull(viewResponse);
        assertNotNull(viewResponse.getPresignedUrl());
        assertTrue(viewResponse.getPresignedUrl().contains(uploadResponse.getTempKey()));
    }
}

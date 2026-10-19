package org.example.aiservice.client;

import org.example.aiservice.client.dto.DocumentClientDto;
import org.example.aiservice.client.dto.InternProfileClientDto;
import org.example.aiservice.client.dto.ProgramClientDto;
import org.example.aiservice.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "intern-and-program-service", url = "${ai.intern-service.url:http://intern-and-program-service:8082}")
public interface InternServiceClient {

    @GetMapping("/api/interns/my-profile")
    ApiResponse<InternProfileClientDto> getMyProfile(
            @RequestHeader("Authorization") String authHeader
    );

    @GetMapping("/api/interns/{id}")
    ApiResponse<InternProfileClientDto> getInternById(
            @PathVariable("id") Long id,
            @RequestHeader("Authorization") String authHeader
    );

    @GetMapping("/api/interns/{internCode}/documents")
    ApiResponse<List<DocumentClientDto>> getDocumentsByInternCode(
            @PathVariable("internCode") String internCode,
            @RequestHeader("Authorization") String authHeader
    );

    @GetMapping("/api/programs/{id}")
    ApiResponse<ProgramClientDto> getProgramById(
            @PathVariable("id") Long id,
            @RequestHeader("Authorization") String authHeader
    );

    @GetMapping("/api/interns/documents/{documentId}/download")
    ResponseEntity<byte[]> downloadDocument(
            @PathVariable("documentId") Long documentId,
            @RequestHeader("Authorization") String authHeader
    );
}

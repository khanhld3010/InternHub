package org.example.employeeservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.employeeservice.intern.entity.enums.DocumentStatus;
import org.example.employeeservice.intern.entity.enums.DocumentType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {

    private Long id;
    private String internCode;
    private DocumentType documentType;
    private String originalFileName;
    private Long fileSize;
    private String contentType;
    private DocumentStatus status;
    private LocalDateTime createdAt;
}

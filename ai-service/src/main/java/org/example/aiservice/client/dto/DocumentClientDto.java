package org.example.aiservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentClientDto {

    private Long id;
    private String internCode;
    private String documentType;
    private String originalFileName;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private String status;
}

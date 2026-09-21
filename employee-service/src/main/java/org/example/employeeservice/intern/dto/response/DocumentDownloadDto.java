package org.example.employeeservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDownloadDto {

    private Resource resource;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
}

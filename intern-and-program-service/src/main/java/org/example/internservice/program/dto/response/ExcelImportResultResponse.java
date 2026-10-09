package org.example.internservice.program.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportResultResponse {
    private Long programId;
    private String programName;
    private int importedCount;
    private int currentInterns;
    private int maxInterns;
    private List<String> importedInternCodes;
    private LocalDateTime importedAt;
}

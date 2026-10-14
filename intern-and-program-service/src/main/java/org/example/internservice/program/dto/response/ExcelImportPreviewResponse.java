package org.example.internservice.program.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.program.dto.request.ExcelInternRowDto;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportPreviewResponse {
    private Long programId;
    private String programName;
    private int totalRows;
    private int validRowsCount;
    private int invalidRowsCount;
    private int availableSlots;
    private boolean isQuotaExceeded;
    private List<ExcelRowError> errors;
    private List<ExcelInternRowDto> previewRows;
}

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
public class ProgramClientDto {

    private Long id;
    private String programCode;
    private String name;
    private String description;
    private String departmentName;
    private Integer maxInterns;
    private Long currentInterns;
}

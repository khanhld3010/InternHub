package org.example.internservice.mission.dto.response;

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
public class AssigneeResponse {

    private Long id;
    private Long userId;
    private String internCode;
    private String fullName;
    private String email;
    private String phone;
    private String appliedPosition;
}

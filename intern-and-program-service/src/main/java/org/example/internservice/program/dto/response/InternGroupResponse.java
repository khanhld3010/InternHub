package org.example.internservice.program.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternGroupResponse {

    private Long id;
    private Long programId;
    private String name;
    private Integer maxMembers;
    private Integer memberCount;
    private Long mentorId;
    private String mentorName;
    private List<GroupMemberResponse> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupMemberResponse {
        private Long id;
        private String internCode;
        private String fullName;
        private String appliedPosition;
        private String email;
        private String phone;
    }
}

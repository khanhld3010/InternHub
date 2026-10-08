package org.example.internservice.program.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchApplyGroupsRequest {

    @NotEmpty(message = "Danh sách nhóm không được để trống")
    @Valid
    private List<GroupBatchItem> groups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupBatchItem {
        @jakarta.validation.constraints.NotBlank(message = "Tên nhóm không được để trống")
        private String name;
        private Integer maxMembers;
        private List<Long> internIds;
    }
}

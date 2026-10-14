package org.example.internservice.program.service;

import org.example.internservice.program.dto.request.BatchApplyGroupsRequest;
import org.example.internservice.program.dto.request.CreateGroupRequest;
import org.example.internservice.program.dto.response.InternGroupResponse;

import java.util.List;

public interface InternGroupService {

    List<InternGroupResponse> getGroupsByProgramId(Long programId);

    InternGroupResponse createGroup(Long programId, CreateGroupRequest request);

    InternGroupResponse updateGroup(Long programId, Long groupId, CreateGroupRequest request);

    void disbandGroup(Long programId, Long groupId);

    List<InternGroupResponse> batchApplyGroups(Long programId, BatchApplyGroupsRequest request);

    void addMemberToGroup(Long programId, Long groupId, Long internId);

    void removeMemberFromGroup(Long programId, Long groupId, Long internId);
}

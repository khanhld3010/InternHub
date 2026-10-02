package org.example.internservice.mission.service;

import org.example.internservice.mission.dto.request.CreateMissionBoardRequest;
import org.example.internservice.mission.dto.request.UpdateMissionBoardRequest;
import org.example.internservice.mission.dto.response.AssigneeResponse;
import org.example.internservice.mission.dto.response.MentorProgramResponse;
import org.example.internservice.mission.dto.response.MissionBoardDetailResponse;
import org.example.internservice.mission.dto.response.MissionBoardResponse;
import org.example.internservice.security.CustomUserDetails;

import java.util.List;

public interface MissionBoardService {

    MissionBoardResponse createBoard(Long programId, CreateMissionBoardRequest request, CustomUserDetails userDetails);

    List<MissionBoardResponse> getBoardsByProgram(Long programId, CustomUserDetails userDetails);

    MissionBoardDetailResponse getBoardDetail(Long boardId, CustomUserDetails userDetails);

    MissionBoardResponse updateBoard(Long boardId, UpdateMissionBoardRequest request, CustomUserDetails userDetails);

    void deleteBoard(Long boardId, CustomUserDetails userDetails);

    List<MentorProgramResponse> getMyMentoredPrograms(CustomUserDetails userDetails);

    List<AssigneeResponse> getProgramInterns(Long programId, CustomUserDetails userDetails);

    void addMentorToProgram(Long programId, Long mentorId, String assignedBy);

    void removeMentorFromProgram(Long programId, Long mentorId);

    List<org.example.internservice.intern.dto.response.MentorOptionResponse> getMentorsByProgram(Long programId);
}

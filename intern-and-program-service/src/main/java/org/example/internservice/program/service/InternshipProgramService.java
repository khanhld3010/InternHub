package org.example.internservice.program.service;

import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.program.dto.request.ChangeProgramStatusRequest;
import org.example.internservice.program.dto.request.CreateProgramRequest;
import org.example.internservice.program.dto.request.ProgramFilterRequest;
import org.example.internservice.program.dto.request.UpdateProgramRequest;
import org.example.internservice.program.dto.response.DepartmentResponse;
import org.example.internservice.program.dto.response.ProgramDetailResponse;
import org.example.internservice.program.dto.response.ProgramSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InternshipProgramService {

    List<DepartmentResponse> getAllDepartments();

    ProgramDetailResponse createProgram(CreateProgramRequest request, String createdBy);

    ProgramDetailResponse updateProgram(Long id, UpdateProgramRequest request);

    PageResponse<ProgramDetailResponse> getPrograms(ProgramFilterRequest filter, Pageable pageable);

    ProgramDetailResponse getProgramDetailById(Long id);

    ProgramSummaryResponse getProgramSummaryById(Long id);

    List<ProgramSummaryResponse> getOpenPrograms();

    ProgramDetailResponse changeStatus(Long id, ChangeProgramStatusRequest request);

    ProgramDetailResponse toggleRecruitment(Long id);

    void deleteProgram(Long id);
}

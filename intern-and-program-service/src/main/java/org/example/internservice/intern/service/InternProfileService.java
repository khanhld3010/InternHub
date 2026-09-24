package org.example.internservice.intern.service;

import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.intern.dto.request.CreateInternRequest;
import org.example.internservice.intern.dto.request.InternDecisionRequest;
import org.example.internservice.intern.dto.request.InternFilterRequest;
import org.example.internservice.intern.dto.request.UpdateInternRequest;
import org.example.internservice.intern.dto.response.InternResponse;
import org.springframework.data.domain.Pageable;

public interface InternProfileService {

    InternResponse createIntern(CreateInternRequest request);

    InternResponse updateIntern(Long id, UpdateInternRequest request);

    InternResponse processDecision(Long id, InternDecisionRequest request, String reviewerUsername);

    InternResponse resendDecisionEmail(Long id, String reviewerUsername);

    void updateEmailStatus(Long id, String status, String errorMessage);

    PageResponse<InternResponse> searchInterns(InternFilterRequest request, Pageable pageable);
}


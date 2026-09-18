package org.example.employeeservice.intern.service;

import org.example.employeeservice.common.dto.response.PageResponse;
import org.example.employeeservice.intern.dto.request.CreateInternRequest;
import org.example.employeeservice.intern.dto.request.InternFilterRequest;
import org.example.employeeservice.intern.dto.request.UpdateInternRequest;
import org.example.employeeservice.intern.dto.response.InternResponse;
import org.springframework.data.domain.Pageable;

public interface InternProfileService {

    InternResponse createIntern(CreateInternRequest request);

    InternResponse updateIntern(Long id, UpdateInternRequest request);

    PageResponse<InternResponse> searchInterns(InternFilterRequest request, Pageable pageable);
}


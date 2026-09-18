package org.example.employeeservice.intern.service;

import org.example.employeeservice.intern.dto.request.CreateInternRequest;
import org.example.employeeservice.intern.dto.response.InternResponse;

public interface InternProfileService {

    InternResponse createIntern(CreateInternRequest request);
}

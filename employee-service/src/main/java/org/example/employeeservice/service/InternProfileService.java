package org.example.employeeservice.service;

import org.example.employeeservice.dto.request.CreateInternRequest;
import org.example.employeeservice.dto.response.InternResponse;

public interface InternProfileService {

    InternResponse createIntern(CreateInternRequest request);
}

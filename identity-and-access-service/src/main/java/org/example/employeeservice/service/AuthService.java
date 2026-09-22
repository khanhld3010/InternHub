package org.example.employeeservice.service;

import org.example.employeeservice.dto.request.LoginRequest;
import org.example.employeeservice.dto.response.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}

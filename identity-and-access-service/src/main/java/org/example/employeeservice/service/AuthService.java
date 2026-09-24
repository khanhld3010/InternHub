package org.example.employeeservice.service;

import org.example.employeeservice.dto.request.LoginRequest;
import org.example.employeeservice.dto.request.RegisterRequest;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.dto.response.RegisterResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    RegisterResponse register(RegisterRequest request);
}

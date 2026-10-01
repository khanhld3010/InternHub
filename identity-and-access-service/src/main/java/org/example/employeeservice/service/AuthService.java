package org.example.employeeservice.service;

import org.example.employeeservice.dto.request.ActivateAccountRequest;
import org.example.employeeservice.dto.request.LoginRequest;
import org.example.employeeservice.dto.request.RegisterRequest;
import org.example.employeeservice.dto.request.ResendActivationRequest;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.dto.response.RegisterResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    RegisterResponse register(RegisterRequest request);
    void activateAccount(ActivateAccountRequest request);
    void resendActivation(ResendActivationRequest request);
    void changePassword(String username, org.example.employeeservice.dto.request.ChangePasswordRequest request);
}

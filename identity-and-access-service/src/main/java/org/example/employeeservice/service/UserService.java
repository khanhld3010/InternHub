package org.example.employeeservice.service;

import org.example.employeeservice.dto.response.UserResponse;
import org.example.employeeservice.entity.User;

import java.util.List;

public interface UserService {

    List<UserResponse> getAllUsers();

    UserResponse getUserById(Integer id);

    UserResponse getUserByEmail(String email);

    User findEntityById(Integer id);

    UserResponse toggleUserStatus(Integer id);

    UserResponse updateCurrentUserProfile(String username, org.example.employeeservice.dto.request.UpdateUserProfileRequest request);
    UserResponse getUserByUsername(String username);
    UserResponse getCurrentUserProfile(String username);

    UserResponse updateOwnProfile(String username, org.example.employeeservice.dto.request.UpdateProfileRequest request);
}

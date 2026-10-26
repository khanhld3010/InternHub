package org.example.employeeservice.service;

import org.example.employeeservice.dto.request.UpdateProfileRequest;
import org.example.employeeservice.dto.request.UpdateUserProfileRequest;
import org.example.employeeservice.dto.response.UserResponse;
import org.example.employeeservice.entity.User;

import java.util.List;

public interface UserService {

    List<UserResponse> getAllUsers();

    UserResponse getUserById(Integer id);

    UserResponse getUserByEmail(String email);

    User findEntityById(Integer id);

    UserResponse toggleUserStatus(Integer id);

    UserResponse updateCurrentUserProfile(String username, UpdateUserProfileRequest request);
    UserResponse getUserByUsername(String username);
    UserResponse getCurrentUserProfile(String username);

    UserResponse updateOwnProfile(String username, UpdateProfileRequest request);

    void resetUserPassword(Integer userId, String newPassword);

    UserResponse updateUserRole(Integer userId, String newRoleName);
}

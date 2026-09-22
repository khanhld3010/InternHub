package org.example.employeeservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.dto.response.UserResponse;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.User;
import org.example.employeeservice.exception.ResourceNotFoundException;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.UserRepository;
import org.example.employeeservice.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    @Override
    public List<UserResponse> getAllUsers() {
        log.info("Lấy danh sách tất cả người dùng trong hệ thống");
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public UserResponse getUserById(Integer id) {
        log.info("Lấy thông tin người dùng với ID: {}", id);
        return userRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        log.info("Lấy thông tin người dùng với email: {}", email);
        return userRepository.findByEmail(email)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    @Override
    public User findEntityById(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));
    }

    @Override
    @Transactional
    public UserResponse toggleUserStatus(Integer id) {
        log.info("Thay đổi trạng thái tài khoản người dùng có ID={}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));

        Account account = user.getAccount();
        if (account == null) {
            account = accountRepository.findByUserId(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản gắn với người dùng ID: " + id));
        }

        String newStatus = "ACTIVE".equalsIgnoreCase(account.getStatus()) ? "INACTIVE" : "ACTIVE";
        account.setStatus(newStatus);
        Account updatedAccount = accountRepository.save(account);
        user.setAccount(updatedAccount);

        return mapToResponse(user);
    }

    private UserResponse mapToResponse(User user) {
        Account account = user.getAccount();
        if (account == null && user.getId() != null) {
            account = accountRepository.findByUserId(user.getId()).orElse(null);
        }

        String roleName = (account != null && account.getRole() != null)
                ? account.getRole().getName().replace("ROLE_", "")
                : "USER";
        String status = (account != null && account.getStatus() != null)
                ? account.getStatus()
                : "ACTIVE";

        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .phone(user.getPhoneNumber())
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender())
                .address(user.getAddress())
                .avatarUrl(user.getAvatarUrl())
                .department("Phòng Kỹ Thuật")
                .position(roleName)
                .status(status)
                .role(roleName)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

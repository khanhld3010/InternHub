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
    private final org.example.employeeservice.client.NotificationServiceClient notificationServiceClient;

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

        // Nếu chuyển sang INACTIVE, phát tín hiệu bảo mật ép đăng xuất tức thì qua WebSocket & Redis
        if ("INACTIVE".equalsIgnoreCase(newStatus)) {
            log.warn("Tài khoản userId={} bị vô hiệu hóa (INACTIVE), phát lệnh ACCOUNT_LOCKED tới socket", id);
            notificationServiceClient.dispatchSecurityCommand(
                    "ACCOUNT_LOCKED",
                    id.longValue(),
                    "Tài khoản của bạn đã bị khóa bởi Quản trị viên hệ thống",
                    "Phiên làm việc đã bị chấm dứt. Vui lòng liên hệ ban quản trị để biết thêm chi tiết."
            );
        }

        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUserProfile(String username, org.example.employeeservice.dto.request.UpdateUserProfileRequest request) {
        log.info("Cập nhật thông tin cá nhân cho người dùng username={}", username);
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với username: " + username));

        User user = account.getUser();
        if (user == null && account.getUserId() != null) {
            user = userRepository.findById(account.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng gắn với tài khoản"));
        }
        if (user == null) {
            throw new ResourceNotFoundException("Không tìm thấy dữ liệu người dùng");
        }

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName().trim());
        }

        String phone = request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : 
                      (request.getPhone() != null ? request.getPhone().trim() : null);
        if (phone != null && !phone.isEmpty()) {
            // Kiểm tra trùng lặp số điện thoại nếu thay đổi
            if (!phone.equals(user.getPhoneNumber()) && userRepository.existsByPhoneNumber(phone)) {
                throw new org.example.employeeservice.exception.DuplicateResourceException("Số điện thoại này đã được sử dụng bởi tài khoản khác");
            }
            user.setPhoneNumber(phone);
        }

        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth());
        }

        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }

        if (request.getAddress() != null) {
            user.setAddress(request.getAddress().trim());
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }

        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        log.info("Lấy thông tin profile cho username: {}", username);
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với username: " + username));
        User user = account.getUser();
        if (user == null && account.getUserId() != null) {
            user = userRepository.findById(account.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng gắn với tài khoản"));
        }
        if (user == null) {
            throw new ResourceNotFoundException("Không tìm thấy dữ liệu người dùng");
        }
        return mapToResponse(user);
    }

    @Override
    public UserResponse getCurrentUserProfile(String username) {
        log.info("Lấy thông tin tài khoản đang đăng nhập: {}", username);
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với username: " + username));

        User user = account.getUser();
        if (user == null && account.getUserId() != null) {
            user = userRepository.findById(account.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ người dùng tương ứng với username: " + username));
        }

        if (user == null) {
            throw new ResourceNotFoundException("Hồ sơ người dùng không tồn tại");
        }

        user.setAccount(account);
        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateOwnProfile(String username, org.example.employeeservice.dto.request.UpdateProfileRequest request) {
        log.info("Xử lý cập nhật hồ sơ cá nhân cho user: {}", username);
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với username: " + username));

        User user = account.getUser();
        if (user == null && account.getUserId() != null) {
            user = userRepository.findById(account.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ người dùng tương ứng với username: " + username));
        }

        if (user == null) {
            throw new ResourceNotFoundException("Hồ sơ người dùng không tồn tại");
        }

        // =========================================================================
        // NHÓM A — Khóa sau khi tài khoản kích hoạt (Fraud/Legal-Sensitive, CHUNG 4 role)
        //fullName, dateOfBirth gắn trực tiếp với giấy tờ pháp lý (CMND/CCCD, bằng cấp, hợp đồng)
        // Chặn tuyệt đối ở tầng Backend, KHÔNG dựa vào việc disable field trên Frontend!
        // =========================================================================
        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            String incomingFullName = request.getFullName().trim();
            if (user.getFullName() != null && !incomingFullName.equalsIgnoreCase(user.getFullName().trim())) {
                log.warn("User {} cố gắng tự thay đổi họ và tên từ '{}' sang '{}'", username, user.getFullName(), incomingFullName);
                throw new org.example.employeeservice.exception.ForbiddenException(
                        "Không thể tự thay đổi họ tên. Vui lòng liên hệ Ban Nhân sự."
                );
            }
        }

        if (request.getDateOfBirth() != null) {
            if (user.getDateOfBirth() != null && !request.getDateOfBirth().equals(user.getDateOfBirth())) {
                log.warn("User {} cố gắng tự thay đổi ngày sinh từ '{}' sang '{}'", username, user.getDateOfBirth(), request.getDateOfBirth());
                throw new org.example.employeeservice.exception.ForbiddenException(
                        "Không thể tự thay đổi ngày sinh. Vui lòng liên hệ Ban Nhân sự."
                );
            }
        }

        // =========================================================================
        // NHÓM B — Tự do chỉnh sửa (Self-Service, CHUNG cả 4 role)
        // phoneNumber, address, bio, gender
        // =========================================================================
        String newPhone = (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty())
                ? request.getPhoneNumber().trim()
                : (request.getPhone() != null && !request.getPhone().trim().isEmpty() ? request.getPhone().trim() : null);

        if (newPhone != null) {
            user.setPhoneNumber(newPhone);
        }

        if (request.getAddress() != null) {
            user.setAddress(request.getAddress().trim());
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }

        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }

        User updatedUser = userRepository.save(user);
        updatedUser.setAccount(account);
        log.info("Cập nhật thông tin cá nhân thành công cho user: {}", username);

        return mapToResponse(updatedUser);
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
                .bio(user.getBio())
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

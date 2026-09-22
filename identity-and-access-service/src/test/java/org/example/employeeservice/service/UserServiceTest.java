package org.example.employeeservice.service;

import org.example.employeeservice.dto.response.UserResponse;
import org.example.employeeservice.entity.User;
import org.example.employeeservice.entity.enums.Gender;
import org.example.employeeservice.exception.ResourceNotFoundException;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.UserRepository;
import org.example.employeeservice.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1)
                .fullName("Nguyễn Văn An")
                .email("admin@internhub.com")
                .phoneNumber("0901112233")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .gender(Gender.MALE)
                .address("Hà Nội")
                .avatarUrl("https://api.dicebear.com/7.x/avataaars/svg?seed=An")
                .build();
    }

    @Test
    @DisplayName("Lấy danh sách tất cả người dùng thành công")
    void testGetAllUsers_ShouldReturnUserList() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));

        List<UserResponse> responses = userService.getAllUsers();

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Nguyễn Văn An", responses.get(0).getFullName());
        assertEquals("admin@internhub.com", responses.get(0).getEmail());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Lấy thông tin người dùng theo ID thành công khi tồn tại")
    void testGetUserById_WhenFound_ShouldReturnResponse() {
        when(userRepository.findById(1)).thenReturn(Optional.of(sampleUser));

        UserResponse response = userService.getUserById(1);

        assertNotNull(response);
        assertEquals(1, response.getId());
        assertEquals("Nguyễn Văn An", response.getFullName());
        verify(userRepository, times(1)).findById(1);
    }

    @Test
    @DisplayName("Ném ResourceNotFoundException khi không tìm thấy người dùng theo ID")
    void testGetUserById_WhenNotFound_ShouldThrowException() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(999));
        verify(userRepository, times(1)).findById(999);
    }

    @Test
    @DisplayName("Lấy thông tin người dùng theo email thành công")
    void testGetUserByEmail_WhenFound_ShouldReturnResponse() {
        when(userRepository.findByEmail("admin@internhub.com")).thenReturn(Optional.of(sampleUser));

        UserResponse response = userService.getUserByEmail("admin@internhub.com");

        assertNotNull(response);
        assertEquals("admin@internhub.com", response.getEmail());
        verify(userRepository, times(1)).findByEmail("admin@internhub.com");
    }
}

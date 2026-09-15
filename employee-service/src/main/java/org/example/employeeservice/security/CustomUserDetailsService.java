package org.example.employeeservice.security;

import lombok.RequiredArgsConstructor;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.repository.AccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản với username: " + username));

        boolean enabled = "ACTIVE".equalsIgnoreCase(account.getStatus());
        String roleName = (account.getRole() != null) ? account.getRole().getName().toUpperCase() : "USER";
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + roleName)
        );

        return new User(
                account.getUsername(),
                account.getPasswordHash(),
                enabled,
                true,
                true,
                true,
                authorities
        );
    }
}

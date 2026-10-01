package org.example.employeeservice.security;

import lombok.RequiredArgsConstructor;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.Permission;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản với username: " + username));

        boolean enabled = "ACTIVE".equalsIgnoreCase(account.getStatus());
        String roleName = (account.getRole() != null) ? account.getRole().getName().toUpperCase() : "USER";
        String authorityName = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(authorityName));

        if (account.getRole() != null && account.getRole().getId() != null) {
            roleRepository.findByIdWithPermissions(account.getRole().getId())
                    .ifPresent(r -> {
                        if (r.getPermissions() != null) {
                            for (Permission p : r.getPermissions()) {
                                authorities.add(new SimpleGrantedAuthority(p.getCode()));
                            }
                        }
                    });
        }

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

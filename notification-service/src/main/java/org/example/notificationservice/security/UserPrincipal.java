package org.example.notificationservice.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.security.Principal;

@Getter
@AllArgsConstructor
public class UserPrincipal implements Principal {
    private final Long userId;
    private final String role;

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}

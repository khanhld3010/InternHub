package org.example.employeeservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.employeeservice.entity.Account;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenRotationResult {

    private Account account;

    private String newRawRefreshToken;

    private boolean rememberMe;
}

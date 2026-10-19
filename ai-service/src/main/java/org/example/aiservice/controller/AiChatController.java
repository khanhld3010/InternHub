package org.example.aiservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.aiservice.dto.request.ChatRequest;
import org.example.aiservice.dto.response.ApiResponse;
import org.example.aiservice.dto.response.ChatResponse;
import org.example.aiservice.dto.response.QuickPromptsResponse;
import org.example.aiservice.service.AiChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('AI_CHAT_USE') or hasRole('INTERN')")
    public ApiResponse<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal String userId,
            HttpServletRequest httpServletRequest
    ) {
        String authHeader = httpServletRequest.getHeader("Authorization");
        return ApiResponse.success(aiChatService.chat(request, userId, authHeader));
    }

    @DeleteMapping("/chat/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('AI_CHAT_USE') or hasRole('INTERN')")
    public ResponseEntity<Void> clearSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal String userId
    ) {
        aiChatService.clearSession(sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/prompts")
    @PreAuthorize("hasAuthority('AI_CHAT_USE') or hasRole('INTERN')")
    public ApiResponse<QuickPromptsResponse> getQuickPrompts() {
        return ApiResponse.success(aiChatService.getQuickPrompts());
    }
}

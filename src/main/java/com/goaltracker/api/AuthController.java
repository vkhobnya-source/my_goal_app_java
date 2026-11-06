package com.goaltracker.api;

import com.goaltracker.dto.AuthRequest;
import com.goaltracker.dto.TokenResponse;
import com.goaltracker.i18n.Messages;
import com.goaltracker.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final Messages messages;

    public AuthController(AuthService authService, Messages messages) {
        this.authService = authService;
        this.messages = messages;
    }

    @PostMapping("/register")
    public TokenResponse register(
            @RequestBody AuthRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return authService.register(request, messages.resolveLanguage(acceptLanguage));
    }

    @PostMapping("/login")
    public TokenResponse login(
            @RequestBody AuthRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return authService.login(request, messages.resolveLanguage(acceptLanguage));
    }
}

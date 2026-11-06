package com.goaltracker.service;

import java.util.Objects;

import com.goaltracker.api.ApiException;
import com.goaltracker.auth.JwtService;
import com.goaltracker.auth.PasswordService;
import com.goaltracker.domain.User;
import com.goaltracker.dto.AuthRequest;
import com.goaltracker.dto.TokenResponse;
import com.goaltracker.i18n.Messages;
import com.goaltracker.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "bearer";

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final Messages messages;

    public AuthService(
            UserRepository userRepository,
            PasswordService passwordService,
            JwtService jwtService,
            Messages messages
    ) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.messages = messages;
    }

    @Transactional
    public TokenResponse register(AuthRequest request, String language) {
        requireCredentials(request, language);

        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    messages.t("email_already_registered", language)
            );
        }

        userRepository.create(request.email(), passwordService.hash(request.password()));
        return token(request.email());
    }

    public TokenResponse login(AuthRequest request, String language) {
        requireCredentials(request, language);

        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordService.matches(request.password(), user.hashedPassword())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    messages.t("invalid_credentials", language)
            );
        }
        return token(user.email());
    }

    private TokenResponse token(String email) {
        return new TokenResponse(jwtService.createToken(email), TOKEN_TYPE);
    }

    private void requireCredentials(AuthRequest request, String language) {
        if (request == null
                || Objects.isNull(request.email())
                || Objects.isNull(request.password())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    messages.t("invalid_request_body", language)
            );
        }
    }
}

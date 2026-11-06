package com.goaltracker.config;

import com.goaltracker.api.ApiException;
import com.goaltracker.auth.JwtService;
import com.goaltracker.domain.User;
import com.goaltracker.i18n.Messages;
import com.goaltracker.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUser} parameters from a Bearer JWT and validates that
 * the user still exists. Invalid or missing tokens produce a localized 401.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final Messages messages;

    public CurrentUserArgumentResolver(
            JwtService jwtService,
            UserRepository userRepository,
            Messages messages
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.messages = messages;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && User.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String language = messages.resolveLanguage(
                request == null ? null : request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)
        );
        String authorization = request == null
                ? null
                : request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw unauthorized(language);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw unauthorized(language);
        }

        try {
            String email = jwtService.parseEmail(token);
            return userRepository.findByEmail(email).orElseThrow(() -> unauthorized(language));
        } catch (JwtException | IllegalArgumentException exception) {
            throw unauthorized(language);
        }
    }

    private ApiException unauthorized(String language) {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                messages.t("could_not_validate_credentials", language),
                "Bearer"
        );
    }
}

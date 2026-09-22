package com.darshan.circl.identity;

import com.darshan.circl.common.error.UnauthorizedException;
import com.darshan.circl.common.web.CurrentUser;
import com.darshan.circl.identity.dto.LoginRequest;
import com.darshan.circl.identity.dto.RefreshRequest;
import com.darshan.circl.identity.dto.SignupRequest;
import com.darshan.circl.identity.dto.TokenResponse;
import com.darshan.circl.identity.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService auth;
    private final RefreshCookie cookie;

    public AuthController(AuthService auth, RefreshCookie cookie) {
        this.auth = auth;
        this.cookie = cookie;
    }

    @PostMapping("/auth/signup")
    public ResponseEntity<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return withCookie(HttpStatus.CREATED, auth.signup(request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withCookie(HttpStatus.OK, auth.login(request));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody(required = false) RefreshRequest request,
                                                 @CookieValue(name = RefreshCookie.NAME, required = false) String fromCookie) {
        return withCookie(HttpStatus.OK, auth.refresh(pick(request, fromCookie)));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request,
                                       @CookieValue(name = RefreshCookie.NAME, required = false) String fromCookie) {
        auth.logout(pick(request, fromCookie));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.clear()).build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(CurrentUser.id(jwt));
    }

    private ResponseEntity<TokenResponse> withCookie(HttpStatus status, TokenResponse tokens) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.issue(tokens.refreshToken()))
                .body(tokens);
    }

    // the body wins so API clients and Postman keep working the same way
    private static String pick(RefreshRequest request, String fromCookie) {
        if (request != null && StringUtils.hasText(request.refreshToken())) {
            return request.refreshToken();
        }
        if (StringUtils.hasText(fromCookie)) {
            return fromCookie;
        }
        throw new UnauthorizedException("No refresh token in the body or cookie");
    }
}

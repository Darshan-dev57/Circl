package com.darshan.circl.identity;

import com.darshan.circl.common.error.ConflictException;
import com.darshan.circl.common.error.ForbiddenException;
import com.darshan.circl.common.error.NotFoundException;
import com.darshan.circl.common.error.UnauthorizedException;
import com.darshan.circl.common.text.TextSanitizer;
import com.darshan.circl.config.JwtProperties;
import com.darshan.circl.identity.dto.LoginRequest;
import com.darshan.circl.identity.dto.SignupRequest;
import com.darshan.circl.identity.dto.TokenResponse;
import com.darshan.circl.identity.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final JwtProperties props;
    private final Clock clock;
    /** compared against when the email does not exist, so both paths cost one BCrypt check */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwords,
                       TokenService tokens, JwtProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.tokens = tokens;
        this.props = props;
        this.clock = clock;
        this.dummyHash = passwords.encode("not-a-real-password");
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public TokenResponse signup(SignupRequest req) {
        String email = normalizeEmail(req.email());
        Role role = req.role() == null ? Role.PARTICIPANT : req.role();
        if (role == Role.ADMIN) {
            throw new ForbiddenException("ADMIN cannot be chosen at signup");
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("email-taken", "An account with this email already exists");
        }
        User user = users.save(new User(email, TextSanitizer.clean(req.name()), passwords.encode(req.password()), role));
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        User user = users.findByEmail(email).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean ok = passwords.matches(req.password(), hash);
        if (user == null || !ok) {
            throw new UnauthorizedException("Email or password is wrong");
        }
        return issueTokens(user);
    }

    /**
     * Rotation: the old refresh token is revoked and a new one is issued.
     * If a token that was already rotated shows up again, someone copied it:
     * revoke every token of that user (they have to log in again).
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenResponse refresh(String rawToken) {
        Instant now = Instant.now(clock);
        RefreshToken token = refreshTokens.findByHashForUpdate(TokenService.sha256(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Refresh token is not valid"));
        if (token.isRevoked()) {
            int revoked = refreshTokens.revokeAllForUser(token.getUserId(), now);
            log.warn("Refresh token reuse for user {}, revoked {} active tokens", token.getUserId(), revoked);
            throw new UnauthorizedException("Refresh token was already used");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new UnauthorizedException("Refresh token has expired");
        }
        token.revoke(now);
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Refresh token is not valid"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByHashForUpdate(TokenService.sha256(rawToken))
                .ifPresent(t -> t.revoke(Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId).map(UserResponse::of)
                .orElseThrow(() -> new NotFoundException("User", userId));
    }

    private TokenResponse issueTokens(User user) {
        String refresh = tokens.newRefreshToken();
        refreshTokens.save(new RefreshToken(user.getId(), TokenService.sha256(refresh),
                Instant.now(clock).plus(props.refreshTtl())));
        return new TokenResponse(tokens.accessToken(user), refresh, "Bearer", tokens.accessTtlSeconds(),
                UserResponse.of(user));
    }
}

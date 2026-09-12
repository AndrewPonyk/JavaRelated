package com.shopflow.auth.api;

import com.shopflow.auth.domain.UserAccount;
import com.shopflow.auth.service.TokenService;
import com.shopflow.auth.service.UserService;
import com.shopflow.common.dto.ApiResponse;
import com.shopflow.common.error.ApiException;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication API: register, login, refresh, current-user, and logout.
 * Credentials are verified against the Redis-backed user store; tokens are
 * issued/validated by {@link TokenService}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService users;
    private final TokenService tokens;

    public AuthController(UserService users, TokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    /** Register a new account. */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserView>> register(@Valid @RequestBody RegisterRequest req) {
        UserAccount account = users.register(req.username(), req.email(), req.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(UserView.of(account)));
    }

    /** Exchange credentials for an access + refresh token pair. */
    @PostMapping("/login")
    public ApiResponse<TokenPair> login(@Valid @RequestBody LoginRequest req) {
        UserAccount account = users.authenticate(req.username(), req.password());
        return ApiResponse.ok(issueFor(account.getUsername(), account.getRoles()));
    }

    /** Rotate a refresh token for a fresh access + refresh pair. */
    @PostMapping("/refresh")
    public ApiResponse<TokenPair> refresh(@Valid @RequestBody RefreshRequest req) {
        String userId = tokens.rotateRefreshToken(req.refreshToken());
        // Roles are re-read from the store so a role change takes effect on refresh.
        String roles = users.rolesOf(userId);
        return ApiResponse.ok(issueFor(userId, roles));
    }

    /** Return the principal described by a valid access token. */
    @GetMapping("/me")
    public ApiResponse<UserView> me(@RequestHeader("Authorization") String authorization) {
        Claims claims = tokens.validateAccessToken(bearer(authorization));
        return ApiResponse.ok(new UserView(claims.getSubject(), null, claims.get("roles", String.class)));
    }

    /** Invalidate a refresh token. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest req) {
        tokens.revokeRefreshToken(req.refreshToken());
        return ApiResponse.ok(null);
    }

    // --- helpers -------------------------------------------------------------

    private TokenPair issueFor(String userId, String roles) {
        return new TokenPair(
                tokens.issueAccessToken(userId, roles),
                tokens.issueRefreshToken(userId),
                "Bearer",
                tokens.getAccessTtlSeconds());
    }

    private static String bearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MISSING_TOKEN",
                    "Authorization: Bearer <token> header required");
        }
        return header.substring("Bearer ".length());
    }

    // --- DTOs ----------------------------------------------------------------

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 40) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }

    public record RefreshRequest(@NotBlank String refreshToken) { }

    public record TokenPair(String accessToken, String refreshToken, String tokenType, long expiresIn) { }

    public record UserView(String username, String email, String roles) {
        static UserView of(UserAccount a) {
            return new UserView(a.getUsername(), a.getEmail(), a.getRoles());
        }
    }
}

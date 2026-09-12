package com.shopflow.auth.service;

import com.shopflow.auth.domain.UserAccount;
import com.shopflow.auth.repository.UserRepository;
import com.shopflow.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * User registration and credential verification, backed by Redis. Passwords are
 * stored only as BCrypt hashes.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String DEFAULT_ROLES = "ROLE_CUSTOMER";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /** Register a new account. 409 if the username is taken. */
    public UserAccount register(String username, String email, String rawPassword) {
        if (users.existsById(username)) {
            throw ApiException.conflict("Username '" + username + "' is already taken");
        }
        UserAccount account = new UserAccount(
                username, email, passwordEncoder.encode(rawPassword), DEFAULT_ROLES);
        UserAccount saved = users.save(account);
        log.info("Registered user {}", username);
        return saved;
    }

    /** Verify credentials, returning the account or throwing 401. */
    public UserAccount authenticate(String username, String rawPassword) {
        UserAccount account = users.findById(username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid username or password"));
        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                    "Invalid username or password");
        }
        return account;
    }

    /** Look up a user's roles by username (used on token refresh). Throws 401 if gone. */
    public String rolesOf(String username) {
        return users.findById(username)
                .map(UserAccount::getRoles)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "USER_GONE", "Account no longer exists"));
    }

    /** Idempotent seed used at startup for a demo account. */
    public void seedIfAbsent(String username, String email, String rawPassword, String roles) {
        if (!users.existsById(username)) {
            users.save(new UserAccount(username, email, passwordEncoder.encode(rawPassword), roles));
            log.info("Seeded demo user {}", username);
        }
    }
}

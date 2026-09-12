package com.shopflow.auth.domain;

import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

/**
 * User account persisted in Redis (hash per user, keyed by username). Stores a
 * BCrypt password hash — never the raw password. {@code roles} is a
 * comma-separated list of Spring Security authorities.
 */
@RedisHash("users")
public class UserAccount implements Serializable {

    @Id
    private String username;
    private String email;
    private String passwordHash;
    private String roles;

    public UserAccount() {
    }

    public UserAccount(String username, String email, String passwordHash, String roles) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = roles;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }
}

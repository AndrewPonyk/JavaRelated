package com.shopflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopflow.auth.domain.UserAccount;
import com.shopflow.auth.repository.UserRepository;
import com.shopflow.common.error.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, encoder);
    }

    @Test
    void register_hashesPassword_andPersists() {
        when(repository.existsById("alice")).thenReturn(false);
        when(repository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        UserAccount created = service.register("alice", "alice@x.io", "secret123");

        assertThat(created.getUsername()).isEqualTo("alice");
        assertThat(created.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(encoder.matches("secret123", created.getPasswordHash())).isTrue();
        assertThat(created.getRoles()).contains("ROLE_CUSTOMER");
    }

    @Test
    void register_duplicateUsername_conflicts() {
        when(repository.existsById("bob")).thenReturn(true);

        assertThatThrownBy(() -> service.register("bob", "b@x.io", "secret123"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void authenticate_correctPassword_returnsAccount() {
        UserAccount stored = new UserAccount("carol", "c@x.io", encoder.encode("hunter2xx"), "ROLE_CUSTOMER");
        when(repository.findById("carol")).thenReturn(Optional.of(stored));

        assertThat(service.authenticate("carol", "hunter2xx").getUsername()).isEqualTo("carol");
    }

    @Test
    void authenticate_wrongPassword_throws401() {
        UserAccount stored = new UserAccount("dave", "d@x.io", encoder.encode("rightpass1"), "ROLE_CUSTOMER");
        when(repository.findById("dave")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.authenticate("dave", "wrongpass1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void authenticate_unknownUser_throws401() {
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("ghost", "whatever12"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rolesOf_found_returnsRoles() {
        when(repository.findById("erin"))
                .thenReturn(Optional.of(new UserAccount("erin", "e@x.io", "h", "ROLE_CUSTOMER,ROLE_ADMIN")));

        assertThat(service.rolesOf("erin")).isEqualTo("ROLE_CUSTOMER,ROLE_ADMIN");
    }

    @Test
    void rolesOf_missing_throws401() {
        when(repository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rolesOf("gone")).isInstanceOf(ApiException.class);
    }

    @Test
    void seedIfAbsent_whenAbsent_persists() {
        when(repository.existsById("demo")).thenReturn(false);

        service.seedIfAbsent("demo", "d@x.io", "password123", "ROLE_CUSTOMER");

        verify(repository).save(any(UserAccount.class));
    }

    @Test
    void seedIfAbsent_whenPresent_doesNothing() {
        when(repository.existsById("demo")).thenReturn(true);

        service.seedIfAbsent("demo", "d@x.io", "password123", "ROLE_CUSTOMER");

        verify(repository, never()).save(any(UserAccount.class));
    }
}

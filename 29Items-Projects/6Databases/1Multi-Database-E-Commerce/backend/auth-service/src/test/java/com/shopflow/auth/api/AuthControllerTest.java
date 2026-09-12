package com.shopflow.auth.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopflow.auth.domain.UserAccount;
import com.shopflow.auth.service.TokenService;
import com.shopflow.auth.service.UserService;
import com.shopflow.common.error.ApiException;
import com.shopflow.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Web-layer tests for {@link AuthController} (standalone MockMvc). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock
    private UserService users;

    @Mock
    private TokenService tokens;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(users, tokens))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_valid_returnsTokens() throws Exception {
        when(users.authenticate("demo", "password123"))
                .thenReturn(new UserAccount("demo", "d@x.io", "hash", "ROLE_CUSTOMER"));
        when(tokens.issueAccessToken("demo", "ROLE_CUSTOMER")).thenReturn("access-tok");
        when(tokens.issueRefreshToken("demo")).thenReturn("refresh-tok");
        when(tokens.getAccessTtlSeconds()).thenReturn(900L);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"demo","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-tok"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(users.authenticate("demo", "wrongpass"))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid username or password"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"demo","password":"wrongpass"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"newuser","email":"n@x.io","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}

/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastermeister.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cherry.mastermeister.audit.AuditLogRepository;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import cherry.mastermeister.user.UserRole;
import cherry.mastermeister.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 認証フローの結合テスト(NFR-U3-06)。
 * ログイン(成功・失敗・ロックアウト)、リフレッシュ(ローテーション・再利用検知・二重リフレッシュ)、
 * ログアウト、認可(ADMIN/USER/匿名)、セキュリティヘッダー、監査記録を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "correct-password-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    private AppUser createUser(String email, UserRole role, UserStatus status) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(status);
        user.setLanguage("ja");
        user.setTheme("system");
        return userRepository.save(user);
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String tokenBody(String refreshToken) {
        return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void ログイン成功でトークンとユーザ情報を返し監査記録する() throws Exception {
        createUser("user@example.com", UserRole.USER, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("user@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.language").value("ja"))
                .andExpect(jsonPath("$.user.theme").value("system"));

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "LOGIN_SUCCEEDED".equals(entry.getEventType())
                        && "user@example.com".equals(entry.getActor()));
    }

    @Test
    void 存在しないユーザと誤パスワードは同一の401応答() throws Exception {
        createUser("user@example.com", UserRole.USER, UserStatus.ACTIVE);

        MvcResult unknown = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("nobody@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andReturn();
        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("user@example.com", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // 列挙対策: 応答ボディが完全一致(ユーザ有無を区別できない)
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString());
    }

    @Test
    void 未承認ユーザのログインは401でNOT_ACTIVEを監査記録する() throws Exception {
        createUser("pending@example.com", UserRole.USER, UserStatus.PENDING_APPROVAL);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("pending@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "LOGIN_FAILED".equals(entry.getEventType())
                        && entry.getDetailJson().contains("NOT_ACTIVE"));
    }

    @Test
    void 連続失敗でロックされ正しいパスワードでも423() throws Exception {
        AppUser user = createUser("lockme@example.com", UserRole.USER, UserStatus.ACTIVE);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType("application/json")
                            .content(loginBody("lockme@example.com", "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("lockme@example.com", PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("AUTH_LOCKED"));

        AppUser locked = userRepository.findById(user.getId()).orElseThrow();
        assertThat(locked.getLockedUntil()).isNotNull();
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "ACCOUNT_LOCKED".equals(entry.getEventType()));
    }

    @Test
    void リフレッシュはローテーションし旧トークンの再提示は再利用検知() throws Exception {
        createUser("user@example.com", UserRole.USER, UserStatus.ACTIVE);
        JsonNode loginResult = login("user@example.com", PASSWORD);
        String firstRefreshToken = loginResult.get("refreshToken").asString();

        // 1 回目: ローテーション成功
        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(tokenBody(firstRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        String secondRefreshToken = objectMapper
                .readTree(refreshed.getResponse().getContentAsString())
                .get("refreshToken").asString();
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // 旧トークンの再提示(二重リフレッシュ)= 再利用検知 → 401 + ファミリ失効
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(tokenBody(firstRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "TOKEN_REUSE_DETECTED".equals(entry.getEventType()));

        // ファミリ失効により新トークンも使えない
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(tokenBody(secondRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ログアウトでファミリ失効し以後リフレッシュ不可() throws Exception {
        createUser("user@example.com", UserRole.USER, UserStatus.ACTIVE);
        JsonNode loginResult = login("user@example.com", PASSWORD);
        String refreshToken = loginResult.get("refreshToken").asString();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType("application/json")
                        .content(tokenBody(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(tokenBody(refreshToken)))
                .andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "LOGOUT".equals(entry.getEventType()));
    }

    @Test
    void 不正トークンのログアウトも204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType("application/json")
                        .content(tokenBody("bogus-token")))
                .andExpect(status().isNoContent());
    }

    @Test
    void 認可_匿名は401_USERはadmin配下403() throws Exception {
        createUser("user@example.com", UserRole.USER, UserStatus.ACTIVE);
        JsonNode loginResult = login("user@example.com", PASSWORD);
        String accessToken = loginResult.get("accessToken").asString();

        mockMvc.perform(get("/api/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void セキュリティヘッダーが付与される() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("nobody@example.com", "x")))
                .andExpect(header().string("Content-Security-Policy",
                        cherry.mastermeister.common.config.SecurityConfig.CONTENT_SECURITY_POLICY))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void バリデーションエラーは400のinvalid_params付きProblemDetails() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.['invalid-params']").isArray());
    }
}

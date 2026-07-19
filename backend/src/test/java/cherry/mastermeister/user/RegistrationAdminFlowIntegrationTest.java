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
package cherry.mastermeister.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.AuditLogRepository;
import cherry.mastermeister.auth.RefreshTokenRepository;
import cherry.mastermeister.common.config.AppProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * 登録・ユーザ管理の結合テスト(US-001〜004/006、NFR-U3-06)。
 * 列挙対策(登録済み/未登録の応答同一)、トークン期限/使用済み、承認フロー、
 * 認可、ブートストラップ冪等を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationAdminFlowIntegrationTest {

    private static final String PASSWORD = "new-user-password-1";

    /** メール基盤の代わりに登録トークンを捕捉するテスト用ゲートウェイ(実装 Bean より優先)。 */
    @TestConfiguration
    static class CapturingGatewayConfig {

        static final AtomicReference<String> lastToken = new AtomicReference<>();
        static final AtomicReference<String> lastApprovedEmail = new AtomicReference<>();

        @Bean
        @org.springframework.context.annotation.Primary
        UserNotificationGateway capturingGateway() {
            return new UserNotificationGateway() {
                @Override
                public boolean sendRegistrationConfirm(String email, String language, String token) {
                    lastToken.set(token);
                    return true;
                }

                @Override
                public boolean sendUserApproved(AppUser user) {
                    lastApprovedEmail.set(user.getEmail());
                    return true;
                }

                @Override
                public boolean sendUserRejected(AppUser user) {
                    return true;
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RegistrationTokenRepository registrationTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditEventPublisher auditEventPublisher;

    @AfterEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        registrationTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        CapturingGatewayConfig.lastToken.set(null);
        CapturingGatewayConfig.lastApprovedEmail.set(null);
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

    private String loginAs(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asString();
    }

    private MvcResult requestRegistration(String email) throws Exception {
        return mockMvc.perform(post("/api/registration/request")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"language\":\"ja\"}".formatted(email)))
                .andExpect(status().isAccepted())
                .andReturn();
    }

    @Test
    void 登録済みと未登録で申請応答が同一_列挙対策() throws Exception {
        createUser("existing@example.com", UserRole.USER, UserStatus.ACTIVE);

        MvcResult registered = requestRegistration("existing@example.com");
        MvcResult unregistered = requestRegistration("new@example.com");

        assertThat(registered.getResponse().getStatus())
                .isEqualTo(unregistered.getResponse().getStatus());
        assertThat(registered.getResponse().getContentAsString())
                .isEqualTo(unregistered.getResponse().getContentAsString());
        // 登録済みにはトークンを発行しない(証跡は alreadyRegistered=true で残る)
        assertThat(registrationTokenRepository.findAll())
                .allMatch(token -> "new@example.com".equals(token.getEmail()));
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "REGISTRATION_REQUESTED".equals(entry.getEventType())
                        && entry.getDetailJson().contains("\"alreadyRegistered\":true"));
    }

    @Test
    void 登録申請から完了までのフロー() throws Exception {
        requestRegistration("newuser@example.com");
        String token = CapturingGatewayConfig.lastToken.get();
        assertThat(token).isNotNull();
        // DB には平文トークンを保存しない(ハッシュのみ — NFR-U3-01)
        assertThat(registrationTokenRepository.findAll())
                .noneMatch(row -> token.equals(row.getTokenHash()));

        mockMvc.perform(post("/api/registration/complete")
                        .contentType("application/json")
                        .content("{\"token\":\"%s\",\"password\":\"%s\",\"displayName\":\"新規 太郎\"}"
                                .formatted(token, PASSWORD)))
                .andExpect(status().isNoContent());

        AppUser created = userRepository.findByEmail("newuser@example.com").orElseThrow();
        assertThat(created.getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
        assertThat(created.getDisplayName()).isEqualTo("新規 太郎");
        assertThat(created.getPasswordHash()).isNotEqualTo(PASSWORD);

        // 使用済みトークンの再利用は 400(理由統一)
        mockMvc.perform(post("/api/registration/complete")
                        .contentType("application/json")
                        .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));
    }

    @Test
    void 不正トークンと短いパスワードは400() throws Exception {
        mockMvc.perform(post("/api/registration/complete")
                        .contentType("application/json")
                        .content("{\"token\":\"bogus\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));

        requestRegistration("short@example.com");
        String token = CapturingGatewayConfig.lastToken.get();
        mockMvc.perform(post("/api/registration/complete")
                        .contentType("application/json")
                        .content("{\"token\":\"%s\",\"password\":\"short1\"}".formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 承認フローと409() throws Exception {
        createUser("admin@example.com", UserRole.ADMIN, UserStatus.ACTIVE);
        AppUser pending = createUser("pending@example.com", UserRole.USER, UserStatus.PENDING_APPROVAL);
        String adminToken = loginAs("admin@example.com");

        mockMvc.perform(post("/api/admin/users/{id}/approve", pending.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.mailSent").value(true));

        assertThat(CapturingGatewayConfig.lastApprovedEmail.get()).isEqualTo("pending@example.com");
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "USER_APPROVED".equals(entry.getEventType())
                        && "admin@example.com".equals(entry.getActor()));

        // 既に ACTIVE → 409
        mockMvc.perform(post("/api/admin/users/{id}/approve", pending.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_NOT_PENDING"));

        // 存在しない ID → 404
        mockMvc.perform(post("/api/admin/users/{id}/approve", 99999)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 却下でREJECTEDになる() throws Exception {
        createUser("admin@example.com", UserRole.ADMIN, UserStatus.ACTIVE);
        AppUser pending = createUser("pending@example.com", UserRole.USER, UserStatus.PENDING_APPROVAL);
        String adminToken = loginAs("admin@example.com");

        mockMvc.perform(post("/api/admin/users/{id}/reject", pending.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("REJECTED"));

        // REJECTED ユーザはログイン不可(401 — 原因は特定させない)
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"pending@example.com\",\"password\":\"%s\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ロック解除でカウントとロックが初期化される() throws Exception {
        createUser("admin@example.com", UserRole.ADMIN, UserStatus.ACTIVE);
        AppUser locked = createUser("locked@example.com", UserRole.USER, UserStatus.ACTIVE);
        locked.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(15));
        locked.setFailedLoginCount(5);
        userRepository.save(locked);
        String adminToken = loginAs("admin@example.com");

        mockMvc.perform(post("/api/admin/users/{id}/unlock", locked.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist())
                .andExpect(jsonPath("$.failedLoginCount").value(0));

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "ACCOUNT_UNLOCKED".equals(entry.getEventType()));

        // 解除後はログインできる
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"locked@example.com\",\"password\":\"%s\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void 一覧はstatusフィルタとキーワードとページングが効く() throws Exception {
        createUser("admin@example.com", UserRole.ADMIN, UserStatus.ACTIVE);
        createUser("alpha@example.com", UserRole.USER, UserStatus.PENDING_APPROVAL);
        createUser("beta@example.com", UserRole.USER, UserStatus.ACTIVE);
        String adminToken = loginAs("admin@example.com");

        mockMvc.perform(get("/api/admin/users")
                        .param("status", "PENDING_APPROVAL")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].email").value("alpha@example.com"));

        mockMvc.perform(get("/api/admin/users")
                        .param("q", "BETA")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].email").value("beta@example.com"));

        mockMvc.perform(get("/api/admin/users")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void 自分の情報取得と設定変更() throws Exception {
        createUser("me@example.com", UserRole.USER, UserStatus.ACTIVE);
        String token = loginAs("me@example.com");

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.language").value("ja"))
                .andExpect(jsonPath("$.theme").value("system"));

        mockMvc.perform(put("/api/me/preferences")
                        .contentType("application/json")
                        .content("{\"language\":\"en\",\"theme\":\"dark\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        AppUser updated = userRepository.findByEmail("me@example.com").orElseThrow();
        assertThat(updated.getLanguage()).isEqualTo("en");
        assertThat(updated.getTheme()).isEqualTo("dark");

        // 不正値は 400
        mockMvc.perform(put("/api/me/preferences")
                        .contentType("application/json")
                        .content("{\"language\":\"fr\",\"theme\":\"dark\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ブートストラップは冪等() {
        AppProperties configured = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef",
                        Duration.ofMinutes(10), Duration.ofHours(24)),
                new AppProperties.UserRegistration(Duration.ofHours(3)),
                new AppProperties.Auth(new AppProperties.Auth.Lockout(5, Duration.ofMinutes(15))),
                new AppProperties.SecurityAlert(Duration.ofMinutes(10), 10),
                new AppProperties.Admin(new AppProperties.Admin.Bootstrap(
                        "Bootstrap@Example.com", "bootstrap-password-1")),
                new AppProperties.Mail("noreply@mastermeister.local", "http://localhost:8080"));
        AdminBootstrap bootstrap = new AdminBootstrap(
                configured, userRepository, passwordEncoder, auditEventPublisher);

        bootstrap.run(null);
        bootstrap.run(null);

        assertThat(userRepository.findAll())
                .filteredOn(user -> "bootstrap@example.com".equals(user.getEmail()))
                .hasSize(1)
                .allSatisfy(user -> {
                    assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
                    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
                });
        assertThat(auditLogRepository.findAll())
                .filteredOn(entry -> "ADMIN_BOOTSTRAPPED".equals(entry.getEventType()))
                .hasSize(1);
    }
}

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
package cherry.mastermeister.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cherry.mastermeister.audit.AuditLogRepository;
import cherry.mastermeister.auth.RefreshTokenRepository;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import cherry.mastermeister.user.UserRole;
import cherry.mastermeister.user.UserStatus;
import java.sql.DriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * 接続管理 API の結合テスト(US-010/011)。
 * CRUD・名前重複 409・dbType 不変 400・応答へのパスワード非含有・ADMIN 認可・
 * 接続テスト(未保存値/保存済み補完/認証失敗分類)・監査記録を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConnectionApiIntegrationTest {

    private static final String PASSWORD = "admin-password-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private DbConnectionRepository connectionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        connectionRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void createUser(String email, UserRole role) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setLanguage("ja");
        user.setTheme("system");
        userRepository.save(user);
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

    private String adminToken() throws Exception {
        createUser("admin@example.com", UserRole.ADMIN);
        return loginAs("admin@example.com");
    }

    private String connectionJson(String name) {
        return """
                {"name":"%s","dbType":"H2","host":"mem:conn-%s","port":null,
                 "databaseName":"unused","username":"sa","password":"target-pw",
                 "options":"DB_CLOSE_DELAY=-1","poolMaxSize":2,"poolTimeoutMs":2000}
                """.formatted(name, name);
    }

    @Test
    void CRUDと応答へのパスワード非含有() throws Exception {
        String token = adminToken();

        // 作成: 201。応答にパスワード関連の項目がない
        MvcResult created = mockMvc.perform(post("/api/admin/connections")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(connectionJson("conn-a")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("conn-a"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordEnc").doesNotExist())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        // DB 上は v1: 形式の暗号文のみ(平文なし)
        DbConnection stored = connectionRepository.findById(id).orElseThrow();
        assertThat(stored.getPasswordEnc()).startsWith("v1:").doesNotContain("target-pw");

        // 一覧・取得
        mockMvc.perform(get("/api/admin/connections")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("conn-a"));
        mockMvc.perform(get("/api/admin/connections/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dbType").value("H2"));

        // 更新(password 空欄 = 既存維持)
        mockMvc.perform(put("/api/admin/connections/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"conn-a2","dbType":"H2","host":"mem:conn-conn-a",
                                 "databaseName":"unused","username":"sa","password":null,
                                 "options":"DB_CLOSE_DELAY=-1","poolMaxSize":3,"poolTimeoutMs":2000}
                                """))
                .andExpect(status().isNoContent());
        DbConnection updated = connectionRepository.findById(id).orElseThrow();
        assertThat(updated.getName()).isEqualTo("conn-a2");
        assertThat(updated.getPasswordEnc()).isEqualTo(stored.getPasswordEnc());

        // 削除 → 404
        mockMvc.perform(delete("/api/admin/connections/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/connections/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // 監査記録(actor = 操作管理者)
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "CONNECTION_CREATED".equals(entry.getEventType())
                        && "admin@example.com".equals(entry.getActor()))
                .anyMatch(entry -> "CONNECTION_UPDATED".equals(entry.getEventType()))
                .anyMatch(entry -> "CONNECTION_DELETED".equals(entry.getEventType()));
        // 監査 detail にパスワードが漏れていない
        assertThat(auditLogRepository.findAll())
                .allMatch(entry -> entry.getDetailJson() == null
                        || !entry.getDetailJson().contains("target-pw"));
    }

    @Test
    void 名前重複は409() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/admin/connections")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(connectionJson("dup")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/connections")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(connectionJson("dup")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONNECTION_NAME_DUPLICATE"));
    }

    @Test
    void dbTypeの変更は400() throws Exception {
        String token = adminToken();
        MvcResult created = mockMvc.perform(post("/api/admin/connections")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(connectionJson("immutable")))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(put("/api/admin/connections/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"immutable","dbType":"POSTGRESQL","host":"h",
                                 "databaseName":"d","username":"u","password":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONNECTION_TYPE_IMMUTABLE"));
    }

    @Test
    void 一般ユーザは403() throws Exception {
        createUser("user@example.com", UserRole.USER);
        String token = loginAs("user@example.com");
        mockMvc.perform(get("/api/admin/connections")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void 接続テスト_未保存のフォーム値で成功する() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/admin/connections/test")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"dbType":"H2","host":"mem:test-unsaved","databaseName":"unused",
                                 "username":"sa","password":"pw"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "CONNECTION_TESTED".equals(entry.getEventType())
                        && "SUCCESS".equals(entry.getOutcome()));
    }

    @Test
    void 接続テスト_認証失敗はAUTH_FAILED() throws Exception {
        // 既存のインメモリ DB をパスワード付きで用意し(テストコードのみ DriverManager 可)、
        // 誤ったパスワードでのテストが AUTH_FAILED に分類されることを確認する
        try (var keepAlive = DriverManager.getConnection(
                "jdbc:h2:mem:test-authfail;DB_CLOSE_DELAY=-1", "sa", "correct-pw")) {
            String token = adminToken();
            mockMvc.perform(post("/api/admin/connections/test")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("""
                                    {"dbType":"H2","host":"mem:test-authfail","databaseName":"unused",
                                     "username":"sa","password":"wrong-pw","options":"IFEXISTS=TRUE"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.reason").value("AUTH_FAILED"));
        }
    }

    @Test
    void 接続テスト_保存済み接続の資格情報で補完される() throws Exception {
        String token = adminToken();
        MvcResult created = mockMvc.perform(post("/api/admin/connections")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(connectionJson("saved-test")))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        // password 省略 + id 指定 → 保存済みパスワードで疎通
        mockMvc.perform(post("/api/admin/connections/test")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"id":%d,"dbType":"H2","host":"mem:conn-saved-test",
                                 "databaseName":"unused","username":"sa","password":null,
                                 "options":"DB_CLOSE_DELAY=-1"}
                                """.formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 接続テスト_パスワード省略でid未指定は400() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/admin/connections/test")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"dbType":"H2","host":"mem:x","databaseName":"unused","username":"sa"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONNECTION_PASSWORD_REQUIRED"));
    }
}

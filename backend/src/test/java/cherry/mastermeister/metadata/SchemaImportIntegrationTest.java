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
package cherry.mastermeister.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cherry.mastermeister.audit.AuditLogRepository;
import cherry.mastermeister.auth.RefreshTokenRepository;
import cherry.mastermeister.connection.CredentialEncryptor;
import cherry.mastermeister.connection.DbConnection;
import cherry.mastermeister.connection.DbConnectionRepository;
import cherry.mastermeister.connection.TargetDataSourceRegistry;
import cherry.mastermeister.common.dialect.DbType;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import cherry.mastermeister.user.UserRole;
import cherry.mastermeister.user.UserStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * スキーマ取込の結合テスト(US-012、NFR-U4-05)。H2 インメモリをターゲットに、
 * テーブル/ビュー/PK/コメントの取込、再取込の全置換、失敗時ロールバック、
 * 未取込時の空応答を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchemaImportIntegrationTest {

    private static final String PASSWORD = "admin-password-1";
    private static final String TARGET_URL = "jdbc:h2:mem:import-target;DB_CLOSE_DELAY=-1";
    private static final String TARGET_PASSWORD = "target-pw";

    /** ターゲット DB を維持するための接続(テストコードのみ DriverManager 可)。 */
    private static Connection keepAlive;

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
    private MetaSchemaRepository metaSchemaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CredentialEncryptor credentialEncryptor;

    @Autowired
    private TargetDataSourceRegistry dataSourceRegistry;

    @BeforeAll
    static void setUpTarget() throws Exception {
        keepAlive = DriverManager.getConnection(TARGET_URL, "sa", TARGET_PASSWORD);
        try (Statement statement = keepAlive.createStatement()) {
            statement.execute("""
                    CREATE TABLE customers (
                        id BIGINT NOT NULL,
                        branch INT NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        note VARCHAR(500),
                        PRIMARY KEY (id, branch)
                    )
                    """);
            statement.execute("COMMENT ON TABLE customers IS 'customer master'");
            statement.execute("COMMENT ON COLUMN customers.name IS 'customer name'");
            statement.execute("CREATE VIEW v_customer_names AS SELECT name FROM customers");
        }
    }

    @AfterAll
    static void tearDownTarget() throws Exception {
        try (Statement statement = keepAlive.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        }
        keepAlive.close();
    }

    @AfterEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        metaSchemaRepository.deleteAll();
        connectionRepository.findAll()
                .forEach(connection -> dataSourceRegistry.evict(connection.getId()));
        connectionRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String adminToken() throws Exception {
        AppUser user = new AppUser();
        user.setEmail("admin@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setLanguage("ja");
        user.setTheme("system");
        userRepository.save(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"admin@example.com\",\"password\":\"%s\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asString();
    }

    private DbConnection createTargetConnection() {
        DbConnection connection = new DbConnection();
        connection.setName("import-target");
        connection.setDbType(DbType.H2);
        connection.setHost("mem:import-target");
        connection.setDatabaseName("unused");
        connection.setUsername("sa");
        connection.setPasswordEnc(credentialEncryptor.encrypt(TARGET_PASSWORD));
        connection.setOptions("DB_CLOSE_DELAY=-1");
        connection.setPoolMaxSize(2);
        connection.setPoolTimeoutMs(2000);
        return connectionRepository.save(connection);
    }

    @Test
    void 取込でテーブル_ビュー_PK_コメントが保存される() throws Exception {
        String token = adminToken();
        DbConnection connection = createTargetConnection();

        mockMvc.perform(post("/api/admin/connections/{id}/schema/import", connection.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables").value(2));

        MvcResult treeResult = mockMvc.perform(
                        get("/api/admin/connections/{id}/schema", connection.getId())
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = objectMapper.readTree(treeResult.getResponse().getContentAsString());
        assertThat(tree.get("importedAt").isNull()).isFalse();

        JsonNode publicSchema = findByName(tree.get("schemas"), "PUBLIC");
        JsonNode customers = findByName(publicSchema.get("tables"), "CUSTOMERS");
        assertThat(customers.get("tableType").asString()).isEqualTo("TABLE");
        assertThat(customers.get("remarks").asString()).isEqualTo("customer master");

        JsonNode idColumn = findByName(customers.get("columns"), "ID");
        assertThat(idColumn.get("primaryKeySeq").asInt()).isEqualTo(1);
        assertThat(idColumn.get("nullable").asBoolean()).isFalse();
        JsonNode branchColumn = findByName(customers.get("columns"), "BRANCH");
        assertThat(branchColumn.get("primaryKeySeq").asInt()).isEqualTo(2);
        JsonNode nameColumn = findByName(customers.get("columns"), "NAME");
        assertThat(nameColumn.get("remarks").asString()).isEqualTo("customer name");
        assertThat(nameColumn.get("primaryKeySeq").isNull()).isTrue();

        JsonNode view = findByName(publicSchema.get("tables"), "V_CUSTOMER_NAMES");
        assertThat(view.get("tableType").asString()).isEqualTo("VIEW");

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "SCHEMA_IMPORTED".equals(entry.getEventType())
                        && "SUCCESS".equals(entry.getOutcome()));

        // 取込状況(一覧用)
        mockMvc.perform(get("/api/admin/metadata/import-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].connectionId").value(connection.getId()));
    }

    @Test
    void 再取込は全置換される() throws Exception {
        String token = adminToken();
        DbConnection connection = createTargetConnection();

        mockMvc.perform(post("/api/admin/connections/{id}/schema/import", connection.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long firstSchemaId = metaSchemaRepository
                .findByConnectionIdOrderByName(connection.getId()).getFirst().getId();

        mockMvc.perform(post("/api/admin/connections/{id}/schema/import", connection.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        var schemasAfter = metaSchemaRepository.findByConnectionIdOrderByName(connection.getId());
        assertThat(schemasAfter).hasSize(1);
        assertThat(schemasAfter.getFirst().getId()).isNotEqualTo(firstSchemaId);
    }

    @Test
    void 取込失敗時はロールバックされ既存メタデータが残る() throws Exception {
        String token = adminToken();
        DbConnection connection = createTargetConnection();

        mockMvc.perform(post("/api/admin/connections/{id}/schema/import", connection.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 接続先を存在しない DB に変更(IFEXISTS=TRUE で新規作成を防ぐ)してプールを破棄
        connection.setHost("mem:no-such-db");
        connection.setOptions("IFEXISTS=TRUE");
        connectionRepository.save(connection);
        dataSourceRegistry.evict(connection.getId());

        mockMvc.perform(post("/api/admin/connections/{id}/schema/import", connection.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SCHEMA_IMPORT_FAILED"));

        // 前回の取込結果が残っている(全ロールバック)
        assertThat(metaSchemaRepository.findByConnectionIdOrderByName(connection.getId()))
                .hasSize(1);
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "SCHEMA_IMPORTED".equals(entry.getEventType())
                        && "FAILURE".equals(entry.getOutcome()));
    }

    @Test
    void 未取込の接続は空のツリーを返す() throws Exception {
        String token = adminToken();
        DbConnection connection = createTargetConnection();

        mockMvc.perform(get("/api/admin/connections/{id}/schema", connection.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedAt").isEmpty())
                .andExpect(jsonPath("$.schemas").isEmpty());
    }

    private static JsonNode findByName(JsonNode array, String name) {
        for (JsonNode node : array) {
            if (name.equals(node.get("name").asString())) {
                return node;
            }
        }
        throw new AssertionError("not found: " + name);
    }
}

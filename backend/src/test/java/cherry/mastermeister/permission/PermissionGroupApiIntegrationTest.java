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
package cherry.mastermeister.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cherry.mastermeister.audit.AuditLogRepository;
import cherry.mastermeister.auth.RefreshTokenRepository;
import cherry.mastermeister.common.dialect.DbType;
import cherry.mastermeister.connection.DbConnection;
import cherry.mastermeister.connection.DbConnectionRepository;
import cherry.mastermeister.metadata.MetaColumn;
import cherry.mastermeister.metadata.MetaColumnRepository;
import cherry.mastermeister.metadata.MetaSchema;
import cherry.mastermeister.metadata.MetaSchemaRepository;
import cherry.mastermeister.metadata.MetaTable;
import cherry.mastermeister.metadata.MetaTableRepository;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import cherry.mastermeister.user.UserRole;
import cherry.mastermeister.user.UserStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * 権限エントリ・グループ管理 API の結合テスト(US-013/014/018/019、D-18)。
 * エントリの設定/削除の 2 操作、孤児フラグ(Q2=A)、グループ CRUD・カスケード削除、
 * メンバー管理の冪等性、監査記録を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionGroupApiIntegrationTest {

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
    private MetaSchemaRepository metaSchemaRepository;

    @Autowired
    private MetaTableRepository metaTableRepository;

    @Autowired
    private MetaColumnRepository metaColumnRepository;

    @Autowired
    private PermissionEntryRepository entryRepository;

    @Autowired
    private PermissionAuxEntryRepository auxEntryRepository;

    @Autowired
    private UserGroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;
    private Long connectionId;
    private Long targetUserId;

    @BeforeEach
    void setUp() throws Exception {
        AppUser admin = createUser("admin@example.com", UserRole.ADMIN);
        targetUserId = createUser("target@example.com", UserRole.USER).getId();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"admin@example.com\",\"password\":\"%s\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asString();
        assertThat(admin.getId()).isNotNull();

        DbConnection connection = new DbConnection();
        connection.setName("perm-api-test");
        connection.setDbType(DbType.H2);
        connection.setHost("mem:perm-api");
        connection.setDatabaseName("unused");
        connection.setUsername("sa");
        connection.setPasswordEnc("v1:k1:aXY=:Y3Q=");
        connection.setPoolMaxSize(2);
        connection.setPoolTimeoutMs(2000);
        connectionId = connectionRepository.save(connection).getId();

        MetaSchema schema = new MetaSchema();
        schema.setConnectionId(connectionId);
        schema.setName("PUBLIC");
        schema.setImportedAt(LocalDateTime.now());
        metaSchemaRepository.save(schema);
        MetaTable table = new MetaTable();
        table.setSchemaId(schema.getId());
        table.setName("T1");
        table.setTableType(MetaTable.TableType.TABLE);
        metaTableRepository.save(table);
        MetaColumn column = new MetaColumn();
        column.setTableId(table.getId());
        column.setName("C1");
        column.setOrdinal(1);
        column.setDataType("VARCHAR");
        column.setNullable(true);
        metaColumnRepository.save(column);
    }

    @AfterEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        entryRepository.deleteAll();
        auxEntryRepository.deleteAll();
        memberRepository.deleteAll();
        groupRepository.deleteAll();
        metaSchemaRepository.deleteAll();
        connectionRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    private AppUser createUser(String email, UserRole role) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setLanguage("ja");
        user.setTheme("system");
        return userRepository.save(user);
    }

    private String entriesUrl() {
        return "/api/admin/connections/" + connectionId + "/permissions";
    }

    @Test
    void エントリの設定_取得_削除と孤児フラグ() throws Exception {
        // 明示的 NONE をカラムに設定(D-18: 未設定とは別)
        mockMvc.perform(put(entriesUrl() + "/entry")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"principalType":"USER","principalId":%d,
                                 "schema":"PUBLIC","table":"T1","column":"C1","permission":"NONE"}
                                """.formatted(targetUserId)))
                .andExpect(status().isNoContent());
        // 孤児になるエントリ(メタデータに存在しないテーブル)
        mockMvc.perform(put(entriesUrl() + "/entry")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"principalType":"USER","principalId":%d,
                                 "schema":"PUBLIC","table":"GONE","permission":"READ"}
                                """.formatted(targetUserId)))
                .andExpect(status().isNoContent());
        // 補助権限
        mockMvc.perform(put(entriesUrl() + "/entry")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"principalType":"USER","principalId":%d,
                                 "schema":"PUBLIC","table":"T1","auxType":"CREATE","granted":true}
                                """.formatted(targetUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(entriesUrl())
                        .header("Authorization", "Bearer " + token)
                        .param("principalType", "USER")
                        .param("principalId", String.valueOf(targetUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.main.length()").value(2))
                .andExpect(jsonPath("$.main[?(@.table=='T1')].orphan").value(false))
                .andExpect(jsonPath("$.main[?(@.table=='GONE')].orphan").value(true))
                .andExpect(jsonPath("$.aux[0].auxType").value("CREATE"))
                .andExpect(jsonPath("$.aux[0].orphan").value(false));

        // エントリ削除 = 未設定に戻す(冪等)
        mockMvc.perform(delete(entriesUrl() + "/entry")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"principalType":"USER","principalId":%d,
                                 "schema":"PUBLIC","table":"T1","column":"C1"}
                                """.formatted(targetUserId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(entriesUrl())
                        .header("Authorization", "Bearer " + token)
                        .param("principalType", "USER")
                        .param("principalId", String.valueOf(targetUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.main.length()").value(1));

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "PERMISSION_SET".equals(entry.getEventType())
                        && "admin@example.com".equals(entry.getActor()))
                .anyMatch(entry -> "PERMISSION_REMOVED".equals(entry.getEventType()));
    }

    @Test
    void 存在しないプリンシパルは404() throws Exception {
        mockMvc.perform(put(entriesUrl() + "/entry")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"principalType":"USER","principalId":99999,
                                 "schema":"PUBLIC","permission":"READ"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRINCIPAL_NOT_FOUND"));
    }

    @Test
    void カラム指定でテーブル省略は400() throws Exception {
        mockMvc.perform(put(entriesUrl() + "/entry")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"principalType":"USER","principalId":%d,
                                 "schema":"PUBLIC","column":"C1","permission":"READ"}
                                """.formatted(targetUserId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERMISSION_INVALID_SCOPE"));
    }

    @Test
    void グループCRUDとカスケード削除() throws Exception {
        // 作成 + 重複 409
        MvcResult created = mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"sales\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long groupId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
        mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"sales\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_NAME_DUPLICATE"));

        // メンバー追加(冪等)
        mockMvc.perform(post("/api/admin/groups/{id}/members", groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"userId\":%d}".formatted(targetUserId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/groups/{id}/members", groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"userId\":%d}".formatted(targetUserId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/groups/{id}/members", groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("target@example.com"));

        // 改名
        mockMvc.perform(put("/api/admin/groups/{id}", groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"sales-dept\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/groups")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("sales-dept"))
                .andExpect(jsonPath("$[0].memberCount").value(1));

        // グループに権限エントリを設定してから削除 → 所属・エントリも消える(US-018)
        mockMvc.perform(put(entriesUrl() + "/entry")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"principalType":"GROUP","principalId":%d,
                                 "schema":"PUBLIC","permission":"READ"}
                                """.formatted(groupId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/groups/{id}", groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(groupRepository.findAll()).isEmpty();
        assertThat(memberRepository.findAll()).isEmpty();
        assertThat(entryRepository.findAll())
                .noneMatch(entry -> entry.getPrincipalType() == PrincipalType.GROUP);
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "GROUP_CREATED".equals(entry.getEventType()))
                .anyMatch(entry -> "GROUP_RENAMED".equals(entry.getEventType()))
                .anyMatch(entry -> "GROUP_MEMBER_ADDED".equals(entry.getEventType()))
                .anyMatch(entry -> "GROUP_DELETED".equals(entry.getEventType()));
    }
}

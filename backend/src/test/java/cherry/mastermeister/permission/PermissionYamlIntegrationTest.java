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

import cherry.mastermeister.audit.AuditLogRepository;
import cherry.mastermeister.common.dialect.DbType;
import cherry.mastermeister.connection.DbConnection;
import cherry.mastermeister.connection.DbConnectionRepository;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import cherry.mastermeister.user.UserRole;
import cherry.mastermeister.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * YAML エクスポート/インポートの結合テスト(US-016/017、PBT-02)。
 * ラウンドトリップ(export → import → export の同一性 + DB 状態一致)と
 * 全体拒否 5 条件(構文・スキーマ・重複・未知プリンシパル・不正値)を検証する。
 */
@SpringBootTest
@ActiveProfiles("test")
class PermissionYamlIntegrationTest {

    @Autowired
    private DbConnectionRepository connectionRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserGroupRepository groupRepository;

    @Autowired
    private PermissionEntryRepository entryRepository;

    @Autowired
    private PermissionAuxEntryRepository auxEntryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PermissionYamlService yamlService;

    @Autowired
    private PermissionService permissionService;

    private Long connectionId;
    private Long userId;
    private Long groupId;

    @BeforeEach
    void setUp() {
        DbConnection connection = new DbConnection();
        connection.setName("yaml-test");
        connection.setDbType(DbType.H2);
        connection.setHost("mem:yaml");
        connection.setDatabaseName("unused");
        connection.setUsername("sa");
        connection.setPasswordEnc("v1:k1:aXY=:Y3Q=");
        connection.setPoolMaxSize(2);
        connection.setPoolTimeoutMs(2000);
        connectionId = connectionRepository.save(connection).getId();

        AppUser user = new AppUser();
        user.setEmail("yaml-user@example.com");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setLanguage("ja");
        user.setTheme("system");
        userId = userRepository.save(user).getId();

        UserGroup group = new UserGroup();
        group.setName("yaml-group");
        groupId = groupRepository.save(group).getId();
    }

    @AfterEach
    void cleanup() {
        entryRepository.deleteAll();
        auxEntryRepository.deleteAll();
        groupRepository.deleteAll();
        connectionRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void seedEntries() {
        permissionService.setMainEntry(connectionId, PrincipalType.USER, userId,
                new PermissionService.Scope("sales", "customers", "name"),
                PermissionLevel.READ, "admin@example.com");
        permissionService.setMainEntry(connectionId, PrincipalType.USER, userId,
                new PermissionService.Scope("sales", null, null),
                PermissionLevel.NONE, "admin@example.com");
        permissionService.setMainEntry(connectionId, PrincipalType.GROUP, groupId,
                new PermissionService.Scope("sales", "customers", null),
                PermissionLevel.UPDATE, "admin@example.com");
        permissionService.setAuxEntry(connectionId, PrincipalType.GROUP, groupId,
                new PermissionService.Scope("sales", "customers", null),
                AuxType.CREATE, true, "admin@example.com");
        permissionService.setAuxEntry(connectionId, PrincipalType.USER, userId,
                new PermissionService.Scope("sales", null, null),
                AuxType.DELETE, false, "admin@example.com");
    }

    @Test
    void ラウンドトリップ_exportをimportして再exportすると同一_PBT02() {
        seedEntries();
        String exported = yamlService.export(connectionId, "admin@example.com");
        assertThat(exported).contains("yaml-user@example.com").contains("yaml-group");

        int imported = yamlService.importReplace(connectionId, exported, "admin@example.com");
        assertThat(imported).isEqualTo(5);

        String reExported = yamlService.export(connectionId, "admin@example.com");
        assertThat(reExported).isEqualTo(exported);

        // DB 状態も一致(件数 + 内容)
        assertThat(entryRepository.findByConnectionId(connectionId)).hasSize(3);
        assertThat(auxEntryRepository.findByConnectionId(connectionId)).hasSize(2);
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "PERMISSION_EXPORTED".equals(entry.getEventType()))
                .anyMatch(entry -> "PERMISSION_IMPORTED".equals(entry.getEventType())
                        && "SUCCESS".equals(entry.getOutcome()));
    }

    @Test
    void インポートは全置換される() {
        seedEntries();
        String yaml = """
                version: 1
                users:
                - email: yaml-user@example.com
                  main:
                  - schema: hr
                    permission: READ
                  aux: []
                groups: []
                """;
        int imported = yamlService.importReplace(connectionId, yaml, "admin@example.com");
        assertThat(imported).isEqualTo(1);
        assertThat(entryRepository.findByConnectionId(connectionId))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getSchemaName()).isEqualTo("hr");
                    assertThat(entry.getPrincipalId()).isEqualTo(userId);
                });
        assertThat(auxEntryRepository.findByConnectionId(connectionId)).isEmpty();
    }

    private PermissionYamlService.YamlValidationException assertRejected(String yaml) {
        seedEntries();
        long before = entryRepository.count();
        PermissionYamlService.YamlValidationException exception =
                Assertions.assertThrows(PermissionYamlService.YamlValidationException.class,
                        (Executable) () -> yamlService.importReplace(
                                connectionId, yaml, "admin@example.com"));
        // 全体拒否 — 既存エントリは無傷
        assertThat(entryRepository.count()).isEqualTo(before);
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "PERMISSION_IMPORTED".equals(entry.getEventType())
                        && "FAILURE".equals(entry.getOutcome()));
        return exception;
    }

    @Test
    void 構文エラーは全体拒否() {
        PermissionYamlService.YamlValidationException e =
                assertRejected("version: 1\nusers: [broken");
        assertThat(e.code()).isEqualTo("YAML_INVALID");
    }

    @Test
    void 未知プロパティは全体拒否_型限定バインド() {
        PermissionYamlService.YamlValidationException e = assertRejected("""
                version: 1
                users: []
                groups: []
                malicious: true
                """);
        assertThat(e.code()).isEqualTo("YAML_INVALID");
    }

    @Test
    void 重複エントリは全体拒否() {
        PermissionYamlService.YamlValidationException e = assertRejected("""
                version: 1
                users:
                - email: yaml-user@example.com
                  main:
                  - schema: sales
                    table: customers
                    permission: READ
                  - schema: sales
                    table: customers
                    permission: UPDATE
                  aux: []
                groups: []
                """);
        assertThat(e.code()).isEqualTo("YAML_DUPLICATE_ENTRY");
        assertThat(e.errors()).singleElement()
                .satisfies(error -> assertThat(error.path()).isEqualTo("users[0].main[1]"));
    }

    @Test
    void 未知プリンシパルは全体拒否() {
        PermissionYamlService.YamlValidationException e = assertRejected("""
                version: 1
                users:
                - email: nobody@example.com
                  main:
                  - schema: sales
                    permission: READ
                  aux: []
                groups:
                - name: no-such-group
                  main: []
                  aux: []
                """);
        assertThat(e.code()).isEqualTo("YAML_UNKNOWN_PRINCIPAL");
        assertThat(e.errors()).hasSize(2);
    }

    @Test
    void 不正な権限値は全体拒否() {
        PermissionYamlService.YamlValidationException e = assertRejected("""
                version: 1
                users:
                - email: yaml-user@example.com
                  main:
                  - schema: sales
                    permission: SUPERUSER
                  aux: []
                groups: []
                """);
        assertThat(e.code()).isEqualTo("YAML_INVALID");
    }
}

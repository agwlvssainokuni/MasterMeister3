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
package cherry.mastermeister;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastermeister.connection.DbConnection;
import cherry.mastermeister.connection.DbConnectionRepository;
import cherry.mastermeister.common.dialect.DbType;
import cherry.mastermeister.metadata.MetaColumn;
import cherry.mastermeister.metadata.MetaColumnRepository;
import cherry.mastermeister.metadata.MetaSchema;
import cherry.mastermeister.metadata.MetaSchemaRepository;
import cherry.mastermeister.metadata.MetaTable;
import cherry.mastermeister.metadata.MetaTableRepository;
import cherry.mastermeister.permission.AuxType;
import cherry.mastermeister.permission.GroupMember;
import cherry.mastermeister.permission.GroupMemberRepository;
import cherry.mastermeister.permission.PermissionAuxEntry;
import cherry.mastermeister.permission.PermissionAuxEntryRepository;
import cherry.mastermeister.permission.PermissionEntry;
import cherry.mastermeister.permission.PermissionEntryRepository;
import cherry.mastermeister.permission.PermissionLevel;
import cherry.mastermeister.permission.PrincipalType;
import cherry.mastermeister.permission.UserGroup;
import cherry.mastermeister.permission.UserGroupRepository;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import cherry.mastermeister.user.UserRole;
import cherry.mastermeister.user.UserStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway V3 適用とエンティティマッピングの整合確認(ユニット④)。
 * 7 テーブルの作成、各エンティティの保存・取得、権限エントリの一意制約
 * (空文字階層表現の重複拒否 — US-017 の DB 面の担保)を確認する。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConnectionPermissionPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DbConnectionRepository connectionRepository;

    @Autowired
    private MetaSchemaRepository metaSchemaRepository;

    @Autowired
    private MetaTableRepository metaTableRepository;

    @Autowired
    private MetaColumnRepository metaColumnRepository;

    @Autowired
    private PermissionEntryRepository permissionEntryRepository;

    @Autowired
    private PermissionAuxEntryRepository permissionAuxEntryRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void FlywayV3で7テーブルが作成される() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME IN ('DB_CONNECTION', 'META_SCHEMA', 'META_TABLE', 'META_COLUMN',
                                     'PERMISSION_ENTRY', 'PERMISSION_AUX_ENTRY',
                                     'USER_GROUP', 'GROUP_MEMBER')
                """,
                Integer.class);
        assertThat(count).isEqualTo(8);
    }

    @Test
    void 各エンティティを保存して取得できる() {
        DbConnection connection = new DbConnection();
        connection.setName("smoke-conn");
        connection.setDbType(DbType.H2);
        connection.setHost("mem");
        connection.setDatabaseName("smoke");
        connection.setUsername("sa");
        connection.setPasswordEnc("v1:k1:aXY=:Y3Q=");
        connection.setPoolMaxSize(5);
        connection.setPoolTimeoutMs(5000);
        connectionRepository.saveAndFlush(connection);
        assertThat(connectionRepository.findByName("smoke-conn"))
                .hasValueSatisfying(found -> {
                    assertThat(found.getDbType()).isEqualTo(DbType.H2);
                    assertThat(found.getCreatedAt()).isNotNull();
                });

        MetaSchema schema = new MetaSchema();
        schema.setConnectionId(connection.getId());
        schema.setName("PUBLIC");
        schema.setImportedAt(LocalDateTime.now());
        metaSchemaRepository.saveAndFlush(schema);

        MetaTable table = new MetaTable();
        table.setSchemaId(schema.getId());
        table.setName("CUSTOMERS");
        table.setTableType(MetaTable.TableType.TABLE);
        metaTableRepository.saveAndFlush(table);

        MetaColumn column = new MetaColumn();
        column.setTableId(table.getId());
        column.setName("ID");
        column.setOrdinal(1);
        column.setDataType("BIGINT");
        column.setNullable(false);
        column.setPrimaryKeySeq(1);
        metaColumnRepository.saveAndFlush(column);

        assertThat(metaSchemaRepository.findByConnectionIdOrderByName(connection.getId())).hasSize(1);
        assertThat(metaTableRepository.findByConnectionId(connection.getId())).hasSize(1);
        assertThat(metaColumnRepository.findByConnectionId(connection.getId()))
                .singleElement()
                .satisfies(c -> assertThat(c.getPrimaryKeySeq()).isEqualTo(1));

        AppUser user = new AppUser();
        user.setEmail("perm-smoke@example.com");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setLanguage("ja");
        user.setTheme("system");
        appUserRepository.saveAndFlush(user);

        UserGroup group = new UserGroup();
        group.setName("smoke-group");
        userGroupRepository.saveAndFlush(group);

        GroupMember member = new GroupMember();
        member.setGroupId(group.getId());
        member.setUserId(user.getId());
        groupMemberRepository.saveAndFlush(member);
        assertThat(groupMemberRepository.findByUserId(user.getId())).hasSize(1);
        assertThat(groupMemberRepository.countByGroupId(group.getId())).isEqualTo(1);

        PermissionEntry entry = new PermissionEntry();
        entry.setConnectionId(connection.getId());
        entry.setPrincipalType(PrincipalType.USER);
        entry.setPrincipalId(user.getId());
        entry.setSchemaName("PUBLIC");
        entry.setTableName("");
        entry.setColumnName("");
        entry.setPermission(PermissionLevel.READ);
        permissionEntryRepository.saveAndFlush(entry);
        assertThat(permissionEntryRepository.findByConnectionIdAndPrincipalTypeAndPrincipalId(
                connection.getId(), PrincipalType.USER, user.getId())).hasSize(1);

        PermissionAuxEntry auxEntry = new PermissionAuxEntry();
        auxEntry.setConnectionId(connection.getId());
        auxEntry.setPrincipalType(PrincipalType.GROUP);
        auxEntry.setPrincipalId(group.getId());
        auxEntry.setSchemaName("PUBLIC");
        auxEntry.setTableName("CUSTOMERS");
        auxEntry.setAuxType(AuxType.CREATE);
        auxEntry.setGranted(true);
        permissionAuxEntryRepository.saveAndFlush(auxEntry);
        assertThat(permissionAuxEntryRepository.findByConnectionIdAndPrincipalTypeAndPrincipalId(
                connection.getId(), PrincipalType.GROUP, group.getId())).hasSize(1);
    }

    @Test
    void 同一スコープの権限エントリ重複は一意制約で拒否される() {
        DbConnection connection = new DbConnection();
        connection.setName("dup-conn");
        connection.setDbType(DbType.H2);
        connection.setHost("mem");
        connection.setDatabaseName("dup");
        connection.setUsername("sa");
        connection.setPasswordEnc("v1:k1:aXY=:Y3Q=");
        connection.setPoolMaxSize(5);
        connection.setPoolTimeoutMs(5000);
        connectionRepository.saveAndFlush(connection);

        PermissionEntry first = new PermissionEntry();
        first.setConnectionId(connection.getId());
        first.setPrincipalType(PrincipalType.USER);
        first.setPrincipalId(1L);
        first.setSchemaName("PUBLIC");
        first.setTableName("");
        first.setColumnName("");
        first.setPermission(PermissionLevel.READ);
        permissionEntryRepository.saveAndFlush(first);

        PermissionEntry duplicate = new PermissionEntry();
        duplicate.setConnectionId(connection.getId());
        duplicate.setPrincipalType(PrincipalType.USER);
        duplicate.setPrincipalId(1L);
        duplicate.setSchemaName("PUBLIC");
        duplicate.setTableName("");
        duplicate.setColumnName("");
        duplicate.setPermission(PermissionLevel.UPDATE);
        assertThatThrownBy(() -> permissionEntryRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

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
import cherry.mastermeister.common.event.PermissionCacheInvalidated;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

/**
 * 実効権限解決の結合テスト(US-015、D-21)。
 * ユーザ確認済みの確定例 2 件、補助権限の操作可否合成(US-014 + D-15)、
 * キャッシュ透過性(PBT P3 の決定的検証)、無効化トリガを検証する。
 *
 * <p>メタデータ: PUBLIC.T1(id[PK], c1, c2)、PUBLIC.T2(id[PK], d1)、
 * PUBLIC.NOPK(x, y — 主キーなし)。
 */
@SpringBootTest
@ActiveProfiles("test")
class EffectivePermissionResolverIntegrationTest {

    @Autowired
    private DbConnectionRepository connectionRepository;

    @Autowired
    private MetaSchemaRepository metaSchemaRepository;

    @Autowired
    private MetaTableRepository metaTableRepository;

    @Autowired
    private MetaColumnRepository metaColumnRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserGroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository memberRepository;

    @Autowired
    private PermissionEntryRepository entryRepository;

    @Autowired
    private PermissionAuxEntryRepository auxEntryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EffectivePermissionResolver resolver;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private Long connectionId;
    private Long memberUserId; // 個別設定を持つグループメンバー
    private Long otherUserId; // 個別設定を持たないグループメンバー
    private Long groupId;

    @BeforeEach
    void setUp() {
        DbConnection connection = new DbConnection();
        connection.setName("resolver-test");
        connection.setDbType(DbType.H2);
        connection.setHost("mem:resolver");
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
        createTable(schema.getId(), "T1", new String[] {"ID", "C1", "C2"}, new Integer[] {1, null, null});
        createTable(schema.getId(), "T2", new String[] {"ID", "D1"}, new Integer[] {1, null});
        createTable(schema.getId(), "NOPK", new String[] {"X", "Y"}, new Integer[] {null, null});

        memberUserId = createUser("member@example.com");
        otherUserId = createUser("other@example.com");
        groupId = groupService.create("resolver-group", "admin@example.com").getId();
        groupService.addMember(groupId, memberUserId, "admin@example.com");
        groupService.addMember(groupId, otherUserId, "admin@example.com");
        resolver.invalidateAll();
    }

    @AfterEach
    void cleanup() {
        entryRepository.deleteAll();
        auxEntryRepository.deleteAll();
        memberRepository.deleteAll();
        groupRepository.deleteAll();
        metaSchemaRepository.deleteAll();
        connectionRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        resolver.invalidateAll();
    }

    private void createTable(Long schemaId, String name, String[] columns, Integer[] pkSeqs) {
        MetaTable table = new MetaTable();
        table.setSchemaId(schemaId);
        table.setName(name);
        table.setTableType(MetaTable.TableType.TABLE);
        metaTableRepository.save(table);
        for (int i = 0; i < columns.length; i++) {
            MetaColumn column = new MetaColumn();
            column.setTableId(table.getId());
            column.setName(columns[i]);
            column.setOrdinal(i + 1);
            column.setDataType("VARCHAR");
            column.setNullable(true);
            column.setPrimaryKeySeq(pkSeqs[i]);
            metaColumnRepository.save(column);
        }
    }

    private Long createUser(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setLanguage("ja");
        user.setTheme("system");
        return userRepository.save(user).getId();
    }

    private void setUserMain(Long userId, String schema, String table, String column,
            PermissionLevel level) {
        permissionService.setMainEntry(connectionId, PrincipalType.USER, userId,
                new PermissionService.Scope(schema, table, column), level, "admin@example.com");
    }

    private void setGroupMain(String schema, String table, String column, PermissionLevel level) {
        permissionService.setMainEntry(connectionId, PrincipalType.GROUP, groupId,
                new PermissionService.Scope(schema, table, column), level, "admin@example.com");
    }

    @Test
    void US015確定例1_個別スキーマREADはグループのカラムUPDATEに優先する() {
        // ユーザ個別 = スキーマに READ、グループ = T1.C1 に UPDATE
        setUserMain(memberUserId, "PUBLIC", null, null, PermissionLevel.READ);
        setGroupMain("PUBLIC", "T1", "C1", PermissionLevel.UPDATE);

        // 当該ユーザはスキーマ内の全カラムが READ(グループのカラム UPDATE は適用されない)
        EffectivePermissions member = resolver.resolve(memberUserId, connectionId);
        assertThat(member.columnLevel("PUBLIC", "T1", "C1")).isEqualTo(PermissionLevel.READ);
        assertThat(member.columnLevel("PUBLIC", "T1", "C2")).isEqualTo(PermissionLevel.READ);
        assertThat(member.columnLevel("PUBLIC", "T2", "D1")).isEqualTo(PermissionLevel.READ);

        // 個別設定を持たない他のメンバーは当該カラムのみ UPDATE、それ以外はアクセス権なし
        EffectivePermissions other = resolver.resolve(otherUserId, connectionId);
        assertThat(other.columnLevel("PUBLIC", "T1", "C1")).isEqualTo(PermissionLevel.UPDATE);
        assertThat(other.columnLevel("PUBLIC", "T1", "C2")).isEqualTo(PermissionLevel.NONE);
        assertThat(other.columnLevel("PUBLIC", "T2", "D1")).isEqualTo(PermissionLevel.NONE);
    }

    @Test
    void US015確定例2_個別カラムREADのカラムのみREADで他はグループのUPDATE() {
        // ユーザ個別 = T1.C1 に READ、グループ = スキーマに UPDATE
        setUserMain(memberUserId, "PUBLIC", "T1", "C1", PermissionLevel.READ);
        setGroupMain("PUBLIC", null, null, PermissionLevel.UPDATE);

        // 当該ユーザはそのカラムのみ READ、スキーマ内の他の全カラム(他テーブル含む)は UPDATE
        EffectivePermissions member = resolver.resolve(memberUserId, connectionId);
        assertThat(member.columnLevel("PUBLIC", "T1", "C1")).isEqualTo(PermissionLevel.READ);
        assertThat(member.columnLevel("PUBLIC", "T1", "C2")).isEqualTo(PermissionLevel.UPDATE);
        assertThat(member.columnLevel("PUBLIC", "T2", "D1")).isEqualTo(PermissionLevel.UPDATE);

        // 個別設定を持たない他のメンバーは全カラム UPDATE
        EffectivePermissions other = resolver.resolve(otherUserId, connectionId);
        assertThat(other.columnLevel("PUBLIC", "T1", "C1")).isEqualTo(PermissionLevel.UPDATE);
        assertThat(other.columnLevel("PUBLIC", "T2", "D1")).isEqualTo(PermissionLevel.UPDATE);
    }

    @Test
    void 操作可否_US014とD15の合成規則() {
        // T1: 全カラム UPDATE + CREATE/DELETE 付与 → 作成可・削除可
        setUserMain(memberUserId, "PUBLIC", "T1", null, PermissionLevel.UPDATE);
        permissionService.setAuxEntry(connectionId, PrincipalType.USER, memberUserId,
                new PermissionService.Scope("PUBLIC", "T1", null), AuxType.CREATE, true,
                "admin@example.com");
        permissionService.setAuxEntry(connectionId, PrincipalType.USER, memberUserId,
                new PermissionService.Scope("PUBLIC", "T1", null), AuxType.DELETE, true,
                "admin@example.com");
        // NOPK: 主キーなし + CREATE/DELETE 付与 → 作成可・削除不可(D-15)
        permissionService.setAuxEntry(connectionId, PrincipalType.USER, memberUserId,
                new PermissionService.Scope("PUBLIC", "NOPK", null), AuxType.CREATE, true,
                "admin@example.com");
        permissionService.setAuxEntry(connectionId, PrincipalType.USER, memberUserId,
                new PermissionService.Scope("PUBLIC", "NOPK", null), AuxType.DELETE, true,
                "admin@example.com");
        // T2: 主キー列が READ どまり + CREATE 付与 → 作成不可(全主キー列 UPDATE が必要)
        setUserMain(memberUserId, "PUBLIC", "T2", null, PermissionLevel.READ);
        permissionService.setAuxEntry(connectionId, PrincipalType.USER, memberUserId,
                new PermissionService.Scope("PUBLIC", "T2", null), AuxType.CREATE, true,
                "admin@example.com");

        EffectivePermissions permissions = resolver.resolve(memberUserId, connectionId);
        EffectivePermissions.TablePermissions t1 =
                permissions.tables().get(new EffectivePermissions.TableKey("PUBLIC", "T1"));
        assertThat(t1.canCreate()).isTrue();
        assertThat(t1.canDelete()).isTrue();
        assertThat(t1.updatable()).isTrue();

        EffectivePermissions.TablePermissions nopk =
                permissions.tables().get(new EffectivePermissions.TableKey("PUBLIC", "NOPK"));
        assertThat(nopk.canCreate()).isTrue(); // 主キーなしテーブルは CREATE 付与のみで作成可
        assertThat(nopk.canDelete()).isFalse(); // D-15: 常に削除不可
        assertThat(nopk.updatable()).isFalse(); // D-15: 更新も不可

        EffectivePermissions.TablePermissions t2 =
                permissions.tables().get(new EffectivePermissions.TableKey("PUBLIC", "T2"));
        assertThat(t2.canCreate()).isFalse();
        assertThat(t2.canDelete()).isFalse(); // DELETE 未付与
    }

    @Test
    void 個別の明示false補助権限はグループのtrueに優先する() {
        permissionService.setAuxEntry(connectionId, PrincipalType.GROUP, groupId,
                new PermissionService.Scope("PUBLIC", null, null), AuxType.CREATE, true,
                "admin@example.com");
        permissionService.setAuxEntry(connectionId, PrincipalType.USER, memberUserId,
                new PermissionService.Scope("PUBLIC", "T1", null), AuxType.CREATE, false,
                "admin@example.com");
        setUserMain(memberUserId, "PUBLIC", null, null, PermissionLevel.UPDATE);

        EffectivePermissions permissions = resolver.resolve(memberUserId, connectionId);
        assertThat(permissions.tables()
                .get(new EffectivePermissions.TableKey("PUBLIC", "T1")).canCreate()).isFalse();
        // 個別 false は T1 のみ。T2 はグループのスキーマ true が効く
        assertThat(permissions.tables()
                .get(new EffectivePermissions.TableKey("PUBLIC", "T2")).canCreate()).isTrue();
    }

    @Test
    void キャッシュ透過性_キャッシュ経由と直接解決が一致する() {
        setUserMain(memberUserId, "PUBLIC", null, null, PermissionLevel.READ);
        setGroupMain("PUBLIC", "T1", "C1", PermissionLevel.UPDATE);

        EffectivePermissions cached = resolver.resolve(memberUserId, connectionId);
        EffectivePermissions direct = resolver.resolveUncached(memberUserId, connectionId);
        assertThat(cached).isEqualTo(direct);
        // 2 回目(キャッシュヒット)も同一
        assertThat(resolver.resolve(memberUserId, connectionId)).isEqualTo(direct);
    }

    @Test
    void 無効化トリガ_権限変更とグループ変更とイベントで最新化される() {
        setGroupMain("PUBLIC", null, null, PermissionLevel.READ);
        assertThat(resolver.resolve(otherUserId, connectionId)
                .columnLevel("PUBLIC", "T1", "C1")).isEqualTo(PermissionLevel.READ);

        // 権限変更(PermissionService 経由)で無効化される
        setGroupMain("PUBLIC", null, null, PermissionLevel.UPDATE);
        assertThat(resolver.resolve(otherUserId, connectionId)
                .columnLevel("PUBLIC", "T1", "C1")).isEqualTo(PermissionLevel.UPDATE);

        // メンバー削除で無効化される(グループ設定が効かなくなる)
        groupService.removeMember(groupId, otherUserId, "admin@example.com");
        assertThat(resolver.resolve(otherUserId, connectionId)
                .columnLevel("PUBLIC", "T1", "C1")).isEqualTo(PermissionLevel.NONE);

        // PermissionCacheInvalidated イベント(接続削除・取込)でも無効化される
        groupService.addMember(groupId, otherUserId, "admin@example.com");
        assertThat(resolver.resolve(otherUserId, connectionId)
                .columnLevel("PUBLIC", "T1", "C1")).isEqualTo(PermissionLevel.UPDATE);
        entryRepository.deleteAll(); // リポジトリ直接変更(無効化されない経路)
        assertThat(resolver.resolve(otherUserId, connectionId)
                .columnLevel("PUBLIC", "T1", "C1")).isEqualTo(PermissionLevel.UPDATE); // 古いキャッシュ
        eventPublisher.publishEvent(new PermissionCacheInvalidated());
        assertThat(resolver.resolve(otherUserId, connectionId)
                .columnLevel("PUBLIC", "T1", "C1")).isEqualTo(PermissionLevel.NONE);
    }
}

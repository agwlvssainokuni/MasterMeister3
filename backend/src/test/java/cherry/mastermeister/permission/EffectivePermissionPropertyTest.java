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

import cherry.mastermeister.permission.EffectivePermissionResolver.AuxKey;
import cherry.mastermeister.permission.EffectivePermissionResolver.ColumnMeta;
import cherry.mastermeister.permission.EffectivePermissionResolver.ScopeKey;
import cherry.mastermeister.permission.EffectivePermissionResolver.TableMeta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * PBT-03(ブロッキング): D-21 実効権限解決の不変条件。
 * P1 個別優先(個別チェーンに明示エントリがあるカラムはグループ設定に影響されない)、
 * P2 グループ単調性(グループ追加で実効権限が狭まらない)、
 * P4 メタデータ交差(孤児エントリが結果に現れない)。
 * P3 キャッシュ透過性は EffectivePermissionResolverIntegrationTest で検証。
 */
class EffectivePermissionPropertyTest {

    private static ColumnMeta col(String name, Integer pkSeq) {
        return new ColumnMeta(name, pkSeq);
    }

    /** 取込済みメタデータの宇宙(S3 / TX / CZ 等はあえて含めない = 孤児スコープ)。 */
    private static final List<TableMeta> UNIVERSE = List.of(
            new TableMeta("S1", "T1", List.of(col("ID", 1), col("C1", null), col("C2", null))),
            new TableMeta("S1", "T2", List.of(col("ID", 1), col("D1", null))),
            new TableMeta("S2", "T3", List.of(col("X", null), col("Y", null))));

    private static final List<Long> ALL_GROUPS = List.of(1L, 2L, 3L, 4L);

    @Provide
    Arbitrary<ScopeKey> scopeKeys() {
        Arbitrary<String> schemas = Arbitraries.of("S1", "S2", "S3");
        Arbitrary<String> tables = Arbitraries.of("", "T1", "T2", "T3", "TX");
        Arbitrary<String> columns = Arbitraries.of("", "ID", "C1", "C2", "D1", "X", "Y", "CZ");
        return net.jqwik.api.Combinators.combine(schemas, tables, columns)
                .as(ScopeKey::new)
                .filter(key -> key.column().isEmpty() || !key.table().isEmpty());
    }

    @Provide
    Arbitrary<Map<ScopeKey, PermissionLevel>> entryMaps() {
        return Arbitraries.maps(scopeKeys(), Arbitraries.of(PermissionLevel.class))
                .ofMaxSize(12);
    }

    @Provide
    Arbitrary<Map<Long, Map<ScopeKey, PermissionLevel>>> groupEntryMaps() {
        return Arbitraries.maps(Arbitraries.of(ALL_GROUPS), entryMaps()).ofMaxSize(4);
    }

    @Provide
    Arbitrary<List<Long>> memberGroups() {
        return Arbitraries.of(ALL_GROUPS).list().uniqueElements().ofMaxSize(4);
    }

    private static EffectivePermissions compute(
            Map<ScopeKey, PermissionLevel> userMain,
            List<Long> groupIds,
            Map<Long, Map<ScopeKey, PermissionLevel>> groupMain) {
        return EffectivePermissionResolver.compute(
                1L, 1L, userMain, Map.<AuxKey, Boolean>of(), groupIds, groupMain,
                Map.<Long, Map<AuxKey, Boolean>>of(), UNIVERSE);
    }

    private static PermissionLevel chainLookup(
            Map<ScopeKey, PermissionLevel> entries, String schema, String table, String column) {
        PermissionLevel value = entries.get(new ScopeKey(schema, table, column));
        if (value != null) {
            return value;
        }
        value = entries.get(new ScopeKey(schema, table, ""));
        if (value != null) {
            return value;
        }
        return entries.get(new ScopeKey(schema, "", ""));
    }

    @Property
    void P1_個別チェーンに明示エントリがあるカラムはグループ設定に影響されない(
            @ForAll("entryMaps") Map<ScopeKey, PermissionLevel> userMain,
            @ForAll("memberGroups") List<Long> groupIds,
            @ForAll("groupEntryMaps") Map<Long, Map<ScopeKey, PermissionLevel>> groupA,
            @ForAll("groupEntryMaps") Map<Long, Map<ScopeKey, PermissionLevel>> groupB) {
        EffectivePermissions withA = compute(userMain, groupIds, groupA);
        EffectivePermissions withB = compute(userMain, groupIds, groupB);
        for (TableMeta table : UNIVERSE) {
            for (ColumnMeta column : table.columns()) {
                PermissionLevel individual = chainLookup(
                        userMain, table.schema(), table.table(), column.name());
                if (individual != null) {
                    assertThat(withA.columnLevel(table.schema(), table.table(), column.name()))
                            .isEqualTo(individual);
                    assertThat(withB.columnLevel(table.schema(), table.table(), column.name()))
                            .isEqualTo(individual);
                }
            }
        }
    }

    @Property
    void P2_グループ追加で実効権限が狭まらない(
            @ForAll("entryMaps") Map<ScopeKey, PermissionLevel> userMain,
            @ForAll("memberGroups") List<Long> groupIds,
            @ForAll("groupEntryMaps") Map<Long, Map<ScopeKey, PermissionLevel>> groupMain,
            @ForAll("entryMaps") Map<ScopeKey, PermissionLevel> extraGroupEntries) {
        EffectivePermissions before = compute(userMain, groupIds, groupMain);

        // 新しいグループ(id=99)に加入
        List<Long> extendedGroups = new ArrayList<>(groupIds);
        extendedGroups.add(99L);
        Map<Long, Map<ScopeKey, PermissionLevel>> extendedMain = new HashMap<>(groupMain);
        extendedMain.put(99L, extraGroupEntries);
        EffectivePermissions after = compute(userMain, extendedGroups, extendedMain);

        for (TableMeta table : UNIVERSE) {
            for (ColumnMeta column : table.columns()) {
                PermissionLevel levelBefore =
                        before.columnLevel(table.schema(), table.table(), column.name());
                PermissionLevel levelAfter =
                        after.columnLevel(table.schema(), table.table(), column.name());
                assertThat(levelAfter.ordinal())
                        .as("%s.%s.%s", table.schema(), table.table(), column.name())
                        .isGreaterThanOrEqualTo(levelBefore.ordinal());
            }
        }
    }

    @Property
    void P4_解決結果は取込済みメタデータの範囲のみ_孤児エントリが現れない(
            @ForAll("entryMaps") Map<ScopeKey, PermissionLevel> userMain,
            @ForAll("memberGroups") List<Long> groupIds,
            @ForAll("groupEntryMaps") Map<Long, Map<ScopeKey, PermissionLevel>> groupMain) {
        EffectivePermissions permissions = compute(userMain, groupIds, groupMain);

        Set<EffectivePermissions.TableKey> universeTables = UNIVERSE.stream()
                .map(table -> new EffectivePermissions.TableKey(table.schema(), table.table()))
                .collect(Collectors.toSet());
        assertThat(permissions.tables().keySet()).isEqualTo(universeTables);

        for (TableMeta table : UNIVERSE) {
            Set<String> universeColumns = table.columns().stream()
                    .map(ColumnMeta::name)
                    .collect(Collectors.toSet());
            assertThat(permissions.tables()
                    .get(new EffectivePermissions.TableKey(table.schema(), table.table()))
                    .columns().keySet())
                    .isEqualTo(universeColumns);
        }
    }
}

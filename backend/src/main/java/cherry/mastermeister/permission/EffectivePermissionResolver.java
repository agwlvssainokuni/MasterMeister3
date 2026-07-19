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

import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.event.PermissionCacheInvalidated;
import cherry.mastermeister.metadata.MetaColumn;
import cherry.mastermeister.metadata.MetaColumnRepository;
import cherry.mastermeister.metadata.MetaSchema;
import cherry.mastermeister.metadata.MetaSchemaRepository;
import cherry.mastermeister.metadata.MetaTableRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 実効権限解決(US-015、D-21 — business-rules.md §4)。
 *
 * <p>カラムごと・補助権限種別ごとに独立して評価する:
 * (1) 個別チェーン優先(詳細 → 上位の最初の明示エントリ。グループは参照しない)、
 * (2) グループ合成(各グループのチェーン解決値をより許可的な方向で合成)、
 * (3) デフォルト拒否。
 *
 * <p>キャッシュ: (userId, connectionId) 単位の Caffeine
 * (サイズ上限 + 防御的 TTL — NFR-U4-03)。無効化は粗粒度の全無効化。
 */
@Service
public class EffectivePermissionResolver {

    private record CacheKey(Long userId, Long connectionId) {
    }

    /** 明示エントリの索引キー(空文字 = その階層の指定なし)。 */
    record ScopeKey(String schema, String table, String column) {
    }

    record AuxKey(String schema, String table, AuxType auxType) {
    }

    /** 解決対象メタデータ(PBT はこの形で入力を生成する)。 */
    record TableMeta(String schema, String table, List<ColumnMeta> columns) {
    }

    record ColumnMeta(String name, Integer primaryKeySeq) {
    }

    private final PermissionEntryRepository entryRepository;
    private final PermissionAuxEntryRepository auxEntryRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MetaSchemaRepository metaSchemaRepository;
    private final MetaTableRepository metaTableRepository;
    private final MetaColumnRepository metaColumnRepository;
    private final Cache<CacheKey, EffectivePermissions> cache;

    public EffectivePermissionResolver(
            PermissionEntryRepository entryRepository,
            PermissionAuxEntryRepository auxEntryRepository,
            GroupMemberRepository groupMemberRepository,
            MetaSchemaRepository metaSchemaRepository,
            MetaTableRepository metaTableRepository,
            MetaColumnRepository metaColumnRepository,
            AppProperties properties) {
        this.entryRepository = entryRepository;
        this.auxEntryRepository = auxEntryRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.metaSchemaRepository = metaSchemaRepository;
        this.metaTableRepository = metaTableRepository;
        this.metaColumnRepository = metaColumnRepository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.permission().cacheMaxSize())
                .expireAfterWrite(properties.permission().cacheTtl())
                .build();
    }

    /** キャッシュ経由の解決(⑤⑥の主経路)。 */
    public EffectivePermissions resolve(Long userId, Long connectionId) {
        return cache.get(new CacheKey(userId, connectionId),
                key -> resolveUncached(key.userId(), key.connectionId()));
    }

    /** 粗粒度の全無効化(権限変更・グループ変更・スキーマ再取込・接続削除)。 */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    @EventListener
    public void onCacheInvalidated(PermissionCacheInvalidated event) {
        invalidateAll();
    }

    /** キャッシュを経由しない解決(PBT P3 のキャッシュ透過性検証にも使用)。 */
    @Transactional(readOnly = true)
    public EffectivePermissions resolveUncached(Long userId, Long connectionId) {
        // 個別チェーン
        Map<ScopeKey, PermissionLevel> userMain = indexMain(
                entryRepository.findByConnectionIdAndPrincipalTypeAndPrincipalId(
                        connectionId, PrincipalType.USER, userId));
        Map<AuxKey, Boolean> userAux = indexAux(
                auxEntryRepository.findByConnectionIdAndPrincipalTypeAndPrincipalId(
                        connectionId, PrincipalType.USER, userId));

        // 所属グループのチェーン(グループごとに独立して解決するため principal 別に索引)
        List<Long> groupIds = groupMemberRepository.findByUserId(userId).stream()
                .map(GroupMember::getGroupId)
                .toList();
        Map<Long, Map<ScopeKey, PermissionLevel>> groupMain = groupIds.isEmpty()
                ? Map.of()
                : entryRepository.findByConnectionIdAndPrincipalTypeAndPrincipalIdIn(
                                connectionId, PrincipalType.GROUP, groupIds).stream()
                        .collect(Collectors.groupingBy(PermissionEntry::getPrincipalId,
                                Collectors.toMap(
                                        e -> new ScopeKey(e.getSchemaName(), e.getTableName(),
                                                e.getColumnName()),
                                        PermissionEntry::getPermission)));
        Map<Long, Map<AuxKey, Boolean>> groupAux = groupIds.isEmpty()
                ? Map.of()
                : auxEntryRepository.findByConnectionIdAndPrincipalTypeAndPrincipalIdIn(
                                connectionId, PrincipalType.GROUP, groupIds).stream()
                        .collect(Collectors.groupingBy(PermissionAuxEntry::getPrincipalId,
                                Collectors.toMap(
                                        e -> new AuxKey(e.getSchemaName(), e.getTableName(),
                                                e.getAuxType()),
                                        PermissionAuxEntry::isGranted)));

        // 取込済みメタデータのカラム集合のみを対象に解決(孤児は結果に現れない — PBT P4)
        Map<Long, MetaSchema> schemas = metaSchemaRepository
                .findByConnectionIdOrderByName(connectionId).stream()
                .collect(Collectors.toMap(MetaSchema::getId, Function.identity()));
        Map<Long, List<MetaColumn>> columnsByTable = metaColumnRepository
                .findByConnectionId(connectionId).stream()
                .collect(Collectors.groupingBy(MetaColumn::getTableId));
        List<TableMeta> tables = metaTableRepository.findByConnectionId(connectionId).stream()
                .map(table -> new TableMeta(
                        schemas.get(table.getSchemaId()).getName(),
                        table.getName(),
                        columnsByTable.getOrDefault(table.getId(), List.<MetaColumn>of()).stream()
                                .map(column -> new ColumnMeta(
                                        column.getName(), column.getPrimaryKeySeq()))
                                .toList()))
                .toList();

        return compute(userId, connectionId, userMain, userAux, groupIds, groupMain, groupAux,
                tables);
    }

    /**
     * 解決の中核(純粋関数 — PBT-03 はここを対象に入力を生成する)。
     * D-21: 個別チェーン優先 → グループ合成(許可的優先・OR)→ デフォルト拒否。
     */
    static EffectivePermissions compute(
            Long userId, Long connectionId,
            Map<ScopeKey, PermissionLevel> userMain,
            Map<AuxKey, Boolean> userAux,
            List<Long> groupIds,
            Map<Long, Map<ScopeKey, PermissionLevel>> groupMain,
            Map<Long, Map<AuxKey, Boolean>> groupAux,
            List<TableMeta> tables) {
        Map<EffectivePermissions.TableKey, EffectivePermissions.TablePermissions> result =
                new HashMap<>();
        for (TableMeta table : tables) {
            String schemaName = table.schema();
            String tableName = table.table();

            Map<String, PermissionLevel> columnLevels = new HashMap<>();
            for (ColumnMeta column : table.columns()) {
                columnLevels.put(column.name(), resolveMain(
                        schemaName, tableName, column.name(), userMain, groupMain, groupIds));
            }

            boolean auxCreate = resolveAux(schemaName, tableName, AuxType.CREATE,
                    userAux, groupAux, groupIds);
            boolean auxDelete = resolveAux(schemaName, tableName, AuxType.DELETE,
                    userAux, groupAux, groupIds);

            List<ColumnMeta> primaryKeyColumns = table.columns().stream()
                    .filter(column -> column.primaryKeySeq() != null)
                    .toList();
            boolean hasPrimaryKey = !primaryKeyColumns.isEmpty();
            boolean allPkUpdate = hasPrimaryKey && primaryKeyColumns.stream().allMatch(
                    column -> columnLevels.get(column.name()) == PermissionLevel.UPDATE);
            boolean allPkReadable = hasPrimaryKey && primaryKeyColumns.stream().allMatch(
                    column -> columnLevels.get(column.name()).ordinal()
                            >= PermissionLevel.READ.ordinal());

            boolean canCreate = auxCreate && (!hasPrimaryKey || allPkUpdate);
            boolean canDelete = auxDelete && allPkReadable;
            boolean updatable = allPkReadable; // D-15: 主キーなしは更新不可

            result.put(new EffectivePermissions.TableKey(schemaName, tableName),
                    new EffectivePermissions.TablePermissions(
                            Map.copyOf(columnLevels), canCreate, canDelete, updatable));
        }
        return new EffectivePermissions(userId, connectionId, Map.copyOf(result));
    }

    private static PermissionLevel resolveMain(
            String schema, String table, String column,
            Map<ScopeKey, PermissionLevel> userMain,
            Map<Long, Map<ScopeKey, PermissionLevel>> groupMain,
            List<Long> groupIds) {
        // (1) 個別チェーン優先
        PermissionLevel individual = chainLookup(userMain, schema, table, column);
        if (individual != null) {
            return individual;
        }
        // (2) グループ合成(より許可的な値)
        PermissionLevel combined = null;
        for (Long groupId : groupIds) {
            PermissionLevel groupValue = chainLookup(
                    groupMain.getOrDefault(groupId, Map.of()), schema, table, column);
            if (groupValue != null) {
                combined = combined == null ? groupValue
                        : PermissionLevel.morePermissive(combined, groupValue);
            }
        }
        // (3) デフォルト拒否
        return combined != null ? combined : PermissionLevel.NONE;
    }

    private static boolean resolveAux(
            String schema, String table, AuxType auxType,
            Map<AuxKey, Boolean> userAux,
            Map<Long, Map<AuxKey, Boolean>> groupAux,
            List<Long> groupIds) {
        // (1) 個別チェーン優先(テーブル → スキーマ)
        Boolean individual = auxChainLookup(userAux, schema, table, auxType);
        if (individual != null) {
            return individual;
        }
        // (2) グループ合成(OR)
        for (Long groupId : groupIds) {
            Boolean groupValue = auxChainLookup(
                    groupAux.getOrDefault(groupId, Map.of()), schema, table, auxType);
            if (groupValue != null && groupValue) {
                return true;
            }
        }
        // (3) デフォルト拒否(明示 false のみ見つかった場合も false)
        return false;
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

    private static Boolean auxChainLookup(
            Map<AuxKey, Boolean> entries, String schema, String table, AuxType auxType) {
        Boolean value = entries.get(new AuxKey(schema, table, auxType));
        if (value != null) {
            return value;
        }
        return entries.get(new AuxKey(schema, "", auxType));
    }

    private static Map<ScopeKey, PermissionLevel> indexMain(List<PermissionEntry> entries) {
        return entries.stream().collect(Collectors.toMap(
                entry -> new ScopeKey(entry.getSchemaName(), entry.getTableName(),
                        entry.getColumnName()),
                PermissionEntry::getPermission));
    }

    private static Map<AuxKey, Boolean> indexAux(List<PermissionAuxEntry> entries) {
        return entries.stream().collect(Collectors.toMap(
                entry -> new AuxKey(entry.getSchemaName(), entry.getTableName(),
                        entry.getAuxType()),
                PermissionAuxEntry::isGranted));
    }
}

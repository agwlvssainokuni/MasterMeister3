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

import cherry.mastermeister.audit.AuditEvent;
import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.AuditEvents;
import cherry.mastermeister.audit.AuditOutcome;
import cherry.mastermeister.common.web.BadRequestException;
import cherry.mastermeister.common.web.NotFoundException;
import cherry.mastermeister.metadata.MetaColumn;
import cherry.mastermeister.metadata.MetaSchema;
import cherry.mastermeister.metadata.MetaSchemaRepository;
import cherry.mastermeister.metadata.MetaTable;
import cherry.mastermeister.metadata.MetaTableRepository;
import cherry.mastermeister.metadata.MetaColumnRepository;
import cherry.mastermeister.user.AppUserRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 権限エントリの管理(US-013/014、D-18 — business-rules.md §3)。
 * 「エントリ削除 = 未設定に戻す」と「明示的 NONE の設定」は別操作。
 * スコープ物理名のメタデータ存在は検証しない(孤児許容 — Q2=A)。
 * 変更はすべて監査記録 + キャッシュ無効化。
 */
@Service
public class PermissionService {

    /** 主権限エントリ(orphan: メタデータに対象が存在しない — Q2=A 画面注記用)。 */
    public record MainEntryView(
            String schema, String table, String column, PermissionLevel permission,
            boolean orphan) {
    }

    /** 補助権限エントリ。 */
    public record AuxEntryView(
            String schema, String table, AuxType auxType, boolean granted, boolean orphan) {
    }

    public record PrincipalEntries(List<MainEntryView> main, List<AuxEntryView> aux) {
    }

    public record Scope(String schema, String table, String column) {

        public Scope {
            if (schema == null || schema.isBlank()) {
                throw new BadRequestException("PERMISSION_INVALID_SCOPE");
            }
            table = table == null ? "" : table;
            column = column == null ? "" : column;
            if (!column.isEmpty() && table.isEmpty()) {
                throw new BadRequestException("PERMISSION_INVALID_SCOPE");
            }
        }

        String display() {
            StringBuilder sb = new StringBuilder(schema);
            if (!table.isEmpty()) {
                sb.append('.').append(table);
            }
            if (!column.isEmpty()) {
                sb.append('.').append(column);
            }
            return sb.toString();
        }
    }

    private final PermissionEntryRepository entryRepository;
    private final PermissionAuxEntryRepository auxEntryRepository;
    private final AppUserRepository userRepository;
    private final UserGroupRepository groupRepository;
    private final MetaSchemaRepository metaSchemaRepository;
    private final MetaTableRepository metaTableRepository;
    private final MetaColumnRepository metaColumnRepository;
    private final EffectivePermissionResolver resolver;
    private final AuditEventPublisher auditEventPublisher;

    public PermissionService(
            PermissionEntryRepository entryRepository,
            PermissionAuxEntryRepository auxEntryRepository,
            AppUserRepository userRepository,
            UserGroupRepository groupRepository,
            MetaSchemaRepository metaSchemaRepository,
            MetaTableRepository metaTableRepository,
            MetaColumnRepository metaColumnRepository,
            EffectivePermissionResolver resolver,
            AuditEventPublisher auditEventPublisher) {
        this.entryRepository = entryRepository;
        this.auxEntryRepository = auxEntryRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.metaSchemaRepository = metaSchemaRepository;
        this.metaTableRepository = metaTableRepository;
        this.metaColumnRepository = metaColumnRepository;
        this.resolver = resolver;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public PrincipalEntries entriesFor(
            Long connectionId, PrincipalType principalType, Long principalId) {
        requirePrincipal(principalType, principalId);
        MetadataIndex index = metadataIndex(connectionId);
        List<MainEntryView> main = entryRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalId(
                        connectionId, principalType, principalId).stream()
                .map(entry -> new MainEntryView(
                        entry.getSchemaName(), entry.getTableName(), entry.getColumnName(),
                        entry.getPermission(),
                        index.orphan(entry.getSchemaName(), entry.getTableName(),
                                entry.getColumnName())))
                .sorted(java.util.Comparator
                        .comparing(MainEntryView::schema)
                        .thenComparing(MainEntryView::table)
                        .thenComparing(MainEntryView::column))
                .toList();
        List<AuxEntryView> aux = auxEntryRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalId(
                        connectionId, principalType, principalId).stream()
                .map(entry -> new AuxEntryView(
                        entry.getSchemaName(), entry.getTableName(), entry.getAuxType(),
                        entry.isGranted(),
                        index.orphan(entry.getSchemaName(), entry.getTableName(), "")))
                .sorted(java.util.Comparator
                        .comparing(AuxEntryView::schema)
                        .thenComparing(AuxEntryView::table)
                        .thenComparing(view -> view.auxType().name()))
                .toList();
        return new PrincipalEntries(main, aux);
    }

    @Transactional
    public void setMainEntry(
            Long connectionId, PrincipalType principalType, Long principalId,
            Scope scope, PermissionLevel permission, String actor) {
        requirePrincipal(principalType, principalId);
        PermissionEntry entry = entryRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameAndColumnName(
                        connectionId, principalType, principalId,
                        scope.schema(), scope.table(), scope.column())
                .orElseGet(() -> {
                    PermissionEntry created = new PermissionEntry();
                    created.setConnectionId(connectionId);
                    created.setPrincipalType(principalType);
                    created.setPrincipalId(principalId);
                    created.setSchemaName(scope.schema());
                    created.setTableName(scope.table());
                    created.setColumnName(scope.column());
                    return created;
                });
        entry.setPermission(permission);
        entryRepository.save(entry);
        afterChange(AuditEvents.PERMISSION_SET, connectionId, actor, Map.of(
                "principalType", principalType.name(), "principalId", principalId,
                "scope", scope.display(), "permission", permission.name()));
    }

    @Transactional
    public void setAuxEntry(
            Long connectionId, PrincipalType principalType, Long principalId,
            Scope scope, AuxType auxType, boolean granted, String actor) {
        requirePrincipal(principalType, principalId);
        if (!scope.column().isEmpty()) {
            throw new BadRequestException("PERMISSION_INVALID_SCOPE");
        }
        PermissionAuxEntry entry = auxEntryRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameAndAuxType(
                        connectionId, principalType, principalId,
                        scope.schema(), scope.table(), auxType)
                .orElseGet(() -> {
                    PermissionAuxEntry created = new PermissionAuxEntry();
                    created.setConnectionId(connectionId);
                    created.setPrincipalType(principalType);
                    created.setPrincipalId(principalId);
                    created.setSchemaName(scope.schema());
                    created.setTableName(scope.table());
                    created.setAuxType(auxType);
                    return created;
                });
        entry.setGranted(granted);
        auxEntryRepository.save(entry);
        afterChange(AuditEvents.PERMISSION_SET, connectionId, actor, Map.of(
                "principalType", principalType.name(), "principalId", principalId,
                "scope", scope.display(), "auxType", auxType.name(), "granted", granted));
    }

    /** エントリ削除 = 未設定に戻す(D-18。冪等 — 存在しない場合は何もしない)。 */
    @Transactional
    public void removeMainEntry(
            Long connectionId, PrincipalType principalType, Long principalId,
            Scope scope, String actor) {
        entryRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameAndColumnName(
                        connectionId, principalType, principalId,
                        scope.schema(), scope.table(), scope.column())
                .ifPresent(entry -> {
                    entryRepository.delete(entry);
                    afterChange(AuditEvents.PERMISSION_REMOVED, connectionId, actor, Map.of(
                            "principalType", principalType.name(), "principalId", principalId,
                            "scope", scope.display()));
                });
    }

    @Transactional
    public void removeAuxEntry(
            Long connectionId, PrincipalType principalType, Long principalId,
            Scope scope, AuxType auxType, String actor) {
        auxEntryRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameAndAuxType(
                        connectionId, principalType, principalId,
                        scope.schema(), scope.table(), auxType)
                .ifPresent(entry -> {
                    auxEntryRepository.delete(entry);
                    afterChange(AuditEvents.PERMISSION_REMOVED, connectionId, actor, Map.of(
                            "principalType", principalType.name(), "principalId", principalId,
                            "scope", scope.display(), "auxType", auxType.name()));
                });
    }

    private void afterChange(
            String eventType, Long connectionId, String actor, Map<String, Object> detail) {
        resolver.invalidateAll();
        auditEventPublisher.publish(new AuditEvent(
                eventType, actor, connectionId, null, AuditOutcome.SUCCESS, detail));
    }

    private void requirePrincipal(PrincipalType principalType, Long principalId) {
        boolean exists = switch (principalType) {
            case USER -> userRepository.existsById(principalId);
            case GROUP -> groupRepository.existsById(principalId);
        };
        if (!exists) {
            throw new NotFoundException("PRINCIPAL_NOT_FOUND");
        }
    }

    private MetadataIndex metadataIndex(Long connectionId) {
        Map<Long, String> schemaNames = metaSchemaRepository
                .findByConnectionIdOrderByName(connectionId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        MetaSchema::getId, MetaSchema::getName));
        List<MetaTable> tables = metaTableRepository.findByConnectionId(connectionId);
        Map<Long, String> tableKeyById = new HashMap<>();
        Set<String> tableKeys = new HashSet<>();
        for (MetaTable table : tables) {
            String key = schemaNames.get(table.getSchemaId()) + " " + table.getName();
            tableKeyById.put(table.getId(), key);
            tableKeys.add(key);
        }
        Set<String> columnKeys = new HashSet<>();
        for (MetaColumn column : metaColumnRepository.findByConnectionId(connectionId)) {
            columnKeys.add(tableKeyById.get(column.getTableId()) + " " + column.getName());
        }
        return new MetadataIndex(
                new HashSet<>(schemaNames.values()), tableKeys, columnKeys);
    }

    private record MetadataIndex(
            Set<String> schemas, Set<String> tables, Set<String> columns) {

        boolean orphan(String schema, String table, String column) {
            if (!column.isEmpty()) {
                return !columns.contains(schema + " " + table + " " + column);
            }
            if (!table.isEmpty()) {
                return !tables.contains(schema + " " + table);
            }
            return !schemas.contains(schema);
        }
    }
}

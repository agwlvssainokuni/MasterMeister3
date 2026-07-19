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
import cherry.mastermeister.user.AppUserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * 権限設定の YAML エクスポート/インポート(US-016/017 — business-rules.md §6)。
 * プリンシパルは email / グループ名で表現(Q3=A)。接続情報は YAML に含めない。
 * インポートは全置換・単一トランザクション。構文/スキーマ/重複/未知プリンシパル/不正値は
 * 1 件でも全体拒否(理由一覧を返す)。型を限定した安全ロードのみ(SECURITY-13)。
 */
@Service
public class PermissionYamlService {

    // === YAML v1 形式(型限定バインド — 未知プロパティ・型不一致は即拒否) ===

    public record YamlDocument(Integer version, List<YamlUser> users, List<YamlGroup> groups) {
    }

    public record YamlUser(String email, List<YamlMain> main, List<YamlAux> aux) {
    }

    public record YamlGroup(String name, List<YamlMain> main, List<YamlAux> aux) {
    }

    public record YamlMain(String schema, String table, String column, String permission) {
    }

    public record YamlAux(String schema, String table, String type, Boolean granted) {
    }

    public record ValidationError(String path, String reason) {
    }

    /** 全体拒否(US-017)。code は先頭エラーの理由、errors に全件。 */
    public static class YamlValidationException extends RuntimeException {

        private final transient List<ValidationError> errors;

        public YamlValidationException(List<ValidationError> errors) {
            super(errors.getFirst().reason());
            this.errors = errors;
        }

        public String code() {
            return errors.getFirst().reason();
        }

        public List<ValidationError> errors() {
            return errors;
        }
    }

    private final YAMLMapper yamlMapper = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final PermissionEntryRepository entryRepository;
    private final PermissionAuxEntryRepository auxEntryRepository;
    private final AppUserRepository userRepository;
    private final UserGroupRepository groupRepository;
    private final EffectivePermissionResolver resolver;
    private final AuditEventPublisher auditEventPublisher;

    public PermissionYamlService(
            PermissionEntryRepository entryRepository,
            PermissionAuxEntryRepository auxEntryRepository,
            AppUserRepository userRepository,
            UserGroupRepository groupRepository,
            EffectivePermissionResolver resolver,
            AuditEventPublisher auditEventPublisher) {
        this.entryRepository = entryRepository;
        this.auxEntryRepository = auxEntryRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.resolver = resolver;
        this.auditEventPublisher = auditEventPublisher;
    }

    /** 正規順序(users: email 昇順、groups: name 昇順、エントリ: スコープ昇順)で出力。 */
    @Transactional(readOnly = true)
    public String export(Long connectionId, String actor) {
        List<PermissionEntry> mainEntries = entryRepository.findByConnectionId(connectionId);
        List<PermissionAuxEntry> auxEntries = auxEntryRepository.findByConnectionId(connectionId);

        Map<Long, String> userEmails = new HashMap<>();
        userRepository.findAll().forEach(user -> userEmails.put(user.getId(), user.getEmail()));
        Map<Long, String> groupNames = new HashMap<>();
        groupRepository.findAll().forEach(group -> groupNames.put(group.getId(), group.getName()));

        Map<String, List<YamlMain>> userMain = new TreeMap<>();
        Map<String, List<YamlMain>> groupMain = new TreeMap<>();
        for (PermissionEntry entry : mainEntries) {
            YamlMain yamlMain = new YamlMain(
                    entry.getSchemaName(),
                    entry.getTableName().isEmpty() ? null : entry.getTableName(),
                    entry.getColumnName().isEmpty() ? null : entry.getColumnName(),
                    entry.getPermission().name());
            principalKey(entry.getPrincipalType(), entry.getPrincipalId(),
                    userEmails, groupNames);
            if (entry.getPrincipalType() == PrincipalType.USER) {
                userMain.computeIfAbsent(userEmails.get(entry.getPrincipalId()),
                        key -> new ArrayList<>()).add(yamlMain);
            } else {
                groupMain.computeIfAbsent(groupNames.get(entry.getPrincipalId()),
                        key -> new ArrayList<>()).add(yamlMain);
            }
        }
        Map<String, List<YamlAux>> userAux = new TreeMap<>();
        Map<String, List<YamlAux>> groupAux = new TreeMap<>();
        for (PermissionAuxEntry entry : auxEntries) {
            YamlAux yamlAux = new YamlAux(
                    entry.getSchemaName(),
                    entry.getTableName().isEmpty() ? null : entry.getTableName(),
                    entry.getAuxType().name(),
                    entry.isGranted());
            if (entry.getPrincipalType() == PrincipalType.USER) {
                userAux.computeIfAbsent(userEmails.get(entry.getPrincipalId()),
                        key -> new ArrayList<>()).add(yamlAux);
            } else {
                groupAux.computeIfAbsent(groupNames.get(entry.getPrincipalId()),
                        key -> new ArrayList<>()).add(yamlAux);
            }
        }

        Set<String> userKeys = new HashSet<>(userMain.keySet());
        userKeys.addAll(userAux.keySet());
        List<YamlUser> users = userKeys.stream().sorted()
                .map(email -> new YamlUser(email,
                        sortMain(userMain.getOrDefault(email, List.of())),
                        sortAux(userAux.getOrDefault(email, List.of()))))
                .toList();
        Set<String> groupKeys = new HashSet<>(groupMain.keySet());
        groupKeys.addAll(groupAux.keySet());
        List<YamlGroup> groups = groupKeys.stream().sorted()
                .map(name -> new YamlGroup(name,
                        sortMain(groupMain.getOrDefault(name, List.of())),
                        sortAux(groupAux.getOrDefault(name, List.of()))))
                .toList();

        String yaml = yamlMapper.writeValueAsString(new YamlDocument(1, users, groups));
        auditEventPublisher.publish(new AuditEvent(
                AuditEvents.PERMISSION_EXPORTED, actor, connectionId, null, AuditOutcome.SUCCESS,
                Map.of("entries", mainEntries.size() + auxEntries.size())));
        return yaml;
    }

    /** 全置換インポート。検証エラーは 1 件でも全体拒否(YamlValidationException)。 */
    @Transactional
    public int importReplace(Long connectionId, String yaml, String actor) {
        YamlDocument document;
        try {
            document = yamlMapper.readValue(yaml, YamlDocument.class);
        } catch (RuntimeException e) {
            reject(connectionId, actor, List.of(new ValidationError("$", "YAML_INVALID")));
            return 0; // 到達しない(reject が throw)
        }

        List<ValidationError> errors = new ArrayList<>();
        if (document.version() == null || document.version() != 1) {
            errors.add(new ValidationError("version", "YAML_INVALID"));
        }
        Map<String, Long> userIds = new HashMap<>();
        userRepository.findAll().forEach(user -> userIds.put(user.getEmail(), user.getId()));
        Map<String, Long> groupIds = new HashMap<>();
        groupRepository.findAll().forEach(group -> groupIds.put(group.getName(), group.getId()));

        List<PermissionEntry> mainEntries = new ArrayList<>();
        List<PermissionAuxEntry> auxEntries = new ArrayList<>();
        Set<String> mainSeen = new HashSet<>();
        Set<String> auxSeen = new HashSet<>();

        List<YamlUser> users = document.users() == null ? List.of() : document.users();
        for (int i = 0; i < users.size(); i++) {
            YamlUser user = users.get(i);
            String path = "users[" + i + "]";
            Long principalId = userIds.get(user.email());
            if (user.email() == null || principalId == null) {
                errors.add(new ValidationError(path, "YAML_UNKNOWN_PRINCIPAL"));
                continue;
            }
            collectEntries(connectionId, PrincipalType.USER, principalId, path,
                    user.main(), user.aux(), mainEntries, auxEntries,
                    mainSeen, auxSeen, errors);
        }
        List<YamlGroup> groups = document.groups() == null ? List.of() : document.groups();
        for (int i = 0; i < groups.size(); i++) {
            YamlGroup group = groups.get(i);
            String path = "groups[" + i + "]";
            Long principalId = groupIds.get(group.name());
            if (group.name() == null || principalId == null) {
                errors.add(new ValidationError(path, "YAML_UNKNOWN_PRINCIPAL"));
                continue;
            }
            collectEntries(connectionId, PrincipalType.GROUP, principalId, path,
                    group.main(), group.aux(), mainEntries, auxEntries,
                    mainSeen, auxSeen, errors);
        }

        if (!errors.isEmpty()) {
            reject(connectionId, actor, errors);
        }

        // 全置換(単一トランザクション)
        entryRepository.deleteByConnectionId(connectionId);
        auxEntryRepository.deleteByConnectionId(connectionId);
        entryRepository.flush();
        auxEntryRepository.flush();
        entryRepository.saveAll(mainEntries);
        auxEntryRepository.saveAll(auxEntries);

        resolver.invalidateAll();
        int count = mainEntries.size() + auxEntries.size();
        auditEventPublisher.publish(new AuditEvent(
                AuditEvents.PERMISSION_IMPORTED, actor, connectionId, null, AuditOutcome.SUCCESS,
                Map.of("entries", count)));
        return count;
    }

    private void collectEntries(
            Long connectionId, PrincipalType principalType, Long principalId, String path,
            List<YamlMain> main, List<YamlAux> aux,
            List<PermissionEntry> mainEntries, List<PermissionAuxEntry> auxEntries,
            Set<String> mainSeen, Set<String> auxSeen, List<ValidationError> errors) {
        List<YamlMain> mainList = main == null ? List.of() : main;
        for (int j = 0; j < mainList.size(); j++) {
            YamlMain entry = mainList.get(j);
            String entryPath = path + ".main[" + j + "]";
            String table = entry.table() == null ? "" : entry.table();
            String column = entry.column() == null ? "" : entry.column();
            if (entry.schema() == null || entry.schema().isBlank()
                    || (!column.isEmpty() && table.isEmpty())) {
                errors.add(new ValidationError(entryPath, "YAML_INVALID"));
                continue;
            }
            PermissionLevel permission;
            try {
                permission = PermissionLevel.valueOf(entry.permission());
            } catch (IllegalArgumentException | NullPointerException e) {
                errors.add(new ValidationError(entryPath, "YAML_INVALID"));
                continue;
            }
            String key = principalType + ":" + principalId + ":" + entry.schema()
                    + ":" + table + ":" + column;
            if (!mainSeen.add(key)) {
                errors.add(new ValidationError(entryPath, "YAML_DUPLICATE_ENTRY"));
                continue;
            }
            PermissionEntry permissionEntry = new PermissionEntry();
            permissionEntry.setConnectionId(connectionId);
            permissionEntry.setPrincipalType(principalType);
            permissionEntry.setPrincipalId(principalId);
            permissionEntry.setSchemaName(entry.schema());
            permissionEntry.setTableName(table);
            permissionEntry.setColumnName(column);
            permissionEntry.setPermission(permission);
            mainEntries.add(permissionEntry);
        }
        List<YamlAux> auxList = aux == null ? List.of() : aux;
        for (int j = 0; j < auxList.size(); j++) {
            YamlAux entry = auxList.get(j);
            String entryPath = path + ".aux[" + j + "]";
            String table = entry.table() == null ? "" : entry.table();
            if (entry.schema() == null || entry.schema().isBlank() || entry.granted() == null) {
                errors.add(new ValidationError(entryPath, "YAML_INVALID"));
                continue;
            }
            AuxType auxType;
            try {
                auxType = AuxType.valueOf(entry.type());
            } catch (IllegalArgumentException | NullPointerException e) {
                errors.add(new ValidationError(entryPath, "YAML_INVALID"));
                continue;
            }
            String key = principalType + ":" + principalId + ":" + entry.schema()
                    + ":" + table + ":" + auxType;
            if (!auxSeen.add(key)) {
                errors.add(new ValidationError(entryPath, "YAML_DUPLICATE_ENTRY"));
                continue;
            }
            PermissionAuxEntry auxEntry = new PermissionAuxEntry();
            auxEntry.setConnectionId(connectionId);
            auxEntry.setPrincipalType(principalType);
            auxEntry.setPrincipalId(principalId);
            auxEntry.setSchemaName(entry.schema());
            auxEntry.setTableName(table);
            auxEntry.setAuxType(auxType);
            auxEntry.setGranted(entry.granted());
            auxEntries.add(auxEntry);
        }
    }

    private void reject(Long connectionId, String actor, List<ValidationError> errors) {
        auditEventPublisher.publish(new AuditEvent(
                AuditEvents.PERMISSION_IMPORTED, actor, connectionId, null, AuditOutcome.FAILURE,
                Map.of("reason", errors.getFirst().reason(), "errorCount", errors.size())));
        throw new YamlValidationException(errors);
    }

    private static void principalKey(
            PrincipalType principalType, Long principalId,
            Map<Long, String> userEmails, Map<Long, String> groupNames) {
        // エクスポート対象のプリンシパルは DB 上に必ず存在する(FK なしだが削除時に
        // エントリも消す実装のため)。万一の欠落は下流の NPE でなくここで検出する
        String resolved = principalType == PrincipalType.USER
                ? userEmails.get(principalId)
                : groupNames.get(principalId);
        if (resolved == null) {
            throw new IllegalStateException(
                    "principal not found: " + principalType + "/" + principalId);
        }
    }

    private static List<YamlMain> sortMain(List<YamlMain> entries) {
        return entries.stream()
                .sorted(Comparator
                        .comparing(YamlMain::schema)
                        .thenComparing(entry -> entry.table() == null ? "" : entry.table())
                        .thenComparing(entry -> entry.column() == null ? "" : entry.column()))
                .toList();
    }

    private static List<YamlAux> sortAux(List<YamlAux> entries) {
        return entries.stream()
                .sorted(Comparator
                        .comparing(YamlAux::schema)
                        .thenComparing(entry -> entry.table() == null ? "" : entry.table())
                        .thenComparing(YamlAux::type))
                .toList();
    }
}

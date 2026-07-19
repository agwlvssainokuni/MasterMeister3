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
import cherry.mastermeister.common.web.ConflictException;
import cherry.mastermeister.common.web.NotFoundException;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * グループ管理(US-018/019 — business-rules.md §5)。
 * 削除は所属(FK CASCADE)+ 当該グループの全接続の権限エントリを同一トランザクションで削除。
 * 変更はすべて監査記録 + キャッシュ無効化。
 */
@Service
public class GroupService {

    public record GroupSummary(Long id, String name, long memberCount) {
    }

    private final UserGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final PermissionEntryRepository entryRepository;
    private final PermissionAuxEntryRepository auxEntryRepository;
    private final AppUserRepository userRepository;
    private final EffectivePermissionResolver resolver;
    private final AuditEventPublisher auditEventPublisher;

    public GroupService(
            UserGroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            PermissionEntryRepository entryRepository,
            PermissionAuxEntryRepository auxEntryRepository,
            AppUserRepository userRepository,
            EffectivePermissionResolver resolver,
            AuditEventPublisher auditEventPublisher) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.entryRepository = entryRepository;
        this.auxEntryRepository = auxEntryRepository;
        this.userRepository = userRepository;
        this.resolver = resolver;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> list() {
        return groupRepository.findAllByOrderByName().stream()
                .map(group -> new GroupSummary(group.getId(), group.getName(),
                        memberRepository.countByGroupId(group.getId())))
                .toList();
    }

    @Transactional
    public UserGroup create(String name, String actor) {
        requireNameAvailable(name, null);
        UserGroup group = new UserGroup();
        group.setName(name);
        groupRepository.save(group);
        auditEventPublisher.publish(AuditEvent.success(
                AuditEvents.GROUP_CREATED, actor,
                Map.of("groupId", group.getId(), "name", name)));
        return group;
    }

    @Transactional
    public void rename(Long groupId, String newName, String actor) {
        UserGroup group = require(groupId);
        if (group.getName().equals(newName)) {
            return;
        }
        requireNameAvailable(newName, groupId);
        String oldName = group.getName();
        group.setName(newName);
        auditEventPublisher.publish(AuditEvent.success(
                AuditEvents.GROUP_RENAMED, actor,
                Map.of("groupId", groupId, "oldName", oldName, "newName", newName)));
    }

    /** 削除: 所属は FK CASCADE、権限エントリ(全接続)は同一トランザクションで削除(US-018)。 */
    @Transactional
    public void delete(Long groupId, String actor) {
        UserGroup group = require(groupId);
        String name = group.getName();
        entryRepository.deleteByPrincipalTypeAndPrincipalId(PrincipalType.GROUP, groupId);
        auxEntryRepository.deleteByPrincipalTypeAndPrincipalId(PrincipalType.GROUP, groupId);
        groupRepository.delete(group);
        resolver.invalidateAll();
        auditEventPublisher.publish(AuditEvent.success(
                AuditEvents.GROUP_DELETED, actor, Map.of("groupId", groupId, "name", name)));
    }

    @Transactional(readOnly = true)
    public List<AppUser> members(Long groupId) {
        require(groupId);
        List<Long> userIds = memberRepository.findByGroupId(groupId).stream()
                .map(GroupMember::getUserId)
                .toList();
        return userRepository.findAllById(userIds).stream()
                .sorted(java.util.Comparator.comparing(AppUser::getEmail))
                .toList();
    }

    /** メンバー追加(冪等 — 既所属なら成功扱い)。 */
    @Transactional
    public void addMember(Long groupId, Long userId, String actor) {
        require(groupId);
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("PRINCIPAL_NOT_FOUND");
        }
        if (memberRepository.findByGroupIdAndUserId(groupId, userId).isPresent()) {
            return;
        }
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        memberRepository.save(member);
        resolver.invalidateAll();
        auditEventPublisher.publish(AuditEvent.success(
                AuditEvents.GROUP_MEMBER_ADDED, actor,
                Map.of("groupId", groupId, "userId", userId)));
    }

    /** メンバー削除(冪等)。 */
    @Transactional
    public void removeMember(Long groupId, Long userId, String actor) {
        require(groupId);
        memberRepository.findByGroupIdAndUserId(groupId, userId).ifPresent(member -> {
            memberRepository.delete(member);
            resolver.invalidateAll();
            auditEventPublisher.publish(AuditEvent.success(
                    AuditEvents.GROUP_MEMBER_REMOVED, actor,
                    Map.of("groupId", groupId, "userId", userId)));
        });
    }

    private void requireNameAvailable(String name, Long selfId) {
        groupRepository.findByName(name).ifPresent(existing -> {
            if (selfId == null || !existing.getId().equals(selfId)) {
                throw new ConflictException("GROUP_NAME_DUPLICATE");
            }
        });
    }

    private UserGroup require(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("GROUP_NOT_FOUND"));
    }
}

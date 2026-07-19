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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * グループ管理 API(ADMIN — US-018/019)。
 */
@RestController
@RequestMapping("/api/admin/groups")
public class GroupController {

    public record GroupRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record GroupResponse(Long id, String name, long memberCount) {
    }

    public record GroupMemberResponse(Long userId, String email, String displayName) {
    }

    public record GroupMemberAddRequest(@NotNull Long userId) {
    }

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public List<GroupResponse> list() {
        return groupService.list().stream()
                .map(summary -> new GroupResponse(
                        summary.id(), summary.name(), summary.memberCount()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(
            @Valid @RequestBody GroupRequest body, @AuthenticationPrincipal Jwt jwt) {
        UserGroup group = groupService.create(body.name(), actor(jwt));
        return new GroupResponse(group.getId(), group.getName(), 0);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(
            @PathVariable Long id,
            @Valid @RequestBody GroupRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        groupService.rename(id, body.name(), actor(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        groupService.delete(id, actor(jwt));
    }

    @GetMapping("/{id}/members")
    public List<GroupMemberResponse> members(@PathVariable Long id) {
        return groupService.members(id).stream()
                .map(user -> new GroupMemberResponse(
                        user.getId(), user.getEmail(), user.getDisplayName()))
                .toList();
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(
            @PathVariable Long id,
            @Valid @RequestBody GroupMemberAddRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        groupService.addMember(id, body.userId(), actor(jwt));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {
        groupService.removeMember(id, userId, actor(jwt));
    }

    private static String actor(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}

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
package cherry.mastermeister.user;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ユーザ管理 API(ADMIN — SecurityConfig の /api/admin/** 認可)。
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    public record UserSummary(
            Long id,
            String email,
            String displayName,
            String role,
            String status,
            String language,
            LocalDateTime lockedUntil,
            int failedLoginCount,
            LocalDateTime createdAt) {

        static UserSummary of(AppUser user) {
            return new UserSummary(
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getRole().name(),
                    user.getStatus().name(),
                    user.getLanguage(),
                    user.getLockedUntil(),
                    user.getFailedLoginCount(),
                    user.getCreatedAt());
        }
    }

    public record UserListResponse(
            List<UserSummary> items, int page, int size, long totalElements, int totalPages) {
    }

    public record UserActionResponse(UserSummary user, boolean mailSent) {
    }

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserListResponse list(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AppUser> result = userService.search(status, q,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("id")));
        return new UserListResponse(
                result.getContent().stream().map(UserSummary::of).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @PostMapping("/{id}/approve")
    public UserActionResponse approve(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        UserService.UserActionResult result = userService.approve(id, adminEmail(jwt));
        return new UserActionResponse(UserSummary.of(result.user()), result.mailSent());
    }

    @PostMapping("/{id}/reject")
    public UserActionResponse reject(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        UserService.UserActionResult result = userService.reject(id, adminEmail(jwt));
        return new UserActionResponse(UserSummary.of(result.user()), result.mailSent());
    }

    @PostMapping("/{id}/unlock")
    public UserSummary unlock(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return UserSummary.of(userService.unlock(id, adminEmail(jwt)));
    }

    private static String adminEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}

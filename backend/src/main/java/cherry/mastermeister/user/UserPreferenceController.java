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

import cherry.mastermeister.common.web.UnauthorizedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ログインユーザ自身の情報と設定(US-047/048、Q3=A)。
 * language / theme はサーバ保存が正 — ログイン後はこの値が localStorage より優先される。
 */
@RestController
@RequestMapping("/api/me")
public class UserPreferenceController {

    public record MeResponse(
            String email, String displayName, String role, String language, String theme) {

        static MeResponse of(AppUser user) {
            return new MeResponse(
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getRole().name(),
                    user.getLanguage(),
                    user.getTheme());
        }
    }

    public record PreferencesBody(
            @NotBlank @Pattern(regexp = "ja|en") String language,
            @NotBlank @Pattern(regexp = "light|dark|system") String theme) {
    }

    private final AppUserRepository userRepository;

    public UserPreferenceController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MeResponse.of(currentUser(jwt));
    }

    @PutMapping("/preferences")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void updatePreferences(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesBody body) {
        AppUser user = currentUser(jwt);
        user.setLanguage(body.language());
        user.setTheme(body.theme());
        userRepository.save(user);
    }

    private AppUser currentUser(Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("AUTH_INVALID_TOKEN"));
    }
}

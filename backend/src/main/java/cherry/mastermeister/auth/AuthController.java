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
package cherry.mastermeister.auth;

import cherry.mastermeister.user.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証 API(公開)。business-logic-model.md §1。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record TokenRequest(@NotBlank String refreshToken) {
    }

    public record UserPayload(
            String email, String displayName, String role, String language, String theme) {

        static UserPayload of(AppUser user) {
            return new UserPayload(
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getRole().name(),
                    user.getLanguage(),
                    user.getTheme());
        }
    }

    public record LoginResponse(String accessToken, String refreshToken, UserPayload user) {
    }

    public record RefreshResponse(String accessToken, String refreshToken) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthTokens tokens = authService.login(request.email(), request.password());
        return new LoginResponse(
                tokens.accessToken(), tokens.refreshToken(), UserPayload.of(tokens.user()));
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody TokenRequest request) {
        AuthService.AuthTokens tokens = authService.refresh(request.refreshToken());
        return new RefreshResponse(tokens.accessToken(), tokens.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody TokenRequest request) {
        authService.logout(request.refreshToken());
    }
}

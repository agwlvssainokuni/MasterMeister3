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

import cherry.mastermeister.audit.AuditEvent;
import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.AuditEvents;
import cherry.mastermeister.common.web.AccountLockedException;
import cherry.mastermeister.common.web.UnauthorizedException;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import cherry.mastermeister.user.UserStatus;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * ログイン・リフレッシュ・ログアウト(US-005/007/008/009)。
 * ログイン判定順序は business-rules.md §2(ユーザ検索 → ロック → パスワード → status)。
 * 失敗応答は AUTH_INVALID_CREDENTIALS に統一(原因を特定させない)。ロック中のみ 423。
 * 意図的に非トランザクション — 失敗カウント等の更新(各サービス内の独立トランザクション)を
 * 失敗応答時のロールバックに巻き込ませない。
 */
@Service
public class AuthService {

    public record AuthTokens(String accessToken, String refreshToken, AppUser user) {
    }

    private static final String CODE_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
    private static final String CODE_INVALID_TOKEN = "AUTH_INVALID_TOKEN";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginAttemptService loginAttemptService;
    private final AuditEventPublisher auditEventPublisher;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            RefreshTokenStore refreshTokenStore,
            LoginAttemptService loginAttemptService,
            AuditEventPublisher auditEventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.loginAttemptService = loginAttemptService;
        this.auditEventPublisher = auditEventPublisher;
    }

    public AuthTokens login(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        AppUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            auditEventPublisher.publish(AuditEvent.failure(
                    AuditEvents.LOGIN_FAILED, email, Map.of("reason", "BAD_CREDENTIALS")));
            throw new UnauthorizedException(CODE_INVALID_CREDENTIALS);
        }
        if (loginAttemptService.isLocked(user)) {
            // ロック中は正しいパスワードでも拒否(D-11)
            auditEventPublisher.publish(AuditEvent.failure(
                    AuditEvents.LOGIN_FAILED, email, Map.of("reason", "LOCKED")));
            throw new AccountLockedException();
        }
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.onFailure(user.getId());
            auditEventPublisher.publish(AuditEvent.failure(
                    AuditEvents.LOGIN_FAILED, email, Map.of("reason", "BAD_CREDENTIALS")));
            throw new UnauthorizedException(CODE_INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditEventPublisher.publish(AuditEvent.failure(
                    AuditEvents.LOGIN_FAILED, email, Map.of("reason", "NOT_ACTIVE")));
            throw new UnauthorizedException(CODE_INVALID_CREDENTIALS);
        }
        loginAttemptService.onSuccess(user.getId());
        AuthTokens tokens = issueTokens(user, UUID.randomUUID().toString());
        auditEventPublisher.publish(AuditEvent.success(AuditEvents.LOGIN_SUCCEEDED, email));
        return tokens;
    }

    public AuthTokens refresh(String refreshTokenPlain) {
        String tokenHash = tokenService.hash(refreshTokenPlain);
        RefreshTokenStore.RotationResult result = refreshTokenStore.rotate(tokenHash);
        return switch (result.status()) {
            case ROTATED -> {
                RefreshToken current = result.token();
                AppUser user = userRepository.findById(current.getUserId()).orElse(null);
                if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                    refreshTokenStore.revokeFamily(current.getFamilyId());
                    throw new UnauthorizedException(CODE_INVALID_TOKEN);
                }
                AuthTokens tokens = issueTokens(user, current.getFamilyId());
                auditEventPublisher.publish(AuditEvent.success(
                        AuditEvents.TOKEN_REFRESHED, user.getEmail()));
                yield tokens;
            }
            case REUSE_DETECTED -> {
                String actor = userRepository.findById(result.token().getUserId())
                        .map(AppUser::getEmail)
                        .orElse(String.valueOf(result.token().getUserId()));
                auditEventPublisher.publish(AuditEvent.failure(
                        AuditEvents.TOKEN_REUSE_DETECTED, actor,
                        Map.of("familyId", result.token().getFamilyId())));
                throw new UnauthorizedException(CODE_INVALID_TOKEN);
            }
            case INVALID -> throw new UnauthorizedException(CODE_INVALID_TOKEN);
        };
    }

    public void logout(String refreshTokenPlain) {
        String tokenHash = tokenService.hash(refreshTokenPlain);
        refreshTokenStore.revokeFamilyOf(tokenHash).ifPresent(token -> {
            String actor = userRepository.findById(token.getUserId())
                    .map(AppUser::getEmail)
                    .orElse(String.valueOf(token.getUserId()));
            auditEventPublisher.publish(AuditEvent.success(AuditEvents.LOGOUT, actor));
        });
        // トークン不正でも 204(冪等 — US-009)
    }

    private AuthTokens issueTokens(AppUser user, String familyId) {
        String accessToken = tokenService.issueAccessToken(user);
        String refreshTokenPlain = tokenService.generateRefreshToken();
        refreshTokenStore.issue(user.getId(), familyId, tokenService.hash(refreshTokenPlain));
        return new AuthTokens(accessToken, refreshTokenPlain, user);
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

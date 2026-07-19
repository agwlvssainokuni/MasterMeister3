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

import cherry.mastermeister.audit.AuditEvent;
import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.AuditEvents;
import cherry.mastermeister.common.web.BadRequestException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザ登録の申請と完了(US-001/002)。
 * 申請はメールアドレスの登録状況にかかわらず応答が変わらない(列挙対策 — 202 固定)。
 * 完了失敗の理由は「無効または期限切れ」に統一する(トークン推測への情報を与えない)。
 */
@Service
public class RegistrationService {

    private static final String CODE_TOKEN_INVALID = "REGISTRATION_TOKEN_INVALID";

    private static final Logger logger = LoggerFactory.getLogger(RegistrationService.class);

    private final AppUserRepository userRepository;
    private final RegistrationTokenStore tokenStore;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectProvider<UserNotificationGateway> notificationGateway;
    private final Clock clock;

    public RegistrationService(
            AppUserRepository userRepository,
            RegistrationTokenStore tokenStore,
            PasswordEncoder passwordEncoder,
            AuditEventPublisher auditEventPublisher,
            ObjectProvider<UserNotificationGateway> notificationGateway,
            Clock clock) {
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
        this.passwordEncoder = passwordEncoder;
        this.auditEventPublisher = auditEventPublisher;
        this.notificationGateway = notificationGateway;
        this.clock = clock;
    }

    /**
     * 登録申請。呼び出し側は結果によらず常に 202 を返すこと(列挙対策)。
     * メール送信失敗でも応答は変わらない(Q4=A — 失敗時の監査はゲートウェイ実装側)。
     */
    public void request(String rawEmail, String language) {
        String email = normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            // 応答では区別しないが証跡は残す(business-rules.md §1)
            auditEventPublisher.publish(AuditEvent.failure(
                    AuditEvents.REGISTRATION_REQUESTED, email,
                    Map.of("alreadyRegistered", true)));
            return;
        }
        String token = tokenStore.issue(email, language);
        UserNotificationGateway gateway = notificationGateway.getIfAvailable();
        if (gateway == null) {
            logger.warn("No UserNotificationGateway available; confirmation mail not sent: {}", email);
        } else {
            gateway.sendRegistrationConfirm(email, language, token);
        }
        auditEventPublisher.publish(AuditEvent.success(
                AuditEvents.REGISTRATION_REQUESTED, email,
                Map.of("alreadyRegistered", false)));
    }

    /**
     * 登録完了(パスワード設定)。検証順序: 存在 → 未使用 → 期限内。
     * 競合(既にユーザ行が存在)時はトークンを使用済み化した上で「無効」応答。
     */
    @Transactional
    public void complete(String tokenPlain, String password, String displayName) {
        RegistrationToken token = tokenStore.findByPlainToken(tokenPlain)
                .orElseThrow(() -> new BadRequestException(CODE_TOKEN_INVALID));
        if (!tokenStore.isValid(token)) {
            throw new BadRequestException(CODE_TOKEN_INVALID);
        }
        if (userRepository.existsByEmail(token.getEmail())) {
            // REQUIRES_NEW — 400 応答(本トランザクションのロールバック)でも使用済み化は残す
            tokenStore.markUsed(token.getId());
            throw new BadRequestException(CODE_TOKEN_INVALID);
        }
        AppUser user = new AppUser();
        user.setEmail(token.getEmail());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? null : displayName.trim());
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.PENDING_APPROVAL);
        user.setLanguage(token.getLanguage());
        user.setTheme("system");
        userRepository.save(user);
        token.setUsedAt(LocalDateTime.now(clock));
        auditEventPublisher.publish(AuditEvent.success(
                AuditEvents.REGISTRATION_COMPLETED, token.getEmail()));
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

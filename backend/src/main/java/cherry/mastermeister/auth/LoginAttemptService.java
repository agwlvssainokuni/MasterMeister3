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
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 連続ログイン失敗のカウントと時限ロック(US-006、D-11)。
 * 各メソッドは独立したトランザクション — ログイン失敗時にもカウント更新は必ずコミットされる。
 */
@Service
public class LoginAttemptService {

    private final AppUserRepository userRepository;
    private final AppProperties properties;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    public LoginAttemptService(
            AppUserRepository userRepository,
            AppProperties properties,
            AuditEventPublisher auditEventPublisher,
            Clock clock) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void onFailure(Long userId) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        int count = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(count);
        if (count >= properties.auth().lockout().threshold()) {
            LocalDateTime until = LocalDateTime.now(clock).plus(properties.auth().lockout().duration());
            user.setLockedUntil(until);
            auditEventPublisher.publish(AuditEvent.success(
                    AuditEvents.ACCOUNT_LOCKED, user.getEmail(),
                    Map.of("until", until.toString())));
        }
    }

    @Transactional
    public void onSuccess(Long userId) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        user.setFailedLoginCount(0);
    }

    public boolean isLocked(AppUser user) {
        return user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now(clock));
    }
}

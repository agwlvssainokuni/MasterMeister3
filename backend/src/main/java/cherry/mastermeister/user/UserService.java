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
import cherry.mastermeister.common.web.ConflictException;
import cherry.mastermeister.common.web.NotFoundException;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザ管理(US-003 承認/却下・US-006 ロック解除・一覧)。
 * 承認・却下は PENDING_APPROVAL のみ対象(それ以外は 409)。
 * 通知メールの失敗は操作を失敗させず mailSent=false で応答に伝える(Q4=A)。
 */
@Service
public class UserService {

    /** 操作結果。mailSent=false は画面の警告 Toast 用(business-rules.md §6)。 */
    public record UserActionResult(AppUser user, boolean mailSent) {
    }

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository userRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectProvider<UserNotificationGateway> notificationGateway;

    public UserService(
            AppUserRepository userRepository,
            AuditEventPublisher auditEventPublisher,
            ObjectProvider<UserNotificationGateway> notificationGateway) {
        this.userRepository = userRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.notificationGateway = notificationGateway;
    }

    @Transactional(readOnly = true)
    public Page<AppUser> search(UserStatus status, String keyword, Pageable pageable) {
        String normalized = keyword == null || keyword.isBlank()
                ? null
                : keyword.trim().toLowerCase(Locale.ROOT);
        return userRepository.search(status, normalized, pageable);
    }

    @Transactional
    public UserActionResult approve(Long userId, String adminEmail) {
        AppUser user = requirePending(userId);
        user.setStatus(UserStatus.ACTIVE);
        auditEventPublisher.publish(AuditEvent.success(
                AuditEvents.USER_APPROVED, adminEmail, Map.of("targetUserId", userId)));
        boolean mailSent = sendMail(gateway -> gateway.sendUserApproved(user));
        return new UserActionResult(user, mailSent);
    }

    @Transactional
    public UserActionResult reject(Long userId, String adminEmail) {
        AppUser user = requirePending(userId);
        user.setStatus(UserStatus.REJECTED);
        auditEventPublisher.publish(AuditEvent.success(
                AuditEvents.USER_REJECTED, adminEmail, Map.of("targetUserId", userId)));
        boolean mailSent = sendMail(gateway -> gateway.sendUserRejected(user));
        return new UserActionResult(user, mailSent);
    }

    @Transactional
    public AppUser unlock(Long userId, String adminEmail) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND"));
        user.setLockedUntil(null);
        user.setFailedLoginCount(0);
        auditEventPublisher.publish(AuditEvent.success(
                AuditEvents.ACCOUNT_UNLOCKED, adminEmail, Map.of("targetUserId", userId)));
        return user;
    }

    private AppUser requirePending(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND"));
        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new ConflictException("USER_NOT_PENDING");
        }
        return user;
    }

    private boolean sendMail(java.util.function.Predicate<UserNotificationGateway> sender) {
        UserNotificationGateway gateway = notificationGateway.getIfAvailable();
        if (gateway == null) {
            logger.warn("No UserNotificationGateway available; notification mail not sent");
            return false;
        }
        return sender.test(gateway);
    }
}

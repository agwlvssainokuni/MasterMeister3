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
package cherry.mastermeister.audit;

import cherry.mastermeister.common.config.AppProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * セキュリティアラート判定(US-045、Q2=A)。
 * 対象イベント(LOGIN_FAILED / TOKEN_REUSE_DETECTED)の記録後に評価される。
 * 時間窓内の件数が閾値以上なら管理者へ通知し、クールダウン(直近通知から window 経過まで)で
 * 再通知を抑止する。TOKEN_REUSE_DETECTED は件数によらず即時通知(重大イベント)。
 */
@Service
public class SecurityAlertService {

    public static final String ALERT_LOGIN_FAILURE_BURST = "LOGIN_FAILURE_BURST";
    public static final String ALERT_TOKEN_REUSE = "TOKEN_REUSE_DETECTED";

    private static final Set<String> TARGET_EVENTS =
            Set.of(AuditEvents.LOGIN_FAILED, AuditEvents.TOKEN_REUSE_DETECTED);

    private static final Logger logger = LoggerFactory.getLogger(SecurityAlertService.class);

    private final AuditLogRepository auditLogRepository;
    private final SecurityAlertStateRepository stateRepository;
    private final AuditLogService auditLogService;
    private final AppProperties properties;
    private final Clock clock;
    private final ObjectProvider<SecurityAlertNotifier> notifierProvider;

    public SecurityAlertService(
            AuditLogRepository auditLogRepository,
            SecurityAlertStateRepository stateRepository,
            AuditLogService auditLogService,
            AppProperties properties,
            Clock clock,
            ObjectProvider<SecurityAlertNotifier> notifierProvider) {
        this.auditLogRepository = auditLogRepository;
        this.stateRepository = stateRepository;
        this.auditLogService = auditLogService;
        this.properties = properties;
        this.clock = clock;
        this.notifierProvider = notifierProvider;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluate(AuditEvent event) {
        if (!TARGET_EVENTS.contains(event.eventType())) {
            return;
        }
        if (AuditEvents.TOKEN_REUSE_DETECTED.equals(event.eventType())) {
            notifyAdmins(ALERT_TOKEN_REUSE, 1);
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime windowStart = now.minus(properties.securityAlert().window());
        long count = auditLogRepository.countByEventTypeInAndOccurredAtAfter(TARGET_EVENTS, windowStart);
        if (count < properties.securityAlert().threshold()) {
            return;
        }
        SecurityAlertState state = stateRepository.findByAlertType(ALERT_LOGIN_FAILURE_BURST).orElse(null);
        if (state != null && state.getLastNotifiedAt().isAfter(windowStart)) {
            return; // クールダウン中
        }
        if (!notifyAdmins(ALERT_LOGIN_FAILURE_BURST, count)) {
            return; // 未通知(通知手段なし)ならクールダウンも開始しない
        }
        if (state == null) {
            state = new SecurityAlertState();
            state.setAlertType(ALERT_LOGIN_FAILURE_BURST);
        }
        state.setLastNotifiedAt(now);
        stateRepository.save(state);
    }

    private boolean notifyAdmins(String alertType, long count) {
        SecurityAlertNotifier notifier = notifierProvider.getIfAvailable();
        if (notifier == null) {
            logger.warn("No SecurityAlertNotifier available; alert not sent: {} (count={})", alertType, count);
            return false;
        }
        notifier.sendAlert(alertType, count);
        auditLogService.record(AuditEvent.success(
                AuditEvents.SECURITY_ALERT_SENT, null,
                Map.of("alertType", alertType, "count", count)));
        return true;
    }
}

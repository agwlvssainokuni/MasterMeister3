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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastermeister.common.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * セキュリティアラート判定(閾値・クールダウン・即時通知)の単体テスト(US-045)。
 */
class SecurityAlertServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final Instant NOW = Instant.parse("2026-07-19T03:00:00Z");

    private AuditLogRepository auditLogRepository;
    private SecurityAlertStateRepository stateRepository;
    private AuditLogService auditLogService;
    private SecurityAlertNotifier notifier;
    private SecurityAlertService service;

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZONE);
    }

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        stateRepository = mock(SecurityAlertStateRepository.class);
        auditLogService = mock(AuditLogService.class);
        notifier = mock(SecurityAlertNotifier.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SecurityAlertNotifier> notifierProvider = mock(ObjectProvider.class);
        when(notifierProvider.getIfAvailable()).thenReturn(notifier);

        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef",
                        Duration.ofMinutes(10), Duration.ofHours(24)),
                new AppProperties.UserRegistration(Duration.ofHours(3)),
                new AppProperties.Auth(new AppProperties.Auth.Lockout(5, Duration.ofMinutes(15))),
                new AppProperties.SecurityAlert(Duration.ofMinutes(10), 10),
                new AppProperties.Admin(new AppProperties.Admin.Bootstrap(null, null)),
                new AppProperties.Mail("noreply@mastermeister.local", "http://localhost:8080"));

        service = new SecurityAlertService(
                auditLogRepository, stateRepository, auditLogService, properties,
                Clock.fixed(NOW, ZONE), notifierProvider);
    }

    @Test
    void 対象外イベントは何もしない() {
        service.evaluate(AuditEvent.success(AuditEvents.LOGIN_SUCCEEDED, "user@example.com"));

        verifyNoInteractions(auditLogRepository, stateRepository, notifier);
    }

    @Test
    void 閾値未満なら通知しない() {
        when(auditLogRepository.countByEventTypeInAndOccurredAtAfter(anyCollection(), any()))
                .thenReturn(9L);

        service.evaluate(AuditEvent.failure(AuditEvents.LOGIN_FAILED, "user@example.com",
                Map.of("reason", "BAD_CREDENTIALS")));

        verifyNoInteractions(notifier);
        verify(stateRepository, never()).save(any());
    }

    @Test
    void 閾値到達で通知しクールダウン状態を保存する() {
        when(auditLogRepository.countByEventTypeInAndOccurredAtAfter(anyCollection(), any()))
                .thenReturn(10L);
        when(stateRepository.findByAlertType(SecurityAlertService.ALERT_LOGIN_FAILURE_BURST))
                .thenReturn(Optional.empty());

        service.evaluate(AuditEvent.failure(AuditEvents.LOGIN_FAILED, "user@example.com",
                Map.of("reason", "BAD_CREDENTIALS")));

        verify(notifier).sendAlert(SecurityAlertService.ALERT_LOGIN_FAILURE_BURST, 10L);
        verify(auditLogService).record(any(AuditEvent.class));
        verify(stateRepository).save(any(SecurityAlertState.class));
    }

    @Test
    void クールダウン中は再通知しない() {
        when(auditLogRepository.countByEventTypeInAndOccurredAtAfter(anyCollection(), any()))
                .thenReturn(15L);
        SecurityAlertState state = new SecurityAlertState();
        state.setAlertType(SecurityAlertService.ALERT_LOGIN_FAILURE_BURST);
        state.setLastNotifiedAt(now().minusMinutes(5)); // 窓(10 分)内に通知済み
        when(stateRepository.findByAlertType(SecurityAlertService.ALERT_LOGIN_FAILURE_BURST))
                .thenReturn(Optional.of(state));

        service.evaluate(AuditEvent.failure(AuditEvents.LOGIN_FAILED, "user@example.com",
                Map.of("reason", "BAD_CREDENTIALS")));

        verifyNoInteractions(notifier);
        verify(stateRepository, never()).save(any());
    }

    @Test
    void クールダウン経過後は再通知する() {
        when(auditLogRepository.countByEventTypeInAndOccurredAtAfter(anyCollection(), any()))
                .thenReturn(12L);
        SecurityAlertState state = new SecurityAlertState();
        state.setAlertType(SecurityAlertService.ALERT_LOGIN_FAILURE_BURST);
        state.setLastNotifiedAt(now().minusMinutes(11)); // 窓(10 分)より前
        when(stateRepository.findByAlertType(SecurityAlertService.ALERT_LOGIN_FAILURE_BURST))
                .thenReturn(Optional.of(state));

        service.evaluate(AuditEvent.failure(AuditEvents.LOGIN_FAILED, "user@example.com",
                Map.of("reason", "BAD_CREDENTIALS")));

        verify(notifier).sendAlert(SecurityAlertService.ALERT_LOGIN_FAILURE_BURST, 12L);
        verify(stateRepository).save(state);
    }

    @Test
    void トークン再利用検知は件数によらず即時通知する() {
        service.evaluate(AuditEvent.failure(AuditEvents.TOKEN_REUSE_DETECTED, "user@example.com",
                Map.of("familyId", "f-1")));

        verify(notifier).sendAlert(SecurityAlertService.ALERT_TOKEN_REUSE, 1L);
        verify(auditLogRepository, never()).countByEventTypeInAndOccurredAtAfter(anyCollection(), any());
    }

    @Test
    void 通知時にSECURITY_ALERT_SENTを記録する() {
        service.evaluate(AuditEvent.failure(AuditEvents.TOKEN_REUSE_DETECTED, "user@example.com",
                Map.of("familyId", "f-1")));

        verify(auditLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditEvents.SECURITY_ALERT_SENT.equals(event.eventType())
                        && event.detail().get("alertType").equals(SecurityAlertService.ALERT_TOKEN_REUSE)));
    }

    @Test
    void 通知手段がなければ通知記録もクールダウンもしない() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SecurityAlertNotifier> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef",
                        Duration.ofMinutes(10), Duration.ofHours(24)),
                new AppProperties.UserRegistration(Duration.ofHours(3)),
                new AppProperties.Auth(new AppProperties.Auth.Lockout(5, Duration.ofMinutes(15))),
                new AppProperties.SecurityAlert(Duration.ofMinutes(10), 10),
                new AppProperties.Admin(new AppProperties.Admin.Bootstrap(null, null)),
                new AppProperties.Mail("noreply@mastermeister.local", "http://localhost:8080"));
        SecurityAlertService detached = new SecurityAlertService(
                auditLogRepository, stateRepository, auditLogService, properties,
                Clock.fixed(NOW, ZONE), emptyProvider);
        when(auditLogRepository.countByEventTypeInAndOccurredAtAfter(anyCollection(), any()))
                .thenReturn(10L);
        when(stateRepository.findByAlertType(SecurityAlertService.ALERT_LOGIN_FAILURE_BURST))
                .thenReturn(Optional.empty());

        detached.evaluate(AuditEvent.failure(AuditEvents.LOGIN_FAILED, "user@example.com",
                Map.of("reason", "BAD_CREDENTIALS")));

        verify(auditLogService, never()).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditEvents.SECURITY_ALERT_SENT.equals(event.eventType())));
        verify(stateRepository, never()).save(any());
        verify(stateRepository).findByAlertType(eq(SecurityAlertService.ALERT_LOGIN_FAILURE_BURST));
    }
}

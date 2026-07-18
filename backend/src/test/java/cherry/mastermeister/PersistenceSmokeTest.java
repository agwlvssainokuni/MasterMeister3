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
package cherry.mastermeister;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastermeister.audit.AuditLog;
import cherry.mastermeister.audit.AuditLogRepository;
import cherry.mastermeister.audit.SecurityAlertState;
import cherry.mastermeister.audit.SecurityAlertStateRepository;
import cherry.mastermeister.auth.RefreshToken;
import cherry.mastermeister.auth.RefreshTokenRepository;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import cherry.mastermeister.user.RegistrationToken;
import cherry.mastermeister.user.RegistrationTokenRepository;
import cherry.mastermeister.user.UserRole;
import cherry.mastermeister.user.UserStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway V2 適用とエンティティマッピングの整合確認。
 * 5 テーブルが作成され、各エンティティが保存・取得できることを確認する。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenceSmokeTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RegistrationTokenRepository registrationTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SecurityAlertStateRepository securityAlertStateRepository;

    @Test
    void FlywayV2で5テーブルが作成される() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME IN ('APP_USER', 'REGISTRATION_TOKEN', 'REFRESH_TOKEN',
                                     'AUDIT_LOG', 'SECURITY_ALERT_STATE')
                """,
                Integer.class);
        assertThat(count).isEqualTo(5);
    }

    @Test
    void 各エンティティを保存して取得できる() {
        AppUser user = new AppUser();
        user.setEmail("smoke@example.com");
        user.setPasswordHash("$2a$10$dummy");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setLanguage("ja");
        user.setTheme("system");
        appUserRepository.saveAndFlush(user);
        assertThat(appUserRepository.findByEmail("smoke@example.com"))
                .hasValueSatisfying(found -> {
                    assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
                    assertThat(found.getCreatedAt()).isNotNull();
                });

        RegistrationToken registrationToken = new RegistrationToken();
        registrationToken.setTokenHash("a".repeat(64));
        registrationToken.setEmail("smoke@example.com");
        registrationToken.setLanguage("ja");
        registrationToken.setExpiresAt(LocalDateTime.now().plusHours(3));
        registrationTokenRepository.saveAndFlush(registrationToken);
        assertThat(registrationTokenRepository.findByTokenHash("a".repeat(64))).isPresent();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash("b".repeat(64));
        refreshToken.setUserId(user.getId());
        refreshToken.setFamilyId("11111111-2222-3333-4444-555555555555");
        refreshToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        refreshTokenRepository.saveAndFlush(refreshToken);
        assertThat(refreshTokenRepository.findByTokenHash("b".repeat(64)))
                .hasValueSatisfying(found ->
                        assertThat(found.getUserId()).isEqualTo(user.getId()));

        AuditLog auditLog = new AuditLog();
        auditLog.setOccurredAt(LocalDateTime.now());
        auditLog.setEventType("LOGIN_SUCCEEDED");
        auditLog.setActor("smoke@example.com");
        auditLog.setOutcome("SUCCESS");
        auditLog.setDetailJson("{}");
        auditLogRepository.saveAndFlush(auditLog);
        assertThat(auditLogRepository.countByEventTypeInAndOccurredAtAfter(
                java.util.List.of("LOGIN_SUCCEEDED"), LocalDateTime.now().minusMinutes(10)))
                .isEqualTo(1);

        SecurityAlertState alertState = new SecurityAlertState();
        alertState.setAlertType("LOGIN_FAILURE_BURST");
        alertState.setLastNotifiedAt(LocalDateTime.now());
        securityAlertStateRepository.saveAndFlush(alertState);
        assertThat(securityAlertStateRepository.findByAlertType("LOGIN_FAILURE_BURST")).isPresent();
    }
}

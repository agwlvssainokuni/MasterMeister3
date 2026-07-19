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
package cherry.mastermeister.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cherry.mastermeister.audit.AuditEvent;
import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.AuditEvents;
import cherry.mastermeister.audit.AuditLogRepository;
import cherry.mastermeister.auth.RefreshTokenRepository;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.AppUserRepository;
import cherry.mastermeister.user.RegistrationTokenRepository;
import cherry.mastermeister.user.UserRole;
import cherry.mastermeister.user.UserStatus;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * メール送信の結合テスト(Q4=A、NFR-U3-05)。
 * JavaMailSender をモックし、言語選択・件名・本文・送信失敗時の応答不変と監査記録を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MailIntegrationTest {

    private static final String PASSWORD = "mail-test-password-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RegistrationTokenRepository registrationTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditEventPublisher auditEventPublisher;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void stubMimeMessage() {
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @AfterEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        registrationTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    private AppUser createUser(String email, UserRole role, UserStatus status, String language) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(status);
        user.setLanguage(language);
        user.setTheme("system");
        return userRepository.save(user);
    }

    private String loginAs(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asString();
    }

    @Test
    void 登録申請で申請時言語の確認メールが送られる() throws Exception {
        mockMvc.perform(post("/api/registration/request")
                        .contentType("application/json")
                        .content("{\"email\":\"newuser@example.com\",\"language\":\"ja\"}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("[MasterMeister] ユーザ登録のご案内");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("newuser@example.com");
        String body = (String) message.getContent();
        assertThat(body).contains("/register/complete?token=");
    }

    @Test
    void 承認メールは対象ユーザの言語で送られる() throws Exception {
        createUser("admin@example.com", UserRole.ADMIN, UserStatus.ACTIVE, "ja");
        AppUser pending = createUser(
                "pending@example.com", UserRole.USER, UserStatus.PENDING_APPROVAL, "en");
        String adminToken = loginAs("admin@example.com");

        mockMvc.perform(post("/api/admin/users/{id}/approve", pending.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mailSent").value(true));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("[MasterMeister] Your account has been approved");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("pending@example.com");
    }

    @Test
    void 送信失敗でも承認は成立しmailSentフラグと監査で伝える() throws Exception {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        createUser("admin@example.com", UserRole.ADMIN, UserStatus.ACTIVE, "ja");
        AppUser pending = createUser(
                "pending@example.com", UserRole.USER, UserStatus.PENDING_APPROVAL, "ja");
        String adminToken = loginAs("admin@example.com");

        mockMvc.perform(post("/api/admin/users/{id}/approve", pending.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mailSent").value(false));

        assertThat(userRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "MAIL_SEND_FAILED".equals(entry.getEventType())
                        && entry.getDetailJson().contains("user-approved"));
    }

    @Test
    void 送信失敗でも登録申請の応答は変わらない() throws Exception {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        mockMvc.perform(post("/api/registration/request")
                        .contentType("application/json")
                        .content("{\"email\":\"newuser@example.com\",\"language\":\"ja\"}"))
                .andExpect(status().isAccepted());

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "MAIL_SEND_FAILED".equals(entry.getEventType()));
    }

    @Test
    void トークン再利用検知で管理者全員にアラートメールが送られる() {
        createUser("admin1@example.com", UserRole.ADMIN, UserStatus.ACTIVE, "ja");
        createUser("admin2@example.com", UserRole.ADMIN, UserStatus.ACTIVE, "en");

        auditEventPublisher.publish(AuditEvent.failure(
                AuditEvents.TOKEN_REUSE_DETECTED, "victim@example.com",
                Map.of("familyId", "f-1")));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, org.mockito.Mockito.times(2)).send(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(message -> message.getSubject())
                .containsExactlyInAnyOrder(
                        "[MasterMeister] セキュリティアラート",
                        "[MasterMeister] Security alert");
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "SECURITY_ALERT_SENT".equals(entry.getEventType()));
    }
}

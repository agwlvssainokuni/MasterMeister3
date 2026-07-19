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

import cherry.mastermeister.audit.AuditEvent;
import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.AuditEvents;
import cherry.mastermeister.common.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * HTML メール送信(Q4=A 同期・NFR-U3-05)。
 * 本文は cherry.mustache テンプレート(言語フォールバック → en)、件名は MessageSource。
 * 送信失敗は例外を伝播させず MAIL_SEND_FAILED を監査記録して false を返す —
 * 業務処理(登録申請・承認・却下)はメール失敗で失敗しない。
 */
@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final MailTemplateRegistry templateRegistry;
    private final MessageSource messageSource;
    private final AppProperties properties;
    private final AuditEventPublisher auditEventPublisher;

    public MailService(
            JavaMailSender mailSender,
            MailTemplateRegistry templateRegistry,
            MessageSource messageSource,
            AppProperties properties,
            AuditEventPublisher auditEventPublisher) {
        this.mailSender = mailSender;
        this.templateRegistry = templateRegistry;
        this.messageSource = messageSource;
        this.properties = properties;
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * @param templateId テンプレート ID(例: registration-confirm)
     * @param language   受信者言語(ja / en。テンプレート不在は en へフォールバック)
     * @param to         宛先
     * @param model      テンプレート変数
     * @return 送信成功なら true
     */
    public boolean send(String templateId, String language, String to, Map<String, Object> model) {
        try {
            String html = templateRegistry.get(templateId, language).render(model);
            String subject = messageSource.getMessage(
                    "mail." + templateId + ".subject", null, Locale.forLanguageTag(language));
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send mail: template={}, to={}", templateId, to, e);
            auditEventPublisher.publish(AuditEvent.failure(
                    AuditEvents.MAIL_SEND_FAILED, null,
                    Map.of("template", templateId, "to", to)));
            return false;
        }
    }
}

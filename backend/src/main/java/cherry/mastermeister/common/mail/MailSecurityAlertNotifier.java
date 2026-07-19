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

import cherry.mastermeister.audit.SecurityAlertNotifier;
import cherry.mastermeister.user.AppUserRepository;
import cherry.mastermeister.user.UserRole;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * SecurityAlertNotifier のメール実装(US-045)。
 * 管理者全員(role=ADMIN)へ各自の言語で送信する。個別の送信失敗は
 * MailService が MAIL_SEND_FAILED を記録し、残りの管理者への送信は継続する。
 */
@Component
public class MailSecurityAlertNotifier implements SecurityAlertNotifier {

    private final MailService mailService;
    private final AppUserRepository userRepository;

    public MailSecurityAlertNotifier(MailService mailService, AppUserRepository userRepository) {
        this.mailService = mailService;
        this.userRepository = userRepository;
    }

    @Override
    public void sendAlert(String alertType, long count) {
        userRepository.findByRole(UserRole.ADMIN).forEach(admin ->
                mailService.send("security-alert", admin.getLanguage(), admin.getEmail(), Map.of(
                        "alertType", alertType,
                        "count", count)));
    }
}

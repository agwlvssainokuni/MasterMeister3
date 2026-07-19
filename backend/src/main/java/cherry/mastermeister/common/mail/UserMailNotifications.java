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

import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.user.AppUser;
import cherry.mastermeister.user.UserNotificationGateway;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * UserNotificationGateway のメール実装(US-001/003)。
 * リンク URL はサーバ側で組み立て済みの値のみをテンプレートへ渡す({{{...}}} 規約)。
 */
@Component
public class UserMailNotifications implements UserNotificationGateway {

    private final MailService mailService;
    private final AppProperties properties;

    public UserMailNotifications(MailService mailService, AppProperties properties) {
        this.mailService = mailService;
        this.properties = properties;
    }

    @Override
    public boolean sendRegistrationConfirm(String email, String language, String token) {
        String completeUrl = properties.mail().baseUrl() + "/register/complete?token=" + token;
        long expiryHours = properties.userRegistration().tokenExpiry().toHours();
        return mailService.send("registration-confirm", language, email, Map.of(
                "completeUrl", completeUrl,
                "expiryHours", expiryHours));
    }

    @Override
    public boolean sendUserApproved(AppUser user) {
        return mailService.send("user-approved", user.getLanguage(), user.getEmail(), Map.of(
                "displayName", displayNameOf(user),
                "loginUrl", properties.mail().baseUrl() + "/login"));
    }

    @Override
    public boolean sendUserRejected(AppUser user) {
        return mailService.send("user-rejected", user.getLanguage(), user.getEmail(), Map.of(
                "displayName", displayNameOf(user)));
    }

    private static String displayNameOf(AppUser user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getEmail()
                : user.getDisplayName();
    }
}

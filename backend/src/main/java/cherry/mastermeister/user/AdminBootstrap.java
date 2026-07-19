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
import cherry.mastermeister.common.config.AppProperties;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初期管理者ブートストラップ(US-004)。
 * mm.app.admin.bootstrap.email / .password が両方設定されているときのみ実行。
 * 同一 email のユーザが存在すれば何もしない(冪等 — 上書きなし)。
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties properties;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventPublisher auditEventPublisher;

    public AdminBootstrap(
            AppProperties properties,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditEventPublisher auditEventPublisher) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin.Bootstrap bootstrap = properties.admin().bootstrap();
        if (!bootstrap.isConfigured()) {
            return;
        }
        String email = bootstrap.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            logger.info("Bootstrap admin already exists: {}", email);
            return;
        }
        AppUser admin = new AppUser();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(bootstrap.password()));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setLanguage("ja");
        admin.setTheme("system");
        userRepository.save(admin);
        auditEventPublisher.publish(AuditEvent.success(AuditEvents.ADMIN_BOOTSTRAPPED, email));
        logger.info("Bootstrap admin created: {}", email);
    }
}

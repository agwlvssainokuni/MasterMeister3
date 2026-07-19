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
package cherry.mastermeister.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * AppProperties の起動時検証(NFR-U3-01/02、NFR-U4-01)。
 * jwt.secret は未設定・32 バイト未満で起動失敗、credential.* は未設定・鍵不正で起動失敗。
 * その他は既定値で動作する。
 */
class AppPropertiesTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef"; // 32 bytes

    private static final String VALID_KEY =
            Base64.getEncoder().encodeToString("A".repeat(32).getBytes(StandardCharsets.UTF_8));

    private static final String[] REQUIRED = {
            "mm.app.jwt.secret=" + VALID_SECRET,
            "mm.app.credential.keys.k1=" + VALID_KEY,
            "mm.app.credential.active-key-id=k1",
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableConfig.class);

    @EnableConfigurationProperties(AppProperties.class)
    static class EnableConfig {
    }

    @Test
    void 必須項目設定済みなら既定値で起動する() {
        runner.withPropertyValues(REQUIRED)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AppProperties props = context.getBean(AppProperties.class);
                    assertThat(props.jwt().secret()).isEqualTo(VALID_SECRET);
                    assertThat(props.jwt().accessTokenExpiry()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(props.jwt().refreshTokenExpiry()).isEqualTo(Duration.ofHours(24));
                    assertThat(props.userRegistration().tokenExpiry()).isEqualTo(Duration.ofHours(3));
                    assertThat(props.auth().lockout().threshold()).isEqualTo(5);
                    assertThat(props.auth().lockout().duration()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(props.securityAlert().window()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(props.securityAlert().threshold()).isEqualTo(10);
                    assertThat(props.admin().bootstrap().isConfigured()).isFalse();
                    assertThat(props.mail().from()).isEqualTo("noreply@mastermeister.local");
                    assertThat(props.mail().baseUrl()).isEqualTo("http://localhost:8080");
                    assertThat(props.credential().activeKeyId()).isEqualTo("k1");
                    assertThat(props.credential().keyBytes("k1")).hasSize(32);
                    assertThat(props.credential().keyBytes("unknown")).isNull();
                    assertThat(props.permission().cacheTtl()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(props.permission().cacheMaxSize()).isEqualTo(1000);
                });
    }

    @Test
    void secret未設定なら起動失敗する() {
        runner.withPropertyValues(
                        "mm.app.credential.keys.k1=" + VALID_KEY,
                        "mm.app.credential.active-key-id=k1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void secretが32バイト未満なら起動失敗する() {
        runner.withPropertyValues(
                        "mm.app.jwt.secret=short-secret",
                        "mm.app.credential.keys.k1=" + VALID_KEY,
                        "mm.app.credential.active-key-id=k1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void credential未設定なら起動失敗する() {
        runner.withPropertyValues("mm.app.jwt.secret=" + VALID_SECRET)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void credential鍵がBase64でないなら起動失敗する() {
        runner.withPropertyValues(
                        "mm.app.jwt.secret=" + VALID_SECRET,
                        "mm.app.credential.keys.k1=!!not-base64!!",
                        "mm.app.credential.active-key-id=k1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void credential鍵が32バイトでないなら起動失敗する() {
        String shortKey = Base64.getEncoder()
                .encodeToString("short".getBytes(StandardCharsets.UTF_8));
        runner.withPropertyValues(
                        "mm.app.jwt.secret=" + VALID_SECRET,
                        "mm.app.credential.keys.k1=" + shortKey,
                        "mm.app.credential.active-key-id=k1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void activeKeyIdがkeysに存在しないなら起動失敗する() {
        runner.withPropertyValues(
                        "mm.app.jwt.secret=" + VALID_SECRET,
                        "mm.app.credential.keys.k1=" + VALID_KEY,
                        "mm.app.credential.active-key-id=k9")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 全項目を上書きできる() {
        runner.withPropertyValues(
                        "mm.app.jwt.secret=" + VALID_SECRET,
                        "mm.app.credential.keys.k1=" + VALID_KEY,
                        "mm.app.credential.active-key-id=k1",
                        "mm.app.jwt.access-token-expiry=5m",
                        "mm.app.auth.lockout.threshold=3",
                        "mm.app.auth.lockout.duration=30m",
                        "mm.app.security-alert.window=5m",
                        "mm.app.security-alert.threshold=20",
                        "mm.app.admin.bootstrap.email=admin@example.com",
                        "mm.app.admin.bootstrap.password=changeit123",
                        "mm.app.mail.from=noreply@example.com",
                        "mm.app.mail.base-url=https://mm.example.com",
                        "mm.app.permission.cache-ttl=5m",
                        "mm.app.permission.cache-max-size=500")
                .run(context -> {
                    AppProperties props = context.getBean(AppProperties.class);
                    assertThat(props.jwt().accessTokenExpiry()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(props.auth().lockout().threshold()).isEqualTo(3);
                    assertThat(props.auth().lockout().duration()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(props.securityAlert().window()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(props.securityAlert().threshold()).isEqualTo(20);
                    assertThat(props.admin().bootstrap().isConfigured()).isTrue();
                    assertThat(props.mail().from()).isEqualTo("noreply@example.com");
                    assertThat(props.mail().baseUrl()).isEqualTo("https://mm.example.com");
                    assertThat(props.permission().cacheTtl()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(props.permission().cacheMaxSize()).isEqualTo(500);
                });
    }
}

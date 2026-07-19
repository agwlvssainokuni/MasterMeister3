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
package cherry.mastermeister.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.web.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 資格情報暗号化(NFR-U4-01)。ラウンドトリップ、鍵 ID による復号鍵選択
 * (段階的ローテーション)、復号失敗時の CREDENTIAL_DECRYPT_FAILED を検証する。
 */
class CredentialEncryptorTest {

    private static final String K1 = base64Key('A');
    private static final String K2 = base64Key('B');

    private static String base64Key(char c) {
        return Base64.getEncoder().encodeToString(
                String.valueOf(c).repeat(32).getBytes(StandardCharsets.UTF_8));
    }

    private static CredentialEncryptor encryptor(Map<String, String> keys, String activeKeyId) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef",
                        Duration.ofMinutes(10), Duration.ofHours(24)),
                new AppProperties.Credential(keys, activeKeyId),
                new AppProperties.UserRegistration(Duration.ofHours(3)),
                new AppProperties.Auth(new AppProperties.Auth.Lockout(5, Duration.ofMinutes(15))),
                new AppProperties.SecurityAlert(Duration.ofMinutes(10), 10),
                new AppProperties.Admin(new AppProperties.Admin.Bootstrap(null, null)),
                new AppProperties.Mail("noreply@mastermeister.local", "http://localhost:8080"),
                new AppProperties.Permission(Duration.ofMinutes(10), 1000));
        return new CredentialEncryptor(properties);
    }

    @Test
    void 暗号化と復号のラウンドトリップ() {
        CredentialEncryptor encryptor = encryptor(Map.of("k1", K1), "k1");
        String stored = encryptor.encrypt("target-db-password");
        assertThat(stored).startsWith("v1:k1:");
        assertThat(stored).doesNotContain("target-db-password");
        assertThat(encryptor.decrypt(stored)).isEqualTo("target-db-password");
    }

    @Test
    void 同じ平文でも暗号文は毎回異なる_IVランダム() {
        CredentialEncryptor encryptor = encryptor(Map.of("k1", K1), "k1");
        assertThat(encryptor.encrypt("pw")).isNotEqualTo(encryptor.encrypt("pw"));
    }

    @Test
    void 鍵ローテーション_旧鍵の暗号文を復号でき新規は新鍵で暗号化される() {
        CredentialEncryptor oldEncryptor = encryptor(Map.of("k1", K1), "k1");
        String storedWithK1 = oldEncryptor.encrypt("pw");

        // k2 を追加して active を切替(旧鍵 k1 は残す)
        CredentialEncryptor rotated = encryptor(Map.of("k1", K1, "k2", K2), "k2");
        assertThat(rotated.decrypt(storedWithK1)).isEqualTo("pw");
        assertThat(rotated.encrypt("pw")).startsWith("v1:k2:");
    }

    @Test
    void 未知の鍵IDはCREDENTIAL_DECRYPT_FAILED() {
        String storedWithK1 = encryptor(Map.of("k1", K1), "k1").encrypt("pw");
        // 旧鍵 k1 を設定から除去してしまった状態
        CredentialEncryptor withoutK1 = encryptor(Map.of("k2", K2), "k2");
        assertThatThrownBy(() -> withoutK1.decrypt(storedWithK1))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CREDENTIAL_DECRYPT_FAILED");
    }

    @Test
    void 改ざんされた暗号文はCREDENTIAL_DECRYPT_FAILED() {
        CredentialEncryptor encryptor = encryptor(Map.of("k1", K1), "k1");
        String stored = encryptor.encrypt("pw");
        String tampered = stored.substring(0, stored.length() - 4) + "AAA=";
        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CREDENTIAL_DECRYPT_FAILED");
    }

    @Test
    void 形式不正はCREDENTIAL_DECRYPT_FAILED() {
        CredentialEncryptor encryptor = encryptor(Map.of("k1", K1), "k1");
        assertThatThrownBy(() -> encryptor.decrypt("plaintext-not-encrypted"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CREDENTIAL_DECRYPT_FAILED");
    }
}

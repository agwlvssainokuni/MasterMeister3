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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mustache.MustacheParseException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * メールテンプレートレジストリ(NFR-U3-05)。
 * 本物のテンプレート 4 種 × 2 言語のコンパイル、言語フォールバック、fail-fast を検証する。
 */
class MailTemplateRegistryTest {

    @Test
    void 全テンプレートがコンパイルされ描画できる() {
        MailTemplateRegistry registry = new MailTemplateRegistry();

        String html = registry.get("registration-confirm", "ja").render(Map.of(
                "completeUrl", "http://localhost:8080/register/complete?token=abc123",
                "expiryHours", 3));
        assertThat(html)
                .contains("http://localhost:8080/register/complete?token=abc123")
                .contains("3 時間");

        assertThat(registry.get("user-approved", "ja")).isNotNull();
        assertThat(registry.get("user-approved", "en")).isNotNull();
        assertThat(registry.get("user-rejected", "ja")).isNotNull();
        assertThat(registry.get("user-rejected", "en")).isNotNull();
        assertThat(registry.get("security-alert", "ja")).isNotNull();
        assertThat(registry.get("security-alert", "en")).isNotNull();
    }

    @Test
    void HTMLエスケープが効く() {
        MailTemplateRegistry registry = new MailTemplateRegistry();

        String html = registry.get("user-approved", "ja").render(Map.of(
                "displayName", "<script>alert(1)</script>",
                "loginUrl", "http://localhost:8080/login"));
        assertThat(html)
                .doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;");
    }

    @Test
    void 未知言語はenへフォールバックする() {
        MailTemplateRegistry registry = new MailTemplateRegistry();

        String html = registry.get("registration-confirm", "de").render(Map.of(
                "completeUrl", "http://localhost:8080/register/complete?token=abc",
                "expiryHours", 3));
        assertThat(html).contains("Complete your MasterMeister registration");
    }

    @Test
    void 不正テンプレートは起動失敗_failFast() {
        assertThatThrownBy(() -> new MailTemplateRegistry("classpath*:mail-broken/*.mustache.html"))
                .isInstanceOf(MustacheParseException.class);
    }

    @Test
    void 存在しないテンプレートIDは例外() {
        MailTemplateRegistry registry = new MailTemplateRegistry();

        assertThatThrownBy(() -> registry.get("no-such-template", "ja"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

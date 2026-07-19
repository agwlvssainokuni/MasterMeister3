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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * アプリケーション設定(mm.app.*)。
 * jwt.secret と credential.* は既定値を持たない(未設定・不正は起動失敗 — NFR-U3-01/U4-01)。
 * それ以外の項目はコード上の既定値で動作し、application.yaml / 環境変数で上書きできる。
 */
@ConfigurationProperties("mm.app")
@Validated
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Credential credential,
        @Valid @DefaultValue UserRegistration userRegistration,
        @Valid @DefaultValue Auth auth,
        @Valid @DefaultValue SecurityAlert securityAlert,
        @Valid @DefaultValue Admin admin,
        @Valid @DefaultValue Mail mail,
        @Valid @DefaultValue Permission permission) {

    /** JWT(HS256)と リフレッシュトークンの有効期間。 */
    public record Jwt(
            @NotBlank String secret,
            @NotNull @DefaultValue("10m") Duration accessTokenExpiry,
            @NotNull @DefaultValue("24h") Duration refreshTokenExpiry) {

        public Jwt {
            if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException(
                        "mm.app.jwt.secret must be at least 32 bytes for HS256");
            }
        }
    }

    /** ユーザ登録トークン。 */
    public record UserRegistration(
            @NotNull @DefaultValue("3h") Duration tokenExpiry) {
    }

    /** 認証まわり(ロックアウト)。 */
    public record Auth(@Valid @DefaultValue Lockout lockout) {

        public record Lockout(
                @Min(1) @DefaultValue("5") int threshold,
                @NotNull @DefaultValue("15m") Duration duration) {
        }
    }

    /** セキュリティアラートの時間窓と閾値(クールダウンも window を流用)。 */
    public record SecurityAlert(
            @NotNull @DefaultValue("10m") Duration window,
            @Min(1) @DefaultValue("10") int threshold) {
    }

    /** 初期管理者ブートストラップ(両方設定されたときのみ実行)。 */
    public record Admin(@Valid @DefaultValue Bootstrap bootstrap) {

        public record Bootstrap(String email, String password) {

            public boolean isConfigured() {
                return email != null && !email.isBlank()
                        && password != null && !password.isBlank();
            }
        }
    }

    /** メール送信元とメール内リンクの基点 URL(SMTP 接続は spring.mail.*)。 */
    public record Mail(
            @NotBlank @DefaultValue("noreply@mastermeister.local") String from,
            @NotBlank @DefaultValue("http://localhost:8080") String baseUrl) {
    }

    /**
     * 接続資格情報の暗号鍵(H-03 / NFR-U4-01)。keys は keyId → Base64(32 バイト)の
     * マップで、activeKeyId の鍵で暗号化・保存形式中の keyId の鍵で復号する。
     * 鍵ローテーション: 新鍵を追加して activeKeyId を切替え、旧鍵は全接続の
     * 再暗号化(保存時に自動)が済むまで残す。
     */
    public record Credential(
            @NotNull Map<String, String> keys,
            @NotBlank String activeKeyId) {

        public Credential {
            if (keys != null && activeKeyId != null) {
                if (keys.isEmpty()) {
                    throw new IllegalArgumentException(
                            "mm.app.credential.keys must contain at least one key");
                }
                keys.forEach((keyId, value) -> {
                    byte[] decoded;
                    try {
                        decoded = Base64.getDecoder().decode(value);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(
                                "mm.app.credential.keys." + keyId + " must be Base64", e);
                    }
                    if (decoded.length != 32) {
                        throw new IllegalArgumentException(
                                "mm.app.credential.keys." + keyId
                                        + " must decode to 32 bytes for AES-256");
                    }
                });
                if (!keys.containsKey(activeKeyId)) {
                    throw new IllegalArgumentException(
                            "mm.app.credential.active-key-id '" + activeKeyId
                                    + "' is not present in mm.app.credential.keys");
                }
            }
        }

        public byte[] keyBytes(String keyId) {
            String value = keys.get(keyId);
            if (value == null) {
                return null;
            }
            return Base64.getDecoder().decode(value);
        }
    }

    /** 実効権限キャッシュ(NFR-U4-03: サイズ上限 + 防御的 TTL)。 */
    public record Permission(
            @NotNull @DefaultValue("10m") Duration cacheTtl,
            @Min(1) @DefaultValue("1000") long cacheMaxSize) {
    }
}

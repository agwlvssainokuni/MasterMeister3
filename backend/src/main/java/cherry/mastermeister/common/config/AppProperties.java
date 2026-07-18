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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * アプリケーション設定(mm.app.*)。
 * jwt.secret のみ既定値を持たない(未設定・32 バイト未満は起動失敗 — NFR-U3-01)。
 * それ以外の項目はコード上の既定値で動作し、application.yaml / 環境変数で上書きできる。
 */
@ConfigurationProperties("mm.app")
@Validated
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @DefaultValue UserRegistration userRegistration,
        @Valid @DefaultValue Auth auth,
        @Valid @DefaultValue SecurityAlert securityAlert,
        @Valid @DefaultValue Admin admin,
        @Valid @DefaultValue Mail mail) {

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
}

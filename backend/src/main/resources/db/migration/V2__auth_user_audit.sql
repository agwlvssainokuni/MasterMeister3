-- Copyright 2026 agwlvssainokuni
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- ユニット③: 認証・ユーザ管理・監査ログの 5 テーブル
-- (aidlc-docs/construction/unit-03-auth-user-audit/functional-design/domain-entities.md)

-- ユーザ。登録申請段階では行を作らない(パスワード設定時に PENDING_APPROVAL で作成)
CREATE TABLE app_user (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    display_name VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    language VARCHAR(5) NOT NULL DEFAULT 'ja',
    theme VARCHAR(10) NOT NULL DEFAULT 'system',
    locked_until TIMESTAMP,
    failed_login_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

-- 登録トークン。平文はメール記載のみ・SHA-256 ハッシュだけを保存
CREATE TABLE registration_token (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    language VARCHAR(5) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_registration_token_hash UNIQUE (token_hash)
);

-- リフレッシュトークン。有効 = rotated_at IS NULL AND revoked_at IS NULL AND expires_at > now
CREATE TABLE refresh_token (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    rotated_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_refresh_token_family_id ON refresh_token (family_id);

-- 監査ログ(記録基盤 — ④〜⑥もこのテーブルに記録する)
CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    actor VARCHAR(255),
    connection_id BIGINT,
    resource VARCHAR(500),
    outcome VARCHAR(10) NOT NULL,
    detail_json TEXT
);

CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at);
CREATE INDEX ix_audit_log_event_type ON audit_log (event_type);

-- セキュリティアラートのクールダウン状態(alert_type ごとの直近通知時刻)
CREATE TABLE security_alert_state (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    last_notified_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_security_alert_state_type UNIQUE (alert_type)
);

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

-- ユニット④: 接続管理・スキーマ取込・権限・グループの 7 テーブル
-- (aidlc-docs/construction/unit-04-connection-permission/functional-design/domain-entities.md)

-- 対象 RDBMS 接続。password_enc は AES-256-GCM 暗号文(v1:{keyId}:{IV}:{暗号文+タグ})のみ
CREATE TABLE db_connection (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    db_type VARCHAR(20) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT,
    database_name VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password_enc VARCHAR(1024) NOT NULL,
    options VARCHAR(1000),
    pool_max_size INT NOT NULL DEFAULT 5,
    pool_timeout_ms INT NOT NULL DEFAULT 5000,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_db_connection_name UNIQUE (name)
);

-- 取込スキーマ(接続 ID 単位に分離。再取込は接続単位の全置換)
CREATE TABLE meta_schema (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    remarks VARCHAR(1000),
    imported_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_meta_schema UNIQUE (connection_id, name),
    CONSTRAINT fk_meta_schema_connection FOREIGN KEY (connection_id)
        REFERENCES db_connection (id) ON DELETE CASCADE
);

CREATE TABLE meta_table (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    schema_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    table_type VARCHAR(10) NOT NULL,
    remarks VARCHAR(1000),
    CONSTRAINT uq_meta_table UNIQUE (schema_id, name),
    CONSTRAINT fk_meta_table_schema FOREIGN KEY (schema_id)
        REFERENCES meta_schema (id) ON DELETE CASCADE
);

CREATE INDEX ix_meta_table_schema_id ON meta_table (schema_id);

-- primary_key_seq: 主キー構成順(NULL = 非 PK)。D-15 / US-014 の判定に使用
CREATE TABLE meta_column (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    ordinal INT NOT NULL,
    data_type VARCHAR(100) NOT NULL,
    column_size INT,
    decimal_digits INT,
    nullable BOOLEAN NOT NULL,
    default_value VARCHAR(1000),
    remarks VARCHAR(1000),
    primary_key_seq INT,
    CONSTRAINT uq_meta_column UNIQUE (table_id, name),
    CONSTRAINT fk_meta_column_table FOREIGN KEY (table_id)
        REFERENCES meta_table (id) ON DELETE CASCADE
);

CREATE INDEX ix_meta_column_table_id ON meta_column (table_id);

-- 主権限エントリ(D-18: 行なし = 未設定、permission='NONE' = 明示的 NONE)。
-- table_name / column_name は '' で「その階層の指定なし」を表現し、一意制約を DB で強制する
CREATE TABLE permission_entry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    principal_type VARCHAR(10) NOT NULL,
    principal_id BIGINT NOT NULL,
    schema_name VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL DEFAULT '',
    column_name VARCHAR(255) NOT NULL DEFAULT '',
    permission VARCHAR(10) NOT NULL,
    CONSTRAINT uq_permission_entry UNIQUE
        (connection_id, principal_type, principal_id, schema_name, table_name, column_name),
    CONSTRAINT fk_permission_entry_connection FOREIGN KEY (connection_id)
        REFERENCES db_connection (id) ON DELETE CASCADE
);

CREATE INDEX ix_permission_entry_principal
    ON permission_entry (connection_id, principal_type, principal_id);

-- 補助権限エントリ(CREATE / DELETE、スキーマ/テーブル単位の真偽値)
CREATE TABLE permission_aux_entry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    principal_type VARCHAR(10) NOT NULL,
    principal_id BIGINT NOT NULL,
    schema_name VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL DEFAULT '',
    aux_type VARCHAR(10) NOT NULL,
    granted BOOLEAN NOT NULL,
    CONSTRAINT uq_permission_aux_entry UNIQUE
        (connection_id, principal_type, principal_id, schema_name, table_name, aux_type),
    CONSTRAINT fk_permission_aux_entry_connection FOREIGN KEY (connection_id)
        REFERENCES db_connection (id) ON DELETE CASCADE
);

CREATE INDEX ix_permission_aux_entry_principal
    ON permission_aux_entry (connection_id, principal_type, principal_id);

-- ユーザグループ(YAML はグループ名参照)
CREATE TABLE user_group (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_group_name UNIQUE (name)
);

-- グループ所属(グループ削除で所属も削除)
CREATE TABLE group_member (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT uq_group_member UNIQUE (group_id, user_id),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id)
        REFERENCES user_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_member_user FOREIGN KEY (user_id)
        REFERENCES app_user (id)
);

CREATE INDEX ix_group_member_user_id ON group_member (user_id);

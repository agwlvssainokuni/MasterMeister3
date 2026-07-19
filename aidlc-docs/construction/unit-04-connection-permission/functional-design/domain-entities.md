# ドメインエンティティ — unit-04-connection-permission

**作成日**: 2026-07-19
**前提**: 内部 DB(H2 ファイルモード)、主キーは BIGINT 自動採番(③Q1=A を踏襲)、Flyway V3 で作成

## 1. エンティティ一覧

### db_connection(対象 RDBMS 接続)
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | 接続 ID(取込スキーマ・権限・監査の分離単位 — US-011) |
| name | VARCHAR(100) | UNIQUE, NOT NULL | 表示名(YAML の参考情報にも使用) |
| db_type | VARCHAR(20) | NOT NULL | `MYSQL` / `MARIADB` / `POSTGRESQL` / `H2` |
| host | VARCHAR(255) | NOT NULL | H2 は TCP サーバモードのホストまたはファイルパス表現(§4) |
| port | INT | NULL | H2 ファイルモードでは未使用 |
| database_name | VARCHAR(255) | NOT NULL | データベース名(H2 はパス) |
| username | VARCHAR(255) | NOT NULL | 対象 RDBMS の接続ユーザ |
| password_enc | VARCHAR(1024) | NOT NULL | 暗号化済み資格情報(§3 の形式。平文保存禁止 — US-010/H-03) |
| options | VARCHAR(1000) | NULL | 追加 JDBC パラメータ(`key=value&...`。TLS 指定等) |
| pool_max_size | INT | NOT NULL DEFAULT 5 | プール最大接続数(US-010「プール設定」) |
| pool_timeout_ms | INT | NOT NULL DEFAULT 5000 | 接続取得タイムアウト |
| created_at / updated_at | TIMESTAMP | NOT NULL | |

### meta_schema / meta_table / meta_column(取込スキーマ — US-012)
| テーブル | カラム | 説明 |
|---|---|---|
| meta_schema | id PK、connection_id FK→db_connection(CASCADE)、name、remarks NULL、imported_at | 接続 ID 単位に分離 |
| meta_table | id PK、schema_id FK→meta_schema(CASCADE)、name、table_type(`TABLE` / `VIEW`)、remarks NULL | |
| meta_column | id PK、table_id FK→meta_table(CASCADE)、name、ordinal、data_type(JDBC 型名)、column_size NULL、decimal_digits NULL、nullable BOOLEAN、default_value NULL、remarks NULL、primary_key_seq INT NULL | primary_key_seq: 主キー構成順(NULL = 非 PK)。D-15 / US-014 の判定に使用 |

- UNIQUE: meta_schema(connection_id, name)、meta_table(schema_id, name)、meta_column(table_id, name)
- 再取込は接続単位の全置換(business-rules §2)。FK CASCADE で一括削除→再挿入

### permission_entry(主権限 — US-013、D-18)
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | |
| connection_id | BIGINT | FK→db_connection(CASCADE), NOT NULL | |
| principal_type | VARCHAR(10) | NOT NULL | `USER` / `GROUP` |
| principal_id | BIGINT | NOT NULL | app_user.id または user_group.id |
| schema_name | VARCHAR(255) | NOT NULL | 物理名(メタデータへの FK は張らない — Q2=A 孤児保持) |
| table_name | VARCHAR(255) | NOT NULL DEFAULT '' | `''` = スキーマ階層のエントリ |
| column_name | VARCHAR(255) | NOT NULL DEFAULT '' | `''` = テーブル以上の階層のエントリ |
| permission | VARCHAR(10) | NOT NULL | `NONE` / `READ` / `UPDATE`(明示的 NONE を含む — D-18) |

- UNIQUE(connection_id, principal_type, principal_id, schema_name, table_name, column_name)
- 「未設定」= 行が存在しないこと。**空文字表現により NULL の一意制約問題を回避**(重複エントリを DB 制約でも防止 — US-017)
- column_name ≠ '' のとき table_name ≠ '' を必須(アプリ検証)

### permission_aux_entry(補助権限 — US-014)
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | |
| connection_id / principal_type / principal_id | | permission_entry と同じ | |
| schema_name | VARCHAR(255) | NOT NULL | |
| table_name | VARCHAR(255) | NOT NULL DEFAULT '' | `''` = スキーマ単位の設定 |
| aux_type | VARCHAR(10) | NOT NULL | `CREATE` / `DELETE` |
| granted | BOOLEAN | NOT NULL | 独立した真偽値(US-014)。行なし = 未設定(D-18 と同じ区別) |

- UNIQUE(connection_id, principal_type, principal_id, schema_name, table_name, aux_type)

### user_group(グループ — US-018)
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | |
| name | VARCHAR(100) | UNIQUE, NOT NULL | 改名可(YAML はグループ名参照 — Q3=A) |
| created_at / updated_at | TIMESTAMP | NOT NULL | |

### group_member(所属 — US-019)
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | |
| group_id | BIGINT | FK→user_group(CASCADE), NOT NULL | グループ削除で所属も削除(US-018) |
| user_id | BIGINT | FK→app_user, NOT NULL | |

- UNIQUE(group_id, user_id)

## 2. 削除時のカスケード規則

- 接続削除(US-011): meta_* / permission_entry / permission_aux_entry を FK CASCADE で削除。監査ログ(audit_log.connection_id)は**残す**(証跡 — US-043)
- グループ削除(US-018): group_member は FK CASCADE。permission_entry / permission_aux_entry の `principal_type='GROUP' AND principal_id=対象` はアプリケーションで同一トランザクション削除(principal は多態参照のため FK なし)
- ユーザは③の設計どおり物理削除しない(DISABLED 化)ため、USER プリンシパルの孤児は発生しない

## 3. 資格情報の暗号化形式(Q1=A、H-03)

- 保存形式: `v1:{keyId}:{Base64(IV 12byte)}:{Base64(暗号文+GCM タグ)}`(AES-256-GCM)
- 鍵は環境変数供給: `mm.app.credential.keys.{keyId}`(Base64 32byte)+ `mm.app.credential.active-key-id`。起動時検証(③ AppProperties パターン: 鍵不足・長さ不正は起動失敗)
- 復号は保存形式の keyId の鍵で行う。**保存(作成・更新)は常に active-key-id で暗号化** → 旧鍵で復号された資格情報は次回保存時に現行鍵へ移行(段階的ローテーション)
- 鍵ローテーション手順(設計定義。一括再暗号化コマンドは将来スコープ): (1) 新 keyId の鍵を追加し active に設定、旧鍵は残す → (2) 各接続を編集保存(または将来の一括再暗号化)で新鍵に移行 → (3) 全接続の keyId 移行を確認後、旧鍵を設定から除去

## 4. H2 接続の表現

- db_type=H2 は TCP サーバモード(`host:port/database_name`)を基本とし、ローカルファイル(`host` に絶対パスを直接指定・port NULL)も許容
- JDBC URL の組み立ては db_type ごとの規則で行う(DbDialect — ⑤⑥でも使用)。URL 文字列を直接入力させない(インジェクション面の考慮 — options はパラメータ形式のみ受付)

## 5. マイグレーション方針

- `V3__connection_permission.sql` で上記 7 テーブルを作成(V2 までは変更しない)
- インデックス: permission_entry(connection_id, principal_type, principal_id)(解決時のチェーン取得)、permission_aux_entry 同様、meta_table(schema_id)、meta_column(table_id)、group_member(user_id)(ユーザの所属グループ取得)

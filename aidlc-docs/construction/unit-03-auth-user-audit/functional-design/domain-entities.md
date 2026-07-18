# ドメインエンティティ — unit-03-auth-user-audit

**作成日**: 2026-07-19
**前提**: 内部 DB(H2 ファイルモード)、主キーは BIGINT 自動採番(Q1=A)、Flyway V2 で作成

## 1. エンティティ一覧

### app_user(ユーザ)
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | |
| email | VARCHAR(255) | UNIQUE, NOT NULL | ログイン ID(小文字正規化して保存) |
| password_hash | VARCHAR(255) | NULL | アダプティブハッシュ(BCrypt)。登録完了前は NULL |
| display_name | VARCHAR(100) | NULL | 任意(パスワード設定時に入力可) |
| role | VARCHAR(20) | NOT NULL | `ADMIN` / `USER` |
| status | VARCHAR(20) | NOT NULL | 状態遷移 §2 参照 |
| language | VARCHAR(5) | NOT NULL DEFAULT 'ja' | `ja` / `en`(US-047、メール言語の正 — Q3=A) |
| theme | VARCHAR(10) | NOT NULL DEFAULT 'system' | `light` / `dark` / `system`(US-048 — Q3=A) |
| locked_until | TIMESTAMP | NULL | ロックアウト解除予定時刻(D-11)。NULL = 非ロック |
| failed_login_count | INT | NOT NULL DEFAULT 0 | 連続ログイン失敗回数(成功でリセット) |
| created_at / updated_at | TIMESTAMP | NOT NULL | |

### registration_token(登録トークン)
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | |
| token_hash | VARCHAR(64) | UNIQUE, NOT NULL | SHA-256。平文はメール記載のみ・保存しない |
| email | VARCHAR(255) | NOT NULL | 申請メールアドレス |
| language | VARCHAR(5) | NOT NULL | 申請時 UI 言語(US-047: 確認メール言語) |
| expires_at | TIMESTAMP | NOT NULL | 発行 + `mm.app.user-registration.token-expiry`(既定 3h) |
| used_at | TIMESTAMP | NULL | 使用済み判定(NULL = 未使用) |
| created_at | TIMESTAMP | NOT NULL | |

### refresh_token(リフレッシュトークン)
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | |
| token_hash | VARCHAR(64) | UNIQUE, NOT NULL | SHA-256(US-007: 平文は保持しない) |
| user_id | BIGINT | FK → app_user, NOT NULL | |
| family_id | VARCHAR(36) | NOT NULL, INDEX | トークンファミリ(ログインごとに新規 UUID)。US-008 の一括失効単位 |
| expires_at | TIMESTAMP | NOT NULL | 発行 + `mm.app.jwt.refresh-token-expiry`(既定 24h) |
| rotated_at | TIMESTAMP | NULL | ローテーションで無効化された時刻(NULL = 現役) |
| revoked_at | TIMESTAMP | NULL | 明示失効(ログアウト・ファミリ失効)時刻 |
| created_at | TIMESTAMP | NOT NULL | |

有効判定: `rotated_at IS NULL AND revoked_at IS NULL AND expires_at > now`。「使用済み(rotated)トークンの再提示」= 再利用検知(US-008)。

### audit_log(監査ログ)— 記録基盤(D-20)。④〜⑥もこのテーブルに記録
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | |
| occurred_at | TIMESTAMP | NOT NULL, INDEX | イベント発生時刻 |
| event_type | VARCHAR(50) | NOT NULL, INDEX | イベント種別コード(H-07。business-rules.md §5 のカタログ) |
| actor | VARCHAR(255) | NULL | ユーザ ID または試行ユーザ名(認証失敗時は入力メール) |
| connection_id | BIGINT | NULL | 対象接続(④以降で使用) |
| resource | VARCHAR(500) | NULL | 対象リソース(例: `user:5`、④以降: `sales.customers`) |
| outcome | VARCHAR(10) | NOT NULL | `SUCCESS` / `FAILURE` |
| detail_json | TEXT | NULL | イベント固有パラメータ(JSON。メッセージ文字列は入れない — H-07) |

### security_alert_state(アラート抑止状態)— US-045 Q2=A のクールダウン管理
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | |
| alert_type | VARCHAR(50) | UNIQUE, NOT NULL | 例: `LOGIN_FAILURE_BURST` |
| last_notified_at | TIMESTAMP | NOT NULL | 直近通知時刻(窓内再通知の抑止) |

## 2. app_user の状態遷移

```text
(なし)--登録申請(US-001)--> ※ユーザ行はまだ作らない(registration_token のみ)
(なし)--パスワード設定(US-002)--> PENDING_APPROVAL(承認待ち)
PENDING_APPROVAL --承認(US-003)--> ACTIVE(ログイン可)
PENDING_APPROVAL --却下(US-003)--> REJECTED(ログイン不可・終端)
ACTIVE --管理者無効化--> DISABLED(ログイン不可。再有効化は将来スコープ)
ACTIVE --連続失敗が閾値到達(D-11)--> ACTIVE のまま locked_until 設定(時限ロック)
初回起動ブートストラップ(US-004)--> ACTIVE(role=ADMIN)を直接作成
```

- ロックは状態ではなく `locked_until` で表現(期限経過で自動解除、管理者解除 = NULL 化 + failed_login_count リセット)
- 登録申請段階でユーザ行を作らないことで、未完了申請がユーザ一覧を汚さない・列挙攻撃の痕跡も残らない

## 3. マイグレーション方針

- `V2__auth_user_audit.sql` で上記 5 テーブルを作成(V1 の app_info は維持)
- インデックス: app_user(email UNIQUE)、refresh_token(family_id)、(token_hash UNIQUE)、audit_log(occurred_at)、(event_type)、registration_token(token_hash UNIQUE)
- 内部 DB は H2 固定のため方言分岐は不要(DbDialect は対象 RDBMS 用 — ④以降)

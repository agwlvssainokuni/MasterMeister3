# NFR Requirements — unit-03-auth-user-audit

**作成日**: 2026-07-19

| ID | NFR | 内容 | 検証方法 |
|---|---|---|---|
| NFR-U3-01 | 認証セキュリティ | BCrypt(strength 10)、JWT HS256、`mm.app.jwt.secret` は 32 バイト以上を起動時検証(不足・未設定は起動失敗)。リフレッシュ/登録トークンは SHA-256 ハッシュのみ永続化。失敗応答の情報最小化(列挙対策・理由統一) | 起動時検証のテスト、トークン平文が DB に存在しないことの結合テスト |
| NFR-U3-02 | 設定プロパティ体系 | `mm.app.*` を @ConfigurationProperties で型安全に集約(§2 カタログ)。全項目に既定値(secret 除く)、起動時 Bean Validation | AppProperties の単体テスト |
| NFR-U3-03 | 監査の耐障害性 | 監査記録は REQUIRES_NEW(主処理と独立)。監査 INSERT 失敗は主処理へ非伝播(ERROR ログ退避)。メール送信失敗も主処理へ非伝播 + MAIL_SEND_FAILED 記録 | 例外注入テスト(主処理ロールバック時の記録残存、監査失敗時の主処理成功) |
| NFR-U3-04 | セキュリティヘッダー(H-06 本則) | CSP `default-src 'self'` 系(business-logic-model.md §3)+ nosniff / Referrer-Policy / frame-ancestors。②資産(外部参照ゼロ・theme-init.js 外部化)により 'self' のみで成立 | MockMvc でヘッダー検証 + WAR 起動での実測 |
| NFR-U3-05 | 自作 Mustache エンジン品質(Q2=C) | HTML エスケープを既定とする(`{{var}}` はエスケープ、`{{{var}}}` のみ非エスケープ)。**PBT-02 ブロッキング対象**: jqwik によるプロパティテスト必須(エスケープ健全性・リテラル保存・セクション整合)。対応構文はサブセットに限定(§3) | jqwik プロパティテスト + 例示テスト |
| NFR-U3-06 | テスト方針 | 内部 DB(H2)完結のため Testcontainers 不要(④以降で使用)。サービス層は単体テスト、認証フローは @SpringBootTest + MockMvc の結合テスト、メールは JavaMailSender モック(+ devenv MailPit で手動確認) | ./gradlew build で全テスト実行 |
| NFR-U3-07 | 性能 | 認証系 API は単純クエリのみで p95 < 200ms 目安(BCrypt 照合コスト含む)。監査 INSERT は同期(D-20 の一貫性優先、Phase 1 の負荷では十分) | 明示的な負荷試験は Build and Test ステージ判断 |

## 2. 設定プロパティカタログ(`mm.app.*`)

| プロパティ | 既定値 | 説明 |
|---|---|---|
| mm.app.jwt.secret | (必須・既定なし) | HS256 鍵。32 バイト以上 |
| mm.app.jwt.access-token-expiry | 10m | アクセストークン有効期間 |
| mm.app.jwt.refresh-token-expiry | 24h | リフレッシュトークン有効期間 |
| mm.app.user-registration.token-expiry | 3h | 登録トークン有効期間 |
| mm.app.auth.lockout.threshold | 5 | ロックアウト閾値(連続失敗回数) |
| mm.app.auth.lockout.duration | 15m | ロック時間 |
| mm.app.security-alert.window | 10m | アラート評価時間窓 |
| mm.app.security-alert.threshold | 10 | 窓内対象イベント件数閾値 |
| mm.app.admin.bootstrap.email / .password | (任意) | 初期管理者(未設定なら何もしない) |
| mm.app.mail.from | noreply@mastermeister.local | 送信元 |
| mm.app.mail.base-url | http://localhost:8080 | メール内リンクの基点 URL |

- SMTP 接続は Spring Boot 標準 `spring.mail.*`(開発既定: localhost:1025 = MailPit)

## 3. 自作 Mustache エンジンの対応構文(サブセット確定)

| 構文 | 対応 | 備考 |
|---|---|---|
| `{{name}}` | ○ | HTML エスケープして展開(&, <, >, ", ') |
| `{{{name}}}` | ○ | 非エスケープ展開(利用は原則リンク URL 等に限定) |
| `{{#section}}...{{/section}}` | ○ | truthy / コレクション反復 |
| `{{^section}}...{{/section}}` | ○ | falsy 時表示 |
| `{{! comment }}` | ○ | 出力しない |
| ドット名 `{{a.b}}` | ○ | ネスト参照 |
| パーシャル `{{> name}}` / デリミタ変更 / ラムダ | × | スコープ外(必要になれば拡張) |

- 未クローズのセクション・不正タグはテンプレート読込時に例外(実行時に壊れた HTML を送らない)
- テンプレート配置: `backend/src/main/resources/mail/{template}_{lang}.mustache.html`(件名は MessageSource)
- エンジン配置: `cherry.mastermeister.common.template`(汎用実装。メール以外にも将来利用可)

## 4. 適用外

- スケーラビリティ(多重インスタンス・分散ロック): 単一インスタンス前提(MVP)。ロックアウトカウントも DB 行で一貫
- MFA: D-12 で MVP 後送り確定済み

# Code Summary — unit-03-auth-user-audit

**作成日**: 2026-07-19
**ストーリー**: US-001〜009、US-042、US-045、US-047/048(13 件)— 全実装

## バックエンド(cherry.mastermeister.*)

| パッケージ | 実装 |
|---|---|
| auth | AuthController(login/refresh/logout)、AuthService(判定順序 BR§2・応答統一)、TokenService(JWT HS256・リフレッシュ 256bit + SHA-256)、RefreshTokenStore(UPDATE 行数による原子的ローテーション・ファミリ失効)、LoginAttemptService(独立 Tx の失敗カウント・時限ロック)、RefreshToken エンティティ |
| user | RegistrationController/Service(202 固定・列挙対策)、RegistrationTokenStore(REQUIRES_NEW 使用済み化)、UserAdminController/UserService(一覧・承認・却下・ロック解除、mailSent フラグ)、UserPreferenceController(/api/me)、AdminBootstrap(冪等)、AppUser/RegistrationToken エンティティ |
| audit | AuditEvent(record)+ AuditEvents(14 種)、AuditEventPublisher、AuditLogListener(@EventListener・例外非伝播)、AuditLogService(REQUIRES_NEW)、SecurityAlertService(時間窓 + クールダウン、TOKEN_REUSE 即時)、SecurityAlertNotifier(IF)、AuditLog/SecurityAlertState エンティティ |
| common.config | AppProperties(mm.app.* 型安全・secret 32 バイト起動時検証・既定値なし)、SecurityConfig(単一チェーン STATELESS・oauth2ResourceServer(jwt)・CSP 本則・401/403 Problem Details・BCrypt 10)、TimeConfig(Clock) |
| common.mail | MailTemplateRegistry(起動時全件コンパイル fail-fast・言語フォールバック en)、MailService(HTML・件名 MessageSource・失敗時 MAIL_SEND_FAILED)、UserMailNotifications、MailSecurityAlertNotifier |
| common.web | ApiException 階層(401/423/400/404/409)、GlobalExceptionHandler(Problem Details・invalid-params)、SecureTokens |
| resources | V2__auth_user_audit.sql(5 テーブル)、mail/ テンプレート 4 種 × ja/en、messages(_ja).properties、application.yaml(既定値・MailPit 開発既定) |

## フロントエンド(frontend/src/)

| 配置 | 実装 |
|---|---|
| app | apiClient(Bearer・401 シングルフライトリフレッシュ・Problem Details 解釈)、AppLayout(AppShell 統合・ナビ・ユーザメニュー)、HomePage(プレースホルダ)、routes |
| features/auth | api.ts(login/logout/me/preferences)、tokenStore(sessionStorage 集約)、AuthProvider(復元・ログイン・ログアウト・サーバ設定適用)、guards(RequireAuth/RequireAdmin)、usePreferences(サーバ保存)、LoginPage |
| features/registration | api.ts(request/complete)、RequestPage、CompletePage |
| features/admin/users | api.ts(list/approve/reject/unlock + 型定義)、UserListPage(サーバページング・フィルタ・承認/却下/ロック解除) |

- API 呼び出し規約: 各 feature が自前の `api.ts` に API 関数と型を集約し、画面は apiClient を直接呼ばない(レビュー指摘 2026-07-19 反映)
| i18n | 辞書 auth / admin(ja/en) |

## テスト

- バックエンド: 208 → 259 件(mustache 197 含む)。AppProperties 検証、Flyway V2 + マッピング整合、監査独立性(例外注入)、アラート判定、認証フロー結合 10、登録・管理結合 9、メール 10
- フロントエンド: 40 件(apiClient シングルフライト、LoginPage、ガード/復元、CompletePage、UserListPage)

## DoD 検証(2026-07-19 実測)

- `./gradlew clean build` 全パス
- WAR + MailPit 実機フロー 29 項目 OK: ブートストラップ → 登録申請(メール受信・トークン抽出)→ パスワード設定 → 承認(メール)→ ログイン → リフレッシュ → 再利用検知(即時アラートメール)→ ログアウト → ロックアウト(423)→ 解除 → バーストアラートメール → US-047/048 別セッション復元 → ヘッダー実測(CSP ほか、SPA/API 両面)→ 認可 403
- audit_log: 12 イベント種別・actor/outcome/detail_json(コードのみ — H-07)を確認
- DB 保存形態: refresh/registration トークンは SHA-256 hex のみ、パスワードは BCrypt($2a$10$)

## 重要な発見・対応

- **Boot 4 で Flyway 自動構成が spring-boot-flyway モジュールに分離**されており、flyway-core 単体ではマイグレーション未実行だった(ユニット①以来の潜在問題)→ 依存追加で修正、テスト・実機で適用を実証
- Boot 4 対応: Jackson 3(tools.jackson)、@AutoConfigureMockMvc の webmvc-test 分離、@MockitoBean、mail ヘルスチェックのモック競合(テストで無効化)

## ④以降への提供物

1. AuditEventPublisher + 種別拡張規約(名詞_過去分詞・detail はコードのみ)
2. SecurityFilterChain / 認可規約(/api/admin/** = ADMIN)+ ApiException/Problem Details 共通変換
3. AppProperties への設定追加パターン
4. apiClient / RequireAuth / AppLayout(全画面の土台)
5. MailService + MailTemplateRegistry(メール種別はテンプレート追加のみ)

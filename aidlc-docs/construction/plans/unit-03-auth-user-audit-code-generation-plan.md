# ユニット③ auth-user-audit — Code Generation プラン

**作成日**: 2026-07-19
**入力**: functional-design/(domain-entities / business-rules / business-logic-model / frontend-components)、nfr-requirements/、nfr-design/
**ストーリー対応**: US-001〜009、US-042、US-045、US-047/048(本実装)— 13 件
**方針**: 各ステップ完了ごとにテストを通しコミット。バックエンドを層ごとに積み上げ(基盤 → 監査 → 認証 → 登録・管理 → メール)、その後フロントエンド、最後に結合検証。本プランが Code Generation の唯一の正とする。

## Step 1: 基盤(依存・設定・マイグレーション・エンティティ)

- [x] 1-1: backend 依存追加(security / oauth2-jose / mail / validation / security-test — すべて BOM 管理)※加えて spring-boot-flyway を追加(Boot 4 で Flyway 自動構成が別モジュール化されており flyway-core 単体では実行されない問題を発見・修正)
- [x] 1-2: `V2__auth_user_audit.sql`(5 テーブル + インデックス)
- [x] 1-3: AppProperties(`mm.app.*` 11 項目、Bean Validation、secret 32 バイト検証)+ application.yaml / application-test.yaml + README(jwt.secret 開発手順)
- [x] 1-4: エンティティ + リポジトリ(AppUser / RegistrationToken / RefreshToken / AuditLog / SecurityAlertState)
- [x] 1-5: テスト(AppProperties 検証、コンテキスト起動、Flyway V2 適用 + エンティティマッピング整合)

## Step 2: 監査基盤(audit)— 全機能が呼ぶため先行

- [x] 2-1: AuditEvent(record)+ イベント種別定数(14 種)+ AuditEventPublisher
- [x] 2-2: AuditLogListener(@EventListener + REQUIRES_NEW、例外非伝播)+ AuditLogService
- [x] 2-3: SecurityAlertService(時間窓 + クールダウン、TOKEN_REUSE は即時)— メール送信は SecurityAlertNotifier インタフェースのみ(Step 5 で接続)
- [x] 2-4: テスト(REQUIRES_NEW 独立性 = 例外注入 2 件、閾値・クールダウン・即時通知 8 件)

## Step 3: セキュリティ構成 + 認証(auth)

- [x] 3-1: SecurityConfig(FilterChain・oauth2ResourceServer・CSP ヘッダー・Problem Details の 401/403・BCrypt)
- [x] 3-2: TokenService(JWT 発行/検証、リフレッシュトークン生成 + SHA-256)+ RefreshTokenStore(原子的ローテーション・ファミリ失効)
- [x] 3-3: AuthService + LoginAttemptService(判定順序・ロックアウト)+ AuthController(login / refresh / logout)
- [x] 3-4: GlobalExceptionHandler(Problem Details 共通変換)+ ApiException 階層(401/423/400/409)
- [x] 3-5: テスト(結合 10 件: ログイン成功/失敗/ロック、リフレッシュ ローテーション/再利用検知/二重リフレッシュ、ログアウト、認可、ヘッダー、列挙対策の応答同一性、バリデーション)※ @AutoConfigureMockMvc は Boot 4 で spring-boot-starter-webmvc-test に分離 → 依存追加

## Step 4: 登録・ユーザ管理(user)

- [x] 4-1: RegistrationService + RegistrationTokenStore + RegistrationController(申請 202 固定・完了)+ UserNotificationGateway インタフェース(メール接続は Step 5)
- [x] 4-2: UserService + UserAdminController(一覧・承認・却下・ロック解除)+ UserPreferenceController(/api/me)
- [x] 4-3: AdminBootstrap(ApplicationRunner)
- [x] 4-4: テスト(結合 9 件: 列挙対策 = 登録済み/未登録の応答同一、トークン使用済み/不正、承認フロー + 409/404、却下、ロック解除、一覧フィルタ/ページング、/api/me、ブートストラップ冪等)

## Step 5: メール(common.mail)

- [ ] 5-1: MailTemplateRegistry(起動時 compile・fail-fast)+ MailService(cherry.mustache 利用、言語フォールバック、失敗時 MAIL_SEND_FAILED)
- [ ] 5-2: テンプレート 4 種 × ja/en(registration-confirm / user-approved / user-rejected / security-alert)— インライン CSS・属性ダブルクォート規約
- [ ] 5-3: 各サービスへの送信接続(申請・承認・却下・アラート)+ SecurityAlertService の送信実装
- [ ] 5-4: テスト(Registry fail-fast、言語選択・フォールバック、送信失敗時の応答不変・監査記録)

## Step 6: フロントエンド(認証基盤 + 画面)

- [ ] 6-1: apiClient(Bearer 付与・401 シングルフライトリフレッシュ・Problem Details 解釈)+ tokenStore
- [ ] 6-2: AuthProvider(復元・ログイン・ログアウト)+ RequireAuth / RequireAdmin + routes 再構成
- [ ] 6-3: usePreferences(US-047/048 サーバ統合: ログイン後サーバ値適用・PUT 保存・localStorage 同期)
- [ ] 6-4: LoginPage / RequestPage / CompletePage(②モックの実装化 + バリデーション)
- [ ] 6-5: AppLayout(AppShell 統合・ナビ・ユーザメニュー)+ ホームプレースホルダ
- [ ] 6-6: UserListPage(サーバページング・フィルタ・承認/却下/ロック解除)
- [ ] 6-7: i18n 辞書 auth / admin(ja/en)
- [ ] 6-8: RTL テスト(AuthProvider 復元・401 リフレッシュ、フォーム検証、UserList 操作)

## Step 7: DoD 検証と文書化

- [ ] 7-1: `./gradlew clean build` 通過(全テスト)
- [ ] 7-2: WAR 起動 + 実機フロー検証: ブートストラップ管理者でログイン → 登録申請(MailPit でメール確認・リンク取得)→ パスワード設定 → 承認(メール確認)→ 新規ユーザでログイン → リフレッシュ → ログアウト。ロックアウト(失敗 5 回)とアラートメール(閾値超過)も確認
- [ ] 7-3: セキュリティヘッダー実測(CSP ほか)+ 監査ログ内容確認(H2 の audit_log)
- [ ] 7-4: US-047/048 実機確認(言語・テーマのサーバ保存 → 別セッションで復元)
- [ ] 7-5: code-summary.md 作成

## テスト方針(拡張ルール)

- Security Baseline: 列挙対策・ロックアウト・再利用検知・ヘッダー・secret 検証をテストで担保(ブロッキング)
- PBT(partial): cherry.mustache の 197 件を維持。③の新規コードでは TokenService のトークン生成(衝突なし・URL-safe)を jqwik で追加(助言扱い)。PBT-03(権限解決)は④のスコープ

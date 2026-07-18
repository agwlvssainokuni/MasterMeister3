# 論理コンポーネント構成 — unit-03-auth-user-audit

**作成日**: 2026-07-19

## backend(`cherry.mastermeister.*`)

| パッケージ | 主要クラス | 備考 |
|---|---|---|
| auth | AuthController / AuthService / TokenService / RefreshTokenStore / LoginAttemptService | JWT は spring-security-oauth2-jose |
| user | RegistrationController / RegistrationService / RegistrationTokenStore / UserAdminController / UserService / UserPreferenceController / AdminBootstrap | |
| audit | AuditEventPublisher / AuditLogListener / AuditLogService / SecurityAlertService | REQUIRES_NEW(nfr-design-patterns.md §3) |
| common.config | AppProperties / SecurityConfig | 起動時検証・CSP 本則 |
| common.mail | MailService / MailTemplateRegistry | cherry.mustache を利用(§4) |
| common.web | GlobalExceptionHandler(Problem Details)+ SpaWebConfig(①既存) | |
| (独立) cherry.mustache | ユーザ実装エンジン(取込済み・変更しない) | テスト 197 件維持 |

- エンティティ/リポジトリ: 各パッケージ配下(User は user、RefreshToken は auth、AuditLog は audit)。Flyway `V2__auth_user_audit.sql`

## frontend(`frontend/src/`)

| 配置 | 内容 |
|---|---|
| features/auth | LoginPage / AuthProvider / tokenStore / api / usePreferences |
| features/registration | RequestPage / CompletePage |
| features/admin/users | UserListPage |
| app | AppLayout(AppShell 統合)/ routes(RequireAuth・RequireAdmin)/ apiClient |
| design-system | ②のまま変更なし(利用のみ) |
| i18n 辞書 | namespace `auth` / `admin` を追加(ja/en) |

## ④以降への提供物(このユニットが基盤)

1. AuditEventPublisher + イベント種別拡張規約(④: 管理操作、⑤⑥: データアクセス)
2. SecurityFilterChain / 認可規約(`/api/admin/**` = ADMIN)
3. AppProperties への設定追加パターン
4. apiClient / RequireAuth / AppLayout(全画面の土台)
5. MailService(将来のメール種別追加)

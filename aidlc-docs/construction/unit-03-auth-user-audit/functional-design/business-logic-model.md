# ビジネスロジックモデル(API・サービス構成)— unit-03-auth-user-audit

**作成日**: 2026-07-19
**前提**: Application Design の components.md / component-methods.md(auth / user / audit / common)を具体化

## 1. API 設計(すべて Problem Details でエラー応答 — RFC 9457)

### 認証(公開)
| メソッド | パス | 説明 | 主応答 |
|---|---|---|---|
| POST | /api/auth/login | email + password | 200 {accessToken, refreshToken, user: {email, displayName, role, language, theme}} / 401 / 423(ロック中) |
| POST | /api/auth/refresh | {refreshToken} | 200 {accessToken, refreshToken} / 401 |
| POST | /api/auth/logout | {refreshToken} | 204(トークン不正でも 204 — 冪等) |

### 登録(公開)
| メソッド | パス | 説明 | 主応答 |
|---|---|---|---|
| POST | /api/registration/request | {email, language} | **常に 202**(列挙対策) |
| POST | /api/registration/complete | {token, password, displayName?} | 204 / 400(無効または期限切れ — 理由統一) |

### ユーザ管理(ADMIN)
| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/admin/users | 一覧(status フィルタ・キーワード・ページング) |
| POST | /api/admin/users/{id}/approve | 承認 |
| POST | /api/admin/users/{id}/reject | 却下 |
| POST | /api/admin/users/{id}/unlock | ロック解除 |

### ユーザ設定(認証済み本人)
| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/me | 自分の情報(email, displayName, role, language, theme) |
| PUT | /api/me/preferences | {language, theme} |

### 認可
- Spring Security: `/api/auth/**`・`/api/registration/**` は permitAll、`/api/admin/**` は ROLE_ADMIN、その他 `/api/**` は認証必須
- JWT フィルタ: Authorization: Bearer を検証し SecurityContext 設定。失敗は 401(Problem Details)

## 2. サービス構成(パッケージ別)

### auth
- `AuthController` — login / refresh / logout
- `AuthService` — ログイン判定(business-rules §2)、監査発行
- `TokenService` — JWT 生成・検証(jjwt 等)、リフレッシュトークン生成・ハッシュ
- `RefreshTokenStore` — 発行・ローテーション(原子的 UPDATE)・ファミリ失効・照合
- `LoginAttemptService` — 失敗カウント・ロック判定・解除

### user
- `RegistrationController` / `RegistrationService` — 申請(列挙対策)・完了
- `RegistrationTokenStore` — 発行・検証・使用済み化
- `UserAdminController` / `UserService` — 一覧・承認・却下・ロック解除
- `UserPreferenceController` — /api/me 系
- `AdminBootstrap` — ApplicationRunner(US-004)

### audit
- `AuditEventPublisher` — publish(AuditEvent record)。全機能の受け口
- `AuditLogListener` — @EventListener(同期)+ REQUIRES_NEW で永続化 → SecurityAlertService 呼出
- `AuditLogService` — INSERT(および⑥の閲覧クエリの土台)
- `SecurityAlertService` — 閾値判定(時間窓 + クールダウン)・管理者メール

### common
- `MailService` — HTML メール(自作 Mustache エンジン — NFR Requirements Q2=C)+ 件名は MessageSource、受信者言語で送信、失敗時 MAIL_SEND_FAILED 発行
- Mustache エンジン(`cherry.mustache` — ユーザ実装・公式 spec 準拠)— MailService がテンプレート描画に使用(機能・品質は nfr-requirements.md §3 / NFR-U3-05)
- `GlobalExceptionHandler` — Problem Details 変換(@RestControllerAdvice)
- `SecurityConfig` — Spring Security チェーン、JWT フィルタ、**CSP・セキュリティヘッダー本則**(下記)
- `AppProperties` — `mm.app.*` の @ConfigurationProperties(型安全・起動時検証)
- i18n メッセージリソース(メール件名・本文キー)

## 3. セキュリティヘッダー(H-06 本則 — ③スコープ)

- CSP: `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'`(②の資産は外部参照ゼロ・theme-init.js 外部ファイル化済みのため 'self' のみで成立)
- そのほか: X-Content-Type-Options: nosniff、Referrer-Policy: no-referrer、X-Frame-Options: DENY(frame-ancestors と併記)
- dev 面(Vite dev server)は H-06 により対象外(プロキシ経由の API のみ)

## 4. 主要フロー(シーケンス概要)

### ログイン
Controller → AuthService.login → (ユーザ検索 → ロック判定 → 照合 → status 判定) → TokenService 発行 + RefreshTokenStore 保存 → AuditEventPublisher(LOGIN_SUCCEEDED) → 応答。失敗パスは各段で publish(LOGIN_FAILED, reason)して 401/423

### リフレッシュ
Controller → RefreshTokenStore.rotate(hash) →
- 確保成功: 新トークン発行 → TOKEN_REFRESHED
- rotated 済み: family 失効 → TOKEN_REUSE_DETECTED → SecurityAlertService(即時通知)→ 401

### 登録申請
Controller → RegistrationService.request → (登録済み? → 監査のみ / 未登録 → トークン発行 → MailService 送信[失敗しても継続]) → 常に 202

### 監査記録(全フロー共通)
publish → AuditLogListener(REQUIRES_NEW で INSERT)→ SecurityAlertService.evaluate(対象イベントのみ)

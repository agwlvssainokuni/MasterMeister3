# NFR Design — unit-03-auth-user-audit

**作成日**: 2026-07-19
**対応**: nfr-requirements.md の NFR-U3-01〜07 を実装構造に落とし込む。

## 1. Spring Security 構成(NFR-U3-01/04)

- **SecurityFilterChain**(単一チェーン):
  - CSRF 無効(ステートレス JWT・Cookie 不使用のため)、セッション STATELESS
  - 認可: `/api/auth/**`・`/api/registration/**` permitAll → `/api/admin/**` hasRole(ADMIN) → `/api/**` authenticated → それ以外(静的リソース・SPA ルート)permitAll
  - `oauth2ResourceServer(jwt)` — Boot 標準のリソースサーバで Bearer 検証(Q1=A)
  - 401/403 は Problem Details を返す AuthenticationEntryPoint / AccessDeniedHandler
- **JWT(HS256)**: `SecretKeySpec(mm.app.jwt.secret, "HmacSHA256")` から `NimbusJwtDecoder.withSecretKey` / `JwtEncoder`(NimbusJwtEncoder + ImmutableSecret)を Bean 化。クレーム: sub=userId, email, role, exp
  - `JwtAuthenticationConverter` で `role` クレーム → `ROLE_*` 権限へマップ
- **secret 検証**: AppProperties の @PostConstruct 相当(Bean Validation + カスタム検証)で 32 バイト未満・未設定なら起動失敗
- **セキュリティヘッダー**: `headers()` で CSP(business-logic-model.md §3 の文字列)・nosniff・frame-ancestors 'none'・Referrer-Policy no-referrer を設定。`/api/**` と静的配信の両方に適用
- **BCryptPasswordEncoder**(strength 10)を Bean 化

## 2. 設定プロパティ(NFR-U3-02)

- `cherry.mastermeister.common.config.AppProperties` — `@ConfigurationProperties("mm.app")` + record ネスト(jwt / userRegistration / auth.lockout / securityAlert / admin.bootstrap / mail)
- `@Validated` + Bean Validation(@NotNull・@Min 等)。Duration 型は Boot の変換(10m / 24h 表記)を利用
- application.yaml に既定値を記載(secret は記載しない — 起動失敗で気づかせる)。テスト用は application-test.yaml で全項目設定

## 3. 監査リスナーのトランザクション構造(NFR-U3-03)

```text
主処理 @Transactional
  └─ publisher.publishEvent(AuditEvent)   ← 同期呼出(同一スレッド)
       └─ AuditLogListener.on(AuditEvent) @Transactional(REQUIRES_NEW)
            ├─ audit_log INSERT(独立コミット)
            └─ SecurityAlertService.evaluate(対象イベントのみ)
                 └─ メール送信(失敗は catch → MAIL_SEND_FAILED を別途 INSERT)
```

- リスナーは `@EventListener`(**@TransactionalEventListener ではない** — 主処理失敗時にも即時実行するため)
- リスナー内例外は catch し ERROR ログ(主処理へ非伝播)。SecurityAlert の評価・送信失敗も同様
- 例外パスの発行: GlobalExceptionHandler では**発行しない**(コンテキスト不足)。各サービスの失敗分岐で明示的に publish(業務ルールどおり)

## 4. メール送信構造(NFR-U3-05、Q2=C)

- `MailTemplateRegistry` — 起動時に `classpath:mail/*.mustache.html` を全件 `Mustache.compile`(cherry.mustache)してキャッシュ。1 件でも parse 失敗なら起動失敗(fail-fast)
- `MailService.send(templateId, lang, to, model)` — Registry から `mail/{templateId}_{lang}` を取得(なければ en にフォールバック)→ render → 件名は MessageSource(`mail.{templateId}.subject`)→ MimeMessage(text/html, UTF-8)送信
- 送信失敗: catch → `MAIL_SEND_FAILED` 監査イベント発行 → 戻り値 boolean(承認/却下 API が警告フラグに使用)
- テンプレート規約: 属性値ダブルクォート(エスケープ 4 文字仕様)、パーシャル・ラムダ・デリミタ変更は使わない、リンク URL は `{{{...}}}`(base-url + パスをサーバ側で組立済みの値のみ)

## 5. トークンローテーションの原子性(NFR-U3-01)

- `UPDATE refresh_token SET rotated_at = ? WHERE token_hash = ? AND rotated_at IS NULL AND revoked_at IS NULL AND expires_at > ?` を JPA の @Modifying クエリで発行し、更新行数 1 のときのみ新トークン発行
- 更新行数 0 → SELECT して状態判定(不在/期限切れ → 401、rotated 済み → 再利用検知フロー)
- ファミリ失効も一括 UPDATE(`WHERE family_id = ?`)

## 6. テスト構造(NFR-U3-06)

- 単体: サービス層(Mockito)、AppProperties 検証、MailTemplateRegistry(不正テンプレート fail-fast)
- 結合(@SpringBootTest + MockMvc + インメモリ H2): 認証フロー一式(登録→承認→ログイン→リフレッシュ→再利用検知→ログアウト)、ロックアウト、認可(ADMIN/USER/匿名)、セキュリティヘッダー、監査記録(REQUIRES_NEW の独立性は例外注入で検証)
- メール: JavaMailSender をモック(送信内容・言語の検証)。実 SMTP は devenv MailPit で手動確認
- cherry.mustache のテスト 197 件は既存のまま維持(build に組込済み)

## 7. フロントエンド(認証まわりの NFR 対応)

- apiClient: 401 → リフレッシュのシングルフライト(進行中 Promise 共有)→ 再試行 1 回のみ(無限ループ防止)。リフレッシュ API 自身の 401 は即ログアウト遷移
- sessionStorage アクセスは tokenStore モジュールに集約(XSS 面の露出を限定・Application Design のリスク受容を局所化)
- Problem Details(application/problem+json)を共通解釈し、フォームエラー(400 の invalid-params)と全体エラー(Toast/Alert)に振り分け

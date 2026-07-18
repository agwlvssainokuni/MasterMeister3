# 業務ルール — unit-03-auth-user-audit

**作成日**: 2026-07-19

## 1. 登録フロー(US-001/002/003)

### 登録申請(US-001)
1. 入力: メールアドレス(形式検証のみ)+ 現在の UI 言語
2. **メールアドレスの登録状況にかかわらず、レスポンスは常に同一**(202 Accepted 相当。列挙攻撃対策)
3. 未登録の場合のみ: registration_token を発行(SHA-256 ハッシュ保存)し、申請時 UI 言語で確認メールを送信(リンク: `/register/complete?token=...`)
4. 登録済みの場合: メール送信もトークン発行もしない(監査には REGISTRATION_REQUESTED / outcome=FAILURE, detail: already-registered で記録 — 応答では区別しないが証跡は残す)
5. 同一メールへの再申請(未登録): 新トークンを発行(旧トークンは有効期限内なら併存可。最初に使われた 1 つで完了)

### 登録完了(US-002)
1. トークン検証の順序: 存在 → 未使用(used_at IS NULL)→ 期限内。失敗理由は「無効または期限切れ」に統一(トークン推測への情報を与えない)
2. パスワード: 8 文字以上(SECURITY-12)。BCrypt で保存
3. 完了処理(単一トランザクション): app_user 作成(status=PENDING_APPROVAL、language=トークンの language)+ used_at 設定
4. 競合(同一メールで既にユーザ行が存在): トークンを使用済みにし「無効」応答

### 承認・却下(US-003)
- 承認: PENDING_APPROVAL → ACTIVE、承認メール(受信者 = 対象ユーザの language)
- 却下: PENDING_APPROVAL → REJECTED、却下メール(同上)
- 対象が PENDING_APPROVAL 以外なら 409(Problem Details)

## 2. ログインとロックアウト(US-005/006、D-11)

判定順序(タイミング差による情報漏えいを避けつつ、ロックを最優先):
1. ユーザ検索(email 小文字正規化)。不在 → 失敗(監査 actor = 入力メール)
2. `locked_until > now` → 失敗(ロック中。**正しいパスワードでも拒否**)
3. パスワード照合失敗 → failed_login_count++。閾値(`mm.app.auth.lockout.threshold`、既定 5)到達で `locked_until = now + mm.app.auth.lockout.duration`(既定 15 分)
4. status ≠ ACTIVE(PENDING_APPROVAL / REJECTED / DISABLED)→ 失敗(未承認は US-003 AC)
5. 成功: failed_login_count = 0、トークン発行(§3)
- 失敗応答はすべて同一メッセージ(原因を特定させない)。ロック中のみ「一時的にロックされています」を返す(D-11 のユーザ体験、正しい/誤ったパスワードの区別は不能)
- 管理者手動解除: locked_until = NULL + failed_login_count = 0(US-006)

## 3. トークン(US-005/007/008/009)

- **アクセストークン**: JWT(HS256、`mm.app.jwt.secret`)。クレーム: sub(user id)、email、role、exp(既定 10 分)。ステートレス検証
- **リフレッシュトークン**: ランダム 256bit(URL-safe Base64)。DB にはハッシュのみ。sessionStorage 保持(Application Design 確定)
- **ログイン**: 新 family_id(UUID)で refresh_token 発行
- **リフレッシュ(US-007)**: 提示トークンをハッシュ照合 →
  - 有効(現役・期限内): rotated_at 設定 → 同 family で新トークン発行 + 新アクセストークン(ローテーション、1 回限り)
  - **rotated 済みの再提示(US-008)**: 同 family の全トークンを revoked_at 設定(一括失効)+ 監査 TOKEN_REUSE_DETECTED + セキュリティイベントとしてアラート判定対象
  - 不在・期限切れ・revoked: 401
  - ローテーションは原子的に行う(同時リフレッシュの二重発行防止: token_hash の行を UPDATE ... WHERE rotated_at IS NULL で確保できた場合のみ成功)
- **ログアウト(US-009)**: 提示リフレッシュトークンの family 全体を revoked(明示終了)。アクセストークンは短命なので失効管理しない

## 4. 初期管理者ブートストラップ(US-004)

- 起動時(ApplicationRunner): `mm.app.admin.bootstrap.email` / `.password` が両方設定されていれば実行
- 同一 email のユーザが存在すれば何もしない(重複作成なし・上書きなし)
- 作成: role=ADMIN、status=ACTIVE、language=ja、theme=system。監査 ADMIN_BOOTSTRAPPED を記録

## 5. 監査記録基盤(US-042、D-20、H-07)

### 記録方式
- 各機能は `AuditEventPublisher.publish(AuditEvent)` を呼ぶ(Spring ApplicationEvent)
- リスナーは**同期** + `@Transactional(propagation = REQUIRES_NEW)` で audit_log に INSERT — 主処理のロールバックに巻き込まれない
- 例外パス: 主処理の catch/例外ハンドラでも publish する(outcome=FAILURE)。監査 INSERT 自体の失敗は主処理を失敗させず、アプリログ(ERROR)に退避

### イベント種別カタログ(Phase 1)+ 拡張規約
| event_type | actor | detail_json 例 |
|---|---|---|
| REGISTRATION_REQUESTED | 申請メール | {"alreadyRegistered": true/false} ※応答では区別不能、証跡のみ |
| REGISTRATION_COMPLETED | 申請メール | {} |
| USER_APPROVED / USER_REJECTED | 操作管理者 | {"targetUserId": n} |
| ADMIN_BOOTSTRAPPED | ブートストラップメール | {} |
| LOGIN_SUCCEEDED / LOGIN_FAILED | メール | 失敗時 {"reason": "BAD_CREDENTIALS"\|"LOCKED"\|"NOT_ACTIVE"} |
| ACCOUNT_LOCKED / ACCOUNT_UNLOCKED | 対象メール / 操作管理者 | {"until": ISO8601} |
| TOKEN_REFRESHED / TOKEN_REUSE_DETECTED | ユーザ | 再利用時 {"familyId": "..."} |
| LOGOUT | ユーザ | {} |
| MAIL_SEND_FAILED | (システム) | {"template": "...", "to": "..."}(Q4=A: 同期送信失敗の証跡) |
| SECURITY_ALERT_SENT | (システム) | {"alertType": "...", "count": n} |

- 拡張規約(④〜⑥向け): 種別コードは `名詞_過去分詞` の大文字スネーク、detail_json にはコード・数値・ID のみ(表示文字列禁止 — H-07)。connection_id / resource は該当時のみ設定

### セキュリティアラート(US-045、Q2=A)
- 対象: LOGIN_FAILED、TOKEN_REUSE_DETECTED(認可違反イベントは④以降で追加)
- 判定: イベント記録後、「直近 `mm.app.security-alert.window`(既定 10 分)の対象イベント件数 ≥ `mm.app.security-alert.threshold`(既定 10)」なら管理者全員(role=ADMIN)へメール
- クールダウン: security_alert_state.last_notified_at から window 経過まで再通知しない
- TOKEN_REUSE_DETECTED は件数によらず即時通知(重大イベント)

## 6. メール送信(Q4=A)

- 同期送信(JavaMailSender)。**HTML メール + 自作 Mustache エンジン**(NFR Requirements Q2=C)。テンプレート: 登録確認 / 承認 / 却下 / セキュリティアラート × ja/en(受信者言語: 申請系はトークンの language、既存ユーザは user.language、アラートは各管理者の language)。件名は MessageSource
- 送信失敗: 例外を握りつぶして MAIL_SEND_FAILED を監査記録。**US-001 の応答は送信失敗でも変わらない**(列挙対策維持)。承認・却下は操作自体は成立させ、画面に警告 Toast 用のフラグを応答に含める
- 設定: `mm.app.mail.from`、SMTP は Spring Boot 標準(`spring.mail.*`)。開発は MailPit(devenv)

## 7. ユーザ設定(US-047/048、Q3=A)

- `GET/PUT /api/me/preferences` — language / theme。PUT はログインユーザ自身のみ
- クライアント規則: 未ログイン時は localStorage(②実装)。ログイン成功時にサーバ値を取得して適用(サーバが正)。ログイン中の変更はサーバ保存 + localStorage 同期(次回の初期表示用)
- メール言語は常に user.language(US-047 AC)

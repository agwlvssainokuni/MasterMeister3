# フロントエンド設計 — unit-03-auth-user-audit

**作成日**: 2026-07-19
**前提**: ②のデザインシステム(コンポーネント・トークン・ThemeProvider・i18n)を本実装に統合。画面パターンは②の代表画面モックを踏襲。

## 1. ディレクトリ構成(②の方針を踏襲)

```text
frontend/src/
├── features/
│   ├── auth/          # ログイン・認証状態管理
│   │   ├── LoginPage.tsx
│   │   ├── AuthProvider.tsx     # 認証コンテキスト
│   │   ├── api.ts               # /api/auth/*, /api/me
│   │   └── tokenStore.ts        # sessionStorage 管理
│   ├── registration/  # 登録申請・パスワード設定
│   │   ├── RequestPage.tsx      # /register
│   │   └── CompletePage.tsx     # /register/complete?token=...
│   └── admin/
│       └── users/UserListPage.tsx  # /admin/users(②の UserListMock を実装化)
├── app/
│   ├── AppLayout.tsx  # AppShell 統合(ナビ項目・ユーザメニュー)
│   ├── routes.tsx     # ルート定義・ガード
│   └── apiClient.ts   # fetch ラッパ(Bearer 付与・401 時自動リフレッシュ・Problem Details 解釈)
└── design-system/     # ②のまま(共有基盤)
```

## 2. ルーティングと認可ガード

| パス | 画面 | ガード |
|---|---|---|
| /login | LoginPage | 未認証のみ(認証済みは / へ) |
| /register, /register/complete | 登録 2 画面 | 公開 |
| / | ホーム(③ではプレースホルダ: ログイン後の着地点) | 認証必須 |
| /admin/users | UserListPage | ADMIN のみ(USER は 403 画面) |
| /mock/* | ②のカタログ | DEV 限定(既存のまま) |

- `RequireAuth` / `RequireAdmin` コンポーネントでラップ。未認証は /login へリダイレクト(復帰先を state に保持)

## 3. 認証状態管理(AuthProvider)

- 状態: `{ user: MeResponse | null, status: "unknown" | "authenticated" | "anonymous" }`
- **tokenStore(sessionStorage)**: `mm.accessToken` / `mm.refreshToken`。タブ閉鎖で消える(Application Design 確定のリスク受容)
- 初期化: sessionStorage にトークンがあれば `GET /api/me` で復元(失敗→リフレッシュ試行→失敗なら anonymous)
- **apiClient の 401 処理**: 401 受信 → リフレッシュを 1 回試行(多重リクエストはシングルフライト化: 進行中の refresh Promise を共有)→ 成功なら元リクエスト再試行、失敗なら状態 anonymous + /login へ
- ログアウト: POST /api/auth/logout(失敗しても続行)→ sessionStorage クリア → /login

## 4. US-047/048 のサーバ設定統合(Q3=A)

- ログイン成功 / /api/me 復元時: 応答の language / theme を i18n・ThemeProvider に適用し、localStorage("mm.lang" / "mm.theme")にも同期(次回未認証時の初期表示用)
- ログイン中の切替(ヘッダーの LanguageSwitcher / ThemeToggle): 即時反映 + `PUT /api/me/preferences` + localStorage 同期。PUT 失敗時は Toast(warning)表示、表示はそのまま(次回ログインでサーバ値に戻る)
- 未認証時: ②の挙動のまま(localStorage のみ)
- 実装: ②の changeLanguage / setTheme をラップする `usePreferences` フックを features/auth に置き、認証状態に応じて保存先を切替(design-system 側は変更しない)

## 5. 画面仕様(②のモックとの差分中心)

### LoginPage(②の LoginMock を実装化)
- 実 API 接続。401 → 共通エラー文言、423 → ロック中文言(Alert danger)
- 成功: user を AuthProvider に設定、復帰先(または /)へ

### RequestPage(登録申請)
- メールアドレス 1 項目 + 送信。**応答は常に成功表示**(「メールを送信しました(登録済みの場合は送信されません)」の説明文言)
- 現在の UI 言語を language として送信(US-047)

### CompletePage(パスワード設定)
- token は URL クエリから。パスワード(8 文字以上・確認入力)+ 表示名(任意)
- 400: 「リンクが無効か期限切れ」+ 再申請への導線
- 成功: 「登録完了。管理者の承認をお待ちください」画面

### UserListPage(②の UserListMock を実装化)
- サーバサイドページング・status フィルタ・キーワード検索(API パラメータ)
- 操作: 承認 / 却下(PENDING_APPROVAL 行)、ロック解除(locked 行 — locked_until 表示)
- ConfirmDialog → API → 成功 Toast(メール送信失敗フラグが立っていれば warning Toast 併発)→ 一覧再取得

### AppLayout
- AppShell(②)にナビ(ホーム / ユーザ管理[ADMIN のみ])とユーザメニュー(表示名・ログアウト)を接続
- 認証済み画面のみ AppShell。/login・/register 系はカード単置きレイアウト(②の LoginMock と同様)

## 6. i18n 辞書

- 新規 namespace: `auth`(ログイン・登録)、`admin`(ユーザ管理)を ja/en で追加(②の common に追記せず分離)
- ②のモック辞書(mock namespace)はモック専用のまま維持

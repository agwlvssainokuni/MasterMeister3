# フロントエンド設計 — unit-04-connection-permission

**作成日**: 2026-07-19
**前提**: ③で確立した規約に従う — 各 feature は自前の `api.ts`(ドメイン動詞関数 + 型)、feature 間参照禁止、app 層基盤は features を参照しない、DTO/関数命名規約

## 1. ディレクトリ構成

```text
frontend/src/features/admin/
├── connections/
│   ├── api.ts                  # listConnections / createConnection / updateConnection / deleteConnection / testConnection / importSchema / fetchSchema + 型
│   ├── ConnectionListPage.tsx  # /admin/connections
│   └── ConnectionEditPage.tsx  # /admin/connections/new, /admin/connections/:id
├── permissions/
│   ├── api.ts                  # fetchPermissions / setPermissionEntry / removePermissionEntry / exportPermissionYaml / importPermissionYaml + 型
│   └── PermissionPage.tsx      # /admin/connections/:id/permissions
└── groups/
    ├── api.ts                  # listGroups / createGroup / renameGroup / deleteGroup / listGroupMembers / addGroupMember / removeGroupMember + 型
    └── GroupListPage.tsx       # /admin/groups
```

- users(③)と同じ `features/admin/` 配下に並置。相互参照はしない(プリンシパル選択に必要なユーザ一覧は permissions/api.ts が自分の API 関数として持つ)

## 2. ルーティングとナビゲーション

| パス | 画面 | ガード |
|---|---|---|
| /admin/connections | ConnectionListPage | ADMIN |
| /admin/connections/new, /admin/connections/:id | ConnectionEditPage | ADMIN |
| /admin/connections/:id/permissions | PermissionPage | ADMIN |
| /admin/groups | GroupListPage | ADMIN |

- AppLayout のナビに「接続管理」「グループ管理」を追加(ADMIN のみ — ③の users と同様)
- ルート定義は app/routes.tsx に追記(合成系からの feature 参照は規約どおり許容)

## 3. 画面仕様

### ConnectionListPage(②の管理系一覧パターン)
- 一覧: name / dbType / host:port / databaseName / 取込状況(importedAt)。行アクション: 編集・権限設定(遷移)・削除(ConfirmDialog — カスケード削除の警告文言)
- 「新規接続」ボタン → /admin/connections/new

### ConnectionEditPage
- フォーム: name / dbType(Select: MySQL / MariaDB / PostgreSQL / H2)/ host / port / databaseName / username / password / options / poolMaxSize / poolTimeoutMs
- パスワード: 新規は必須。**編集時は空欄 = 変更しない**(プレースホルダで明示)。入力値は表示専用マスク(②の PasswordInput)
- 「接続テスト」ボタン(Q4=A): 保存済み接続に対して実行(未保存の変更がある場合は保存を促す)。結果を Alert(success / danger + 理由)で表示
- 「スキーマ取込」ボタン: ConfirmDialog(全置換の説明)→ 実行 → 結果(スキーマ/テーブル/カラム件数)を Toast + 画面に取込済みツリー(読み取り専用の概要)を表示
- 409(名前重複)・502(取込失敗)は ApiError.code で文言分岐

### PermissionPage(Q5=A: プリンシパル軸ツリー)
- 上部: プリンシパル選択(種別トグル USER / GROUP + 検索付き Select — ユーザは email、グループは name)
- 本体: スキーマ → テーブル → カラムの展開ツリー(取込済みメタデータ)。各行に:
  - 主権限: 4 値 Select(未設定 / NONE / READ / UPDATE)。「未設定」選択 = エントリ削除(D-18 の 2 操作を UI 上は 1 つの Select で表現し、API では PUT / DELETE に振り分け)
  - 補助権限(スキーマ・テーブル行のみ): CREATE / DELETE の 3 状態(未設定 / 許可 / 不許可)
- 変更は即時 API 反映(楽観的更新はしない: 応答確定後に表示更新 + エラー時 Toast)
- **孤児エントリ(Q2=A)**: 選択中プリンシパルのエントリのうちメタデータに存在しない対象を「対象なし」セクションに一覧表示(Badge: warning)。行ごとに削除ボタン
- ヘッダー: 「YAML エクスポート」(ダウンロード)/「YAML インポート」(ファイル選択 → ConfirmDialog[全置換の説明] → 実行)。拒否時は理由一覧(行位置・理由)を Alert に表示

### GroupListPage
- 一覧: name / メンバー数。作成(モーダル)・改名(モーダル)・削除(ConfirmDialog — 権限設定も消えることを警告)
- 行展開または詳細モーダルでメンバー管理: 現メンバー一覧(削除ボタン)+ ユーザ検索追加(groups/api.ts の searchUsers — ③ /api/admin/users を自 feature の関数として定義)

## 4. 状態・エラー処理

- 一覧系は③ UserListPage と同じパターン(useState + load 関数、ConfirmDialog、成功/警告 Toast)
- すべての API 呼び出しは feature の api.ts 経由(apiClient 直接呼び出し禁止 — ③規約)
- 権限ツリーは接続単位で全件取得(メタデータ + プリンシパルのエントリ)し、クライアントで結合表示。テーブル数が多い場合もスキーマ/テーブル折りたたみで対応(ページングなし — 管理者操作のため)

## 5. i18n 辞書

- namespace `admin`(③新設)に connections / permissions / groups のキー群を追加(ja/en)
- 理由コード(CONNECT_FAILED 等)・YAML 拒否理由コードは辞書で表示文言に解決(H-07 の方針と同じ: API はコードのみ返す)

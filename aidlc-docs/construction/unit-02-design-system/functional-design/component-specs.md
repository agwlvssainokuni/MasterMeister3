# 共有 UI コンポーネント仕様 — unit-02-design-system

**作成日**: 2026-07-19
**実装方式**: React 関数コンポーネント + CSS Modules(Q3=A)。スタイルはセマンティックトークンのみ参照。
**配置**: `frontend/src/design-system/components/`(本番コード。モックではなくアプリ本体が使う)

## 1. インベントリ(②で実装する範囲)

③〜⑥の画面(認証・管理系一覧・レコード編集・クエリ実行)を成立させる最小完全セット。

| 分類 | コンポーネント |
|---|---|
| 入力 | Button, IconButton, TextInput, PasswordInput, TextArea, Select, Checkbox, RadioGroup, Switch, FormField, SearchInput |
| 表示 | Badge, Alert, Card, Spinner, EmptyState, CodeBlock, KeyValueList |
| オーバーレイ | Modal, ConfirmDialog, Toast(+ ToastProvider), Dropdown, Tooltip |
| ナビゲーション | AppShell(Header / SideNav / Content), Tabs, Pagination |
| データ | Table(高密度・ソート・選択・セル状態) |
| アプリ固有 | ThemeToggle, LanguageSwitcher |

日付入力はブラウザネイティブ(`<input type="date">` 等)を TextInput のバリアントとして扱う(専用 DatePicker は作らない — 必要になった時点で⑤で判断)。

## 2. 主要コンポーネント仕様

### Button / IconButton
- Props: `variant: "primary" | "secondary" | "danger" | "ghost"`、`size: "sm" | "md"`(既定 md)、`loading`(Spinner 内蔵・操作不可)、`disabled`
- danger は破壊的操作(削除・失効)専用。IconButton は `aria-label` 必須

### TextInput / PasswordInput / TextArea / Select / SearchInput
- Props: `invalid`(エラー枠線)、`disabled`、`readOnly`。ネイティブ要素の属性は透過
- PasswordInput は表示/非表示トグル(IconButton)内蔵
- Select はネイティブ `<select>` ベース(カスタムドロップダウン化は将来判断)

### Checkbox / RadioGroup / Switch
- ラベルクリックで操作可能。Switch は `role="switch"` + `aria-checked`。テーマ切替等の即時反映系に使用

### FormField
- Props: `label`、`required`(視覚 + `aria-required`)、`help`、`error`
- `error` があるとき子入力へ `invalid` と `aria-describedby` を接続。ラベルは `htmlFor` で関連付け

### Table
- 高密度(行高 `--mm-table-row-height`)、ヘッダー固定(縦スクロール)、横スクロール対応
- 機能: カラムソート(`aria-sort`)、行選択(チェックボックス列)、セル状態(編集済み = primary-subtle 背景 / エラー = danger-subtle 背景。⑤の編集バッファ表示に使用)
- 状態表示: ローディング(Spinner オーバーレイ)/ 空(EmptyState)を内蔵パターンとして提供
- ページングは Table 外の Pagination と組み合わせる(サーバサイドページング前提)

### Modal / ConfirmDialog
- フォーカストラップ、`Esc` で閉じる、背景クリックは既定で閉じない(業務系: 誤操作防止)、`aria-modal` + `aria-labelledby`
- ConfirmDialog: `tone: "default" | "danger"`。danger は確認ボタンを danger バリアントに

### Toast / ToastProvider
- `success | info | warning | danger`、自動消滅(既定 5 秒、danger は手動クローズのみ)、`aria-live="polite"`(danger は `assertive`)

### Dropdown / Tooltip
- Dropdown: メニュー項目のキーボード操作(↑↓/Enter/Esc)。Tooltip: ホバー + フォーカスで表示、`aria-describedby`

### Tabs
- `role="tablist"` 準拠、←→ キーで移動。クエリ実行画面(ビルダー⇄SQL)等で使用

### AppShell
- Header(アプリ名・LanguageSwitcher・ThemeToggle・ユーザメニュー枠)+ SideNav(折りたたみ可)+ Content
- ②のモックでは管理系一覧・レコード編集・クエリ実行画面の外枠として使用。ナビ項目は i18n キーで定義

### Badge / Alert / Card / Spinner / EmptyState / CodeBlock / KeyValueList
- Badge: `tone: neutral | primary | success | warning | danger`(権限表示・ステータス表示に使用)
- Alert: ページ内の恒常的な通知(Toast は一時通知)
- CodeBlock: `--mm-font-mono`、SQL 表示用(コピー・折返し切替)
- EmptyState: アイコン + メッセージ + アクション(任意)

### ThemeToggle / LanguageSwitcher
- 機能仕様は screen-specs.md §5(US-047/048 前段)を参照

## 3. 共通規約

- すべての操作要素はキーボード到達可能、フォーカスリング(`--mm-focus-ring`)表示
- 文言は直接記述禁止 — react-i18next(Q6=A)の翻訳キー経由(ja/en — D-09)
- カラーはセマンティックトークンのみ参照(プリミティブ層直接参照禁止)
- 各コンポーネントは `*.tsx` + `*.module.css` + 必要に応じ `*.test.tsx`(RTL)のセット
- Props 型は `export` し、③以降の画面実装から再利用する

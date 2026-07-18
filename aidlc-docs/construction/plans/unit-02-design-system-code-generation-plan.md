# ユニット② design-system — Code Generation プラン

**作成日**: 2026-07-19
**入力**: functional-design/(design-tokens / component-specs / screen-specs / mock-structure)、nfr-requirements/、nfr-design/
**ストーリー対応**: US-047(言語切替)/ US-048(テーマ切替)の UI 前段(主割当は③)。他ストーリーは画面モックとして先行可視化(実装は③〜⑥)
**方針**: 各ステップ完了ごとに `./gradlew :frontend:npmTest` 相当のチェックを通し、コミットする(細かめの節目)。本プランが Code Generation の唯一の正とする。

## Step 1: 依存導入とトークン基盤

- [x] 1-1: npm 依存追加(react-router-dom 7.18.1 / i18next 26.3.6 / react-i18next 17.0.10 / @fontsource/noto-sans-jp 5.2.9 / @fontsource/noto-sans-mono 5.2.10)
- [x] 1-2: `design-system/tokens/tokens.css` — プリミティブ(5 パレット × 10 段)+ セマンティック(ライト `:root` / ダーク `:root[data-theme="dark"]`)+ タイポ・スペーシング・形状・寸法トークン
- [x] 1-3: フォント import(main.tsx: Sans JP 400/500/700、Mono 400/700)と `--mm-font-*` 適用
- [x] 1-4: `public/theme-init.js`(FOUC 防止・外部ファイル方式)+ `index.html` head 参照

## Step 2: テーマ・i18n・ルーティング骨格

- [x] 2-1: `design-system/theme/ThemeProvider.tsx`(light/dark/system、localStorage("mm.theme")、matchMedia 追従)
- [x] 2-2: `design-system/i18n/`(i18next 初期化、common 辞書 ja/en、初期言語 = localStorage("mm.lang") → navigator.language、`<html lang>` 同期)
- [x] 2-3: main.tsx 再構成 — Router 導入(`/` は①の動作確認ページ維持)+ Provider 階層 + DEV 限定 `/mock/*` 遅延登録
- [x] 2-4: ThemeToggle / LanguageSwitcher コンポーネント(Dropdown ベース)

## Step 3: 入力系コンポーネント

- [x] 3-1: Button / IconButton(variant×size×loading×disabled)
- [x] 3-2: TextInput / PasswordInput / TextArea / Select / SearchInput(invalid/disabled/readOnly)
- [x] 3-3: Checkbox / RadioGroup / Switch
- [x] 3-4: FormField(label/required/help/error、aria 接続)
- [x] 3-5: RTL テスト(Button の variant/disabled、FormField の error 接続、PasswordInput のトグル)

## Step 4: 表示系・オーバーレイ系コンポーネント

- [x] 4-1: Badge / Alert / Card / Spinner / EmptyState / CodeBlock / KeyValueList
- [x] 4-2: Modal / ConfirmDialog(フォーカストラップ、Esc、aria-modal)
- [x] 4-3: Toast + ToastProvider(自動消滅、aria-live)
- [x] 4-4: Dropdown(キーボード操作)/ Tooltip / Tabs(tablist 準拠)
- [x] 4-5: RTL テスト(Modal フォーカストラップ・Esc、Toast 表示消滅、Tabs キーボード)

## Step 5: レイアウト・データ系コンポーネント

- [x] 5-1: AppShell(Header 48px / SideNav 220px 折りたたみ / Content)
- [x] 5-2: Table(高密度・ヘッダー固定・ソート aria-sort・行選択・セル状態 edited/error・ローディング/空内蔵)
- [x] 5-3: Pagination
- [x] 5-4: RTL テスト(Table ソート・選択、AppShell ナビ折りたたみ)

## Step 6: モックカタログ(トークン・コンポーネント)

- [x] 6-1: `mock/MockCatalog.tsx` — カタログレイアウト(左ナビ + 上部バーに ThemeToggle/LanguageSwitcher 常設)
- [x] 6-2: TokensPage(パレット全段・セマンティック対応表・タイポ・スペーシング・コントラスト確認欄)
- [x] 6-3: ComponentsPage(全コンポーネント × 状態網羅: hover/focus/disabled/error/loading)

## Step 7: 代表画面モック 4 系統

- [x] 7-1: サンプルデータ(`mock/data/` — ユーザ一覧・レコード・クエリ結果の静的データ)
- [x] 7-2: Login モック(エラー状態・ローディング切替つき)
- [x] 7-3: UserList モック(一覧・フィルタ・EmptyState・ConfirmDialog・Toast)
- [x] 7-4: RecordEdit モック(編集済み/エラーセル・新規/削除行・一括反映バー)
- [x] 7-5: QueryRun モック(ビルダー/SQL タブ・結果テーブル・実行状態)

## Step 8: DoD 検証と文書化

- [x] 8-1: `./gradlew clean build` 通過(tsc / ESLint / ヘッダー検査 / Vitest / backend テスト)
- [x] 8-2: NFR-U2-02 検証 — `vite build` の dist/ に mock チャンク・サンプルデータ・カタログ文言が含まれないことを文字列検索で確認
- [x] 8-3: NFR-U2-01 検証 — dist/ に外部オリジン参照(http/https URL のアセット読込)がないことを確認
- [x] 8-4: NFR-U2-05 記録 — バンドルサイズ(gzip)実測を code-summary.md に記録(目安 300KB 以下)
- [x] 8-5: dev サーバで `/mock` 動作確認(テーマ × 言語 4 通り、OS 連動、localStorage 永続)
- [x] 8-6: `aidlc-docs/construction/unit-02-design-system/code/code-summary.md` 作成

## 完了ゲート

Step 8 完了後、モックの確認方法(`npm run dev` → `/mock`)と確認観点(mock-structure.md §3)を提示し、**ユーザのモック承認(D-08)をもって本ステージ承認**とする。

## テスト方針(拡張ルール)

- PBT(partial): 本ユニットに PBT ブロッキング対象(PBT-02/03 の該当アルゴリズム)なし。UI コンポーネントは RTL による状態・アクセシビリティのテストを実施(PBT-07/08 は該当プロパティなしのため N/A、①導入済みの fast-check 基盤は維持)
- Security Baseline: NFR-U2-01/02 の検証を Step 8 に組込(ブロッキング)

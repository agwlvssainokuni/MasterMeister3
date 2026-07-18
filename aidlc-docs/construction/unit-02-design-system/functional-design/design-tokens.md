# デザイントークン仕様 — unit-02-design-system

**作成日**: 2026-07-19
**確定事項**: Q1=A(業務システム標準)、Q2=A(ブルー系)、Q4=B(Noto Sans JP 同梱)、Q5=A(14px・高密度)

## 1. 実装方式と命名規約

- CSS カスタムプロパティで定義(D-10)。プレフィックスは `--mm-`
- **2 層構造**:
  - プリミティブ層 `--mm-palette-{color}-{stop}` — 生のカラーパレット。コンポーネントから直接参照しない
  - セマンティック層 `--mm-color-{role}` ほか — 役割ベース。コンポーネント・画面はこの層のみ参照する
- テーマ切替はセマンティック層の値の差し替えで行う:
  - `:root` にライトテーマの値(既定)
  - `:root[data-theme="dark"]` にダークテーマの上書き
  - 「システム連動」時は JS が `matchMedia("(prefers-color-scheme: dark)")` を監視して `data-theme` を付け替える(切替仕様は screen-specs.md §5)

## 2. カラーパレット(プリミティブ層)

ブルーをプライマリとし、ニュートラルグレー + 状態色 3 系統。各色 50〜900 の 10 段(値は 500 を代表値として記載、全段は実装時に生成し、トークンカタログページで確認する)。

| パレット | 代表値(500) | 用途 |
|---|---|---|
| `blue` | #2563EB | プライマリ(Q2=A) |
| `gray` | #6B7280 | テキスト・境界・背景の基調 |
| `green` | #16A34A | 成功 |
| `amber` | #D97706 | 警告 |
| `red` | #DC2626 | エラー・破壊的操作 |

## 3. セマンティックカラートークン

| トークン | ライト | ダーク | 用途 |
|---|---|---|---|
| `--mm-color-bg` | gray-50 | gray-900 | アプリ背景 |
| `--mm-color-surface` | white | gray-800 | カード・パネル・テーブル背景 |
| `--mm-color-surface-raised` | white | gray-700 | モーダル・ドロップダウン |
| `--mm-color-text` | gray-900 | gray-100 | 本文 |
| `--mm-color-text-muted` | gray-500 | gray-400 | 補助テキスト・ラベル |
| `--mm-color-text-inverse` | white | gray-900 | プライマリボタン上の文字 |
| `--mm-color-border` | gray-200 | gray-600 | 境界線 |
| `--mm-color-border-strong` | gray-300 | gray-500 | 入力枠線 |
| `--mm-color-primary` | blue-600 | blue-500 | 主操作・リンク・選択状態 |
| `--mm-color-primary-hover` | blue-700 | blue-400 | ホバー |
| `--mm-color-primary-subtle` | blue-50 | blue-900/30% | 選択行背景・情報背景 |
| `--mm-color-success` / `-subtle` | green-600 / green-50 | green-500 / green-900/30% | 成功表示 |
| `--mm-color-warning` / `-subtle` | amber-600 / amber-50 | amber-500 / amber-900/30% | 警告表示 |
| `--mm-color-danger` / `-hover` / `-subtle` | red-600 / red-700 / red-50 | red-500 / red-400 / red-900/30% | エラー・破壊的操作 |
| `--mm-color-focus-ring` | blue-500/50% | blue-400/60% | フォーカスリング |

**コントラスト基準**: 本文・操作要素は WCAG AA(4.5:1、大きい文字 3:1)を両テーマで満たすこと。トークンカタログページにコントラスト確認欄を設ける。

## 4. タイポグラフィ

- `--mm-font-sans`: `"Noto Sans JP", "Hiragino Kaku Gothic ProN", "Yu Gothic UI", Meiryo, sans-serif`(Q4=B: Noto Sans JP を WAR に同梱。フォールバックにシステム日本語フォント)
- `--mm-font-mono`: `"Noto Sans Mono", ui-monospace, "SF Mono", Menlo, Consolas, monospace`(SQL・コード表示。**Noto Sans Mono も同梱**し等幅も見た目を揃える — レビュー時ユーザ指示。CJK 文字はフォールバックの Noto Sans JP / システムフォントで表示)
- **フォント同梱方針**: Noto Sans JP(400/500/700)+ Noto Sans Mono(400/700)の woff2 を `frontend/src/design-system/tokens/fonts/` に配置し `@font-face`(`font-display: swap`)で読み込む。**ライセンス: いずれも SIL OFL 1.1** — 同梱物にライセンス文を添付し、D-17 と同様にサードパーティライセンスとして文書化する
- サイズ(ベース 14px — Q5=A):

| トークン | 値 | 用途 |
|---|---|---|
| `--mm-font-size-xs` | 11px | バッジ・注記 |
| `--mm-font-size-sm` | 12px | テーブル補助・キャプション |
| `--mm-font-size-md` | 14px | **本文・テーブル・入力(基準)** |
| `--mm-font-size-lg` | 16px | セクション見出し |
| `--mm-font-size-xl` | 18px | ページタイトル |
| `--mm-font-size-2xl` | 22px | ログイン画面タイトル等 |

- ウェイト: `--mm-font-weight-normal` 400 / `-medium` 500 / `-bold` 700
- 行高: `--mm-line-height-tight` 1.3(テーブル・見出し)/ `--mm-line-height-normal` 1.6(本文)

## 5. スペーシング・形状・エレベーション

- スペーシング(4px グリッド): `--mm-space-0_5` 2px / `-1` 4px / `-2` 8px / `-3` 12px / `-4` 16px / `-5` 20px / `-6` 24px / `-8` 32px / `-10` 40px
- 角丸: `--mm-radius-sm` 3px(入力・ボタン)/ `--mm-radius-md` 6px(カード・モーダル)/ `--mm-radius-full` 9999px(バッジ)
- 影: `--mm-shadow-sm`(カード)/ `--mm-shadow-md`(ドロップダウン)/ `--mm-shadow-lg`(モーダル)— ダークテーマでは影を弱め境界線で補う
- フォーカス: `--mm-focus-ring: 0 0 0 2px var(--mm-color-focus-ring)` — キーボードフォーカスで全操作要素に必須
- Z-index: `--mm-z-dropdown` 1000 / `--mm-z-modal` 1300 / `--mm-z-toast` 1400 / `--mm-z-tooltip` 1500
- トランジション: `--mm-duration-fast` 100ms(ホバー)/ `--mm-duration-normal` 200ms(モーダル・テーマ切替)

## 6. コンポーネント寸法トークン(高密度 — Q5=A)

| トークン | 値 | 用途 |
|---|---|---|
| `--mm-control-height-sm` | 26px | テーブル内ボタン・コンパクト入力 |
| `--mm-control-height-md` | 32px | **標準のボタン・入力(基準)** |
| `--mm-table-row-height` | 34px | テーブル行(1 行表示・省略記号) |
| `--mm-header-height` | 48px | アプリヘッダー |
| `--mm-sidenav-width` | 220px | サイドナビ(折りたたみ時 48px) |

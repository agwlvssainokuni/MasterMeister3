# NFR Design — unit-02-design-system(簡略)

**作成日**: 2026-07-19
**対応**: nfr-requirements.md の NFR-U2-01〜05 を実装構造に落とし込む。

## 1. テーマ機構(D-10 / US-048 前段)

- `ThemeProvider`(`design-system/theme/`): React Context で `theme: "light" | "dark" | "system"` と `setTheme` を提供
  - 初期化: `localStorage("mm.theme")` → なければ `system`
  - 反映: `document.documentElement` の `data-theme` 属性に解決値(light/dark)を設定。`system` 時は `matchMedia("(prefers-color-scheme: dark)")` を購読して追従
  - 初期表示のちらつき(FOUC)防止: **外部ファイル方式**(レビューで確定 — 2026-07-19)。`frontend/public/theme-init.js`(数行、localStorage 読取→ data-theme 設定)を `index.html` の `<head>` で同期読み込みする。インラインスクリプトを使わないため、③の CSP 本則は `script-src 'self'` のみで完結する(静的 index.html と nonce/hash の相性問題を回避)
- トークン CSS は `:root { ... }` + `:root[data-theme="dark"] { ... }` の 2 ブロック(design-tokens.md §1)

## 2. i18n 機構(D-09 / US-047 前段)

- `design-system/i18n/`: i18next 初期化(`resources` に ja/en の辞書 JSON を静的 import、`fallbackLng: "en"`)
- 初期言語: `localStorage("mm.lang")` → `navigator.language`(ja 系→ja、他→en)。detector ライブラリは使わない(NFR Requirements 判断メモ)
- 言語切替時: `i18n.changeLanguage` + `<html lang>` 同期 + `localStorage` 保存
- 辞書ファイルは機能単位で分割(`common.json` を②で作成、③以降は機能別 namespace を追加)— H-07(監査のロケール非依存)は③以降のバックエンド設計事項として申し送り

## 3. フォント読み込み(NFR-U2-03)

- `main.tsx` で Fontsource の該当ウェイトのみ import:
  - `@fontsource/noto-sans-jp/{400,500,700}.css`
  - `@fontsource/noto-sans-mono/{400,700}.css`
- Fontsource の CSS は unicode-range 分割済み → ブラウザが表示に必要なサブセット woff2 のみ取得(NFR-U2-03 成立)。Vite が woff2 を dist/assets へバンドルし self ホストになる(NFR-U2-01 成立)

## 4. モック除外機構(NFR-U2-02)

```
main.tsx:
  const MockCatalog = import.meta.env.DEV
    ? React.lazy(() => import("./mock/MockCatalog"))
    : null
  → DEV のときのみ /mock/* ルートを Router に登録
```

- production ビルドでは `import.meta.env.DEV` が定数 `false` に置換され、Rollup が `./mock/` 配下を到達不能コードとしてチャンク生成しない
- **検証**(Code Generation DoD): `vite build` 後に `dist/` を検索し、mock 固有の識別子(例: カタログページタイトルのキー文字列)が存在しないことを確認する

## 5. Tree-shaking 構成(NFR-U2-05)

- `design-system/components/index.ts` で named re-export(barrel)。`sideEffects` の扱い: CSS import があるため package.json の `sideEffects` は設定せず、コンポーネント単位の import 粒度で管理
- バンドルサイズは `vite build` の出力レポートで確認し、code-summary.md に②完了時点の実測値を記録する

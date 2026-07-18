# Code Summary — ユニット② design-system(デザインシステム+モック)

**作成日**: 2026-07-19
**対応プラン**: unit-02-design-system-code-generation-plan.md(8 ステップ)

## 生成物一覧

### デザインシステム本体(本番同梱)— `frontend/src/design-system/`
- `tokens/tokens.css` — 2 層トークン(プリミティブ 5 パレット × 10 段 + セマンティック、ライト/ダーク両テーマ)、タイポ・スペーシング・形状・Z-index・寸法
- `theme/ThemeProvider.tsx` — light/dark/system(localStorage("mm.theme")、matchMedia 追従)。FOUC 防止は `public/theme-init.js`(外部ファイル方式 — CSP は script-src 'self' で完結)
- `i18n/` — i18next 初期化(ja/en 辞書静的 import、ブラウザ言語検出、html lang 同期、localStorage("mm.lang"))
- `components/` — 27 コンポーネント(Button/IconButton、TextInput/PasswordInput/TextArea/Select/SearchInput、Checkbox/RadioGroup/Switch、FormField、Badge/Alert/Card/Spinner/EmptyState/CodeBlock/KeyValueList、Modal/ConfirmDialog、Toast、Dropdown/Tooltip、Tabs、AppShell、Table、Pagination、ThemeToggle/LanguageSwitcher)+ barrel(named export)
- フォント: Fontsource(@fontsource/noto-sans-jp 400/500/700、@fontsource/noto-sans-mono 400/700)を main.tsx で import — unicode-range サブセット分割・self ホスト。**ライセンス: SIL OFL 1.1(node_modules 内 LICENSE 同梱)**

### モックカタログ(dev 専用)— `frontend/src/mock/`
- `MockCatalog.tsx` — /mock 配下のカタログ(左ナビ + テーマ/言語切替バー常設)
- `pages/TokensPage.tsx` / `pages/ComponentsPage.tsx` — トークン・コンポーネントカタログ
- `pages/screens/` — 代表画面 4 系統(Login / UserList / RecordEdit / QueryRun)
- `data/sample.ts` — 静的サンプルデータ(日本語中心)
- `i18n-mock.ts` — モック専用辞書(addResourceBundle で動的登録 — 本番辞書と分離)

### 変更(既存)
- `main.tsx` — Router 導入(`/` = ①の動作確認ページ維持、DEV 限定 `/mock/*` lazy)+ Provider 階層
- `index.html` — theme-init.js の head 参照
- `eslint.config.js` — public/ 用 globals 追加。`scripts/check-license-header.mjs` — public/ を検査対象に追加
- `vite.config.ts` — vitest globals: true(RTL 自動クリーンアップ)
- `tsconfig.json` — resolveJsonModule 追加

## 実装中の技術判断(プランからの差分)

| 事項 | 判断 |
|---|---|
| ThemeToggle / LanguageSwitcher | 汎用 Dropdown ではなくスタイル済みネイティブ `<select>` で実装(「ネイティブ要素ベース」原則に沿う。FD の記載「Dropdown ベース」より単純化) |
| @testing-library/user-event | RTL テストの操作記述に追加導入 |
| Toast の danger | 自動消滅なし・手動クローズのみ(見落とし防止) |

## テスト(RTL)

22 件(挙動を持つコンポーネント中心): Button(click/disabled/loading)、FormField(aria 接続/required)、PasswordInput(トグル)、Modal(Esc/フォーカストラップ循環/aria)、Toast(自動消滅/danger 手動)、Tabs(クリック/矢印キー)、Table(ソート aria-sort/行選択/EmptyState)、AppShell(折りたたみ)、App、fast-check サンプル。

## DoD 検証結果(2026-07-19 実施)

| 検証項目 | 結果 |
|---|---|
| 8-1 `./gradlew clean build` | ✅ 成功(tsc / ESLint / ヘッダー検査 62 ファイル / Vitest 22 件 / backend テスト込み) |
| 8-2 mock 混入検査(NFR-U2-02) | ✅ dist/ に mock 識別子・カタログ文言・サンプルデータなし(文字列検索で確認) |
| 8-3 外部参照検査(NFR-U2-01) | ✅ dist/ の外部 URL は名前空間 URI・ライセンス文・エラードキュメント URL の文字列のみ(アセット読込なし)。フォントは woff2 384 ファイルを self ホスト |
| 8-4 バンドルサイズ(NFR-U2-05) | ✅ JS gzip **88.5KB** / CSS gzip 110KB(Fontsource の @font-face 定義を含む)— 合計 198KB < 目安 300KB。woff2 は計 8.6MB だが unicode-range により必要サブセットのみ遅延取得 |
| 8-5 dev サーバ確認 | ✅ /mock 200・MockCatalog モジュール変換 200・theme-init.js 200(配信確認)。**テーマ × 言語 4 通りの視覚確認はモック承認ゲート(D-08)でユーザが実施** |

## モックの確認方法(D-08)

```bash
cd frontend && npm run dev
# → http://localhost:5173/mock (ポートは起動ログ参照)
```

確認観点: mock-structure.md §3(トークン/コンポーネント状態網羅/代表画面 4 系統/テーマ・言語切替)

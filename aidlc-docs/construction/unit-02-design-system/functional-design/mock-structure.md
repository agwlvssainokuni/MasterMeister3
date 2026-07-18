# モックカタログ構成 — unit-02-design-system

**作成日**: 2026-07-19
**位置づけ**: D-08(モック承認ゲート)の確認場所。モックカタログは dev 専用、デザインシステム本体は本番コード(使い捨てにしない)。

## 1. ディレクトリ構成

```text
frontend/src/
├── design-system/                # 本番コード(WAR に同梱される)
│   ├── tokens/
│   │   ├── tokens.css            # プリミティブ + セマンティックトークン(ライト + ダーク)
│   │   ├── fonts.css             # @font-face(Noto Sans JP)
│   │   └── fonts/                # woff2 + OFL ライセンス文
│   ├── components/               # 共有コンポーネント(*.tsx + *.module.css + *.test.tsx)
│   ├── theme/                    # ThemeProvider(data-theme 制御、localStorage、matchMedia)
│   └── i18n/                     # i18next 初期化 + 辞書 ja/en(共通分)
└── mock/                         # モックカタログ(dev 専用・本番ビルド除外)
    ├── MockCatalog.tsx           # カタログのルーティングとレイアウト
    ├── data/                     # 画面モック用の静的サンプルデータ
    └── pages/
        ├── TokensPage.tsx        # トークンカタログ(パレット・タイポ・スペーシング・コントラスト確認)
        ├── ComponentsPage.tsx    # コンポーネントカタログ(全コンポーネント × 状態一覧)
        └── screens/              # 代表画面 4 系統(Login / UserList / RecordEdit / QueryRun)
```

## 2. ルーティングと本番ビルド除外(NFR-06)

- ルーターとして **react-router-dom を②で導入**する(モックカタログのページ遷移に必要。③以降の本実装でも使用するため、ここでの導入が実装基盤になる)
- ルート設計: `/mock` 配下にカタログを配置(`/mock/tokens`, `/mock/components`, `/mock/screens/{login|user-list|record-edit|query-run}`)。`/` は①の動作確認ページを維持
- **除外方式**: `import.meta.env.DEV` ガード + dynamic import(`React.lazy`)
  - dev(`npm run dev`)でのみ `/mock` ルートを登録。production ビルドでは条件が定数 false となり、Vite/Rollup が mock チャンクを生成しない
  - **検証手順を Code Generation の DoD に含める**: `vite build` 後の `dist/` に mock 関連チャンク・サンプルデータが含まれないこと(文字列検索で確認)
- カタログ共通レイアウト: 左にページナビ、上部バーに ThemeToggle(light/dark/system)と LanguageSwitcher(ja/en)を常設 — 全ページで 4 通り(テーマ × 言語)の確認ができる

## 3. モック承認ゲート(D-08)の運用

- 確認方法: `npm run dev`(または `./gradlew build` 後の WAR ではなく dev サーバ)で `/mock` を開いて確認
- 確認観点(承認依頼時に提示):
  1. トークン: 配色(両テーマ)、コントラスト、タイポグラフィ、密度
  2. コンポーネント: 全コンポーネントの状態網羅(hover/focus/disabled/error/loading)
  3. 代表画面 4 系統: screen-specs.md の確認点
  4. テーマ・言語切替の動作(即時反映・保存・OS 連動)
- 承認後、モックの画面パターン・コンポーネントはそのまま③〜⑥の実装土台となる(モックページ自体は dev 専用のまま残し、デザインシステムのリファレンスとして維持する)

## 4. 新規依存(②で追加)

| パッケージ | 用途 | 備考 |
|---|---|---|
| react-router-dom | ルーティング(モック + 本実装) | ②で導入し③以降も使用 |
| react-i18next + i18next | i18n(Q6=A) | ブラウザ言語検出は自前実装(§screen-specs 5)で十分か実装時判断 |
| Noto Sans JP(woff2 同梱) | フォント(Q4=B) | npm パッケージではなくアセット同梱。SIL OFL 1.1 をライセンス文書化 |

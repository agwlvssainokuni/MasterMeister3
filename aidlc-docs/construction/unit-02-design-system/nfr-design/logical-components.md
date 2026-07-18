# 論理コンポーネント構成 — unit-02-design-system(簡略)

**作成日**: 2026-07-19

| 論理コンポーネント | 配置 | 本番同梱 | 責務 |
|---|---|---|---|
| tokens | `design-system/tokens/` | ○ | tokens.css(2 層トークン・両テーマ)。フォントは Fontsource import に置き換え(fonts/ ディレクトリは不要に) |
| components | `design-system/components/` | ○ | 共有 UI コンポーネント 27 種(component-specs.md) |
| theme | `design-system/theme/` | ○ | ThemeProvider + FOUC 防止スクリプト |
| i18n | `design-system/i18n/` | ○ | i18next 初期化 + common 辞書(ja/en) |
| mock | `mock/` | ×(DEV 専用) | カタログ 3 種 + 代表画面 4 系統 + サンプルデータ |

**②完了時点のアプリ構成**: `main.tsx` は Router(`/` = ①の動作確認ページを維持、DEV 時のみ `/mock/*`)+ ThemeProvider + i18n 初期化を組み込む。①の App.tsx の内容は維持しつつ Provider 階層に包む。

**③への申し送り**:
1. FOUC 防止インラインスクリプトの CSP(nonce/hash)対応
2. テーマ・言語のユーザ設定(サーバ保存)との統合(localStorage は初期表示用に降格)
3. H-07: 監査イベントのロケール非依存記録(バックエンド側)

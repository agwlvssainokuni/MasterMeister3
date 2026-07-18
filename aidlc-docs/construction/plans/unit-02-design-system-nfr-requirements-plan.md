# ユニット② design-system — NFR Requirements プラン(簡略)

**作成日**: 2026-07-19
**簡略実施の理由**: 技術スタックはユニット①で確定済み。②固有の NFR は UI 資産の配信・セキュリティ整合・モック除外に限られる。ユーザへのブロッキング質問はなし(実装既定値 2 点を完了メッセージで確認)。

## ステップ

- [x] Step 1: Functional Design 成果物の分析(トークン/コンポーネント/画面/モック構成)
- [x] Step 2: ②固有 NFR の抽出(CSP 整合 H-06、モック本番除外 NFR-06、フォント配信性能、アクセシビリティ、バンドル影響)
- [x] Step 3: 新規依存のバージョン確定(npm レジストリで実在・peer 制約確認)
- [x] Step 4: 成果物生成(nfr-requirements.md / tech-stack-decisions.md)

## 実装既定値(完了メッセージで確認)

1. フォント同梱の実装手段: Fontsource npm パッケージ(@fontsource/noto-sans-jp, @fontsource/noto-sans-mono)を採用 — サブセット分割済み woff2 + unicode-range CSS + OFL ライセンス文が npm で版管理される
2. CSP 本則(本番向けヘッダー設定)の導入はユニット③(セキュリティ設定)スコープ。②は「外部オリジン参照ゼロ」をデザインシステム側の保証として先行担保

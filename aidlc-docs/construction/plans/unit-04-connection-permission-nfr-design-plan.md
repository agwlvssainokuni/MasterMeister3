# ユニット④ connection-permission — NFR Design プラン

**作成日**: 2026-07-19
**方針**: 簡略実施(③と同様)。NFR-U4-01〜08 は Functional Design で構造がほぼ確定しており、本ステージは実装構造への落とし込みに集中する。回復性(リトライ・サーキットブレーカ)は Resiliency Baseline 無効のため対象外、スケーラビリティは単一 WAR 構成(要件確定)のため対象外。

## ステップ

- [x] Step 1: NFR-U4-01〜08 の実装構造への割付
- [x] Step 2: 暗号化・プール・キャッシュ・取込・YAML・接続テストの各パターン設計(nfr-design-patterns.md)
- [x] Step 3: 論理コンポーネント構成の確定(logical-components.md)
- [x] Step 4: 確認事項 3 点の提示(完了メッセージ内 — 実装既定値の性格のもの)

## 確認事項(完了メッセージで提示)

1. メタデータ保存は JPA バッチ(hibernate.jdbc.batch_size)で行う(JdbcTemplate 直書きまで落とさない)
2. YAML ボディ上限 1 MB はアプリケーション内検証で実施(Tomcat の maxPostSize はフォーム POST 限定のため)
3. Testcontainers 結合テストは「接続テスト + スキーマ取込」の 2 系統 × 3 エンジンの最小構成

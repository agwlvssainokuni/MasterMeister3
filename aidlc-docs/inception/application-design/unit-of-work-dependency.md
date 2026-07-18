# ユニット依存関係(Unit of Work Dependency)— MasterMeister3

**作成日**: 2026-07-18
**開発順序**: 厳密直列 ①→②→③→④→⑤→⑥(ユニットプラン Q2=A)。各ユニットの Code Generation 完了(動作確認)後に次へ進む。

## 1. 依存マトリクス

行 = 依存元(後続)、列 = 依存先(先行)。○ = 直接依存(先行ユニットの成果物を利用)。

| 依存元 \ 依存先 | ① foundation | ② design-system | ③ auth-user-audit | ④ connection-permission | ⑤ record-maintenance | ⑥ query |
|---|---|---|---|---|---|---|
| ② design-system | ○(Vite 環境) | - | | | | |
| ③ auth-user-audit | ○(ビルド・Flyway・WAR 同梱) | ○(UI コンポーネント・トークン) | - | | | |
| ④ connection-permission | ○ | ○ | ○(認証・監査基盤・管理画面骨格) | - | | |
| ⑤ record-maintenance | ○ | ○ | ○(認証・監査) | ○(接続・メタデータ・実効権限) | - | |
| ⑥ query | ○ | ○ | ○(認証・監査。監査閲覧 US-046 を含む) | ○(接続・メタデータ・スキーマ許可) | - | - |

**要点**:
- 依存は常に「後続 → 先行」の一方向のみ。逆流・循環はない(直列開発の前提が成立)
- ⑥は⑤に依存しない(クエリ系はメンテナンス機能を使わない)。ただし開発順序は Q2=A により⑤→⑥の直列を維持する
- コンポーネント依存(component-dependency.md)とユニット順序は整合: 先行ユニットが構築するパッケージ(common → audit → auth/user → connection/metadata/permission → record → query)は、後続が依存する側から先に完成する

## 2. 各ユニットが先行ユニットから受け取るもの

| ユニット | 受け取る成果物 |
|---|---|
| ② | ①: フロントエンドの Vite ビルド環境(モックページのホスト) |
| ③ | ①: WAR 同梱ビルド・内部 DB/Flyway 骨格・devenv、②: design-system(認証・管理画面の UI 部品、テーマ/言語切替 UI) |
| ④ | ③: 認証(管理者ロール)、監査記録基盤(US-043 が利用)、管理画面骨格 |
| ⑤ | ④: TargetDataSourceRegistry(接続)、取込メタデータ、EffectivePermissionResolver(実効権限) |
| ⑥ | ④: 接続・メタデータ・スキーマ許可検証(D-16)、③: 監査記録(大量取得記録)+ 監査閲覧の対象データ |

## 3. 開発順序の図(テキスト)

```
① foundation → ② design-system → ③ auth-user-audit → ④ connection-permission → ⑤ record-maintenance → ⑥ query
   (ビルド貫通)   (モック承認ゲート)   (Phase 1)            (Phase 2)                 (Phase 3)              (Phase 4)
```

各ユニットは CONSTRUCTION の per-unit ループ(Functional Design → NFR Requirements → NFR Design → Code Generation)を完了してから次のユニットへ進む。全ユニット完了後に Build and Test ステージを実施する。

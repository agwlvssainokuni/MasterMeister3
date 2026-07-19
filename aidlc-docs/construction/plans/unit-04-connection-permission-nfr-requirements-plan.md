# ユニット④ connection-permission — NFR Requirements プラン

**作成日**: 2026-07-19
**Part A**: 質問回答(下記 Q1〜Q3)→ **Part B**: 成果物生成(nfr-requirements.md / tech-stack-decisions.md)

## ステップ

- [ ] Step 1: Functional Design 成果物の分析(暗号化・プール・取込規模・キャッシュ・YAML の NFR 抽出)
- [ ] Step 2: 質問(Q1〜Q3)の回答確定
- [ ] Step 3: `mm.app.credential.*` ほか設定プロパティ体系の確定(既定値・起動時検証ルール)
- [ ] Step 4: 追加依存のバージョン確定と成果物生成

## 確認事項(回答をお願いします)

**Q1. 対象 RDBMS の JDBC ドライバ選定(WAR 同梱のためライセンス考慮)**

A) MySQL / MariaDB とも **MariaDB Connector/J**(LGPL 2.1)で接続し、PostgreSQL は公式ドライバ(BSD-2)、H2 は①で同梱済み — MySQL Connector/J(GPLv2 + FOSS 例外)を避け、Apache-2.0 の本プロジェクトに同梱するライセンス構成を単純化(推奨)

B) 各公式ドライバを使用 — MySQL Connector/J(GPLv2 + FOSS 例外)、MariaDB Connector/J、PostgreSQL。MySQL 固有機能・最新互換の面では最良だが、同梱ライセンスの検討が必要

C) その他(下の [Answer]: の後に記述してください)

[Answer]: 

**Q2. YAML の読み書きライブラリ(US-016/017)**

A) **jackson-dataformat-yaml(Jackson 3 / tools.jackson 系)** — ③で使用中の Jackson 3 と同一系列に統一。型を限定したデータバインドで安全ロード要件(SECURITY-13)を自然に満たし、エクスポートの正規順序も POJO 定義で制御(推奨)

B) SnakeYAML を直接使用 — 低レベル制御はしやすいが、型限定ロード(SafeConstructor 相当)や順序制御を手書きすることになり、Jackson と二重の YAML 経路になる

C) その他(下の [Answer]: の後に記述してください)

[Answer]: 

**Q3. 実効権限キャッシュ(Caffeine)の失効ポリシー**

A) サイズ上限 + **防御的 TTL 併用**(既定 10 分、`mm.app.permission.cache-ttl` で設定可能)— 無効化は明示トリガ(権限・グループ・取込・接続削除)が正だが、万一の無効化漏れでも古い権限が TTL を超えて残らない(多層防御 — SECURITY-11)(推奨)

B) サイズ上限のみ(TTL なし)— 明示無効化を信頼。キャッシュヒット率は最大化されるが、無効化漏れバグが恒久的な権限齟齬になる

C) その他(下の [Answer]: の後に記述してください)

[Answer]: 

## 実装既定値(異論なければこのまま)

1. コネクションプール実装: **HikariCP**(Spring Boot 標準)を接続ごとに生成。maximumPoolSize = pool_max_size、connectionTimeout = pool_timeout_ms、他は既定値
2. 暗号鍵の起動時検証: `mm.app.credential.keys.{keyId}` は Base64 復号で 32 バイト、`active-key-id` が keys に存在することを検証(不備は起動失敗 — ③ jwt.secret と同じ方針)。**鍵未設定でも④の機能を使わなければ起動可**とはせず、常に必須(設定漏れの早期検出)
3. 接続テストの疎通確認: JDBC `Connection.isValid(5 秒)` + 方言の検証クエリ。接続確立タイムアウトは pool_timeout_ms を適用
4. Caffeine キャッシュの容量: maximumSize 1,000(ユーザ × 接続の解決結果)
5. YAML インポートのサイズ上限: リクエストボディ 1 MB(Spring Boot 設定で明示)
6. 性能目安(受け入れ基準ではなく設計ガイド): 接続あたり 2,000 テーブル / 50,000 カラム規模のスキーマ取込が単一トランザクションで完了し、実効権限解決はキャッシュミス時でも 1 秒以内

## 承認

Q1〜Q3 の回答後、曖昧さがなければ Part B(成果物生成)を実行します。

# NFR Requirements — unit-04-connection-permission

**作成日**: 2026-07-19

| ID | NFR | 内容 | 検証方法 |
|---|---|---|---|
| NFR-U4-01 | 資格情報保護(H-03) | AES-256-GCM(96bit IV・128bit タグ)、保存形式 `v1:{keyId}:{IV}:{暗号文+タグ}`。鍵は環境変数供給・起動時検証(§2)。平文パスワードは API 応答・アプリログ・監査 detail のいずれにも出力しない | 暗号化ラウンドトリップ + 鍵不正時の起動失敗テスト、応答・監査に平文/暗号文が含まれないことの結合テスト |
| NFR-U4-02 | 接続分離とプール | HikariCP を接続 ID ごとに遅延生成(maximumPoolSize = pool_max_size、connectionTimeout = pool_timeout_ms)。更新・削除で確実にクローズ(リーク防止)。DriverManager 直接使用禁止(US-010) | Registry の生成・破棄テスト(破棄後の取得で再生成、クローズ済みプールへの参照が残らない) |
| NFR-U4-03 | 実効権限キャッシュ(Q3=A) | Caffeine: maximumSize(既定 1,000)+ **防御的 TTL(既定 10 分)**。無効化の正は明示トリガ(権限・グループ・取込・接続削除)の invalidateAll(粗粒度)で、TTL は無効化漏れ時の残存上限(多層防御 — SECURITY-11) | PBT-03(キャッシュ透過性)+ 無効化トリガの結合テスト |
| NFR-U4-04 | YAML 安全性(SECURITY-13) | jackson-dataformat-yaml の**型限定データバインドのみ**(任意型のデシリアライズ禁止)。リクエストボディ上限 1 MB。インポートは単一トランザクション全置換(部分適用なし) | PBT-02(ラウンドトリップ)+ 不正 YAML(構文・型・重複・未知プリンシパル)の全体拒否テスト |
| NFR-U4-05 | 取込の性能・堅牢性 | 設計ガイド: 接続あたり 2,000 テーブル / 50,000 カラム規模を単一トランザクションで取込完了。読取・保存はバッチ処理(1 行ずつの INSERT を避ける)。失敗時は全ロールバック(取込前状態を維持) | 結合テスト(取込 → 再取込 → 途中失敗ロールバック)。大規模実測は Build and Test ステージ判断 |
| NFR-U4-06 | 権限解決の性能 | キャッシュミス時でも 1 接続分の全カラム解決を 1 秒以内(エントリ・グループ・メタデータを各 1 クエリで取得しメモリ上で解決 — N+1 禁止) | 解決ロジックの単体テスト + クエリ数の検証 |
| NFR-U4-07 | テスト方針 | 単体・結合テストは**ターゲットも H2**(db_type=H2 の接続を登録)で完結。4 方言の実接続・取込・方言差(スキーマ概念・URL)は devenv(docker compose: MySQL/MariaDB/PostgreSQL)での DoD 実機検証で確認。Testcontainers は導入しない(devenv と二重になるため — §3 判断メモ) | ./gradlew build で全テスト + DoD 実機フロー |
| NFR-U4-08 | 監査の粒度 | 管理操作は 1 操作 = 1 イベント(YAML インポートも 1 イベント + 件数 detail — エントリごとに記録しない)。③の REQUIRES_NEW 基盤をそのまま使用 | 結合テストでイベント種別・件数を検証 |

## 2. 設定プロパティ追加(`mm.app.*` — ③のカタログに追加)

| プロパティ | 既定値 | 説明 |
|---|---|---|
| mm.app.credential.active-key-id | (必須・既定なし) | 暗号化に使う鍵 ID。keys に存在しなければ起動失敗 |
| mm.app.credential.keys.{keyId} | (必須・既定なし。1 件以上) | Base64 の 32 バイト鍵。復号長不正は起動失敗。環境変数例: `MM_APP_CREDENTIAL_KEYS_K1` |
| mm.app.permission.cache-ttl | 10m | 実効権限キャッシュの防御的 TTL(Q3=A) |
| mm.app.permission.cache-max-size | 1000 | キャッシュ容量(ユーザ × 接続) |

- 鍵は④の機能を使わない構成でも**常に必須**(設定漏れの早期検出 — 実装既定値 2)
- YAML ボディ上限 1 MB は Spring Boot 設定(`spring.servlet.multipart` ではなく通常ボディの max サイズ設定)で明示

## 3. 判断メモ

- **Testcontainers 不採用**: ①で整備済みの devenv(docker compose)が同じ 3 RDBMS を提供しており、二重管理を避ける。自動テストは H2 ターゲットで方言非依存部を網羅し、方言依存部(URL 組み立て・スキーマ概念)は DbDialect の単体テスト + DoD 実機で担保する
- 接続テストの疎通確認は JDBC `Connection.isValid(5 秒)` + 方言検証クエリ(実装既定値 3)
- 性能目安(NFR-U4-05/06)は受け入れ基準ではなく設計ガイド(実測での見直しを許容)

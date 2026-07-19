# 技術スタック決定 — unit-04-connection-permission(追加分)

**作成日**: 2026-07-19
**注**: 基盤スタックは①、UI 系は②、認証・メール系は③で確定済み。本書は④で追加する依存のみ。

## backend 追加依存(すべて Spring Boot 4.1 BOM 管理 — 個別バージョン指定不要)

| 依存 | スコープ | 用途 | ライセンス |
|---|---|---|---|
| com.mysql:mysql-connector-j | testRuntimeOnly → **runtimeOnly に昇格** | MySQL 接続(Q1=B: 公式ドライバ — ①の D-17 決定と一致。①では Testcontainers 用に test スコープ導入済み) | GPLv2 + FOSS 例外(§判断メモ) |
| org.mariadb.jdbc:mariadb-java-client | testRuntimeOnly → **runtimeOnly に昇格** | MariaDB 接続(同上) | LGPL 2.1 |
| org.postgresql:postgresql | testRuntimeOnly → **runtimeOnly に昇格** | PostgreSQL 接続(同上) | BSD-2-Clause |
| com.github.ben-manes.caffeine:caffeine | implementation | 実効権限キャッシュ(Application Design 確定) | Apache-2.0 |
| tools.jackson.dataformat:jackson-dataformat-yaml | implementation | YAML 読み書き(Q2=A — Jackson 3 系列に統一) | Apache-2.0 |

- H2 は①で同梱済み(内部 DB と共用 — ターゲット接続 db_type=H2 にも使用)
- Testcontainers(junit-jupiter + mysql/mariadb/postgresql モジュール)は**①で導入済み**(追加依存なし)。④は①の実エンジンスモークテスト基盤を取込・接続の結合テストに拡張する(NFR-U4-07)
- HikariCP は Spring Boot 標準(既存のデータアクセス starter に含まれる)。接続別プールは HikariDataSource を直接生成(auto-configuration は内部 DB 用のまま)
- 暗号化(AES-256-GCM)は JDK 標準(javax.crypto)で実装 — 追加依存なし

## frontend

- 追加依存なし(②のデザインシステム + ③の apiClient / ガードで完結。YAML はファイルとして扱うのみでパースしない)

## 判断メモ

- **MySQL Connector/J のライセンス(Q1=B)**: GPLv2 + Universal FOSS Exception。本プロジェクト(Apache-2.0)は FOSS 例外の対象ライセンスであり、WAR への同梱・再配布は例外条件下で可能。配布物のライセンス表記に Connector/J の GPLv2 + FOSS 例外を明記する(Build and Test ステージの成果物に含める)
- ドライバ 3 種はいずれも Spring Boot BOM がバージョン管理するため、Boot のアップグレードに追随する(個別固定しない — SECURITY-10 のパッチ追随)
- jqwik(PBT-02/03)は①で導入済み。fast-check の出番は④ frontend にはなし(ロジックはサーバ側)

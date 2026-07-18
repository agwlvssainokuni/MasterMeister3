# NFR Requirements Plan — ユニット① foundation(骨格・開発環境)

**作成日**: 2026-07-18
**前提**: Functional Design はスキップ(業務ロジックなし — unit-of-work.md §3 の見込みどおり、開始時判定で確定)

**このユニットで確定しない事項**: スケーラビリティ・可用性・応答時間等の実行時 NFR は要件 NFR-01〜08 で全体確定済みであり、本ユニット(ビルド骨格・開発環境)には固有の実行時 NFR はない。本プランは**開発基盤のツールチェーン選定**に集中する。該当しない質問カテゴリ(Scalability / Performance / Availability / Usability)は上記理由により N/A。

---

## Part A: 質問

### Question 1: 対象 RDBMS の開発環境バージョン(docker compose)
devenv で起動する対象 RDBMS のイメージとバージョンをどうしますか?

A) 最新 LTS/安定系(推奨)— MySQL 8.4(LTS)、MariaDB 11.8(LTS)、PostgreSQL 18。新規開発なので最新の長期サポート版に合わせ、サポート期間を最大化する

B) 保守的な普及版 — MySQL 8.0、MariaDB 10.11、PostgreSQL 16。既存環境との互換を重視する場合の選択

X) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 2: PBT フレームワークの確定(PBT-09)
プロパティベーステストのフレームワークを確定します。

A) jqwik(バックエンド/JUnit 5 統合)+ fast-check(フロントエンド/Vitest 統合)(推奨)— 要件分析時の想定どおり。いずれもシュリンク・シード再現(PBT-08)をサポートする

X) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 3: テストフレームワーク一式
ユニット③以降のテストで使う基盤を①で導入します。

A) 推奨セット — バックエンド: JUnit 5 + AssertJ + Mockito + Spring Boot Test、対象 RDBMS 結合テストに Testcontainers。フロントエンド: Vitest + React Testing Library。E2E(Playwright 等)の要否は Build and Test ステージで判断

B) A から Testcontainers を除外 — 結合テストは devenv の docker compose に接続する方式(テストの再現性は下がるが軽量)

X) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 4: コード品質ツール
静的解析・整形の方針をどうしますか?

A) 標準セット(推奨)— フロントエンド: ESLint + Prettier + TypeScript strict モード。バックエンド: コンパイラ警告(-Xlint)+ Spotless(コード整形・Apache 2.0 ライセンスヘッダーの自動付与/検査に利用)

B) 最小 — ESLint + TypeScript strict のみ(整形・ヘッダー管理は手動)

C) A + Checkstyle/Error Prone など静的解析を追加 — 品質は上がるがソロ開発では過剰の可能性

X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

## Part B: 成果物生成(回答確定後に実行)

- [ ] `aidlc-docs/construction/unit-01-foundation/nfr-requirements/nfr-requirements.md` — 本ユニットの NFR(ビルド再現性、開発環境の起動容易性、ライセンスヘッダー方針、N/A カテゴリの根拠)
- [ ] `aidlc-docs/construction/unit-01-foundation/nfr-requirements/tech-stack-decisions.md` — ツールチェーン確定表(Java 25 / Node 24 / Gradle 9.6 / Spring Boot 4.1 / React 19 / Vite + Q1〜Q4 の決定、PBT-09 の記録)
- [ ] Security Baseline / PBT 拡張ルールの本ステージ適用判定(コンプライアンスサマリ)

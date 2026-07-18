# NFR Requirements — ユニット① foundation(骨格・開発環境)

**作成日**: 2026-07-18
**入力**: unit-of-work.md(ユニット①定義)、requirements.md(D-01〜21、NFR-01〜08)、unit-01-foundation-nfr-requirements-plan.md(Q1〜Q4 = すべて A)

## 1. 本ユニットの NFR

### NFR-U1-01: ビルド再現性
- `./gradlew build` の単一コマンドで、フロントエンド(npm/Vite)ビルド → 実行可能 WAR 生成まで完結する(D-14)
- ツールチェーンのバージョンを固定する: Gradle は wrapper(9.6)、Node.js 24 はビルドプラグインが取得するバージョンを固定、npm 依存は `package-lock.json` で固定
- クリーンチェックアウト + Docker のみを前提とし、それ以外の事前インストールを要求しない(Java はツールチェーン取得で対応)

### NFR-U1-02: 開発環境の起動容易性
- `docker compose up` の単一コマンドで、対象 RDBMS 3 種(MySQL 8.4 LTS / MariaDB 11.8 LTS / PostgreSQL 18 — Q1=A)と MailPit(メール確認)が起動する
- 各コンテナの接続情報(ポート・初期ユーザ・初期スキーマ)は devenv 内の設定ファイルで宣言し、README に記載する
- H2(内部 DB・対象 RDBMS としての H2)はアプリ内蔵のため compose には含めない

### NFR-U1-03: テスト基盤(以後の全ユニットが使用)
- バックエンド: JUnit 5 + AssertJ + Mockito + Spring Boot Test。対象 RDBMS の結合テストは Testcontainers(実エンジンでの方言検証。Q3=A 確定)
- フロントエンド: Vitest + React Testing Library
- PBT: jqwik(バックエンド)+ fast-check(フロントエンド)— PBT-09 の選定確定。いずれもシュリンクとシード再現(PBT-08)をサポートする
- E2E テストの要否は Build and Test ステージで判断する

### NFR-U1-04: コード品質・ライセンス遵守
- フロントエンド: ESLint + Prettier + TypeScript strict モード
- バックエンド: コンパイラ警告(-Xlint)+ Spotless(整形)
- Apache License 2.0 ヘッダー(Copyright 2026 agwlvssainokuni)は Spotless の licenseHeader で自動付与・ビルド時検査する(手動運用にしない)。フロントエンドも ESLint もしくは同等の仕組みでヘッダーを検査する

## 2. 適用外カテゴリと根拠

| カテゴリ | 判定 | 根拠 |
|---|---|---|
| Scalability / Performance / Availability | N/A | 実行時 NFR は要件 NFR-01〜08 で全体確定済み。本ユニットは実行時機能を持たない |
| Usability | N/A | UI はユニット②(デザインシステム)以降の対象 |
| Reliability(実行時) | N/A | 同上。ビルドの信頼性は NFR-U1-01 でカバー |

## 3. 拡張ルール コンプライアンスサマリ(本ステージ)

| ルール | 判定 | 備考 |
|---|---|---|
| Security Baseline(SECURITY-01〜15) | 大半 N/A | 本ステージ成果物は文書のみ。関連事項として、依存バージョンの固定(lockfile / wrapper)と、秘密情報をリポジトリに置かない方針(H-03: 鍵は環境変数)をツールチェーン前提に織り込み済み。コードに適用されるルールはユニット①の Code Generation 以降で評価 |
| PBT-09(フレームワーク選定) | 準拠 | jqwik + fast-check に確定(Q2=A) |
| PBT-07 / PBT-08 | 準拠(前提確認) | 両フレームワークがカスタムジェネレータ・シュリンク・シード再現をサポートすることを選定理由に含めた。実適用は PBT 対象ユニット(④⑥)で評価 |
| PBT-02 / PBT-03 | N/A | 対象コンポーネント(実効権限解決・YAML・クエリビルダー)は本ユニットに存在しない |

ブロッキング所見なし。

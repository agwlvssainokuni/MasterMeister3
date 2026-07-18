# Tech Stack Decisions — ユニット① foundation

**作成日**: 2026-07-18
**注**: 要件(D-01〜03、D-14)で確定済みの項目と、本ステージ(Q1〜Q4)で確定した項目の統合一覧。以後の全ユニットはこの表に従う。

## 1. 言語・フレームワーク(要件で確定済み)

| 項目 | 決定 | 出典 |
|---|---|---|
| バックエンド | Java 25 + Spring Boot 4.1 | D-02 |
| ビルド | Gradle 9.6(wrapper)、ルートマルチプロジェクト、`./gradlew build` で WAR に フロントエンド成果物を同梱 | D-14 |
| フロントエンド | React 19 + TypeScript + Vite(Node.js 24) | D-02 |
| 内部 DB | H2(ファイルモード)+ JPA + Flyway | D-03, H-02 |
| 対象 RDBMS アクセス | NamedParameterJdbcTemplate(接続別 DataSource) | D-03 |
| キャッシュ | Caffeine(Spring Cache 抽象 `@Cacheable`) | D-05 系決定 + 設計確認 |
| 認証 | JWT(アクセス + ローテーションするリフレッシュ)、sessionStorage 保持 | D-06 系決定 + 設計確認 |

## 2. 本ステージで確定(Q1〜Q4)

| 項目 | 決定 | 根拠 |
|---|---|---|
| 開発環境 RDBMS(docker compose) | MySQL **8.4**(LTS)/ MariaDB **11.8**(LTS)/ PostgreSQL **18** | Q1=A: 新規開発のため最新 LTS/安定系でサポート期間を最大化 |
| メール確認(開発環境) | MailPit(SMTP 受信 + Web UI。利用開始はユニット③) | Units Generation 承認時の確認事項 1 |
| PBT フレームワーク | **jqwik**(JUnit 5 統合)+ **fast-check**(Vitest 統合) | Q2=A(PBT-09 確定)。シュリンク・シード再現対応(PBT-08) |
| バックエンドテスト | JUnit 5 + AssertJ + Mockito + Spring Boot Test + **Testcontainers**(対象 RDBMS 実エンジン結合テスト) | Q3=A(説明の上確定)。devenv は手動確認用、Testcontainers は自動テスト用と役割分担 |
| フロントエンドテスト | Vitest + React Testing Library | Q3=A |
| E2E テスト | 要否を Build and Test ステージで判断(候補: Playwright) | Q3=A |
| 品質(フロントエンド) | ESLint + Prettier + TypeScript strict | Q4=A |
| 品質(バックエンド) | -Xlint + **Spotless**(整形 + Apache 2.0 ライセンスヘッダー自動付与/検査) | Q4=A |

## 3. バージョン管理方針

- Gradle: wrapper 同梱(gradle-wrapper.properties でバージョン固定)
- Node.js: ビルドプラグイン(gradle-node-plugin 系)が取得するバージョンを 24 系に固定。npm 依存は package-lock.json で固定
- Java: Gradle toolchain で 25 を宣言(ローカルの JDK に依存しない)
- Docker イメージ: compose ファイルでメジャー・マイナーまでタグ指定(`latest` 禁止)

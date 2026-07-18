# Code Summary — ユニット① foundation(骨格・開発環境)

**作成日**: 2026-07-18
**対応プラン**: unit-01-foundation-code-generation-plan.md(8 ステップ)

## 生成物一覧

### ルート(Step 1)
- `settings.gradle.kts` — マルチプロジェクト定義(backend / frontend)、Foojay toolchain resolver
- `gradle/libs.versions.toml` — バージョンカタログ(Spring Boot 4.1.0 / Spotless 8.8.0 / node-gradle 7.1.0 / jqwik 1.10.1 / Node 24.18.0)
- `gradlew` + `gradle/wrapper/` — Gradle 9.6.1 wrapper
- `.gitignore` — build/・node_modules/・data/ 等(`.idea/` は含めない — ユーザ指示)
- `config/spotless/license-header.java` — Apache 2.0 ヘッダーテンプレート

### backend(Step 2, 4, 6)
- `backend/build.gradle.kts` — war + Spring Boot、Java 25 toolchain(Temurin)、-Xlint、Spotless(ヘッダー自動付与/検査)、frontend dist の WAR 同梱(processResources → static/)
- `cherry.mastermeister.MasterMeisterApplication` — 実行可能 WAR 対応メインクラス
- `cherry.mastermeister.common.web.SpaWebConfig` — SPA フォールバック(静的リソース非存在時に index.html。/api・/actuator は除外)
- `application.yaml` — H2 ファイルモード(./data/)、ddl-auto: none(Flyway 一本化)、Actuator health のみ公開
- `db/migration/V1__baseline.sql` — ベースラインマイグレーション(app_info テーブル)
- テスト: コンテキスト起動(インメモリ H2)、Testcontainers 方言別抽象基底 + MySQL 8.4 / MariaDB 11.8 / PostgreSQL 18 接続スモーク(Docker 不在時スキップ)、jqwik サンプルプロパティ

### frontend(Step 3, 6)
- `package.json` / `package-lock.json` — React 19.2、Vite 8、TypeScript 5.9(7 系は typescript-eslint 未対応のため 5.9 を採用)、Vitest 4、fast-check 4、ESLint 10 + typescript-eslint 8、Prettier
- `vite.config.ts` — dev proxy(/api, /actuator → :8080)、Vitest 設定(jsdom)
- `eslint.config.js` / `.prettierrc.json` / `scripts/check-license-header.mjs` — 品質ゲート(ヘッダー検査は npm run check に組込)
- `frontend/build.gradle.kts` — Node 24.18.0 自動取得、npm ci 固定、assemble=npmBuild / check=npmTest
- `src/` — 動作確認ページ(App、data-testid 付与)、テスト(RTL + fast-check サンプル)

### devenv(Step 5)
- `devenv/compose.yaml` — mysql:8.4(3306)/ mariadb:11.8(3307)/ postgres:18(5432)/ mailpit:v1.30(1025・8025)。タグ固定・名前付きボリューム
- `devenv/init/*/01-schema.sql` — 開発用ユーザ mmdev + 空スキーマ sample
- `devenv/README.md` — 起動手順・接続情報

### ドキュメント(Step 7)
- ルート `README.md` — ビルド・起動・開発環境手順

## 実装中の技術判断(プランからの差分)

| 事項 | 判断 |
|---|---|
| TypeScript 7.0(latest) | typescript-eslint 8.64 が TS <6.1 制約のため **5.9 系**を採用。TS7 対応後にアップグレード検討 |
| Testcontainers 座標 | Spring Boot 4.1 BOM は 2.x 系の新名称(`testcontainers-junit-jupiter` 等)を管理。新名称で導入(API は従来互換) |
| `@eslint/js` | ESLint 10 では別パッケージのため明示追加 |

## DoD 検証結果

Step 8 実施時に本節へ追記する。

# Code Generation Plan — ユニット① foundation(骨格・開発環境)

**作成日**: 2026-07-18
**入力**: unit-of-work.md(ユニット①定義・DoD)、nfr-requirements.md、tech-stack-decisions.md、nfr-design-patterns.md、logical-components.md
**本プランが Code Generation の単一の正**(実行はこのプランのステップ順のみに従う)

## ユニットコンテキスト

- **対応ストーリー**: なし(基盤整備ユニット。全 48 ストーリーは②〜⑥で実装)
- **DoD**: `./gradlew build` 一発で「空の React 画面を配信する実行可能 WAR」が得られる。`docker compose up` で対象 RDBMS 3 種 + MailPit が起動する
- **依存**: なし(最初のユニット)。後続ユニットへの提供物: ビルドパイプライン、テスト基盤、devenv、品質ゲート
- **コード配置**: ワークスペースルート(`backend/` `frontend/` `devenv/`)。aidlc-docs/ にはコード禁止(サマリのみ)
- **Java ルートパッケージ / Gradle group**: `cherry.mastermeister`(ユーザ指定。機能パッケージは `cherry.mastermeister.auth` 等)
- **Gradle wrapper 生成**: セットアップ済みの Gradle 9.6 で `gradle wrapper` を実行して生成
- **ライセンス**: 全ソースファイルに Apache 2.0 ヘッダー(Copyright 2026 agwlvssainokuni)。Spotless / ESLint で自動検査

## 生成ステップ

### Step 1: ルート Gradle 骨格
- [x] `settings.gradle.kts`(backend / frontend をインクルード、Foojay toolchain resolver プラグイン)
- [x] `gradle/libs.versions.toml`(バージョンカタログ: Spring Boot 4.1.0、プラグイン・依存の一元管理)
- [x] Gradle wrapper 9.6.1(`gradlew`, `gradle/wrapper/`)
- [x] ルート `.gitignore`(build/, node_modules/, data/ 等。**`.idea/` は入れない** — ユーザ指示)

### Step 2: backend サブプロジェクト骨格
- [x] `backend/build.gradle.kts`(Spring Boot 4.1 + war、Java 25 toolchain、-Xlint、Spotless(ライセンスヘッダー)、JPA/Flyway/H2/Actuator 依存)
- [x] メインアプリケーションクラス(実行可能 WAR 対応 — D-14)
- [x] `application.yaml`(`mm.app.*` 設定骨格、H2 ファイルモード(データは `data/` 配下)、Flyway 有効化、Actuator health のみ公開)
- [x] Flyway `V1` マイグレーション(最小: マイグレーション動作確認用)
- [x] SPA フォールバック設定(非 `/api/**` → index.html。common パッケージ配下)

### Step 3: frontend サブプロジェクト骨格
- [x] `frontend/package.json` + Vite + React 19 + TypeScript(strict)雛形(App + 動作確認ページ)※TypeScript は 5.9 系(7 系は typescript-eslint 未対応のため)
- [x] `frontend/vite.config.ts`(dev proxy `/api` → localhost:8080、ビルド出力設定)
- [x] ESLint + Prettier 設定(ライセンスヘッダー検査は scripts/check-license-header.mjs)
- [x] `frontend/build.gradle.kts`(gradle-node-plugin: Node 24.18.0 固定、npm ci → tsc/eslint/ヘッダー検査 → vite build のタスク鎖)

### Step 4: ビルド統合(WAR 同梱)
- [x] frontend のビルド成果物(`dist/`)を backend の WAR に静的リソースとして同梱するタスク接続(processResources → static/)
- [x] `./gradlew build` の一本鎖(npm ci → lint/tsc → vite build → 同梱 → spotlessCheck → test → war)を確立(WAR 内に index.html/assets 同梱を確認)

### Step 5: devenv(docker compose)
- [x] `devenv/compose.yaml`(mysql:8.4=3306、mariadb:11.8=3307、postgres:18=5432、mailpit:v1.30=1025/8025。タグ固定・名前付きボリューム)
- [x] 各 RDBMS の初期化スクリプト(開発用ユーザ mmdev + 空のサンプルスキーマ sample)
- [x] `devenv/README.md`(起動手順・接続情報)

### Step 6: テスト基盤
- [x] backend: JUnit 5 + AssertJ + Mockito + Spring Boot Test 導入 + コンテキスト起動テスト(インメモリ H2 + Flyway 適用)
- [x] backend: Testcontainers 導入(Boot BOM 管理の 2.x 新座標)+ 方言別抽象基底 + 3 RDBMS 接続スモークテスト(@Testcontainers disabledWithoutDocker、colima ソケット自動検出)— 実エンジン 3 種でパス
- [x] backend: jqwik 導入 + サンプルプロパティ(シード再現の動作確認)
- [x] frontend: Vitest + React Testing Library 導入 + サンプルテスト
- [x] frontend: fast-check 導入 + サンプルプロパティ

### Step 7: ドキュメント
- [x] ルート `README.md`(プロジェクト概要、ビルド・起動・devenv 手順、Apache 2.0 表記)
- [x] `aidlc-docs/construction/unit-01-foundation/code/code-summary.md`(生成物サマリ — markdown のみ)

### Step 8: 検証(DoD 確認)
- [ ] `./gradlew build` が通る(品質ゲート・テスト込み)
- [ ] `java -jar` で WAR を起動し、React 画面が配信される(SPA フォールバック確認)
- [ ] `docker compose up` で 4 サービスが起動し、接続スモークテストが通る

## 補足

- 各 Step 完了ごとにチェックボックスを [x] 更新し、細かめの節目でコミットする(ユーザ標準指示)
- UI 要素には `data-testid` を付与する(①では動作確認ページのみ)
- Spring Security・機能パッケージ・design-system は本ユニットの対象外(logical-components.md §5)

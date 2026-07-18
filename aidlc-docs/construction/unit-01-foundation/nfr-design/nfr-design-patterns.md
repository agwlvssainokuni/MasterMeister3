# NFR Design Patterns — ユニット① foundation

**作成日**: 2026-07-18
**入力**: nfr-requirements.md(NFR-U1-01〜04)、tech-stack-decisions.md

## 1. 単一コマンド・ビルドパイプライン(NFR-U1-01)

Gradle のタスク依存で次の一本鎖を構成する。中間手順の手動実行を不要にする:

```
./gradlew build
  └─ backend:war
       └─ フロントエンド成果物の同梱(processResources 連携)
            └─ frontend の Vite ビルド(npm run build)
                 └─ npm ci(package-lock.json による依存固定)
```

- フロントエンドのビルドは gradle-node-plugin 系プラグインで Gradle タスク化し、Node.js 24 をプラグインが取得(ローカルインストール不要)
- Vite の成果物(`dist/`)は WAR の静的リソース(`static/`)として同梱
- SPA ルーティングのため、未知のパスを `index.html` へフォワードするフォールバックを backend 側に用意(API パス `/api/**` は除外)

## 2. バージョン固定パターン(NFR-U1-01)

| 対象 | 固定手段 |
|---|---|
| Gradle | wrapper(gradle-wrapper.properties) |
| Java | toolchain 宣言(25)+ Foojay resolver 自動プロビジョニング(Temurin) |
| Node.js / npm | プラグイン設定でバージョン固定 + package-lock.json |
| Docker イメージ | compose でタグ明示(`latest` 禁止) |
| Gradle プラグイン・依存 | バージョンカタログ(gradle/libs.versions.toml)で一元管理 |

## 3. ビルド時品質ゲート(NFR-U1-04、フェイルファスト)

`./gradlew build` に以下の検査を組み込み、違反時はビルド失敗とする:

- **Spotless**: Java コード整形 + Apache 2.0 ライセンスヘッダー(Copyright 2026 agwlvssainokuni)の付与・検査。`spotlessApply` で自動修正、`build` は `spotlessCheck` に依存
- **フロントエンド**: `npm run build` の前段で `tsc --noEmit`(strict)+ ESLint を実行。ライセンスヘッダーは ESLint ルール(header 検査)で強制
- テスト(`test` タスク)は build に含まれる(JUnit 5 / Vitest)

## 4. 開発/本番 2 面構成(承認済み既定値 2)

| 面 | フロントエンド | バックエンド | 用途 |
|---|---|---|---|
| 開発 | Vite dev server(ホットリロード、`/api` を localhost:8080 へ proxy) | `bootRun` | 日常開発。H-06 の dev CSP 緩和はこの面に適用 |
| 本番相当 | WAR 同梱静的リソース | 実行可能 WAR(`java -jar`) | DoD 確認・リリース |

## 5. 方言別テスト基盤パターン(NFR-U1-03)

- Testcontainers による結合テストは、方言別に共通化した抽象基底(パラメータ化)を用意し、後続ユニットが「同一テストを MySQL 8.4 / MariaDB 11.8 / PostgreSQL 18 / 内蔵 H2 の 4 方言で実行」できる構造にする(ユニット①ではコンテナ起動と接続確認のスモークテストまで実装)
- PBT(jqwik / fast-check)は依存導入とサンプルプロパティ(動作確認用)まで。実プロパティは対象ユニット(④⑥)で実装
- Testcontainers 実行には Docker が必要。Docker 不在環境ではタグで除外可能にする(既定は実行)

## 6. 開発環境サービス分離(NFR-U1-02)

- devenv(docker compose)は手動動作確認用、Testcontainers は自動テスト用と役割を分離(相互依存させない)
- compose のデータは名前付きボリュームに永続化し、`docker compose down -v` で初期化できる

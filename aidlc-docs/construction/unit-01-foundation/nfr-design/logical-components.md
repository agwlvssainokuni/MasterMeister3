# Logical Components — ユニット① foundation

**作成日**: 2026-07-18

## 1. Gradle プロジェクト構成

```
<WORKSPACE-ROOT>/
├── settings.gradle.kts          # ルート。backend / frontend をインクルード
├── build.gradle.kts             # ルート共通設定(あれば最小限)
├── gradle/libs.versions.toml    # バージョンカタログ(依存・プラグイン一元管理)
├── gradlew, gradle/wrapper/     # Gradle 9.6 wrapper
├── backend/
│   └── build.gradle.kts         # Spring Boot 4.1、war プラグイン、Spotless、
│                                # frontend 成果物の同梱タスク、Testcontainers 依存
├── frontend/
│   ├── build.gradle.kts         # gradle-node-plugin 系(Node 24 固定、npm ci / build)
│   ├── package.json / package-lock.json
│   ├── vite.config.ts           # ビルド出力 + dev proxy(/api → localhost:8080)
│   └── src/                     # 雛形(App + 動作確認ページ)
└── devenv/
    ├── compose.yaml             # 下記サービス定義
    └── README.md                # 起動手順・接続情報
```

## 2. backend 骨格(Spring Boot)

| 要素 | 内容 |
|---|---|
| メインクラス | Spring Boot アプリ(war 化。実行可能 WAR — D-14) |
| SPA フォールバック | 非 `/api/**` パスを index.html へフォワードする設定(common パッケージ配下) |
| 内部 DB | H2(ファイルモード)+ JPA + Flyway。V1 マイグレーションは空 or 最小(実テーブルはユニット③以降) |
| 設定 | `application.yaml` に `mm.app.*` プレフィックスの設定骨格(実項目は後続ユニットで追加) |
| ヘルスチェック | Spring Boot Actuator の health のみ有効化(動作確認用) |

## 3. devenv(docker compose)サービス

| サービス | イメージ(タグ固定) | 公開ポート(既定) | 用途 |
|---|---|---|---|
| mysql | mysql:8.4 | 3306 | 対象 RDBMS(開発確認用) |
| mariadb | mariadb:11.8 | 3307 | 同上 |
| postgres | postgres:18 | 5432 | 同上 |
| mailpit | axllent/mailpit(安定タグ) | 1025(SMTP)/ 8025(Web UI) | メール確認(利用開始はユニット③) |

- 各 RDBMS には開発用ユーザとサンプルスキーマ(空)を初期化スクリプトで作成
- データは名前付きボリュームで永続化

## 4. テスト基盤の骨格

| 要素 | 内容 |
|---|---|
| backend テスト | JUnit 5 + AssertJ + Mockito + Spring Boot Test の依存導入 + サンプルテスト |
| Testcontainers | MySQL/MariaDB/PostgreSQL モジュール導入 + 方言別パラメータ化の抽象基底 + 接続スモークテスト |
| jqwik | 依存導入 + サンプルプロパティ(シード再現の動作確認) |
| frontend テスト | Vitest + React Testing Library 導入 + サンプルテスト |
| fast-check | 依存導入 + サンプルプロパティ |

## 5. ユニット①で作らないもの(境界の明確化)

- 機能パッケージ(auth 等)の実装 — ユニット③以降
- design-system — ユニット②
- 認証・セキュリティ設定(Spring Security 構成)— ユニット③(骨格では Actuator health と静的リソースのみ)
- CI(GitHub Actions)— 対象外(ユニットプラン Q4=A)

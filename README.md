# MasterMeister

社内の複数の RDBMS(MySQL / MariaDB / PostgreSQL / H2)に接続し、業務ユーザが安全にマスタデータを参照・保守できる Web アプリケーション。

- バックエンド: Java 25 + Spring Boot 4.1(実行可能 WAR)
- フロントエンド: React 19 + TypeScript + Vite(WAR に同梱)
- 内部 DB: H2(ファイルモード)+ Flyway
- ライセンス: [Apache License 2.0](LICENSE)

## ビルド

前提: JDK・Node.js の事前インストールは不要(Gradle toolchain / gradle-node-plugin が自動取得)。Testcontainers による結合テストには Docker が必要(不在時は自動スキップ)。

```bash
./gradlew build
```

生成物: `backend/build/libs/mastermeister-<version>.war`(フロントエンド同梱・実行可能)

## 起動

JWT 署名鍵(32 バイト以上)と接続資格情報の暗号鍵(AES-256、Base64 の 32 バイト)の設定が必須。未設定の場合は起動に失敗する(誤って既知の鍵のまま本番稼働する事故を防ぐため、既定値は用意していない)。

```bash
# 開発用の鍵を生成して環境変数に設定(例)
export MM_APP_JWT_SECRET="$(openssl rand -base64 48)"
export MM_APP_CREDENTIAL_KEYS_K1="$(openssl rand -base64 32)"
export MM_APP_CREDENTIAL_ACTIVEKEYID=k1

# 本番相当(実行可能 WAR)
java -jar backend/build/libs/mastermeister-0.1.0-SNAPSHOT.war
# → http://localhost:8080

# 開発(ホットリロード)
./gradlew :backend:bootRun            # バックエンド(:8080)
cd frontend && npm run dev            # フロントエンド(:5173、/api を :8080 へ proxy)
```

初期管理者は環境変数 `MM_APP_ADMIN_BOOTSTRAP_EMAIL` / `MM_APP_ADMIN_BOOTSTRAP_PASSWORD` を設定して起動すると初回に作成される(既存なら何もしない)。メールの動作確認は devenv の MailPit(SMTP :1025 / UI http://localhost:8025)を使う。

## 開発環境(対象 RDBMS + メール確認)

```bash
cd devenv
docker compose up -d
```

詳細は [devenv/README.md](devenv/README.md) を参照。

## プロジェクト構成

```
backend/    Spring Boot(機能パッケージ: cherry.mastermeister.*)
frontend/   React + TypeScript + Vite
devenv/     docker compose(MySQL 8.4 / MariaDB 11.8 / PostgreSQL 18 / MailPit)
aidlc-docs/ 開発プロセスドキュメント(AI-DLC)
```

## License

Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0.

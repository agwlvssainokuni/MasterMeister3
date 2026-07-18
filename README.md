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

生成物: `backend/build/libs/backend-<version>.war`(フロントエンド同梱・実行可能)

## 起動

```bash
# 本番相当(実行可能 WAR)
java -jar backend/build/libs/backend-0.1.0-SNAPSHOT.war
# → http://localhost:8080

# 開発(ホットリロード)
./gradlew :backend:bootRun            # バックエンド(:8080)
cd frontend && npm run dev            # フロントエンド(:5173、/api を :8080 へ proxy)
```

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

# MasterMeister 開発環境(devenv)

docker compose で対象 RDBMS 3 種とメール確認サーバを起動する。

## 起動・停止

```bash
cd devenv
docker compose up -d      # 起動
docker compose ps         # 状態確認
docker compose down       # 停止(データ保持)
docker compose down -v    # 停止 + データ初期化
```

## 接続情報

| サービス | 接続先 | ユーザ / パスワード | 備考 |
|---|---|---|---|
| MySQL 8.4 | `localhost:3306` | mmdev / mmdev(root: rootpass) | DB: `mmdev`、空スキーマ `sample` |
| MariaDB 11.8 | `localhost:3307` | mmdev / mmdev(root: rootpass) | DB: `mmdev`、空スキーマ `sample` |
| PostgreSQL 18 | `localhost:5432` | mmdev / mmdev | DB: `mmdev`、空スキーマ `sample` |
| MailPit | SMTP `localhost:1025` / Web UI <http://localhost:8025> | なし | メール確認(ユニット③から利用) |

- H2(内部 DB・対象 RDBMS としての H2)はアプリ内蔵のため compose には含まれない
- 認証情報は開発専用。本番運用には使用しないこと

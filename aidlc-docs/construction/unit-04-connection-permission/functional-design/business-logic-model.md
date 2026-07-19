# ビジネスロジックモデル(API・サービス構成)— unit-04-connection-permission

**作成日**: 2026-07-19
**前提**: Application Design の components.md / component-methods.md(connection / metadata / permission)を具体化。エラーは Problem Details(③基盤)、認可は `/api/admin/**` = ADMIN(③ SecurityConfig のまま追加設定不要)

## 1. API 設計(すべて ADMIN)

### 接続管理
| メソッド | パス | 説明 | 主応答 |
|---|---|---|---|
| GET | /api/admin/connections | 一覧(パスワード非含有) | 200 [{id, name, dbType, host, port, databaseName, username, options, poolMaxSize, poolTimeoutMs, importedAt?}] |
| POST | /api/admin/connections | 作成(password 必須) | 201 / 409(名前重複) |
| GET | /api/admin/connections/{id} | 詳細(パスワード非含有) | 200 / 404 |
| PUT | /api/admin/connections/{id} | 更新(password null = 維持、dbType 変更不可) | 204 / 409 / 400(種別変更) |
| DELETE | /api/admin/connections/{id} | 削除(メタデータ・権限カスケード) | 204 |
| POST | /api/admin/connections/test | 接続テスト(未保存のフォーム値で実行可 — レビュー確認 2)。ボディ {id?, dbType, host, port?, databaseName, username, password?, options?}。password 省略時は id 必須(保存済み資格情報で補完) | 200 {success, reason?} |

### スキーマ取込・メタデータ参照
| メソッド | パス | 説明 | 主応答 |
|---|---|---|---|
| POST | /api/admin/connections/{id}/schema/import | 取込実行(全置換) | 200 {schemas, tables, columns} / 502(接続失敗 — 理由コード) |
| GET | /api/admin/connections/{id}/schema | 取込済みツリー(スキーマ→テーブル→カラム) | 200(未取込は空) |

### 権限設定
| メソッド | パス | 説明 | 主応答 |
|---|---|---|---|
| GET | /api/admin/connections/{id}/permissions?principalType=&principalId= | 指定プリンシパルの全エントリ(主 + 補助)。孤児判定フラグ付き(Q2=A) | 200 {main: [...], aux: [...]} |
| PUT | /api/admin/connections/{id}/permissions/entry | 主権限の設定(明示 NONE 含む)/ 補助権限の設定 | 204 |
| DELETE | /api/admin/connections/{id}/permissions/entry | エントリ削除 = 未設定に戻す(D-18) | 204 |
| GET | /api/admin/connections/{id}/permissions/yaml | エクスポート(text/yaml ダウンロード) | 200 |
| PUT | /api/admin/connections/{id}/permissions/yaml | インポート(全置換) | 200 {entries} / 400(理由一覧 — 全体拒否) |

- entry の設定/削除のボディ: {principalType, principalId, schema, table?, column?, permission} または {..., auxType, granted}(table/column の省略で階層を表現)

### グループ管理
| メソッド | パス | 説明 | 主応答 |
|---|---|---|---|
| GET | /api/admin/groups | 一覧(メンバー数付き) | 200 |
| POST | /api/admin/groups | 作成 | 201 / 409(名前重複) |
| PUT | /api/admin/groups/{id} | 改名 | 204 / 409 |
| DELETE | /api/admin/groups/{id} | 削除(所属・権限カスケード) | 204 |
| GET | /api/admin/groups/{id}/members | メンバー一覧 | 200 |
| POST | /api/admin/groups/{id}/members | 追加 {userId}(冪等) | 204 |
| DELETE | /api/admin/groups/{id}/members/{userId} | 削除 | 204 |

### エラーコード(③の code 体系に追加)
`CONNECTION_NAME_DUPLICATE` / `CONNECTION_TYPE_IMMUTABLE` / `CONNECTION_NOT_FOUND` / `CONNECTION_TEST_FAILED`(理由: `CONNECT_FAILED` / `AUTH_FAILED` / `TIMEOUT`)/ `SCHEMA_IMPORT_FAILED` / `PERMISSION_INVALID_SCOPE` / `PRINCIPAL_NOT_FOUND` / `GROUP_NAME_DUPLICATE` / `GROUP_NOT_FOUND` / `YAML_INVALID` / `YAML_DUPLICATE_ENTRY` / `YAML_UNKNOWN_PRINCIPAL`

## 2. サービス構成(パッケージ別)

### connection
- `ConnectionController` — CRUD + test
- `ConnectionService` — 業務ルール §1(名前重複・password 維持・カスケード・監査発行)
- `CredentialEncryptor` — AES-256-GCM 暗号化/復号(鍵 ID 付き形式・複数鍵 — domain-entities §3)。鍵設定は AppProperties(`mm.app.credential.*`)で起動時検証
- `TargetDataSourceRegistry` — 接続 ID → プール済み DataSource(遅延生成・破棄)。⑤⑥もここから取得

### metadata
- `MetadataController` — import / ツリー参照
- `SchemaImportService` — 接続メタデータ読取(DbDialect で方言差補正)→ 全置換保存 → キャッシュ無効化イベント → 監査
- `MetadataQueryService` — 取込済み構造の参照(④の画面と⑤⑥の権限フィルタ適用側が使用)

### permission
- `PermissionController` / `PermissionService` — エントリ CRUD(D-18 の 2 操作)・監査・無効化
- `EffectivePermissionResolver` — D-21 解決 + 操作可否合成(business-rules §4)+ Caffeine キャッシュ + `invalidateAll()`
- `GroupController` / `GroupService` — グループ CRUD・メンバー管理・カスケード
- `PermissionYamlService` — export(正規順序)/ importReplace(全置換・全体拒否)。安全ロード限定

### common(拡張)
- `DbDialect` — ④で新設(接続 URL 組み立て・スキーマ概念の方言差・識別子クォートの基盤)。MySQL / MariaDB / PostgreSQL / H2 の 4 実装。⑤⑥で SQL 組み立てに拡張
- `AppProperties` — `mm.app.credential.keys.*` / `mm.app.credential.active-key-id` を追加(③のパターン)

## 3. 主要フロー(シーケンス概要)

### 接続作成/更新
Controller → ConnectionService →(name 重複検査・dbType 不変検査[更新時]→ CredentialEncryptor.encrypt[active 鍵]→ 保存 → 旧プール破棄[更新時])→ AuditEventPublisher(CONNECTION_CREATED/UPDATED)

### 接続テスト
Controller → ConnectionService.test(params) →(password 補完[id 指定時: 保存済みを復号]→ DbDialect で URL 組立 → プール非経由の単発接続 + 検証クエリ)→ AuditEventPublisher(CONNECTION_TESTED)→ 200 {success, reason?}

### スキーマ取込
Controller → SchemaImportService →(TargetDataSourceRegistry から DataSource 取得 → メタデータ読取 → 単一 Tx で全置換)→ EffectivePermissionResolver.invalidateAll → AuditEventPublisher(SCHEMA_IMPORTED)。読取失敗は全ロールバック + FAILURE 監査 + 502

### 実効権限解決(⑤⑥からの利用が主)
呼出側 → EffectivePermissionResolver.resolve(userId, connId) → キャッシュヒットなら返却 / ミスなら(個別エントリ + 所属グループのエントリ + メタデータのカラム集合を取得 → D-21 で全カラム解決)→ キャッシュ格納

### YAML インポート
Controller → PermissionYamlService.importReplace →(安全ロード → 検証[構文・重複・プリンシパル・値]→ 全削除 + 再構築[単一 Tx])→ invalidateAll → AuditEventPublisher(PERMISSION_IMPORTED)。検証失敗は全体拒否(400 + 理由一覧)

## 4. セキュリティ設計上の考慮(Security Baseline 対応)

- 資格情報: 平文保存禁止・応答非含有・監査 detail 非記録(SECURITY-01/12)。暗号鍵は環境変数のみ(ハードコード禁止)
- 対象 RDBMS への TLS: options で方言別 TLS パラメータを指定可能(運用判断。devenv はローカルのため平文)
- YAML: 型限定の安全ロードのみ(任意型デシリアライズ禁止 — SECURITY-13)。サイズ上限(リクエストボディ制限)適用
- 認可: 全 API が ADMIN(サーバサイド強制 — SECURITY-08)。実効権限はデフォルト拒否(SECURITY-15 fail-closed)
- 誤用シナリオ考慮(SECURITY-11): 接続テストの未保存値実行による任意ホストへの接続試行(ADMIN 限定 + 全テスト監査記録でリスク受容 — レビュー確認)、YAML による権限一括昇格(インポート自体を監査記録・全置換で差分が明確)、孤児エントリを利用した「見えない権限」(解決対象はメタデータ交差のみ — PBT P4 で保証)

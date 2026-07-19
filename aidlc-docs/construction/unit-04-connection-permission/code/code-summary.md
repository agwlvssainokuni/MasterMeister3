# Code Summary — unit-04-connection-permission

**作成日**: 2026-07-20
**ストーリー**: US-010〜019、US-043(11 件)— 全実装

## バックエンド(cherry.mastermeister.*)

| パッケージ | 実装 |
|---|---|
| connection | ConnectionController/Service(CRUD・名前重複 409・dbType 不変・password null 維持・接続テスト[未保存値可・プール外単発・AUTH_FAILED/TIMEOUT/CONNECT_FAILED 分類])、CredentialEncryptor(AES-256-GCM・`v1:{keyId}:{IV}:{暗号文}`・active 鍵暗号化・keyId 復号・CREDENTIAL_DECRYPT_FAILED)、TargetDataSourceRegistry(接続別 HikariCP 遅延生成・evict・@PreDestroy)、DbConnection エンティティ |
| metadata | MetadataController(import / ツリー / import-status)、SchemaImportService(DatabaseMetaData 読取・全置換単一 Tx・失敗 502/503 分類・キャッシュ無効化・監査)、MetadataQueryService(3 クエリのツリー組立)、MetaSchema/MetaTable/MetaColumn エンティティ |
| permission | PermissionController/Service(設定と削除の 2 操作 — D-18、孤児許容 + orphan フラグ)、EffectivePermissionResolver(D-21 個別優先→グループ合成→デフォルト拒否、操作可否合成 US-014 + D-15、Caffeine サイズ + TTL、解決コアは純粋関数 compute — PBT シーム)、GroupController/Service(CRUD・カスケード削除・メンバー冪等)、PermissionYamlService(正規順序 export / 全置換 import・全体拒否 5 条件・厳格バインド)、エンティティ 4 種 |
| common(拡張) | dialect: DbDialect + 4 実装(URL 組立・カタログ/スキーマ概念・システムスキーマ除外。DbType も common.dialect へ配置)、event: PermissionCacheInvalidated(connection/metadata → permission の片方向イベント — 循環参照回避)、web: ServiceUnavailableException(503 + Retry-After)、config: AppProperties に credential(鍵検証)/permission 追加 |
| resources | V3__connection_permission.sql(7 テーブル。権限の階層は空文字表現で一意制約を DB 強制)、application.yaml(permission 既定値)、AuditEvents に④の 14 種追加 |

## フロントエンド(frontend/src/)

| 配置 | 実装 |
|---|---|
| features/admin/connections | api.ts(listConnections/createConnection/updateConnection/deleteConnection/testConnection/importSchema/fetchSchema/fetchImportStatus)、ConnectionListPage(取込状況併記・カスケード警告付き削除)、ConnectionEditPage(dbType 編集 disabled・password 空欄維持・フォーム現在値での接続テスト・取込 + ツリー概要) |
| features/admin/permissions | api.ts(fetchPermissions/setMainPermission/setAuxPermission/removeMainPermission/removeAuxPermission/exportPermissionYaml/importPermissionYaml + 選択肢取得)、PermissionPage(プリンシパル軸ツリー — Q5=A。4 値 Select で D-18 の 2 操作を表現、補助権限 3 状態、孤児セクション + 明示削除、YAML export/import + 理由一覧表示) |
| features/admin/groups | api.ts(listGroups/createGroup/renameGroup/deleteGroup/listGroupMembers/addGroupMember/removeGroupMember/searchUserCandidates)、GroupListPage(CRUD モーダル・メンバー管理モーダル・カスケード警告) |
| app | routes に 5 ルート追加、AppLayout ナビ 2 件追加(合成系 — 規約内)。apiClient を拡張: contentType(YAML ボディ)/ responseType: "text" / Problem Details の errors 配列を invalidParams に取込 |
| i18n | admin ns に connections/permissions/groups、auth ns に nav 2 件(ja/en) |

- ③で確立した規約(feature ごとの api.ts・feature 間参照禁止・レイヤ方向・命名)をすべて適用。プリンシパル選択肢・メンバー候補は各 feature が自前の API 関数で取得(users feature を参照しない)

## テスト

- バックエンド: 259 → 315 件。内訳(④分 56): AppProperties credential 4、V3 整合 3、CredentialEncryptor 6、Registry 2、接続 API 結合 7、取込結合 4、Resolver 結合 6(US-015 確定例 2 件・操作可否・キャッシュ透過性 P3・無効化トリガ)、**PBT-03 jqwik 3 プロパティ**(P1 個別優先・P2 グループ単調性・P4 メタデータ交差 — 解決コアの純粋関数を対象)、権限/グループ API 結合 4、**PBT-02 ラウンドトリップ + YAML 拒否系 7**、**Testcontainers 実エンジン 6**(MySQL 8.4 / MariaDB 11.8 / PostgreSQL 18 × 接続テスト・取込検証 — 実行確認済み)
- フロントエンド: 40 → 48 件(connections 4・permissions 2・groups 2)

## DoD 検証(2026-07-20 実測)

- `./gradlew clean build` 全パス(Docker あり環境で実エンジン 6 テスト含む)
- WAR + devenv 実機フロー 38 項目 OK: ブートストラップログイン → **4 種接続登録**(MySQL/MariaDB/PostgreSQL/H2)→ 接続テスト(保存済み資格情報補完 4 種・誤パスワード AUTH_FAILED)→ **4 種スキーマ取込**(customers の PK・コメント確認)→ グループ作成・メンバー追加 → 権限エントリ(US-015 例 1 の構成・明示的 NONE と削除の 2 操作・補助権限)→ 孤児フラグ → **YAML エクスポート→インポート→再エクスポート同一**(PBT-02 実機)→ 重複/未知プリンシパルの全体拒否(既存無傷)→ グループ削除カスケード → 409/400/404/401
- DB 実測: 監査 13 種別を記録、password_enc は全件 `v1:k1:` 形式のみ(平文なし)、監査 detail にパスワード漏えいなし
- US-015 確定例 2 件の解決結果検証は自動テスト(結合 + 実機は設定構成まで — 解決結果を返す API は⑤で登場)

## 設計判断の実装ノート

- **パッケージ依存は一方向**: metadata→connection、permission→metadata/user、connection/metadata→common。逆向きの権限キャッシュ無効化は common.event の PermissionCacheInvalidated で疎結合化(connection⇄permission の循環回避)
- DbType は common.dialect に配置(common→connection の逆参照回避。設計書の connection 配置から変更)
- YAML 検証エラーの理由一覧は PermissionController 局所の @ExceptionHandler で応答(共通ハンドラに feature 固有の型を持ち込まない)
- 追加エラーコード(設計後に発生): `CONNECTION_PASSWORD_REQUIRED`(未保存テストで password 省略)、`YAML_TOO_LARGE`(1 MB 超)

## ⑤⑥への提供物

1. `TargetDataSourceRegistry` — 接続 ID → プール済み DataSource(データアクセス経路。プール枯渇は 503 TARGET_DB_BUSY の規約)
2. `EffectivePermissionResolver` — resolve(userId, connectionId) → EffectivePermissions(columnLevel / canRead / TablePermissions.canCreate・canDelete・updatable)
3. `MetadataQueryService` — 取込済み構造のツリー・取込状況
4. `DbDialect`(common.dialect)— URL 組立・カタログ/スキーマ概念。⑤⑥で識別子クォート・SQL 組立に拡張
5. 監査イベント種別の拡張実績(AuditEvents へ 14 種追加・connectionId/resource 付き発行パターン)

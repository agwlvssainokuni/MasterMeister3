# 論理コンポーネント構成 — unit-04-connection-permission

**作成日**: 2026-07-19

## backend(`cherry.mastermeister.*`)

| パッケージ | 主要クラス | 備考 |
|---|---|---|
| connection | ConnectionController / ConnectionService / CredentialEncryptor / TargetDataSourceRegistry | 暗号化(nfr-design-patterns.md §1)・プール(§2)・接続テスト(§6) |
| metadata | MetadataController / SchemaImportService / MetadataQueryService | 取込(§4)・ツリー参照。エンティティ MetaSchema / MetaTable / MetaColumn |
| permission | PermissionController / PermissionService / EffectivePermissionResolver / GroupController / GroupService / PermissionYamlService | キャッシュ(§3)・YAML(§5)。エンティティ PermissionEntry / PermissionAuxEntry / UserGroup / GroupMember |
| common(拡張) | DbDialect(+ 4 実装)/ AppProperties(credential・permission 追加) | DbDialect は⑤⑥の SQL 組み立て基盤に拡張予定 |

- エンティティ/リポジトリは各パッケージ配下(③の構成規約を踏襲)。Flyway `V3__connection_permission.sql`
- 監査は③の AuditEventPublisher をそのまま使用(④の新規クラスなし。イベント種別 13 種を AuditEvents に追加)

## frontend(`frontend/src/`)

| 配置 | 内容 |
|---|---|
| features/admin/connections | ConnectionListPage / ConnectionEditPage / api.ts |
| features/admin/permissions | PermissionPage / api.ts |
| features/admin/groups | GroupListPage / api.ts |
| app | routes に 4 ルート追加、AppLayout ナビに「接続管理」「グループ管理」追加(いずれも合成系 — 規約内) |
| design-system / apiClient / ガード | ②③のまま変更なし(利用のみ) |
| i18n 辞書 | namespace `admin` に connections / permissions / groups のキー群を追加(ja/en) |

## ⑤⑥への提供物(このユニットが基盤)

1. `TargetDataSourceRegistry` — 接続 ID → プール済み DataSource(⑤レコード操作・⑥クエリ実行のデータアクセス経路)
2. `EffectivePermissionResolver` — 実効権限 + 操作可否判定(⑤のカラムフィルタ・⑥のスキーマ検証)
3. `MetadataQueryService` — 取込済み構造の参照(⑤の一覧・⑥のビルダー)
4. `DbDialect` — URL・スキーマ概念・識別子クォート(⑤⑥で SQL 組み立てに拡張)
5. 監査イベント種別の拡張実績(④の 13 種追加パターン)

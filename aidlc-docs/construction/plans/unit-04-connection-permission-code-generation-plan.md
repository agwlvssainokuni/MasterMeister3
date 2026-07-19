# ユニット④ connection-permission — Code Generation プラン

**作成日**: 2026-07-20
**入力**: functional-design/(domain-entities / business-rules / business-logic-model / frontend-components)、nfr-requirements/、nfr-design/
**ストーリー対応**: US-010〜019、US-043(管理操作の監査)— 11 件
**方針**: 各ステップ完了ごとにテストを通しコミット。バックエンドを層ごとに積み上げ(基盤 → 接続 → 取込 → 権限・グループ・YAML)、実エンジンテスト、フロントエンド、最後に結合検証。③で確立した規約(api.ts・命名・レイヤ方向)に従う。本プランが Code Generation の唯一の正とする。

## ユニットコンテキスト

- **依存**: ③の基盤(AuditEventPublisher / SecurityFilterChain / AppProperties / apiClient / RequireAdmin / AppLayout)を利用。①の Testcontainers 基盤・devenv を利用
- **提供物(⑤⑥向け)**: TargetDataSourceRegistry / EffectivePermissionResolver / MetadataQueryService / DbDialect
- **所有エンティティ**: db_connection、meta_schema / meta_table / meta_column、permission_entry / permission_aux_entry、user_group / group_member(Flyway V3)

## Step 1: 基盤(依存・設定・マイグレーション・エンティティ)

- [ ] 1-1: backend 依存追加(JDBC ドライバ 3 種を testRuntimeOnly → runtimeOnly に昇格、caffeine、jackson-dataformat-yaml — すべて BOM 管理)
- [ ] 1-2: `V3__connection_permission.sql`(7 テーブル + インデックス。permission 系は空文字表現の一意制約)
- [ ] 1-3: AppProperties 拡張(credential.keys / active-key-id の起動時検証、permission.cache-ttl / cache-max-size)+ application.yaml / application-test.yaml + README(開発用鍵の手順)
- [ ] 1-4: エンティティ + リポジトリ(DbConnection / MetaSchema / MetaTable / MetaColumn / PermissionEntry / PermissionAuxEntry / UserGroup / GroupMember)
- [ ] 1-5: テスト(鍵検証の起動失敗系、Flyway V3 適用 + マッピング整合、一意制約の重複拒否)

## Step 2: 接続管理(connection + common.DbDialect)

- [ ] 2-1: CredentialEncryptor(AES-256-GCM、`v1:{keyId}:{IV}:{暗号文}`、active 鍵で暗号化・keyId で復号、復号失敗 CREDENTIAL_DECRYPT_FAILED)
- [ ] 2-2: DbDialect + 4 実装(MySQL / MariaDB / PostgreSQL / H2 — URL 組み立て・ドライバクラス・カタログ/スキーマ概念)※⑤⑥で拡張する基盤
- [ ] 2-3: TargetDataSourceRegistry(ConcurrentHashMap + 遅延生成、evict でクローズ、@PreDestroy、プール枯渇 503 TARGET_DB_BUSY)
- [ ] 2-4: ConnectionService + ConnectionController(CRUD・一覧パスワード非含有・db_type 不変・password null 維持・接続テスト[未保存値可・プール外単発・理由分類]・監査 CONNECTION_* 4 種)
- [ ] 2-5: テスト(Encryptor ラウンドトリップ / 鍵ローテーション / 未知 keyId、Registry 生成・破棄・再生成、CRUD 結合[409・400 種別変更・応答に password 無し]、接続テスト H2 成功/失敗分類)

## Step 3: スキーマ取込(metadata)

- [ ] 3-1: SchemaImportService(DatabaseMetaData 読取・方言差は DbDialect・全置換単一 Tx・saveAll・キャッシュ無効化・監査 SCHEMA_IMPORTED)+ MetadataQueryService(ツリー参照)+ MetadataController
- [ ] 3-2: テスト(H2 ターゲット: テーブル/ビュー/PK/コメント/型の取込結果、再取込全置換、途中失敗ロールバック、未取込は空応答)

## Step 4: 権限・グループ・YAML(permission)

- [ ] 4-1: PermissionService + PermissionController(エントリ設定/削除の 2 操作 — D-18、孤児許容、プリンシパル検証、監査 PERMISSION_SET/REMOVED + 無効化)
- [ ] 4-2: GroupService + GroupController(CRUD・改名 409・削除カスケード[所属 + 権限エントリ]・メンバー管理冪等・監査 GROUP_* 5 種 + 無効化)
- [ ] 4-3: EffectivePermissionResolver(D-21 個別優先 → グループ合成 → デフォルト拒否、操作可否合成[US-014 + D-15]、Caffeine maximumSize + TTL、invalidateAll)
- [ ] 4-4: テスト(US-015 確定例 2 件の受け入れテスト、PBT-03 = jqwik 4 プロパティ[個別優先・グループ単調性・キャッシュ透過性・メタデータ交差]、無効化トリガ結合)
- [ ] 4-5: PermissionYamlService(export 正規順序 / importReplace 全置換・全体拒否 5 条件[構文・スキーマ・重複・未知プリンシパル・不正値])+ YAML API(1 MB アプリ内検証)+ 監査 PERMISSION_EXPORTED/IMPORTED
- [ ] 4-6: テスト(PBT-02 = export→import→export 同一性 + DB 状態一致、拒否 5 条件の全体拒否、Problem Details 理由一覧)

## Step 5: Testcontainers 実エンジンテスト(Q5=A)

- [ ] 5-1: ①の方言別抽象基底を拡張し 3 エンジン × 2 系統 = 6 テスト(接続テスト成功 + 誤資格情報 AUTH_FAILED / サンプルスキーマ取込の meta_* 検証)。disabledWithoutDocker 維持

## Step 6: フロントエンド(3 feature + 統合)

- [ ] 6-1: features/admin/connections(api.ts + ConnectionListPage + ConnectionEditPage — 接続テスト[フォーム現在値]・スキーマ取込・db_type 編集 disabled・password 空欄維持)
- [ ] 6-2: features/admin/permissions(api.ts + PermissionPage — プリンシパル軸ツリー・4 値 Select で D-18 表現・補助権限 3 状態・孤児セクション・YAML export/import)
- [ ] 6-3: features/admin/groups(api.ts + GroupListPage — CRUD・メンバー管理・削除警告)
- [ ] 6-4: routes 4 ルート追加 + AppLayout ナビ(接続管理・グループ管理)+ i18n 辞書(admin ns に connections/permissions/groups、ja/en)
- [ ] 6-5: RTL テスト(ConnectionListPage・ConnectionEditPage[テスト実行・エラー分類表示]・PermissionPage[ツリー設定・孤児表示]・GroupListPage)

## Step 7: DoD 検証と文書化

- [ ] 7-1: `./gradlew clean build` 通過(全テスト。Docker あり環境で実エンジン 6 テスト含む)
- [ ] 7-2: WAR + devenv 実機フロー: 接続登録(MySQL/MariaDB/PostgreSQL/H2 の 4 種)→ 接続テスト(成功・認証失敗)→ スキーマ取込 → 権限設定(個別/グループ)→ **US-015 確定例 2 件の実機再現** → YAML エクスポート → インポート(全置換・重複拒否)→ グループ削除カスケード → 孤児エントリ表示
- [ ] 7-3: DB 実測(資格情報が `v1:` 形式のみで平文なし、監査 13 種のイベントと detail、キャッシュ無効化の挙動)
- [ ] 7-4: code-summary.md 作成(⑤⑥への提供物・確立した規約の追記)

## テスト方針(拡張ルール)

- Security Baseline(ブロッキング): 資格情報の非露出(応答・ログ・監査)、ADMIN 認可、YAML 安全ロード・全体拒否、デフォルト拒否の解決をテストで担保
- PBT(ブロッキング): PBT-03 = EffectivePermissionResolver 4 プロパティ(4-4)、PBT-02 = PermissionYamlService ラウンドトリップ(4-6)。いずれも jqwik

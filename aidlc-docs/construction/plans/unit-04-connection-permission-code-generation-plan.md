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

- [x] 1-1: backend 依存追加(JDBC ドライバ 3 種を testRuntimeOnly → runtimeOnly に昇格、caffeine、jackson-dataformat-yaml — すべて BOM 管理)
- [x] 1-2: `V3__connection_permission.sql`(7 テーブル + インデックス。permission 系は空文字表現の一意制約)
- [x] 1-3: AppProperties 拡張(credential.keys / active-key-id の起動時検証、permission.cache-ttl / cache-max-size)+ application.yaml / application-test.yaml + README(開発用鍵の手順)
- [x] 1-4: エンティティ + リポジトリ(DbConnection / MetaSchema / MetaTable / MetaColumn / PermissionEntry / PermissionAuxEntry / UserGroup / GroupMember)
- [x] 1-5: テスト(AppPropertiesTest に credential 検証 4 件追加、ConnectionPermissionPersistenceTest 3 件 — V3 適用・マッピング整合・一意制約重複拒否。③の AppProperties 直接生成テスト 2 件も追随修正)

## Step 2: 接続管理(connection + common.DbDialect)

- [x] 2-1: CredentialEncryptor(AES-256-GCM、`v1:{keyId}:{IV}:{暗号文}`、active 鍵で暗号化・keyId で復号、復号失敗 CREDENTIAL_DECRYPT_FAILED)
- [x] 2-2: DbDialect + 4 実装(MySQL / MariaDB / PostgreSQL / H2 — URL 組み立て・ドライバクラス・カタログ/スキーマ概念)※ DbType を common.dialect へ移動(common→connection の逆参照回避)。PermissionCacheInvalidated イベント(common.event)と ServiceUnavailableException(503 + Retry-After)も追加
- [x] 2-3: TargetDataSourceRegistry(ConcurrentHashMap + 遅延生成、evict でクローズ、@PreDestroy。503 TARGET_DB_BUSY の送出は取込・⑤⑥のプール利用側)
- [x] 2-4: ConnectionService + ConnectionController(CRUD・一覧パスワード非含有・db_type 不変・password null 維持・接続テスト[未保存値可・プール外単発・理由分類]・監査 CONNECTION_* 4 種。AuditEvents に④の 14 種を追加)
- [x] 2-5: テスト 15 件(CredentialEncryptorTest 6・TargetDataSourceRegistryTest 2・ConnectionApiIntegrationTest 7 — 409/400 種別変更/password 非含有/403/接続テスト成功・AUTH_FAILED 分類・保存済み補完・id 未指定 400/監査 detail 非漏えい)

## Step 3: スキーマ取込(metadata)

- [x] 3-1: SchemaImportService(DatabaseMetaData 読取・方言差は DbDialect・全置換単一 Tx・saveAll・キャッシュ無効化・監査 SCHEMA_IMPORTED。プール初期生成失敗も 502 に分類)+ MetadataQueryService(ツリー 3 クエリ組立 + import-status)+ MetadataController(import / ツリー / 一覧用取込状況)
- [x] 3-2: テスト 4 件(H2 ターゲット: テーブル/ビュー/複合 PK/コメント/NULL 可否の取込結果 + import-status、再取込全置換、失敗ロールバック[502 + FAILURE 監査 + 既存データ残存]、未取込は空応答)

## Step 4: 権限・グループ・YAML(permission)

- [x] 4-1: PermissionService + PermissionController(エントリ設定/削除の 2 操作 — D-18、孤児許容 + orphan フラグ、プリンシパル検証、監査 PERMISSION_SET/REMOVED + 無効化)
- [x] 4-2: GroupService + GroupController(CRUD・改名 409・削除カスケード[所属 + 権限エントリ]・メンバー管理冪等・監査 GROUP_* 5 種 + 無効化)
- [x] 4-3: EffectivePermissionResolver(D-21 個別優先 → グループ合成 → デフォルト拒否、操作可否合成[US-014 + D-15]、Caffeine maximumSize + TTL、invalidateAll + PermissionCacheInvalidated リスナー。解決コアを純粋関数 compute に抽出 — PBT のシーム)
- [x] 4-4: テスト 9 件(US-015 確定例 2 件・操作可否合成・補助権限の個別 false 優先・キャッシュ透過性[P3 決定的検証]・無効化トリガの結合 6 件 + PBT-03 jqwik 3 プロパティ[P1 個別優先・P2 グループ単調性・P4 メタデータ交差。P3 は結合テストで担保 — 純粋関数化の範囲に合わせた調整])
- [x] 4-5: PermissionYamlService(export 正規順序 / importReplace 全置換・全体拒否 5 条件[構文・スキーマ・重複・未知プリンシパル・不正値]、厳格バインド FAIL_ON_UNKNOWN_PROPERTIES)+ YAML API(1 MB アプリ内検証・controller 局所 @ExceptionHandler で理由一覧応答)+ 監査 PERMISSION_EXPORTED/IMPORTED
- [x] 4-6: テスト 11 件(PBT-02 ラウンドトリップ[export→import→export 文字列一致 + DB 状態一致]・全置換・拒否 5 条件[構文/未知プロパティ/重複 + path/未知プリンシパル/不正値]・API 結合 4 件[孤児フラグ・404・400・グループカスケード])

## Step 5: Testcontainers 実エンジンテスト(Q5=A)

- [x] 5-1: ①の方言別抽象基底を拡張し 3 エンジン × 2 系統 = 6 テスト(接続テスト成功 + 誤資格情報 AUTH_FAILED / サンプルスキーマ取込の meta_* 検証 — テーブル・ビュー・PK・NULL 可否)。disabledWithoutDocker 維持。実エンジン(MySQL 8.4 / MariaDB 11.8 / PostgreSQL 18)で 6 件全パスを確認(2026-07-20)

## Step 6: フロントエンド(3 feature + 統合)

- [x] 6-1: features/admin/connections(api.ts + ConnectionListPage + ConnectionEditPage — 接続テスト[フォーム現在値]・スキーマ取込 + ツリー概要・db_type 編集 disabled・password 空欄維持。apiClient に contentType / responseType / errors 解釈を拡張)
- [x] 6-2: features/admin/permissions(api.ts + PermissionPage — プリンシパル軸ツリー[details 折りたたみ]・4 値 Select で D-18 表現・補助権限 3 状態・孤児セクション + 明示削除・YAML export[Blob DL]/import[ファイル選択 + 全置換確認 + 理由一覧表示])
- [x] 6-3: features/admin/groups(api.ts + GroupListPage — CRUD モーダル・メンバー管理モーダル[検索追加/削除]・カスケード削除警告)
- [x] 6-4: routes 5 ルート追加 + AppLayout ナビ(接続管理 🔌・グループ管理 🗂️)+ i18n 辞書(admin ns に connections/permissions/groups、auth ns に nav 2 件、ja/en)
- [x] 6-5: RTL テスト 8 件(ConnectionListPage 2[一覧 + 取込状況・削除 DELETE]・ConnectionEditPage 2[フォーム現在値テスト・AUTH_FAILED 文言]・PermissionPage 2[ツリー設定 PUT・孤児表示 + 削除]・GroupListPage 2[作成 POST・メンバー追加/削除])— 全 48 テストパス・check クリーン

## Step 7: DoD 検証と文書化

- [x] 7-1: `./gradlew clean build` 通過(全テスト。実エンジン 6 テスト含む — 2026-07-20)
- [x] 7-2: WAR + devenv 実機フロー **38 項目 OK**: 4 種接続登録 → 接続テスト(保存済み補完 4 種・誤パスワード AUTH_FAILED)→ 4 種スキーマ取込(PK・コメント確認)→ 権限設定(US-015 例 1 の構成・D-18 の 2 操作・補助権限)→ 孤児フラグ → YAML ラウンドトリップ同一 + 重複/未知プリンシパル全体拒否(既存無傷)→ グループ削除カスケード → 409/400/404/401 ※US-015 の解決結果検証は自動テストで担保(解決結果を返す API は⑤で登場するため実機は設定構成まで)
- [x] 7-3: DB 実測(password_enc は全件 `v1:k1:` 形式のみ・平文なし、監査 13 種別記録、監査 detail に資格情報漏えいなし)
- [x] 7-4: code-summary.md 作成(⑤⑥への提供物 5 点・パッケージ依存の一方向規約・設計後の追加エラーコード 2 種を記録)

## テスト方針(拡張ルール)

- Security Baseline(ブロッキング): 資格情報の非露出(応答・ログ・監査)、ADMIN 認可、YAML 安全ロード・全体拒否、デフォルト拒否の解決をテストで担保
- PBT(ブロッキング): PBT-03 = EffectivePermissionResolver 4 プロパティ(4-4)、PBT-02 = PermissionYamlService ラウンドトリップ(4-6)。いずれも jqwik

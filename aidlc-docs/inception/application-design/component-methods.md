# コンポーネントメソッド定義(Component Methods)— MasterMeister3

**作成日**: 2026-07-18
**注**: 主要メソッドのシグネチャと入出力の高レベル定義。詳細な業務ルールは各ユニットの Functional Design で定義する。型名は概念名(実際の DTO 名は Code Generation で確定)。

---

## auth

| メソッド | 入力 → 出力 | 目的 |
|---|---|---|
| `AuthService.login(email, password)` | 資格情報 → `TokenPair`(access + refresh) | 認証。失敗時はロックアウト判定(D-11)と監査記録 |
| `AuthService.refresh(refreshToken)` | 旧トークン → `TokenPair` | ローテーション。再利用検知時はファミリ一括失効(US-008) |
| `AuthService.logout(refreshToken)` | トークン → void | リフレッシュトークン無効化 |
| `LoginAttemptService.recordFailure(email)` / `isLocked(email)` / `unlock(email)` | — | 失敗カウント・ロック判定・管理者解除 |
| `TokenService.issue(user)` / `validate(jwt)` | — | JWT 発行・検証(署名/有効期限/発行者) |

## user

| メソッド | 入力 → 出力 | 目的 |
|---|---|---|
| `RegistrationService.requestRegistration(email, locale)` | メール+申請時言語 → void | 申請受付。存在有無によらず同一応答(US-001)。言語はメールに引き継ぐ(US-047) |
| `RegistrationService.completeRegistration(token, password)` | トークン+パスワード → void | 登録完了。期限・使用済み検証 |
| `UserService.approve(userId)` / `reject(userId)` | → void | 承認/却下 + 結果メール(US-003) |
| `UserService.listPendingUsers(page)` | → `Page<UserSummary>` | 承認待ち一覧 |
| `AdminBootstrap.ensureAdminUser()` | 環境変数 → void | 起動時初期管理者作成(US-004、冪等) |

## connection

| メソッド | 入力 → 出力 | 目的 |
|---|---|---|
| `ConnectionService.create/update/delete(connectionConfig)` | 接続設定 → `ConnectionId` | CRUD。パスワードは `CredentialEncryptor` で暗号化保存 |
| `ConnectionService.list()` | → `List<ConnectionSummary>` | 一覧(パスワード非含有) |
| `TargetDataSourceRegistry.getDataSource(connId)` | → `DataSource` | 接続別プール取得(遅延生成・設定変更時再生成) |

## metadata

| メソッド | 入力 → 出力 | 目的 |
|---|---|---|
| `SchemaImportService.importSchema(connId)` | → `ImportResult`(件数・結果) | 構造読取 → 内部 DB 保存 → キャッシュ無効化イベント → 監査記録 |
| `MetadataQueryService.listSchemas(connId)` / `listTables(connId, schema)` / `getTable(connId, schema, table)` | → メタデータ DTO | 取込済み構造の参照(権限フィルタは呼び出し側で適用) |

## permission

| メソッド | 入力 → 出力 | 目的 |
|---|---|---|
| `PermissionService.setEntry(principal, scope, value)` | 主体+階層+値 → void | 明示エントリ設定(NONE 含む) |
| `PermissionService.removeEntry(principal, scope)` | → void | エントリ削除 = 未設定に戻す(D-18。NONE 設定とは別操作) |
| `EffectivePermissionResolver.resolve(userId, connId)` | → `EffectivePermissions`(カラム単位解決結果) | D-21 アルゴリズム + Caffeine キャッシュ |
| `EffectivePermissionResolver.invalidate(scope)` | → void | 権限変更/グループ変更/再取込時の無効化 |
| `GroupService.create/rename/delete(group)` / `addMember/removeMember(groupId, userId)` | → void | グループ管理(削除はカスケード — US-018) |
| `PermissionYamlService.export(connId)` | → YAML 文字列 | エクスポート(US-016) |
| `PermissionYamlService.importReplace(connId, yaml)` | → `ImportResult` | 全置換 + 重複拒否 + 単一トランザクション(US-017) |

## record

| メソッド | 入力 → 出力 | 目的 |
|---|---|---|
| `RecordService.listAccessibleTables(userId, connId, schema)` | → `List<TableSummary>` | READ 以上のテーブル/ビュー一覧(US-020) |
| `RecordService.fetchRecords(userId, connId, schema, table, criteria, page)` | 絞り込み条件(UI 指定 + WHERE/ORDER BY 手入力)→ `RecordPage`(行データ + カラムメタデータ) | READ 以上のカラムのみ返却。NONE カラム完全非表示(D-18)。手入力条件は D-19 のとおり許容。応答には権限のあるカラムのメタデータ(名前・型・PK/NULL 可否・実効権限)を同梱し、フロントエンドは表示・編集可否をこれで判定する |
| `RecordBatchService.applyBatch(userId, connId, schema, table, changes)` | 作成/更新/削除の変更セット → `BatchResult` | 統一 API。全件検証 → 単一トランザクション反映。1 件でも失敗なら全ロールバック(US-027)。監査記録は別トランザクション(D-20) |
| `RecordChangeValidator.validate(effectivePermissions, tableMeta, changes)` | → `ValidationResult` | 権限判定(D-15/US-014 のルール)+ 制約事前チェック |

## query

| メソッド | 入力 → 出力 | 目的 |
|---|---|---|
| `QueryBuilderService.buildSql(builderModel)` | タブ入力モデル → SQL 文字列 | スキーマ非修飾 SQL 生成(US-029) |
| `QueryBuilderService.parseSql(sql)` | SQL → `builderModel` | リバースエンジニアリング(US-030。解析不能時は明示エラー) |
| `SavedQueryService.save/update/retire(savedQuery)` | → void | 保存・編集(作成者のみ)・retired 化(US-031〜033) |
| `QueryExecutionService.execute(userId, connId, schema, sql, params, paging)` | → `QueryResult`(行データ + 結果セットのカラムメタデータ) | 読み取り専用強制(H-01)、スキーマ許可リスト検証(D-16)+ 方言別切替、履歴記録、大量取得の監査(US-034〜038)。応答には結果セットのカラムメタデータ(名前・型)を同梱する |
| `QueryExecutionService.detectParameters(sql)` | SQL → `List<ParamName>` | `:param` 自動検出(US-035) |
| `QueryHistoryService.search(criteria, page)` | 絞り込み条件 → `Page<HistoryEntry>` | 履歴一覧・絞り込み(US-039〜040) |

## audit

| メソッド | 入力 → 出力 | 目的 |
|---|---|---|
| `AuditLogService.record(auditEvent)` | イベント種別コード+パラメータ → void | **別トランザクション(`REQUIRES_NEW`、内部 DB 側)**で記録(D-20)。ロケール非依存コード(H-07) |
| `SecurityAlertService.evaluate(securityEvent)` | → void | 閾値判定 → 超過時に管理者メール(D-13) |
| `AuditLogController.search(criteria, page)` | → `Page<AuditEntry>` | 管理者のみ閲覧(US-046) |

## common

| メソッド | 入力 → 出力 | 目的 |
|---|---|---|
| `DbDialect.applySchema(connection, schema)` | → void | 方言別スキーマ切替(PostgreSQL/H2: `SET search_path` 等。MySQL/MariaDB: no-op)(US-036) |
| `DbDialect.quoteIdentifier(name)` / `mapColumnType(meta)` | → String / 型情報 | 識別子クォート・型マッピング(H-04 のフォールバック判定含む) |
| `MailService.send(recipient, template, locale, params)` | → void | テンプレートメール送信(受信者言語 — D-09) |
| `GlobalExceptionHandler.handle(exception)` | → `ProblemDetail` | RFC 9457 変換。内部詳細の秘匿(SECURITY-09/15)、監査記録連携 |

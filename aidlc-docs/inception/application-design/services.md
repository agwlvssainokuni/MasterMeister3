# サービス層設計(Services)— MasterMeister3

**作成日**: 2026-07-18

## 1. サービス層の原則

- **Controller は薄く**: リクエスト検証(入力バリデーション — SECURITY-05)と DTO 変換のみ。業務ロジックは Service に置く
- **Service = トランザクション境界**: `@Transactional` は Service メソッドに付与。Controller・Repository には付けない
- **2 つのデータソースを明確に分離**:
  - **内部 DB(H2/JPA)**: 既定のトランザクションマネージャ
  - **対象 RDBMS(接続別 DataSource/JdbcTemplate)**: `TargetDataSourceRegistry` 経由でのみ取得し、record / query / metadata のサービスだけが触る。トランザクションは対象接続ごとに手動管理(`TransactionTemplate`)
- **セキュリティ制御は Service 層で強制**(SECURITY-08): 実効権限チェックは UI ではなく `RecordService` / `QueryExecutionService` / `RecordChangeValidator` が必ず実施

## 2. 主要オーケストレーション

### 2.1 一括反映(RecordBatchService.applyBatch)— US-027
```
1. EffectivePermissionResolver.resolve() で実効権限取得(キャッシュ経由)
2. RecordChangeValidator.validate() で全変更を事前検証(権限 D-15/D-18、制約)
   → 1 件でも NG なら対象 RDBMS に触らず全体拒否
3. 対象 RDBMS の単一トランザクション内で全変更を実行
   → SQL エラー時は全ロールバック(オールオアナッシング)
4. AuditLogService.record() — 別トランザクション(D-20)。
   手順 2/3 の失敗時も、失敗ステータス付きで必ず記録される
```

### 2.2 クエリ実行(QueryExecutionService.execute)— US-034〜038
```
1. スキーマ許可リスト検証(D-16: ユーザがアクセス権限を持つスキーマか)
2. 読み取り専用の強制(H-01: 構文チェック + 読み取り専用トランザクション/接続)
3. DbDialect.applySchema() で方言別スキーマ切替
4. NamedParameterJdbcTemplate でパラメータバインド実行(ページング適用)
5. QueryHistoryService へ履歴記録 + 大量取得(閾値超過)時は監査記録(別トランザクション)
```

### 2.3 実効権限の解決とキャッシュ無効化 — D-21
```
resolve(userId, connId):
  キャッシュヒット → 返却
  ミス → カラムごとに (1)個別チェーン (2)グループ合成 (3)デフォルト拒否 で解決 → キャッシュ格納

invalidate は以下から呼ばれる(イベント連携):
  - PermissionService(エントリ設定/削除)
  - GroupService(グループ変更・所属変更・削除)
  - SchemaImportService(再取込)
```

### 2.4 監査記録(AuditLogService)— D-20
- 全サービスは `AuditEventPublisher` 経由でイベント(種別コード + パラメータ — H-07)を発行
- `AuditLogService.record()` は内部 DB 側の **`REQUIRES_NEW`** で永続化 — 主処理(対象 RDBMS トランザクションや内部 DB トランザクション)の成否と独立
- 記録後、`SecurityAlertService.evaluate()` がセキュリティイベントの閾値を判定し、超過時は `MailService` で管理者へ通知(D-13)

### 2.5 登録・承認フロー(RegistrationService / UserService)— US-001〜003
```
requestRegistration: 存在チェック(応答は常に同一 — 列挙攻撃対策)
  → 新規のみトークン発行(期限付き)+ 申請時ロケールで確認メール
completeRegistration: トークン検証(期限・未使用)→ パスワード設定(アダプティブハッシュ)
approve/reject: 状態遷移 + 結果メール(受信者言語)+ 監査記録
```

### 2.6 認証・トークン(AuthService)— US-005〜009
```
login: ロック判定(D-11)→ 資格情報検証 → TokenPair 発行 → 失敗時カウント + 監査
refresh: トークン検証 → 使用済み検知ならファミリ一括失効(US-008)+ 監査
        → 正常ならローテーション(旧無効化・新発行)
logout: リフレッシュトークン無効化
```

## 3. トランザクションマネージャ構成

| 対象 | 管理方式 | 用途 |
|---|---|---|
| 内部 DB(H2/JPA) | `JpaTransactionManager`(既定) | ユーザ・権限・保存クエリ・履歴等の CRUD |
| 内部 DB(監査) | 同上 + `REQUIRES_NEW` | 監査記録の独立コミット(D-20) |
| 対象 RDBMS | 接続別 `DataSourceTransactionManager` / `TransactionTemplate` | 一括反映の単一トランザクション、読み取り専用クエリ実行 |

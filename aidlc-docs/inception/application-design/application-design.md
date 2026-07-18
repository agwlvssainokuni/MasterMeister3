# アプリケーション設計書(Application Design)— MasterMeister3

**作成日**: 2026-07-18
**構成ドキュメント**: [components.md](components.md) / [component-methods.md](component-methods.md) / [services.md](services.md) / [component-dependency.md](component-dependency.md)

## 1. 設計方針(確定事項)

| 項目 | 決定 | 出典 |
|---|---|---|
| バックエンド構成 | 機能別パッケージ + レイヤード(`auth / user / connection / metadata / permission / record / query / audit / common`) | 設計プラン Q1=A, Q1-2=A |
| API 規約 | リソース指向 REST + RFC 9457 Problem Details(`errorCode` + `params` 拡張で i18n 連携) | 設計プラン Q2=A |
| フロントエンド状態管理 | TanStack Query(サーバ状態)+ useState/useReducer(編集バッファ)+ Context(認証・言語・テーマ) | 設計プラン Q3=A |

## 2. 全体像

```
[React SPA]  --(REST/JSON, JWT, Problem Details)-->  [Spring Boot]
  app 基盤 / design-system / features                  機能パッケージ x8 + common
                                                        |            |
                                                   [内部 DB(H2)]  [対象 RDBMS x N]
                                                    JPA             NamedParameterJdbcTemplate
                                                                    (接続別プール)
```

- **8 機能パッケージ**: auth(JWT・ロックアウト)、user(登録・承認)、connection(接続・暗号化・プール)、metadata(スキーマ取込)、permission(権限・グループ・YAML・実効権限解決)、record(マスタメンテナンス)、query(ビルダー・保存・実行・履歴)、audit(記録・アラート・閲覧)
- **common**: DB 方言抽象化(`DbDialect` x4)、Problem Details 例外ハンドラ、メール、セキュリティ設定、i18n、設定プロパティ

## 3. 横断的設計判断(要件決定事項との対応)

| 決定 | 設計上の実現 |
|---|---|
| D-20 監査ログ別トランザクション | `AuditEvent`(Spring イベント)→ `AuditLogService` が `REQUIRES_NEW` で内部 DB に記録。audit は他機能に依存しない一方向依存 |
| D-21 実効権限解決 | `EffectivePermissionResolver` に集約(Spring Cache 抽象 `@Cacheable` + Caffeine)。キャッシュ対象は実効権限解決に限らず複数メソッドの想定(メタデータ参照系等)。無効化はイベント連携(権限変更/グループ変更/再取込)で粗粒度一括退避 |
| D-18 NONE と未設定の区別 | `PermissionService.setEntry`(NONE 含む)と `removeEntry`(未設定に戻す)を別 API として定義 |
| D-15 主キーなしテーブル | `RecordChangeValidator` が作成のみ許可(更新・削除は拒否) |
| D-16 スキーマレベル検証 | `QueryExecutionService` 冒頭でスキーマ許可リスト検証のみ実施 |
| D-11 ロックアウト | `LoginAttemptService`(閾値・ロック時間は `mm.app.*` 設定) |
| D-13 アラートメール | `SecurityAlertService`(記録後に閾値評価 → `MailService`) |
| H-01 読み取り専用強制 | 構文チェック + 読み取り専用トランザクション/接続の二重防御 |
| H-03 接続パスワード | `CredentialEncryptor`(鍵は環境変数) |
| Q2=A エラー処理 | `GlobalExceptionHandler` → Problem Details。内部詳細秘匿(SECURITY-09/15)を一箇所で強制 |

## 4. トランザクション設計(要点)

- **内部 DB**: JPA 既定トランザクション。監査記録のみ `REQUIRES_NEW`
- **対象 RDBMS**: 接続別に `TransactionTemplate` で手動管理。一括反映は単一トランザクション(オールオアナッシング)、クエリ実行は読み取り専用
- 詳細は [services.md](services.md) §2〜3

## 5. 検証結果

- **FR カバレッジ**: FR-01〜13 の全機能がいずれかのコンポーネントに割当済み(components.md の対応ストーリー参照)
- **D-01〜21 カバレッジ**: 設計に影響する決定はすべて §3 で実現方法を定義
- **循環依存なし**: 依存マトリクス(component-dependency.md)で確認。audit・common への一方向依存構造
- **Security Baseline**: SECURITY-05(入力検証は Controller 層)、SECURITY-08(認可は Service 層で強制)、SECURITY-09/15(Problem Details 一元化)、SECURITY-11(認証・認可・監査を専用モジュールに分離)、SECURITY-12(ロックアウト・ハッシュ保存)の実現位置を確定。残るルールは NFR Design / Code Generation で対応
- **PBT(Partial)**: 対象候補を持つコンポーネントを特定 — `EffectivePermissionResolver`(不変条件 PBT-03)、`PermissionYamlService`(ラウンドトリップ PBT-02)、`QueryBuilderService.buildSql/parseSql`(ラウンドトリップ PBT-02)。フレームワーク選定は NFR Requirements(PBT-09)

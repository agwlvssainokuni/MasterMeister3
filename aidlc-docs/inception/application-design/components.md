# コンポーネント定義(Components)— MasterMeister3

**作成日**: 2026-07-18
**前提**: 機能別パッケージ + レイヤード(Q1=A)、パッケージ構成 `auth / user / connection / metadata / permission / record / query / audit / common`(Q1-2=A)

---

## 1. バックエンド機能コンポーネント

### auth(認証)
- **責務**: ログイン / リフレッシュ / ログアウト(FR-07)、JWT 発行・検証、リフレッシュトークンのローテーションと再利用検知、アカウントロックアウト(D-11)
- **主要クラス**: `AuthController`、`AuthService`、`TokenService`(JWT 生成・検証)、`RefreshTokenStore`(ハッシュ化永続化・ファミリ管理)、`LoginAttemptService`(失敗回数・ロック管理)
- **対応ストーリー**: US-005〜009

### user(ユーザ管理)
- **責務**: 登録申請〜完了(FR-01)、管理者承認・却下、初期管理者ブートストラップ、登録トークン管理
- **主要クラス**: `RegistrationController`、`RegistrationService`、`RegistrationTokenStore`、`UserAdminController`(承認/却下/ロック解除)、`UserService`、`AdminBootstrap`(起動時初期化)
- **対応ストーリー**: US-001〜004

### connection(対象 RDBMS 接続管理)
- **責務**: 接続設定の CRUD(FR-02)、接続パスワードの可逆暗号化(H-03)、接続ごとのコネクションプール生成・破棄
- **主要クラス**: `ConnectionController`、`ConnectionService`、`CredentialEncryptor`、`TargetDataSourceRegistry`(接続 ID → プール済み DataSource)
- **対応ストーリー**: US-010, US-011

### metadata(スキーマ取込)
- **責務**: 対象 RDBMS からのテーブル/ビュー構造読み取りと内部 DB への取込(FR-03)。取込完了時の権限キャッシュ無効化イベント発行
- **主要クラス**: `MetadataController`、`SchemaImportService`、`MetadataQueryService`(取込済み構造の参照)
- **対応ストーリー**: US-012

### permission(アクセス制御・グループ)
- **責務**: 権限エントリの CRUD(明示的 NONE と未設定の区別 — D-18)、実効権限解決(D-21 + Caffeine キャッシュ)、グループ管理(FR-06)、YAML エクスポート/インポート(FR-05)
- **主要クラス**: `PermissionController`、`PermissionService`、`EffectivePermissionResolver`(解決 + キャッシュ + 無効化)、`GroupController`、`GroupService`、`PermissionYamlService`
- **対応ストーリー**: US-013〜019

### record(マスタメンテナンス)
- **責務**: アクセス可能テーブル一覧、レコード一覧・絞り込み(READ 以上のカラムのみ表示、NONE カラム完全非表示 — D-18)、WHERE/ORDER BY 手入力(D-19)、一括反映の統一 API(オールオアナッシング)、主キーなしテーブルの制限(D-15)
- **主要クラス**: `RecordController`、`RecordService`(参照系)、`RecordBatchService`(反映系)、`RecordSqlBuilder`(方言対応 SQL 組み立て)、`RecordChangeValidator`(権限・制約の事前検証)
- **対応ストーリー**: US-020〜027

### query(クエリ系)
- **責務**: クエリビルダーの SQL 生成・解析(FR-09)、保存クエリ(FR-10)、クエリ実行(FR-11: 読み取り専用強制 H-01、スキーマ許可リスト検証 D-16、方言別スキーマ切替)、実行履歴(FR-12)
- **主要クラス**: `QueryBuilderService`(生成/リバースエンジニアリング)、`SavedQueryController`、`SavedQueryService`、`QueryExecutionController`、`QueryExecutionService`、`QueryHistoryService`
- **対応ストーリー**: US-028〜041

### audit(監査ログ)
- **責務**: 全機能からのイベント記録(D-20: 別トランザクション、主処理失敗時も記録)、セキュリティイベントの閾値監視と管理者メール通知(D-13)、監査ログ閲覧 API(管理者のみ、Phase 4)
- **主要クラス**: `AuditLogService`(記録。`REQUIRES_NEW`)、`AuditEventPublisher`(各機能からの受け口)、`SecurityAlertService`、`AuditLogController`(閲覧)
- **対応ストーリー**: US-042〜046

### common(横断基盤)
- **責務**: DB 方言抽象化(`DbDialect`: MySQL / MariaDB / PostgreSQL / H2 — スキーマ切替・識別子クォート・型マッピング)、グローバル例外ハンドラ(RFC 9457 Problem Details — Q2=A)、メール送信(`MailService`: テンプレート + 受信者言語 — D-09)、セキュリティ設定(Spring Security、CSP/セキュリティヘッダー)、設定プロパティ(`mm.app.*`)、i18n メッセージリソース

## 2. フロントエンドコンポーネント

### アプリ基盤(app)
- **責務**: ルーティング、認証ガード、TanStack Query クライアント(Q3=A)、API クライアント(トークン自動リフレッシュ)、i18n プロバイダ(D-09)、テーマプロバイダ(D-10)

### デザインシステム(design-system)
- **責務**: デザイントークン(ライト/ダーク 2 テーマ — D-10)、基本コンポーネント(Button / Input / Select / Table / Tabs / Modal / Toast 等)、画面パターン。フルスクラッチ構築(D-07)、モックページで事前確認(D-08)

### 機能画面(features)
- **auth**: ログイン、登録申請、パスワード設定
- **admin**: ユーザ承認、接続管理、スキーマ取込、権限マトリクス、グループ管理、監査ログ閲覧
- **record**: テーブル一覧、レコードグリッド(編集バッファ = ローカル状態)、一括反映
- **query**: クエリビルダー(タブ UI)、実行画面(パラメータ入力・結果表示)、保存クエリ、履歴

## 3. データストア

- **内部 DB(H2 + JPA)**: ユーザ、登録トークン、リフレッシュトークン(ハッシュ)、接続設定(パスワード暗号化)、取込スキーマ、グループ・所属、権限エントリ、保存クエリ、実行履歴、監査ログ
- **対象 RDBMS(NamedParameterJdbcTemplate + 接続別プール)**: メンテナンス対象データ。record / query / metadata コンポーネントのみがアクセス

# コンポーネント依存関係(Component Dependency)— MasterMeister3

**作成日**: 2026-07-18

## 1. 依存関係マトリクス(バックエンド)

行 = 依存元、列 = 依存先。○ = 直接依存(呼び出し)。

| 依存元 \ 依存先 | auth | user | connection | metadata | permission | record | query | audit | common |
|---|---|---|---|---|---|---|---|---|---|
| auth | - | ○(ユーザ参照) | | | | | | ○(記録) | ○ |
| user | | - | | | | | | ○(記録) | ○(メール) |
| connection | | | - | | | | | ○(記録) | ○(暗号化) |
| metadata | | | ○(DataSource) | - | ○(キャッシュ無効化) | | | ○(記録) | ○(方言) |
| permission | | ○(ユーザ/グループ) | | ○(構造参照) | - | | | ○(記録) | ○ |
| record | | | ○(DataSource) | ○(構造参照) | ○(実効権限) | - | | ○(記録) | ○(方言) |
| query | | | ○(DataSource) | ○(構造参照) | ○(実効権限/スキーマ許可) | | - | ○(記録) | ○(方言) |
| audit | | | | | | | | - | ○(メール=D-13) |
| common | | | | | | | | | - |

**設計上の要点**:
- **audit は他機能へ依存しない**(common のメール送信のみ)。全機能 → audit の一方向依存で、循環を作らない
- **permission → record/query の逆依存はない**。実効権限は resolver 経由で参照される(pull 型)
- **対象 RDBMS への接続は connection(TargetDataSourceRegistry)に集約**し、metadata / record / query のみが利用する
- **common はどこにも依存しない**(最下層)

## 2. 通信パターン

- **同期呼び出し**: Controller → Service → Repository / Registry(通常のメソッド呼び出し)
- **イベント連携(疎結合)**: 以下は Spring のアプリケーションイベントで結合を切る
  - 権限変更・グループ変更・スキーマ再取込 → `PermissionCacheInvalidationEvent` → `EffectivePermissionResolver`
  - 各機能の監査対象操作 → `AuditEvent` → `AuditLogService`(別トランザクションで永続化 — D-20)
- **フロントエンド ↔ バックエンド**: REST(JSON)。エラーは RFC 9457 Problem Details(Q2=A)。認証は Bearer JWT + リフレッシュ(自動リトライは API クライアント層で実装)

## 3. データフロー(主要 2 系統)

### 参照・編集系(record)
```
[React record 画面]
   v (REST: 一覧取得 / 一括反映)
[RecordController] -> [RecordService / RecordBatchService]
   v 実効権限          v SQL 組み立て(方言)
[EffectivePermissionResolver]  [RecordSqlBuilder + DbDialect]
   v キャッシュ(Caffeine)       v
[permission エントリ(内部 DB)] [対象 RDBMS(接続別プール)]
                                  v
                               [AuditEventPublisher] -> [AuditLogService(REQUIRES_NEW)] -> [内部 DB]
```

### クエリ系(query)
```
[React query 画面(ビルダー/実行/履歴)]
   v (REST)
[QueryExecutionController] -> [QueryExecutionService]
   v スキーマ許可(D-16)   v 読み取り専用強制(H-01) + 方言スキーマ切替
[EffectivePermissionResolver]  [対象 RDBMS]
                                  v 結果 + 履歴
                               [QueryHistoryService(内部 DB)] + [AuditLogService(閾値超過時)]
```

## 4. フロントエンド依存構造

```
[features/*(画面)]
   v                v
[design-system]   [api クライアント + TanStack Query フック]
   v                v
[デザイントークン]  [認証 Context(トークン・自動リフレッシュ)]
```

- features は design-system と api 層にのみ依存(features 間の直接依存は禁止。画面間連携はルーティングとクエリパラメータで行う — 例: 履歴 → クエリビルダーへの SQL 引き継ぎ)
- design-system はアプリ機能に依存しない(独立ユニットとしてモック確認 — D-08)

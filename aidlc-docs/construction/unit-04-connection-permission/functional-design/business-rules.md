# 業務ルール — unit-04-connection-permission

**作成日**: 2026-07-19

## 1. 接続管理(US-010/011)

- CRUD は ADMIN のみ。一覧・詳細応答にパスワードは**一切含めない**(暗号文も返さない)
- 登録・更新: name 重複は 409(`CONNECTION_NAME_DUPLICATE`)。パスワードは登録時必須、**更新時は未入力(null)なら既存値を維持**(変更時のみ再暗号化 — 常に active 鍵で保存)
- 暗号化: domain-entities §3 の形式(AES-256-GCM・鍵 ID 付き・環境変数鍵 — Q1=A/H-03)
- プール(Q4=A): 接続 ID → DataSource を TargetDataSourceRegistry が遅延生成しキャッシュ。接続の更新・削除で当該プールを破棄(クローズ)。DriverManager 直接使用は不可(US-010)
- 接続テスト(Q4=A): 保存とは独立の操作。登録済み接続に対して疎通確認(検証クエリ実行)を行い、成功/失敗 + 失敗理由(接続不可/認証失敗などのコード)を返す。結果は監査記録(CONNECTION_TESTED、outcome で成否)
- 削除: 取込メタデータ・権限エントリをカスケード削除(domain-entities §2)+ プール破棄 + 実効権限キャッシュ無効化。監査ログは残す
- 変更系操作(作成・更新・削除)はすべて監査記録(US-011)。detail_json に接続 ID・name を含める(パスワード関連は記録しない)

## 2. スキーマ取込(US-012)

- 対象: 接続先の TABLE / VIEW の物理名・コメント・カラム(型・サイズ・NULL 可否・デフォルト・コメント)・主キー構成。読取は接続メタデータ API(JDBC DatabaseMetaData 相当)を DbDialect で補正(方言別のスキーマ概念差: MySQL/MariaDB = database、PostgreSQL/H2 = schema)
- 取込は接続単位の**全置換・単一トランザクション**: 既存 meta_* を削除して再挿入。途中失敗時は全ロールバック(取込前の状態を維持)
- 完了時: 実効権限キャッシュ無効化(US-012)+ 監査記録 SCHEMA_IMPORTED(detail: スキーマ/テーブル/カラム件数)。失敗時も監査記録(outcome=FAILURE、detail: 理由コード)
- **孤児権限エントリ(Q2=A)**: 権限エントリは物理名参照のため、再取込で対象が消えてもエントリは保持する。実効権限解決は取込済みメタデータに存在するカラムのみを対象とするため孤児は無害。権限画面で「対象なし」注記を表示し、削除は管理者の明示操作

## 3. 権限エントリ(US-013/014、D-18)

- 主権限: スキーマ(table_name='')/テーブル(column_name='')/カラムの 3 階層に `NONE` / `READ` / `UPDATE` を設定。**「エントリ削除 = 未設定に戻す」と「明示的 NONE の設定」は別操作**(D-18)
- 補助権限: スキーマ/テーブル単位の `CREATE` / `DELETE` × granted 真偽値。行なし = 未設定
- 検証: permission / aux_type / principal の存在(app_user・user_group)、column 指定時は table 必須。**スコープ物理名のメタデータ存在は検証しない**(孤児許容 — Q2=A と一貫。UI はツリー選択のみを提供するため通常は存在する名前になる)
- 変更(設定・変更・削除)はすべて: 監査記録(PERMISSION_SET / PERMISSION_REMOVED)+ 実効権限キャッシュ無効化(US-013)

## 4. 実効権限解決(US-015、D-21)

カラムごと・補助権限種別ごとに独立して評価する:

1. **個別チェーン優先**: ユーザ個別のエントリを詳細→上位(主権限: カラム→テーブル→スキーマ、補助: テーブル→スキーマ)の順に探索し、**最初に見つかった明示エントリの値が実効値**(この項目についてグループ設定は一切参照しない)
2. **グループ合成**: 個別チェーンに明示エントリがなければ、所属各グループのチェーンを同様に解決し、解決できたグループ値を**より許可的な方向で合成**(主権限: UPDATE > READ > NONE の最大、補助権限: OR)
3. **デフォルト拒否**: どこにも明示エントリがなければアクセス権なし(明示的 NONE とは別状態 — D-18。効果は NONE と同じ)

- 確定例(US-015 の 2 例)をそのまま受け入れテスト化する
- 操作可否の合成規則(US-014、D-15):
  - 閲覧: 実効主権限 READ 以上のカラムのみ(NONE / アクセス権なしは非表示 — ⑤で適用)
  - 更新: カラムが UPDATE、かつテーブルに主キーがあり全主キー列が READ 以上(D-15: 主キーなしは更新不可)
  - 作成: テーブルの実効 CREATE = true、かつ(主キーなしテーブル、または全主キー列が UPDATE)
  - 削除: テーブルの実効 DELETE = true、かつ主キーがあり全主キー列が READ 以上(主キーなしは常に削除不可 — D-15)
- キャッシュ: キー (userId, connectionId) → 接続内全カラムの解決結果(Caffeine)。無効化トリガ = 権限エントリ変更・グループ変更(作成/削除/改名/メンバー変更)・スキーマ再取込・接続削除。**粗粒度の全無効化**(Application Design 確定 — 誤って古い権限が残ることを構造的に防ぐ)
- **PBT-03(ブロッキング)**: (P1) 個別チェーンに明示エントリがあるカラムはグループ設定をどう変えても実効値が変わらない、(P2) グループ追加・グループへの付与追加で実効権限が狭まらない(合成の単調性)、(P3) キャッシュ経由と直接解決の結果が常に一致、(P4) 解決結果は取込済みメタデータのカラム集合のみを対象とする(孤児エントリが結果に現れない)

## 5. グループ管理(US-018/019)

- 作成・改名: name 重複は 409(`GROUP_NAME_DUPLICATE`)。改名は YAML(グループ名参照)に影響するため監査 detail に old/new を記録
- 削除: 所属(FK CASCADE)+ 当該グループの全接続の権限エントリを同一トランザクションで削除(US-018)+ キャッシュ無効化
- メンバー追加・削除: 重複追加は冪等(既所属なら成功扱い)。変更で対象ユーザのキャッシュ無効化(粗粒度: 全無効化で包含)
- すべて監査記録: GROUP_CREATED / GROUP_RENAMED / GROUP_DELETED / GROUP_MEMBER_ADDED / GROUP_MEMBER_REMOVED

## 6. YAML エクスポート/インポート(US-016/017、Q3=A)

### 形式(version 1)
```yaml
version: 1
connection: sales-db          # 参考情報(インポート時は URL の接続 ID が正。不一致は警告扱いにしない)
users:
  - email: user@example.com   # プリンシパルは email で表現(Q3=A)
    main:
      - {schema: sales, table: customers, column: name, permission: READ}   # table / column は省略可(階層)
    aux:
      - {schema: sales, table: customers, type: CREATE, granted: true}      # table 省略可(スキーマ単位)
groups:
  - name: 営業部              # プリンシパルはグループ名で表現(Q3=A)
    main: [...]
    aux: [...]
```

### エクスポート(US-016)
- 接続単位の全権限エントリ(主 + 補助)を上記形式で出力。**正規順序**(users は email 昇順、groups は name 昇順、エントリは schema/table/column/aux_type 昇順)で出力し、ラウンドトリップの同一性を保証
- 監査記録 PERMISSION_EXPORTED(独立イベント種別 — US-016)

### インポート(US-017)
- **全置換・単一トランザクション**: 対象接続の既存エントリ(主 + 補助)を全削除 → YAML 内容から再構築。いかなる失敗でも全ロールバック(部分適用なし)
- 全体拒否の条件(いずれか 1 件でも): YAML 構文エラー / スキーマ不正(未知キー・型不一致)/ 重複エントリ(同一プリンシパル×スキーマ×テーブル×カラム、または同一プリンシパル×スキーマ×テーブル×補助種別)/ **未知プリンシパル**(email・グループ名が存在しない — Q3=A)/ 不正値(permission・type・granted)
- 拒否応答は Problem Details に理由一覧(行位置・理由コード)を含める。適用成功時は件数を返す
- YAML の解析は**型を限定した安全ロード**のみ(任意オブジェクトのデシリアライズ禁止 — SECURITY-13)
- 完了で監査記録 PERMISSION_IMPORTED(detail: 件数)+ キャッシュ無効化。拒否時も outcome=FAILURE で記録
- **PBT-02(ブロッキング)**: export → import → export の同一性(ラウンドトリップ)、および import 後の DB 状態が YAML 内容と一致すること

## 7. 監査イベントカタログ(④追加分 — US-043、③の拡張規約に従う)

| event_type | connection_id | detail_json 例 |
|---|---|---|
| CONNECTION_CREATED / CONNECTION_UPDATED / CONNECTION_DELETED | 対象 | {"name": "..."}(資格情報は記録しない) |
| CONNECTION_TESTED | 対象 | 失敗時 {"reason": "CONNECT_FAILED"\|"AUTH_FAILED"\|...} |
| SCHEMA_IMPORTED | 対象 | 成功 {"schemas": n, "tables": n, "columns": n} / 失敗 {"reason": "..."} |
| PERMISSION_SET / PERMISSION_REMOVED | 対象 | {"principalType": "USER", "principalId": n, "scope": "sales.customers.name", "permission": "READ"}(補助は "auxType"/"granted") |
| GROUP_CREATED / GROUP_DELETED | なし | {"groupId": n, "name": "..."} |
| GROUP_RENAMED | なし | {"groupId": n, "oldName": "...", "newName": "..."} |
| GROUP_MEMBER_ADDED / GROUP_MEMBER_REMOVED | なし | {"groupId": n, "userId": n} |
| PERMISSION_EXPORTED | 対象 | {"entries": n} |
| PERMISSION_IMPORTED | 対象 | 成功 {"entries": n} / 失敗 {"reason": "YAML_DUPLICATE_ENTRY", ...} |

- actor = 操作管理者(③の基盤が自動設定)。detail_json はコード・数値・ID のみ(H-07)

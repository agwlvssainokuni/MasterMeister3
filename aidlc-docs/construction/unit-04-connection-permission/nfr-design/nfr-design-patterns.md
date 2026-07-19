# NFR Design — unit-04-connection-permission

**作成日**: 2026-07-19
**対応**: nfr-requirements.md の NFR-U4-01〜08 を実装構造に落とし込む。

## 1. 資格情報暗号化(NFR-U4-01)

- `AppProperties` に `credential(Map<String, String> keys, String activeKeyId)` を追加。コンパクトコンストラクタで検証: keys 非空・全値が Base64 復号で 32 バイト・activeKeyId が keys に存在。不備は起動失敗(③ jwt.secret と同じ方針)
- `CredentialEncryptor`: `Cipher("AES/GCM/NoPadding")`、IV は SecureRandom 12 バイト・タグ 128 ビット。encrypt は常に active 鍵、decrypt は保存形式の keyId で鍵選択(未知 keyId は復号エラー = 設定から鍵を早く消しすぎた運用ミスを明示)
- 平文の非露出: ConnectionService の DTO 変換でパスワード項目を持たない(応答型に存在しない)。toString/ログ出力にも含めない(record の項目から除外)
- 復号失敗時(鍵ローテーションで旧鍵を早く除去した等): 接続の利用時(テスト・取込・プール生成)に理由コード `CREDENTIAL_DECRYPT_FAILED`(400 系。汎用 500 にしない — 運用ミスを明確に指す)を返し監査記録。復旧手段はパスワード再入力(active 鍵で再暗号化)

## 2. 接続別プール(NFR-U4-02)

- `TargetDataSourceRegistry`: `ConcurrentHashMap<Long, HikariDataSource>` + computeIfAbsent で遅延生成。JDBC URL は `DbDialect.buildUrl(connection)` で組み立て
- 生成時設定: maximumPoolSize = pool_max_size、connectionTimeout = pool_timeout_ms、poolName = `mm-target-{id}`(ログで識別)
- `evict(connectionId)`: マップから除去して close(接続の更新・削除時に ConnectionService から呼出)。アプリ終了時は @PreDestroy で全 close
- 取込・(⑤⑥の)データアクセスはこのプール経由。**プール外の単発接続は接続テストのみ**(§6)
- **リトライしない(Q1=A)**: 接続確立・操作の失敗は即時に理由コード付きで返す(fail-fast)。管理者の対話操作であり明示的な再実行が自然
- **プール枯渇(Q2=B)**: pool_timeout_ms 内に接続を取得できない場合は **503 + Retry-After ヘッダー**(理由コード `TARGET_DB_BUSY`)。上流 DB 障害(502)と区別する。この規約は⑤⑥のデータアクセスにも適用

## 3. 実効権限キャッシュ(NFR-U4-03)

- `EffectivePermissionResolver` 内に Caffeine キャッシュ: `maximumSize(cache-max-size)` + `expireAfterWrite(cache-ttl)`(Q3=A)
- キー: `(userId, connectionId)` の record。値: 解決済み `EffectivePermissions`(カラム単位の主権限 + テーブル/スキーマ単位の補助権限、操作可否判定メソッド付きイミュータブル)
- `invalidateAll()` の呼出箇所(粗粒度): PermissionService(エントリ変更)・GroupService(作成/改名/削除/メンバー変更)・SchemaImportService(取込完了)・ConnectionService(接続削除)

## 4. スキーマ取込(NFR-U4-05)

- 読取: プールから 1 Connection を取得し `DatabaseMetaData`(getTables / getColumns / getPrimaryKeys)。方言差(カタログ/スキーマの使い分け)は `DbDialect` が吸収(MySQL/MariaDB = catalog、PostgreSQL/H2 = schema)
- 保存: **シンプルに JPA saveAll(バッチ最適化なし — Q4=C)**。PK が IDENTITY 採番のため Hibernate の JDBC バッチはどのみち効かず、内部 H2 は同一プロセスで往復コストがない。取込は低頻度の管理者操作であり数秒の応答を許容。実測で問題が出た場合に SEQUENCE 採番 + バッチ化を検討
- 単一 @Transactional で「全削除 → 再挿入」。例外は全ロールバック + SCHEMA_IMPORTED(FAILURE)発行(発行は catch 側 = ロールバック外)

## 5. YAML(NFR-U4-04)

- `YAMLMapper`(tools.jackson.dataformat.yaml)+ record バインド: `PermissionYamlV1(version, users, groups)` — **未知プロパティは拒否**(厳格モード)、型不一致も即エラー
- コントローラは `String` で受領し、まずサイズ検証(1 MB 超は 400)→ パース → 検証 → 適用。**Tomcat の maxPostSize はフォーム POST 限定のためアプリ内検証で担保**
- エクスポートは record を構築(users/groups/エントリを正規順序でソート済みリストに詰める)→ writeValueAsString。順序は record 定義順で安定

## 6. 接続テスト(NFR-U4-06 関連)

- プール外の単発接続: Spring の `SimpleDriverDataSource` 相当(DbDialect からドライバクラスと URL を取得)+ `Connection.isValid(5)`。try-with-resources で確実にクローズ
- 失敗理由の分類: SQLState 28xxx / ドライバ固有の認証エラー → `AUTH_FAILED`、タイムアウト → `TIMEOUT`、その他 SQLException → `CONNECT_FAILED`(スタックトレースは応答に含めない — SECURITY-09/15)

## 7. Testcontainers 結合テスト(NFR-U4-07)

- ①の実エンジンスモークテスト基盤(方言別抽象基底・disabledWithoutDocker・colima 対応)を拡張。コンテナはエンジンごと 1 個(クラス単位で起動・破棄)・計 3 個
- 各エンジン(MySQL / MariaDB / PostgreSQL)× 2 系統 = **6 テスト(Q5=A)**: (a) 接続テストが成功する(+ 誤資格情報で AUTH_FAILED)、(b) サンプルスキーマ(テーブル・ビュー・PK・コメント)を取込み meta_* が期待どおり
- H2 ターゲットの結合テスト(取込 → 権限設定 → 解決 → YAML 往復)は Docker 不要で常時実行

## 8. PBT 構成(PBT-02/03 — ブロッキング)

- ジェネレータ: プリンシパル集合(ユーザ/グループ + 所属)・スコープ空間(スキーマ×テーブル×カラムの小さな宇宙)・エントリ集合(主権限 3 値 + 補助 2 種 × granted)を jqwik Arbitrary で合成
- PBT-03: P1 個別優先(グループ設定の任意変更に対する不変)/ P2 グループ単調性 / P3 キャッシュ透過性 / P4 解決結果はメタデータ交差のみ
- PBT-02: エントリ集合 → export → import → export の同一性(文字列一致)+ import 後の DB 状態一致

# Story Generation Plan(ストーリー生成プラン)

**作成日**: 2026-07-18
**入力**: [requirements.md](../requirements/requirements.md)(FR-01〜13、NFR-01〜08、D-01〜17)、[project-overview.md](../requirements/project-overview.md)

本プランは 2 部構成: 前半は方針を決める質問(要回答)、後半は承認後に実行する生成手順のチェックリスト。

---

## Part A: 方針決定の質問

以下の質問の `[Answer]:` タグに回答を記入してください。

### Question 1: ペルソナの構成
どのペルソナ構成でストーリーを作成しますか?

A) 2 ペルソナ: 管理者 / 一般ユーザ — システムの役割区分と一致するシンプルな構成(推奨)

B) 3 ペルソナ: 管理者 / データ更新ユーザ / データ閲覧ユーザ — 権限差(UPDATE 可否)をペルソナとして分離

C) 4 ペルソナ以上: 上記に加え、未登録ユーザ(登録申請者)を独立ペルソナとして扱う

X) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 2: ストーリーのブレークダウン方式
ストーリーをどの方式で構成しますか?

A) エピック基準(推奨)— FR-01〜13 をエピックとし、その配下に個別ストーリーを配置。要件とのトレーサビリティが最も明確で、MVP の段階リリース順序(ユーザ管理 → 接続 → 権限 → データ表示 → クエリ系)ともそのまま対応する

B) ユーザジャーニー基準 — 「初回登録から初データ編集まで」のような体験フローに沿って構成。体験の欠落は見つけやすいが、要件との対応付けが複雑になる

C) ペルソナ基準 — 管理者ストーリー群 / 一般ユーザストーリー群に大別

X) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 3: ストーリーの粒度
個々のストーリーの粒度はどうしますか?

A) 機能操作単位(推奨)— 「管理者として接続を登録する」「ユーザとしてレコードを絞り込む」のように、1 つの操作・画面遷移で完結する単位(FR あたり 2〜6 ストーリー、全体で 40〜60 程度を想定)

B) 機能単位 — FR とほぼ 1:1 の粗い粒度(全体で 15〜20 程度)。管理しやすいが受け入れ基準が長大になる

C) 詳細操作単位 — バリデーションエラー系も独立ストーリーに分割する細かい粒度(全体で 80 以上)。網羅的だが単独開発では過剰になりやすい

X) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 4: 受け入れ基準の形式
受け入れ基準(Acceptance Criteria)はどの形式で記述しますか?

A) Given / When / Then(Gherkin 形式)— テストコードへの変換が機械的にでき、例示ベーステストおよび PBT の不変条件抽出(PBT-03)と相性が良い(推奨)

B) チェックリスト形式 — 「〜できること」の箇条書き。読みやすいがテスト化の際に条件の再解釈が必要

C) ハイブリッド — 正常系は Given/When/Then、細かい制約はチェックリスト

X) Other (please describe after [Answer]: tag below)

[Answer]: A

### Question 5: MVP フェーズのタグ付け
requirements.md の MVP スコープ境界(§8)に基づき、各ストーリーにリリースフェーズをタグ付けしますか?

A) タグ付けする(推奨)— Phase 1: ユーザ管理・認証 / Phase 2: 接続・スキーマ取込・アクセス制御 / Phase 3: マスタメンテナンス / Phase 4: クエリ系機能(ビルダー・保存・実行・履歴)+ 監査ログ閲覧、の 4 フェーズを付与し、Units Generation・実装順序の基礎にする

B) タグ付けしない — フェーズ分けは Workflow Planning / Units Generation で別途行う

X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

## Part B: 生成手順(プラン承認後に実行)

### Phase 1: ペルソナ生成
- [ ] Question 1 の回答に基づき personas.md を作成(各ペルソナ: 名前、役割、目標、技術リテラシー、主な利用シーン、フラストレーション)
- [ ] ペルソナと FR-01〜13 の対応マップを personas.md に含める

### Phase 2: ストーリー生成
- [ ] Question 2/3 の回答に基づき、FR-01〜13 を網羅するストーリーを stories.md に作成
- [ ] 各ストーリーは INVEST 基準(Independent / Negotiable / Valuable / Estimable / Small / Testable)に準拠
- [ ] 各ストーリーに Question 4 の形式で受け入れ基準を記述
- [ ] 決定事項 D-01〜17(特に D-11 ロックアウト、D-13 メール通知、D-15 主キーなしテーブル、D-16 スキーマレベル検証)をストーリーの受け入れ基準に反映
- [ ] Question 5 の回答に基づきフェーズタグを付与
- [ ] 各ストーリーに要件 ID(FR-xx)への参照を付け、トレーサビリティを確保

### Phase 3: 検証
- [ ] FR-01〜13 のすべてが 1 つ以上のストーリーでカバーされていることを確認(カバレッジ表を stories.md 末尾に付す)
- [ ] ペルソナ⇔ストーリーのマッピングを確認
- [ ] スコープ外事項(requirements.md §7)がストーリーに混入していないことを確認

### Phase 4: 完了処理
- [ ] aidlc-state.md / audit.md を更新
- [ ] 完了メッセージを提示しユーザ承認を待つ

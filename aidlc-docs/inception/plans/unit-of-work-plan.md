# Unit of Work Plan(ユニット分割プラン)

**作成日**: 2026-07-18
**入力**: requirements.md(D-01〜21)、stories.md(48 ストーリー / 14 エピック)、execution-plan.md(6 ユニット見通し)、application-design 成果物一式

本プランは 2 部構成: 前半は分割方針の質問(要回答)、後半は承認後に実行する生成作業のチェックリスト。

**前提(確定済みのため質問しない事項)**:
- デプロイ形態は単一実行可能 WAR のモノリス(D-14)。よって各ユニットは独立デプロイのサービスではなく、**開発順序を区切る作業単位(モジュール)**である
- コード配置はルート Gradle マルチプロジェクト + フロントエンドを WAR に同梱(要件 §6)。ユニット分割によるディレクトリ構成の変更はない
- ソロ開発のため、チーム分担・所有権の質問は該当なし

---

## Part A: 分割方針の質問

### Question 1: ユニット分割案
execution-plan.md の見通しでは 6 ユニットとしていました。この分割を採用しますか?

A) 6 ユニット案(推奨)— ①骨格・開発環境 ②デザインシステム+モック(承認ゲート — D-08) ③Phase 1: 認証・ユーザ管理・監査基盤 ④Phase 2: 接続・スキーマ取込・権限 ⑤Phase 3: マスタメンテナンス ⑥Phase 4: クエリ系 + 監査閲覧。各ユニットが「動くものを確認できる」節目になり、MVP 優先順位(D 系決定)とも一致する

B) 5 ユニット案 — ①と②を統合(骨格構築とデザインシステムを 1 ユニットで実施)。節目は減るが、②のモック承認ゲートが大きくなる

C) 4 ユニット案 — Phase 単位のみ(骨格・デザインシステムは Phase 1 ユニットに吸収)。ユニット数は最少だが、最初のユニットが肥大化しゲート確認が粗くなる

X) Other (please describe after [Answer]: tag below)

[Answer]:

### Question 2: ユニットの開発順序
ユニット間の進め方をどうしますか?

A) 厳密に直列(推奨)— ①→②→③→④→⑤→⑥ の順に、各ユニットの Code Generation 完了(動作確認)後に次へ進む。ソロ開発では最も安全で、依存の前提が常に成立する

B) 一部並行 — 例えば②(デザインシステム)のモック作成と③(認証)の設計を並行する。文脈切替のコストが増える

X) Other (please describe after [Answer]: tag below)

[Answer]:

### Question 3: 監査ログ閲覧画面(US-046)の割当
監査記録の基盤はユニット③(Phase 1)で構築しますが、閲覧画面はストーリーのフェーズタグどおり Phase 4 です。どちらのユニットに割り当てますか?

A) ユニット⑥(推奨)— フェーズタグどおり。閲覧はクエリ履歴画面(US-039〜040)と UI パターンが近く、まとめて作るのが効率的

B) ユニット③ — 監査基盤と同時に閲覧まで作り、早期に記録内容を目視確認できるようにする(MVP としては過剰の可能性)

X) Other (please describe after [Answer]: tag below)

[Answer]:

### Question 4: ユニット①(骨格・開発環境)の成果物範囲
最初のユニットに含める範囲をどうしますか?

A) ビルド骨格 + 開発環境(推奨)— ルート Gradle マルチプロジェクト(`./gradlew build` で WAR 生成まで通る空アプリ)、React+Vite 雛形、内部 DB(H2)+ Flyway の骨格、docker compose による対象 RDBMS 4 種(MySQL/MariaDB/PostgreSQL/H2 ※H2 は内蔵)の開発環境。CI は含めない(ソロ開発・ローカルビルドで十分)

B) A + GitHub Actions CI — プッシュ時のビルド・テスト自動実行まで整備する

X) Other (please describe after [Answer]: tag below)

[Answer]:

---

## Part B: 生成作業(承認後に実行)

- [ ] `aidlc-docs/inception/application-design/unit-of-work.md` — ユニット定義と責務、各ユニットの完了条件(Definition of Done)、グリーンフィールドのコード編成方針(要件 §6 のルート Gradle マルチプロジェクト構成を反映)
- [ ] `aidlc-docs/inception/application-design/unit-of-work-dependency.md` — ユニット間依存マトリクスと開発順序
- [ ] `aidlc-docs/inception/application-design/unit-of-work-story-map.md` — 48 ストーリー(US-001〜048)の全ユニット割当マップ(漏れなし検証付き)
- [ ] ユニット境界と依存の妥当性検証(機能パッケージ構成・依存マトリクスとの整合)
- [ ] 全ストーリーの割当完了検証(未割当ゼロ)

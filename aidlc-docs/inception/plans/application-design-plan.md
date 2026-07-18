# Application Design Plan(アプリケーション設計プラン)

**作成日**: 2026-07-18
**入力**: requirements.md(D-01〜21)、stories.md(48 ストーリー)、execution-plan.md

本プランは 2 部構成: 前半は設計方針の質問(要回答)、後半は承認後に実行する設計作業のチェックリスト。

---

## Part A: 設計方針の質問

### Question 1: バックエンドのアーキテクチャスタイル
Spring Boot バックエンドの構成をどうしますか?

A) 機能別パッケージ + レイヤード(推奨)— `auth` / `user` / `connection` / `metadata` / `permission` / `maintenance` / `query` / `audit` のような機能パッケージの内部に Controller / Service / Repository を置く。機能単位の見通しが良く、ユニット分割(Phase 1〜4)ともそのまま対応する

B) レイヤー別パッケージ — `controller` / `service` / `repository` の技術レイヤーで大分類する伝統的構成。小規模では単純だが、機能横断の見通しが悪くなりがち

C) ヘキサゴナルアーキテクチャ(ポート & アダプタ)— ドメインを中心に据え、DB・Web を アダプタとして分離。テスト容易性は最良だが、本規模では抽象化のオーバーヘッドが大きい

X) Other (please describe after [Answer]: tag below)

[Answer]: A(ただし機能パッケージ名のうち `maintenance` は再考中 — 下記 Question 1-2 参照)

### Question 1-2: マスタメンテナンス機能のパッケージ名
FR-08(テーブル/ビュー一覧、レコード一覧・絞り込み・編集・作成・削除・一括反映)を担う機能パッケージの名前をどうしますか?

A) `record` — 機能の実体(レコードの閲覧・編集・一括反映)に最も一致。短く曖昧さがない(推奨)

B) `data` — 広義のデータ操作。ただしクエリ系(query)もデータを扱うため境界が曖昧になりやすい

C) `table` — テーブル/ビュー一覧が入口である点に着目。ただし操作対象はレコードなのでややずれる

D) `editor` — 編集機能である点に着目。閲覧(READ のみのユーザ)も含むため範囲がずれる

X) Other (please describe after [Answer]: tag below)

[Answer]:

### Question 2: REST API の規約
API の設計規約をどうしますか?

A) リソース指向 REST + RFC 9457 Problem Details(推奨)— `/api/connections/{id}/schemas` のようなリソース階層。エラー応答は RFC 9457(application/problem+json)で統一し、フロントエンドのエラーハンドリングと i18n(エラーコード → 表示文言)を一元化する

B) リソース指向 REST + 独自エラー形式 — `{code, message, details}` のようなプロジェクト独自のエラー JSON

C) 操作指向(RPC 風)— `/api/executeQuery` のような動詞ベース。画面との対応は素直だがリソースの一貫性が下がる

X) Other (please describe after [Answer]: tag below)

[Answer]:

### Question 3: フロントエンドの状態管理
React 19 での状態管理方針をどうしますか?

A) TanStack Query + ローカル状態(推奨)— サーバ状態(レコード、スキーマ、権限等)は TanStack Query のキャッシュで管理し、画面ローカルの編集状態は useState / useReducer。認証トークン等の少量のグローバル状態のみ Context。本アプリはサーバ状態が支配的なため最も自然

B) Redux Toolkit — 全状態を一元管理。強力だが本規模ではボイラープレートが過剰

C) Zustand + 手書きフェッチ — 軽量だがサーバ状態のキャッシュ・再検証を自作することになる

X) Other (please describe after [Answer]: tag below)

[Answer]:

---

## Part B: 設計作業(承認後に実行)

- [ ] components.md — コンポーネント定義と責務(バックエンド機能コンポーネント、フロントエンド画面/共有コンポーネント、横断基盤: 監査ログ D-20、権限解決 D-21、DB 方言抽象化)
- [ ] component-methods.md — 各コンポーネントの主要メソッドシグネチャ(入出力型。詳細な業務ルールは Functional Design で定義)
- [ ] services.md — サービス層の定義とオーケストレーション(トランザクション境界、別トランザクション監査記録、キャッシュ無効化の連携)
- [ ] component-dependency.md — 依存関係マトリクスと通信パターン、データフロー
- [ ] application-design.md — 上記を統合した設計書
- [ ] 設計の完全性・一貫性の検証(FR-01〜13 / D-01〜21 とのトレース、Security Baseline 該当ルールの確認)

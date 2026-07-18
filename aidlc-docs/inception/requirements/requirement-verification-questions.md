# 要件確認質問(Requirements Verification Questions)

以下の質問にご回答ください。各質問の `[Answer]:` タグの後に回答を記入してください。
選択式の質問は該当する選択肢の記号(A、B、C…)を記入し、どの選択肢にも当てはまらない場合は「Other」を選んで内容を記述してください。

---

## Question 1(自由記述)
今回開発したいシステム/アプリケーションについて、概要を教えてください。
(目的、主な機能、想定ユーザー、解決したい課題など、わかる範囲で自由にご記入ください)

[Answer]:

---

## Question 2
アプリケーションの形態はどれを想定していますか?

A) Web アプリケーション(ブラウザから利用)

B) CLI ツール(コマンドラインから利用)

C) デスクトップアプリケーション

D) ライブラリ/フレームワーク(他のプログラムから利用)

E) Other (please describe after [Answer]: tag below)

[Answer]: A(SPA タイプの Web アプリケーション)

---

## Question 3
技術スタック(プログラミング言語・フレームワーク・データベースなど)について、指定はありますか?

A) 指定あり(下の [Answer]: タグの後に具体的に記述してください)

B) おまかせ(要件に応じて AI が提案し、承認を得る)

C) Other (please describe after [Answer]: tag below)

[Answer]: A(フロントエンド: React + TypeScript + Vite、バックエンド: Spring Boot + Java 25 + Gradle (wrapper))

---

## Question 4: Security Extensions
このプロジェクトにセキュリティ拡張ルールを適用しますか?

A) はい — すべての SECURITY ルールをブロッキング制約として適用する(本番運用を想定したアプリケーションに推奨)

B) いいえ — SECURITY ルールをスキップする(PoC・プロトタイプ・実験的プロジェクト向け)

X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

## Question 5: Resiliency Extensions
このプロジェクトにレジリエンシーベースラインを適用しますか?

**この拡張の内容**: 有効にすると、AWS Well-Architected Framework(信頼性の柱)およびレジリエンスレビューのガイダンスに基づく、**設計時の方向性を示すベストプラクティス**が適用されます。フォールトトレランス、高可用性、可観測性、復旧可能性に向けて要件・設計・コードを導きます(ビジネス目標、変更管理、可観測性、高可用性、災害復旧、継続的改善にわたる 15 のプラクティス領域)。

**この拡張がしないこと**: 有効にしてもワークロードが本番対応になることを保証するものではなく、可用性・RTO・RPO の目標を認証・保証するものでもありません。早期に適切なレジリエンシー判断を支援する**出発点**であり、構築後のシステムに対する正式な AWS Well-Architected Review の代替ではありません。

A) はい — レジリエンシーベースラインを設計時ガイダンスとして適用する(ビジネスクリティカルなワークロードに推奨)

B) いいえ — レジリエンシーベースラインをスキップする(迅速な反復を優先する PoC・プロトタイプ・実験的プロジェクト向け)

X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

## Question 6: Property-Based Testing Extension
このプロジェクトにプロパティベーステスト(PBT)ルールを適用しますか?

A) はい — すべての PBT ルールをブロッキング制約として適用する(ビジネスロジック、データ変換、シリアライゼーション、ステートフルコンポーネントを含むプロジェクトに推奨)

B) 一部適用 — 純粋関数とシリアライゼーションのラウンドトリップのみに PBT ルールを適用する(アルゴリズム的複雑性が限定的なプロジェクト向け)

C) いいえ — PBT ルールをスキップする(シンプルな CRUD アプリケーション、UI のみのプロジェクト、薄い統合レイヤー向け)

X) Other (please describe after [Answer]: tag below)

[Answer]: B(2026-07-18 訂正: 当初 A と回答後、B に変更)

---

## Question 7: 画面デザインシステムの作成方針
デザインシステム(UI コンポーネント・デザイントークン・画面パターンの体系)をどのように作成しますか?

本アプリはデータ密度の高い管理系 SPA(データグリッド、権限マトリクス、タブ型クエリビルダー、SQL エディタ、フォーム群)であり、単独開発・MVP ファーストという制約があります。また Security Baseline 適用により CSP で `unsafe-inline` を避ける必要があり、ランタイム CSS-in-JS を使うライブラリとは相性が悪い点を考慮してください。

A) Mantine ベース(推奨)— CSS Modules ベースで CSP と相性が良く、フォーム・モーダル・日付入力・通知など管理画面に必要な部品が最初から揃う。データグリッドは TanStack Table を併用。デザイントークンは Mantine のテーマ機構に集約

B) shadcn/ui + Tailwind CSS ベース — Radix UI 上のコンポーネントをコードとしてリポジトリに取り込む方式。カスタマイズ自由度と資産の所有権が最大だが、部品の組み立て工数は A より多い。データグリッドは TanStack Table を併用

C) MUI または Ant Design ベース — 最も部品が豊富だが、ランタイム CSS-in-JS のため CSP(SECURITY-04)対応に nonce 導入などの追加作業が必要

D) フルスクラッチで独自デザインシステムを構築 — 統制は最大だが単独開発・MVP ファーストとは不整合

X) Other (please describe after [Answer]: tag below)

[Answer]: D(デザインシステムを作成し、実装前にモックで確認する方針)

---

## Question 8: デザインシステムのモック確認方法
フルスクラッチのデザインシステム(Q7=D)を実装前にモックで確認する方法として、どれを希望しますか?

A) リポジトリ内の静的 HTML モック — ビルド不要でブラウザから直接開ける HTML/CSS ファイル群(デザイントークン一覧・コンポーネントカタログ・主要画面のモック)を作成。確認後、React コンポーネントとして本実装

B) Vite 上のモックページ — frontend プロジェクト内にモック用ルートを設け、実際の React + CSS 資産としてモックを作成(モックがそのまま本実装の土台になる)

C) Storybook 導入 — コンポーネントカタログとしてストーリー単位で確認(導入・維持の工数は最も大きい)

X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

# 追加確認質問(2026-07-18 承認前確認)

## Question 9: UI の言語
画面の表示言語はどうしますか?(デザインシステム・メッセージ設計に影響)

A) 日本語のみ

B) 英語のみ

C) i18n 対応(日本語 + 英語、切替可能)

X) Other (please describe after [Answer]: tag below)

[Answer]: C

---

## Question 10: ダークモード対応
デザインシステムをフルスクラッチで作るにあたり、ダークモードに対応しますか?(デザイントークンの設計に直結するため、後付けよりも最初に決めるのが効率的)

A) ライトモードのみ

B) ライト + ダーク両対応(OS 設定連動 + 手動切替)

C) ダークモードのみ

X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

## Question 11: ログインのブルートフォース対策(SECURITY-12 対応)
Security Baseline はログインエンドポイントへのブルートフォース対策(ロックアウト / 漸進遅延 / CAPTCHA のいずれか)を必須としています。project-overview.md には規定がないため、方式を確定してください。

A) アカウントロックアウト(一定回数失敗でロック、時間経過または管理者解除で復帰)

B) 漸進遅延(失敗のたびに応答遅延を増加)

C) ロックアウト + 漸進遅延の併用

X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

## Question 12: 管理者アカウントの MFA(SECURITY-12 対応)
Security Baseline は管理者アカウントの MFA サポートを必須としています。対応方針を確定してください。

A) TOTP(認証アプリ)方式の MFA を管理者に提供(MVP に含める)

B) TOTP 方式の MFA を提供するが、MVP 後のフェーズに先送り(SECURITY-12 の例外として文書化)

C) MFA は提供しない(約 10 名の内部ツールという前提でリスク受容を文書化。SECURITY-12 の例外)

X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

## Question 13: セキュリティイベントのアラート通知(SECURITY-14 対応)
Security Baseline は認証失敗の反復・認可違反などへのアラート設定を必須としています。約 10 名規模の内部ツールという前提で、MVP での扱いを確定してください。
(なお監査ログの保持は「パージ機能未実装 = 事実上無期限保持」のため SECURITY-14 の 90 日要件は自然に満たします)

A) アプリ内通知で対応 — 管理者ダッシュボードにセキュリティイベント(連続ログイン失敗等)の警告表示を設ける(MVP に含める)

B) メール通知で対応 — 閾値超過時に管理者へメール送信(MVP に含める)

C) MVP ではログ記録のみとし、アラート通知は後続フェーズに先送り(SECURITY-14 の例外として文書化)

X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

## Question 14: プロジェクトのディレクトリ構成
project-overview.md §4 の backend / frontend / devenv 構成は MUST ではないとのことなので、ビルド統合の観点を含めて構成を確定してください。

A) 原案どおり(backend / frontend / devenv を独立プロジェクトとして並置。ビルドはそれぞれ個別に実行し、WAR へのフロントエンド組み込みはスクリプト等で連携)

B) 原案 + ルート Gradle マルチプロジェクト統合(推奨)— トップレベルの 3 ディレクトリ構成は維持しつつ、Gradle wrapper と settings.gradle.kts をルートに置き、backend をサブプロジェクト化。フロントエンドのビルド(npm/Vite)を Gradle タスクとして統合し、`./gradlew build` の単一コマンドで frontend ビルド → WAR 組み込みまで完結させる

C) モノレポツール導入(Nx / Turborepo 等)— 大規模・多パッケージ向け。本プロジェクトの規模では過剰

D) バックエンド内包型(backend/src/main/frontend に React を配置)— ディレクトリが深くなり、フロントエンドの独立性が下がる

X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

## Question 15: 主キーなしテーブルの UPDATE の扱い
project-overview.md §5.2 は主キーなしテーブルについて「削除は常に不可」「作成は可能」と規定していますが、**更新(UPDATE)の扱いが未規定**です。主キーがない場合、更新対象行を一意に特定できず、同一値の複数行を意図せず更新するリスクがあります。

A) 主キーなしテーブルは読み取り + 作成のみ(UPDATE 不可)— 安全側に倒す(推奨)

B) 全カラム値の一致で行を特定して UPDATE を許可 — 同一値の行が複数ある場合は全行更新となる旨を UI で警告

C) 更新前に全カラム一致で件数を確認し、1 件に特定できる場合のみ UPDATE を許可

X) Other (please describe after [Answer]: tag below)

[Answer]:

---

## Question 16: 自由入力 SQL に対する権限モデルの適用範囲
クエリ実行機能(§5.7)では読み取り専用 SQL を自由に入力できますが、テーブル/カラム単位の主権限(§5.2)をこの SQL にどこまで適用するかが未規定です。スキーマ単位の許可リスト検証のみだと、READ 権限のないテーブル/カラムのデータを SELECT で参照できてしまいます。

A) SQL を解析し、参照テーブル/カラムに対して READ 権限を検証する — 権限モデルと完全に整合(推奨。クエリビルダーのリバースエンジニアリング(§5.5)にも SQL パーサーが必要なため実装資産を共用できる)

B) テーブルレベルまで検証(参照テーブルすべてに READ 以上を要求。カラム単位は適用外として文書化)

C) スキーマレベルの検証のみ(§5.7 の現行記述どおり。カラム/テーブル権限はクエリ実行機能には適用されないことをリスクとして文書化)

X) Other (please describe after [Answer]: tag below)

[Answer]:

---

## Question 17: MySQL 接続用 JDBC ドライバーの選定
MySQL Connector/J は GPLv2(Universal FOSS Exception 付き)であり、Apache 2.0 の本プロジェクトが WAR に同梱することは FOSS Exception の範囲内ですが、ライセンス管理の単純さに影響します。

A) MariaDB Connector/J(LGPL)で MySQL / MariaDB の両方に接続する — ドライバーを 1 本化でき、ライセンスも単純(推奨。MySQL プロトコル互換で実用上問題なし)

B) MySQL Connector/J を同梱する(FOSS Exception の範囲内と判断し、その旨を文書化)

C) MySQL Connector/J を同梱せず、利用者が実行環境に配置する方式(クラスパス追加)

X) Other (please describe after [Answer]: tag below)

[Answer]:

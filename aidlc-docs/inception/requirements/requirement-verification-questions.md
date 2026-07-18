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

[Answer]:

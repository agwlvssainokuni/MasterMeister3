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

[Answer]: A

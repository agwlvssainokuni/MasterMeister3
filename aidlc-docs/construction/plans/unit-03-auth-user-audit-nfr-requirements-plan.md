# ユニット③ auth-user-audit — NFR Requirements プラン

**作成日**: 2026-07-19
**Part A**: 質問回答(下記 Q1〜Q2)→ **Part B**: 成果物生成(nfr-requirements.md / tech-stack-decisions.md)

## ステップ

- [x] Step 1: Functional Design 成果物の分析(セキュリティ・性能・運用要件の抽出)
- [x] Step 2: 質問(Q1〜Q2)の回答確定
- [x] Step 3: `mm.app.*` 設定プロパティ体系の確定(既定値・検証ルール)
- [x] Step 4: 追加依存のバージョン確定と成果物生成

## 確認事項(回答をお願いします)

**Q1. JWT の実装ライブラリ**

- A) spring-security-oauth2-jose(Nimbus JOSE)— Spring Security BOM 管理(7.1.0)でバージョン管理不要、Spring 公式の JwtEncoder/JwtDecoder、リソースサーバ設定と自然に統合(推奨)
- B) jjwt(io.jsonwebtoken)— 軽量で API が簡潔だが BOM 外のため個別バージョン管理
- C) 自前実装(HMAC + Base64)— 依存ゼロだが検証実装のリスク
- D) その他

[Answer]: A(spring-security-oauth2-jose — Security BOM 管理)

**Q2. メールの形式・テンプレート方式**

- A) プレーンテキスト + MessageSource(i18n メッセージリソースで本文組立)— 追加依存なし、業務通知には十分、H-07 の i18n 基盤と共通(推奨)
- B) HTML メール + Thymeleaf(spring-boot-starter-thymeleaf)— リッチだがテンプレート・CSS 管理が増える
- C) その他

[Answer]: C(HTML メール + 自作 Mustache エンジン。エンジンのソースは backend ソースツリーに配置 — ユーザ指定 2026-07-19。自作パーサのため PBT-02 のブロッキング対象)

## 実装既定値(異論なければこのまま)

1. パスワードハッシュ: BCrypt(Spring Security 既定、strength 10)
2. JWT 署名: HS256、`mm.app.jwt.secret`(32 バイト以上を起動時検証。未設定なら起動失敗 — 本番事故防止)
3. Actuator は health のみ公開を維持(①の設定のまま)

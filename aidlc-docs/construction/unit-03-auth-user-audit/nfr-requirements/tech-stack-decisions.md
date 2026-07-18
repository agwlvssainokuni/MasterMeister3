# 技術スタック決定 — unit-03-auth-user-audit(追加分)

**作成日**: 2026-07-19
**注**: 基盤スタックは①、UI 系は②で確定済み。本書は③で追加する依存のみ。

## backend 追加依存(すべて Spring Boot 4.1 BOM / Spring Security BOM 7.1.0 管理 — 個別バージョン指定不要)

| 依存 | 用途 |
|---|---|
| spring-boot-starter-security | 認証・認可・セキュリティヘッダー |
| org.springframework.security:spring-security-oauth2-jose | JWT(Nimbus JOSE)エンコード/デコード(Q1=A) |
| spring-boot-starter-mail | SMTP 送信(JavaMailSender) |
| spring-boot-starter-validation | AppProperties・リクエスト DTO の Bean Validation |
| spring-security-test(test) | 認証付き MockMvc テスト |

## 自作コンポーネント(依存追加なし — Q2=C)

| コンポーネント | 配置 | 備考 |
|---|---|---|
| Mustache エンジン(サブセット) | `cherry.mastermeister.common.template` | HTML メールのテンプレート描画。対応構文は nfr-requirements.md §3。**PBT-02 ブロッキング対象**(jqwik) |

## frontend

- 追加依存なし(②の react-router-dom / i18next / デザインシステムで完結)

## 判断メモ

- Thymeleaf 等の既成エンジンを使わない選択(ユーザ指定)により、依存は増えない一方で品質担保は自前になる。PBT(jqwik)+ テンプレート読込時の構文検証(fail-fast)で担保する
- HTML メールはインライン CSS のシンプルなレイアウトとする(メールクライアントの CSS 制約のため②のデザイントークン CSS は再利用せず、配色値のみ揃える)

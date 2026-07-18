# 技術スタック決定 — unit-02-design-system(①からの追加分)

**作成日**: 2026-07-19
**注**: 基盤スタックはユニット①で確定済み(tech-stack-decisions.md 参照)。本書は②で追加する依存のみ。バージョンは npm レジストリで実在確認済み(2026-07-19)。

| 依存 | バージョン | 用途 | 備考 |
|---|---|---|---|
| react-router-dom | 7.18.1 | ルーティング(モックカタログ + ③以降の本実装) | FD レビューでユーザ確認済み |
| i18next | 26.3.6 | i18n コア | |
| react-i18next | 17.0.10 | React バインディング(Q6=A) | peer: i18next >=26.2, TS ^5\|^6\|^7 — TS 6.0 と整合 |
| @fontsource/noto-sans-jp | 5.2.9 | Noto Sans JP woff2 同梱(Q4=B) | サブセット分割済み woff2 + unicode-range CSS + OFL-1.1 ライセンス文を npm で版管理。ウェイト 400/500/700 のみ import |
| @fontsource/noto-sans-mono | 5.2.10 | Noto Sans Mono 同梱(等幅もそろえる — ユーザ指示) | ウェイト 400/700 のみ import |

## 判断メモ

- **Fontsource 採用**: FD(mock-structure.md)では「woff2 手動配置」を想定していたが、Fontsource パッケージなら (1) unicode-range サブセット分割済みで NFR-U2-03 を満たし、(2) OFL ライセンス文が同梱され、(3) バージョン管理が npm に一本化される。手動配置より優れるため実装手段として採用(自己ホストであることは変わらず、CSP・オフライン要件に影響なし)
- **i18n の言語検出**: i18next-browser-languagedetector は導入せず、初期言語決定(localStorage → navigator.language)は自前の数行で実装(screen-specs.md §5 の仕様が単純なため)

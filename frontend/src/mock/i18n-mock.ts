/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// モック専用辞書。本番辞書(common)に混ぜず、mock ロード時に動的登録する
// (NFR-U2-02: dist への文言混入を防ぐ)。
import i18n from "../design-system/i18n";

const ja = {
  nav: {
    tokens: "デザイントークン",
    components: "コンポーネント",
    screens: "代表画面",
    login: "ログイン",
    userList: "ユーザ管理一覧",
    recordEdit: "レコード一覧・編集",
    queryRun: "クエリ実行",
  },
  login: {
    title: "MasterMeister にログイン",
    email: "メールアドレス",
    password: "パスワード",
    register: "アカウント登録を申請する",
    failed: "メールアドレスまたはパスワードが正しくありません。",
    emailFormat: "メールアドレスの形式が正しくありません。",
  },
  users: {
    title: "ユーザ管理",
    searchPlaceholder: "メールアドレス・表示名で検索",
    statusAll: "すべての状態",
    statusPending: "承認待ち",
    statusActive: "有効",
    statusDisabled: "無効",
    email: "メールアドレス",
    displayName: "表示名",
    status: "状態",
    registeredAt: "登録日時",
    actions: "操作",
    approve: "承認",
    disable: "無効化",
    approveTitle: "ユーザを承認しますか?",
    approveMessage: "を承認します。承認するとログインが可能になります。",
    approved: "を承認しました",
    disableTitle: "ユーザを無効化しますか?",
    disableMessage: "を無効化します。無効化するとログインできなくなります。",
    disabled: "を無効化しました",
  },
  records: {
    title: "レコード一覧・編集",
    connection: "接続",
    schema: "スキーマ",
    table: "テーブル",
    permission: "実効権限",
    addRow: "行を追加",
    changes: "追加 {{added}} / 更新 {{updated}} / 削除 {{removed}}",
    applyTitle: "変更を一括反映しますか?",
    applyMessage: "すべての変更をまとめて反映します。1 件でも失敗した場合は全体が取り消されます(オールオアナッシング)。",
    applied: "変更を反映しました",
    cellError: "数値のみ入力できます",
  },
  query: {
    title: "クエリ実行",
    tabBuilder: "ビルダー",
    tabSql: "SQL",
    targetTable: "対象テーブル",
    columns: "取得カラム",
    where: "WHERE 条件",
    orderBy: "ORDER BY",
    addCondition: "条件を追加",
    run: "実行",
    save: "クエリを保存",
    paramPlaceholder: "値",
    resultCount: "{{count}} 件",
    elapsed: "{{ms}} ms",
  },
  catalog: {
    title: "MasterMeister モックカタログ",
    subtitle: "デザインシステム確認用(dev 専用・本番ビルドには含まれません)",
    palette: "カラーパレット(プリミティブ層)",
    semantic: "セマンティックトークン",
    typography: "タイポグラフィ",
    spacing: "スペーシング",
    contrast: "コントラスト確認",
    contrastNote: "本文・操作要素は WCAG AA(4.5:1)を両テーマで満たすこと",
  },
};

const en = {
  nav: {
    tokens: "Design Tokens",
    components: "Components",
    screens: "Screens",
    login: "Login",
    userList: "User Management",
    recordEdit: "Records",
    queryRun: "Query",
  },
  login: {
    title: "Log in to MasterMeister",
    email: "Email address",
    password: "Password",
    register: "Request an account",
    failed: "Incorrect email address or password.",
    emailFormat: "Invalid email address format.",
  },
  users: {
    title: "User Management",
    searchPlaceholder: "Search by email or display name",
    statusAll: "All statuses",
    statusPending: "Pending",
    statusActive: "Active",
    statusDisabled: "Disabled",
    email: "Email",
    displayName: "Display name",
    status: "Status",
    registeredAt: "Registered at",
    actions: "Actions",
    approve: "Approve",
    disable: "Disable",
    approveTitle: "Approve this user?",
    approveMessage: " will be approved and able to log in.",
    approved: " has been approved",
    disableTitle: "Disable this user?",
    disableMessage: " will be disabled and unable to log in.",
    disabled: " has been disabled",
  },
  records: {
    title: "Records",
    connection: "Connection",
    schema: "Schema",
    table: "Table",
    permission: "Effective permission",
    addRow: "Add row",
    changes: "Added {{added}} / Updated {{updated}} / Removed {{removed}}",
    applyTitle: "Apply all changes?",
    applyMessage: "All changes are applied at once. If any change fails, everything is rolled back (all-or-nothing).",
    applied: "Changes applied",
    cellError: "Only numeric values are allowed",
  },
  query: {
    title: "Query",
    tabBuilder: "Builder",
    tabSql: "SQL",
    targetTable: "Target table",
    columns: "Columns",
    where: "WHERE conditions",
    orderBy: "ORDER BY",
    addCondition: "Add condition",
    run: "Run",
    save: "Save query",
    paramPlaceholder: "value",
    resultCount: "{{count}} rows",
    elapsed: "{{ms}} ms",
  },
  catalog: {
    title: "MasterMeister Mock Catalog",
    subtitle: "For design review (dev only, excluded from production build)",
    palette: "Color palette (primitive layer)",
    semantic: "Semantic tokens",
    typography: "Typography",
    spacing: "Spacing",
    contrast: "Contrast check",
    contrastNote: "Text and controls must meet WCAG AA (4.5:1) in both themes",
  },
};

i18n.addResourceBundle("ja", "mock", ja);
i18n.addResourceBundle("en", "mock", en);

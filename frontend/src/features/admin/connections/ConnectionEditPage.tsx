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

import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../../../app/apiClient";
import {
  Alert,
  Button,
  ConfirmDialog,
  FormField,
  PasswordInput,
  Select,
  TextInput,
  useToast,
} from "../../../design-system/components";
import {
  createConnection,
  fetchSchema,
  getConnection,
  importSchema,
  testConnection,
  updateConnection,
} from "./api";
import type { ConnectionInput, DbType, SchemaTree } from "./api";
import styles from "./connections.module.css";

interface FormState {
  name: string;
  dbType: DbType;
  host: string;
  port: string;
  databaseName: string;
  username: string;
  password: string;
  options: string;
  poolMaxSize: string;
  poolTimeoutMs: string;
}

const emptyForm: FormState = {
  name: "",
  dbType: "MYSQL",
  host: "",
  port: "",
  databaseName: "",
  username: "",
  password: "",
  options: "",
  poolMaxSize: "5",
  poolTimeoutMs: "5000",
};

/**
 * 接続の登録・編集(US-010)。dbType は編集時変更不可。
 * 接続テストはフォームの現在値で実行(保存不要 — レビュー確認 2)。
 * 編集時パスワード空欄 = 変更しない(テスト時は保存済み資格情報で補完)。
 * スキーマ取込(US-012)は保存済み接続に対して実行し、取込済みツリーの概要を表示する。
 */
export function ConnectionEditPage() {
  const { t } = useTranslation("admin");
  const { showToast } = useToast();
  const navigate = useNavigate();
  const params = useParams();
  const id = params.id === undefined ? null : Number(params.id);
  const isNew = id === null;

  const [form, setForm] = useState<FormState>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; reason: string | null } | null>(
    null,
  );
  const [errorCode, setErrorCode] = useState<string | null>(null);
  const [tree, setTree] = useState<SchemaTree | null>(null);
  const [confirmImport, setConfirmImport] = useState(false);
  const [importing, setImporting] = useState(false);

  const set = (key: Exclude<keyof FormState, "dbType">) => (value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const loadExisting = useCallback(async () => {
    if (id === null) {
      return;
    }
    try {
      const [connection, schemaTree] = await Promise.all([getConnection(id), fetchSchema(id)]);
      setForm({
        name: connection.name,
        dbType: connection.dbType,
        host: connection.host,
        port: connection.port === null ? "" : String(connection.port),
        databaseName: connection.databaseName,
        username: connection.username,
        password: "",
        options: connection.options ?? "",
        poolMaxSize: String(connection.poolMaxSize),
        poolTimeoutMs: String(connection.poolTimeoutMs),
      });
      setTree(schemaTree);
    } catch {
      showToast("danger", t("connections.toast.failed"));
    }
  }, [id, showToast, t]);

  useEffect(() => {
    void Promise.resolve().then(() => loadExisting());
  }, [loadExisting]);

  const toInput = (): ConnectionInput => ({
    name: form.name.trim(),
    dbType: form.dbType,
    host: form.host.trim(),
    port: form.port.trim() === "" ? null : Number(form.port),
    databaseName: form.databaseName.trim(),
    username: form.username.trim(),
    password: form.password === "" ? null : form.password,
    options: form.options.trim() === "" ? null : form.options.trim(),
    poolMaxSize: Number(form.poolMaxSize),
    poolTimeoutMs: Number(form.poolTimeoutMs),
  });

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setErrorCode(null);
    setSaving(true);
    try {
      if (isNew) {
        const created = await createConnection({ ...toInput(), password: form.password });
        showToast("success", t("connections.toast.created"));
        navigate(`/admin/connections/${created.id}`, { replace: true });
      } else {
        await updateConnection(id, toInput());
        showToast("success", t("connections.toast.updated"));
        await loadExisting();
      }
    } catch (error) {
      setErrorCode(error instanceof ApiError ? error.code : "UNKNOWN_ERROR");
    } finally {
      setSaving(false);
    }
  };

  const onTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const input = toInput();
      const result = await testConnection({
        id: id ?? undefined,
        dbType: input.dbType,
        host: input.host,
        port: input.port,
        databaseName: input.databaseName,
        username: input.username,
        password: input.password,
        options: input.options,
      });
      setTestResult(result);
    } catch (error) {
      setTestResult({
        success: false,
        reason: error instanceof ApiError ? error.code : "UNKNOWN_ERROR",
      });
    } finally {
      setTesting(false);
    }
  };

  const runImport = async () => {
    if (id === null) {
      return;
    }
    setImporting(true);
    try {
      const result = await importSchema(id);
      showToast(
        "success",
        t("connections.toast.imported", {
          schemas: result.schemas,
          tables: result.tables,
          columns: result.columns,
        }),
      );
      setTree(await fetchSchema(id));
    } catch (error) {
      const code = error instanceof ApiError ? error.code : "UNKNOWN_ERROR";
      showToast("danger", t(`connections.importError.${code}`, t("connections.toast.failed")));
    } finally {
      setImporting(false);
      setConfirmImport(false);
    }
  };

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>
        {isNew ? t("connections.createTitle") : t("connections.editTitle")}
      </h1>
      {errorCode ? (
        <Alert tone="danger">
          {t(`connections.error.${errorCode}`, t("connections.toast.failed"))}
        </Alert>
      ) : null}
      <form className={styles.form} onSubmit={onSubmit}>
        <FormField label={t("connections.field.name")} required>
          <TextInput
            value={form.name}
            onChange={(event) => set("name")(event.target.value)}
            required
          />
        </FormField>
        <FormField
          label={t("connections.field.dbType")}
          required
          help={isNew ? undefined : t("connections.dbTypeImmutable")}
        >
          <Select
            value={form.dbType}
            disabled={!isNew}
            onChange={(event) =>
              setForm((prev) => ({ ...prev, dbType: event.target.value as DbType }))
            }
          >
            <option value="MYSQL">MySQL</option>
            <option value="MARIADB">MariaDB</option>
            <option value="POSTGRESQL">PostgreSQL</option>
            <option value="H2">H2</option>
          </Select>
        </FormField>
        <FormField label={t("connections.field.host")} required>
          <TextInput
            value={form.host}
            onChange={(event) => set("host")(event.target.value)}
            required
          />
        </FormField>
        <FormField label={t("connections.field.port")}>
          <TextInput
            type="number"
            value={form.port}
            onChange={(event) => set("port")(event.target.value)}
          />
        </FormField>
        <FormField label={t("connections.field.databaseName")} required>
          <TextInput
            value={form.databaseName}
            onChange={(event) => set("databaseName")(event.target.value)}
            required
          />
        </FormField>
        <FormField label={t("connections.field.username")} required>
          <TextInput
            value={form.username}
            onChange={(event) => set("username")(event.target.value)}
            required
          />
        </FormField>
        <FormField
          label={t("connections.field.password")}
          required={isNew}
          help={isNew ? undefined : t("connections.passwordKeep")}
        >
          <PasswordInput
            value={form.password}
            onChange={(event) => set("password")(event.target.value)}
            required={isNew}
          />
        </FormField>
        <FormField label={t("connections.field.options")}>
          <TextInput
            value={form.options}
            onChange={(event) => set("options")(event.target.value)}
          />
        </FormField>
        <FormField label={t("connections.field.poolMaxSize")}>
          <TextInput
            type="number"
            value={form.poolMaxSize}
            onChange={(event) => set("poolMaxSize")(event.target.value)}
          />
        </FormField>
        <FormField label={t("connections.field.poolTimeoutMs")}>
          <TextInput
            type="number"
            value={form.poolTimeoutMs}
            onChange={(event) => set("poolTimeoutMs")(event.target.value)}
          />
        </FormField>
        <div className={styles.formActions}>
          <Button type="submit" variant="primary" loading={saving}>
            {isNew ? t("connections.action.create") : t("connections.action.save")}
          </Button>
          <Button type="button" onClick={() => void onTest()} loading={testing}>
            {t("connections.action.test")}
          </Button>
          <Button type="button" variant="ghost" onClick={() => navigate("/admin/connections")}>
            {t("connections.action.back")}
          </Button>
        </div>
      </form>
      {testResult ? (
        testResult.success ? (
          <Alert tone="success">{t("connections.test.success")}</Alert>
        ) : (
          <Alert tone="danger">
            {t(
              `connections.test.${testResult.reason ?? "CONNECT_FAILED"}`,
              t("connections.test.CONNECT_FAILED"),
            )}
          </Alert>
        )
      ) : null}
      {!isNew ? (
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>{t("connections.schema.title")}</h2>
          <div className={styles.formActions}>
            <Button onClick={() => setConfirmImport(true)} loading={importing}>
              {t("connections.action.import")}
            </Button>
            <span className={styles.muted}>
              {tree?.importedAt
                ? t("connections.schema.importedAt", {
                    at: new Date(tree.importedAt).toLocaleString(),
                  })
                : t("connections.notImported")}
            </span>
          </div>
          {tree && tree.schemas.length > 0 ? (
            <ul className={styles.tree}>
              {tree.schemas.map((schema) => (
                <li key={schema.name}>
                  {schema.name}
                  <ul>
                    {schema.tables.map((table) => (
                      <li key={table.name}>
                        {table.name}
                        {table.tableType === "VIEW" ? ` (${t("connections.schema.view")})` : ""}
                        {" — "}
                        {t("connections.schema.columns", { count: table.columns.length })}
                        {table.remarks ? ` / ${table.remarks}` : ""}
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          ) : null}
        </section>
      ) : null}
      <ConfirmDialog
        open={confirmImport}
        title={t("connections.confirm.importTitle")}
        message={t("connections.confirm.importMessage")}
        processing={importing}
        onConfirm={() => {
          void runImport();
        }}
        onCancel={() => setConfirmImport(false)}
      />
    </div>
  );
}

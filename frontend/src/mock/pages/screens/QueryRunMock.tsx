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

import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  AppShell,
  Button,
  Checkbox,
  CodeBlock,
  EmptyState,
  Select,
  Table,
  Tabs,
  TextInput,
} from "../../../design-system/components";
import type { TableColumn } from "../../../design-system/components";
import { queryResultColumns, queryResultRows, sampleSql } from "../../data/sample";
import styles from "./screens.module.css";

type ResultRow = readonly string[];

export function QueryRunMock() {
  const { t } = useTranslation("mock");
  const { t: tc } = useTranslation();
  const [tab, setTab] = useState("builder");
  const [running, setRunning] = useState(false);
  const [hasResult, setHasResult] = useState(false);

  const run = () => {
    setRunning(true);
    window.setTimeout(() => {
      setRunning(false);
      setHasResult(true);
    }, 800);
  };

  const resultTableColumns: readonly TableColumn<ResultRow>[] = queryResultColumns.map(
    (name, index) => ({
      key: name,
      header: name,
      align: name === "amount" ? "right" : "left",
      render: (row) => row[index],
    }),
  );

  const builder = (
    <div className={styles.queryBuilder}>
      <div className={styles.conditionRow}>
        <span>{t("query.targetTable")}:</span>
        <Select defaultValue="orders" style={{ width: "auto" }}>
          <option value="orders">sales.orders</option>
          <option value="customers">sales.customers</option>
        </Select>
      </div>
      <div>
        <p style={{ margin: "0 0 4px" }}>{t("query.columns")}:</p>
        <div className={styles.columnChecks}>
          <Checkbox label="order_id" defaultChecked />
          <Checkbox label="customer_name" defaultChecked />
          <Checkbox label="amount" defaultChecked />
          <Checkbox label="ordered_at" defaultChecked />
          <Checkbox label="note" />
        </div>
      </div>
      <div>
        <p style={{ margin: "0 0 4px" }}>{t("query.where")}:</p>
        <div className={styles.conditionRow}>
          <Select defaultValue="ordered_at" style={{ width: "auto" }}>
            <option value="ordered_at">ordered_at</option>
            <option value="amount">amount</option>
          </Select>
          <Select defaultValue=">=" style={{ width: "auto" }}>
            <option value=">=">&gt;=</option>
            <option value="=">=</option>
            <option value="<">&lt;</option>
          </Select>
          <TextInput placeholder=":from" style={{ width: 160 }} defaultValue="2026-07-14" />
          <Button size="sm" variant="ghost">
            + {t("query.addCondition")}
          </Button>
        </div>
      </div>
      <div className={styles.conditionRow}>
        <span>{t("query.orderBy")}:</span>
        <Select defaultValue="ordered_at" style={{ width: "auto" }}>
          <option value="ordered_at">ordered_at</option>
          <option value="order_id">order_id</option>
        </Select>
        <Select defaultValue="asc" style={{ width: "auto" }}>
          <option value="asc">ASC</option>
          <option value="desc">DESC</option>
        </Select>
      </div>
    </div>
  );

  return (
    <AppShell
      navItems={[
        { key: "users", label: t("users.title") },
        { key: "records", label: t("nav.recordEdit") },
        { key: "query", label: t("nav.queryRun"), active: true },
      ]}
    >
      <h1 className={styles.screenTitle}>{t("query.title")}</h1>
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          { key: "builder", label: t("query.tabBuilder"), content: builder },
          { key: "sql", label: t("query.tabSql"), content: <CodeBlock code={sampleSql} /> },
        ]}
      />
      <div className={styles.toolbar} style={{ marginTop: 16 }}>
        <Button variant="primary" onClick={run} loading={running}>
          {tc("action.execute")}
        </Button>
        <Button variant="secondary">{t("query.save")}</Button>
        <span className={styles.toolbarSpacer} />
      </div>
      {hasResult || running ? (
        <>
          {hasResult && !running ? (
            <div className={styles.resultMeta}>
              <span>{t("query.resultCount", { count: queryResultRows.length })}</span>
              <span>{t("query.elapsed", { ms: 124 })}</span>
            </div>
          ) : null}
          <Table
            columns={resultTableColumns}
            rows={running ? [] : queryResultRows}
            rowKey={(row) => row[0]}
            loading={running}
            emptyState={<EmptyState />}
          />
        </>
      ) : null}
    </AppShell>
  );
}

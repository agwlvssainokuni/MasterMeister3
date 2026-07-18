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

import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  AppShell,
  Badge,
  Button,
  ConfirmDialog,
  Table,
  Tooltip,
  useToast,
} from "../../../design-system/components";
import type { CellState, TableColumn } from "../../../design-system/components";
import { customerColumns, customerRows } from "../../data/sample";
import type { CustomerRow } from "../../data/sample";
import styles from "./screens.module.css";

interface EditState {
  edits: Record<string, Record<string, string>>;
  removed: readonly string[];
  added: readonly CustomerRow[];
}

const INITIAL: EditState = {
  edits: { "1002": { credit_limit: "abc" }, "1003": { status: "ACTIVE" } },
  removed: ["1004"],
  added: [
    {
      id: "(新規)",
      name: "株式会社ほしぞら製菓",
      kana: "ホシゾラセイカ",
      credit_limit: "2000000",
      status: "ACTIVE",
      updated_at: "-",
    },
  ],
};

function isNumeric(value: string): boolean {
  return /^\d+$/.test(value);
}

export function RecordEditMock() {
  const { t } = useTranslation("mock");
  const { t: tc } = useTranslation();
  const { showToast } = useToast();
  const [state, setState] = useState<EditState>(INITIAL);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const rows = useMemo(() => [...customerRows, ...state.added], [state.added]);

  const cellValue = (row: CustomerRow, column: string): string =>
    state.edits[row.id]?.[column] ?? row[column] ?? "";

  const onCellChange = (rowId: string, column: string, value: string) => {
    setState((current) => ({
      ...current,
      edits: {
        ...current.edits,
        [rowId]: { ...current.edits[rowId], [column]: value },
      },
    }));
  };

  const cellStates = useMemo(() => {
    const result: Record<string, Record<string, CellState>> = {};
    for (const [rowId, columns] of Object.entries(state.edits)) {
      result[rowId] = {};
      for (const [column, value] of Object.entries(columns)) {
        result[rowId][column] =
          column === "credit_limit" && !isNumeric(value) ? "error" : "edited";
      }
    }
    return result;
  }, [state.edits]);

  const rowStates = useMemo(() => {
    const result: Record<string, "added" | "removed"> = {};
    for (const removedId of state.removed) {
      result[removedId] = "removed";
    }
    for (const added of state.added) {
      result[added.id] = "added";
    }
    return result;
  }, [state.removed, state.added]);

  const changeCount = {
    added: state.added.length,
    updated: Object.keys(state.edits).length,
    removed: state.removed.length,
  };
  const hasChanges = changeCount.added + changeCount.updated + changeCount.removed > 0;

  const columns: readonly TableColumn<CustomerRow>[] = customerColumns.map((meta) => ({
    key: meta.name,
    header: (
      <span>
        {meta.name}
        {meta.primaryKey ? " 🔑" : ""}
        <span style={{ marginLeft: 4, fontWeight: 400, textTransform: "none" }}>
          <Badge tone={meta.permission === "UPDATE" ? "primary" : "neutral"}>
            {meta.permission}
          </Badge>
        </span>
      </span>
    ),
    align: meta.type.startsWith("DECIMAL") || meta.type === "BIGINT" ? "right" : "left",
    render: (row) => {
      const editable = meta.permission === "UPDATE" && !state.removed.includes(row.id);
      if (!editable) {
        return cellValue(row, meta.name);
      }
      const value = cellValue(row, meta.name);
      const hasError = meta.name === "credit_limit" && !isNumeric(value);
      const input = (
        <input
          className={styles.cellInput}
          value={value}
          aria-label={`${row.id}-${meta.name}`}
          onChange={(event) => onCellChange(row.id, meta.name, event.target.value)}
        />
      );
      return hasError ? <Tooltip content={t("records.cellError")}>{input}</Tooltip> : input;
    },
  }));

  return (
    <AppShell
      navItems={[
        { key: "users", label: t("users.title") },
        { key: "records", label: t("nav.recordEdit"), active: true },
        { key: "query", label: t("nav.queryRun") },
      ]}
    >
      <h1 className={styles.screenTitle}>{t("records.title")}</h1>
      <div className={styles.contextBar}>
        <span className={styles.contextItem}>
          {t("records.connection")}: <span className={styles.contextValue}>本番参照系(MySQL)</span>
        </span>
        <span className={styles.contextItem}>
          {t("records.schema")}: <span className={styles.contextValue}>sales</span>
        </span>
        <span className={styles.contextItem}>
          {t("records.table")}: <span className={styles.contextValue}>customers(顧客マスタ)</span>
        </span>
        <span className={styles.contextItem}>
          {t("records.permission")}: <Badge tone="primary">UPDATE</Badge>
        </span>
      </div>
      <div className={styles.toolbar}>
        <Button
          size="sm"
          onClick={() =>
            setState((current) => ({
              ...current,
              added: [
                ...current.added,
                {
                  id: "(新規)",
                  name: "",
                  kana: "",
                  credit_limit: "0",
                  status: "ACTIVE",
                  updated_at: "-",
                },
              ],
            }))
          }
        >
          + {t("records.addRow")}
        </Button>
      </div>
      <Table
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        cellStates={cellStates}
        rowStates={rowStates}
      />
      {hasChanges ? (
        <div className={styles.applyBar}>
          <span className={styles.applyCount}>
            {t("records.changes", changeCount)}
          </span>
          <Button variant="ghost" onClick={() => setState({ edits: {}, removed: [], added: [] })}>
            {tc("action.discard")}
          </Button>
          <Button variant="primary" onClick={() => setConfirmOpen(true)}>
            {tc("action.apply")}
          </Button>
        </div>
      ) : null}
      <ConfirmDialog
        open={confirmOpen}
        title={t("records.applyTitle")}
        message={t("records.applyMessage")}
        onConfirm={() => {
          setConfirmOpen(false);
          setState({ edits: {}, removed: [], added: [] });
          showToast("success", t("records.applied"));
        }}
        onCancel={() => setConfirmOpen(false)}
      />
    </AppShell>
  );
}

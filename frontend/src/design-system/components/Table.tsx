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

import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Checkbox } from "./Choice";
import { EmptyState } from "./Display";
import { Spinner } from "./Spinner";
import styles from "./Table.module.css";

export type SortDirection = "asc" | "desc";

export interface TableColumn<Row> {
  key: string;
  header: ReactNode;
  render: (row: Row) => ReactNode;
  sortable?: boolean;
  width?: string;
  align?: "left" | "right";
}

export type CellState = "edited" | "error";

export interface TableProps<Row> {
  columns: readonly TableColumn<Row>[];
  rows: readonly Row[];
  rowKey: (row: Row) => string;
  loading?: boolean;
  emptyState?: ReactNode;
  sortKey?: string;
  sortDirection?: SortDirection;
  onSortChange?: (key: string, direction: SortDirection) => void;
  selectable?: boolean;
  selectedKeys?: ReadonlySet<string>;
  onSelectionChange?: (keys: ReadonlySet<string>) => void;
  /** セル状態(編集済み/エラー)の指定: rowKey → columnKey → 状態 */
  cellStates?: Readonly<Record<string, Readonly<Record<string, CellState>>>>;
  /** 行状態(新規/削除予定)の指定: rowKey → 状態 */
  rowStates?: Readonly<Record<string, "added" | "removed">>;
}

export function Table<Row>({
  columns,
  rows,
  rowKey,
  loading = false,
  emptyState,
  sortKey,
  sortDirection,
  onSortChange,
  selectable = false,
  selectedKeys,
  onSelectionChange,
  cellStates,
  rowStates,
}: TableProps<Row>) {
  const { t } = useTranslation();
  const allKeys = rows.map(rowKey);
  const selected = selectedKeys ?? new Set<string>();
  const allSelected = allKeys.length > 0 && allKeys.every((key) => selected.has(key));

  const toggleAll = () => {
    onSelectionChange?.(allSelected ? new Set() : new Set(allKeys));
  };

  const toggleRow = (key: string) => {
    const next = new Set(selected);
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    onSelectionChange?.(next);
  };

  const onSortClick = (column: TableColumn<Row>) => {
    if (!column.sortable || !onSortChange) {
      return;
    }
    const nextDirection: SortDirection =
      sortKey === column.key && sortDirection === "asc" ? "desc" : "asc";
    onSortChange(column.key, nextDirection);
  };

  return (
    <div className={styles.container}>
      <table className={styles.table}>
        <thead>
          <tr>
            {selectable ? (
              <th className={styles.checkboxCell}>
                <Checkbox
                  checked={allSelected}
                  onChange={toggleAll}
                  aria-label={t("table.selectAll")}
                />
              </th>
            ) : null}
            {columns.map((column) => {
              const isSorted = sortKey === column.key;
              return (
                <th
                  key={column.key}
                  style={{ width: column.width }}
                  className={column.align === "right" ? styles.alignRight : undefined}
                  aria-sort={
                    isSorted ? (sortDirection === "asc" ? "ascending" : "descending") : undefined
                  }
                >
                  {column.sortable ? (
                    <button
                      type="button"
                      className={styles.sortButton}
                      onClick={() => onSortClick(column)}
                      aria-label={
                        isSorted && sortDirection === "asc"
                          ? t("table.sortDesc")
                          : t("table.sortAsc")
                      }
                    >
                      {column.header}
                      <span className={styles.sortMark} aria-hidden="true">
                        {isSorted ? (sortDirection === "asc" ? "▲" : "▼") : "△"}
                      </span>
                    </button>
                  ) : (
                    column.header
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const key = rowKey(row);
            const rowState = rowStates?.[key];
            const rowClass = [
              rowState === "added" ? styles.rowAdded : null,
              rowState === "removed" ? styles.rowRemoved : null,
              selected.has(key) ? styles.rowSelected : null,
            ]
              .filter(Boolean)
              .join(" ");
            return (
              <tr key={key} className={rowClass || undefined}>
                {selectable ? (
                  <td className={styles.checkboxCell}>
                    <Checkbox
                      checked={selected.has(key)}
                      onChange={() => toggleRow(key)}
                      aria-label={t("table.selectRow")}
                    />
                  </td>
                ) : null}
                {columns.map((column) => {
                  const state = cellStates?.[key]?.[column.key];
                  const cellClass = [
                    state === "edited" ? styles.cellEdited : null,
                    state === "error" ? styles.cellError : null,
                    column.align === "right" ? styles.alignRight : null,
                  ]
                    .filter(Boolean)
                    .join(" ");
                  return (
                    <td key={column.key} className={cellClass || undefined}>
                      {column.render(row)}
                    </td>
                  );
                })}
              </tr>
            );
          })}
        </tbody>
      </table>
      {loading ? (
        <div className={styles.loadingOverlay}>
          <Spinner size="lg" />
        </div>
      ) : null}
      {!loading && rows.length === 0 ? (emptyState ?? <EmptyState />) : null}
    </div>
  );
}

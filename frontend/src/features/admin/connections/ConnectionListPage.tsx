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
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import {
  Button,
  ConfirmDialog,
  EmptyState,
  Table,
  useToast,
} from "../../../design-system/components";
import type { TableColumn } from "../../../design-system/components";
import { deleteConnection, fetchImportStatus, listConnections } from "./api";
import type { ConnectionSummary } from "./api";
import styles from "./connections.module.css";

/**
 * 接続一覧(US-010/011)。取込状況を併記し、編集・権限設定・削除への導線を提供する。
 * 削除はメタデータ・権限設定も消えるためカスケードの警告を出す。
 */
export function ConnectionListPage() {
  const { t } = useTranslation("admin");
  const { t: tc } = useTranslation();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [items, setItems] = useState<ConnectionSummary[]>([]);
  const [importedAt, setImportedAt] = useState<Map<number, string>>(new Map());
  const [loading, setLoading] = useState(true);
  const [confirmDelete, setConfirmDelete] = useState<ConnectionSummary | null>(null);
  const [processing, setProcessing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [connections, status] = await Promise.all([listConnections(), fetchImportStatus()]);
      setItems(connections);
      setImportedAt(new Map(status.map((row) => [row.connectionId, row.importedAt])));
    } catch {
      showToast("danger", t("connections.toast.failed"));
    } finally {
      setLoading(false);
    }
  }, [showToast, t]);

  useEffect(() => {
    void Promise.resolve().then(() => load());
  }, [load]);

  const runDelete = async () => {
    if (!confirmDelete) {
      return;
    }
    setProcessing(true);
    try {
      await deleteConnection(confirmDelete.id);
      showToast("success", t("connections.toast.deleted"));
      await load();
    } catch {
      showToast("danger", t("connections.toast.failed"));
    } finally {
      setProcessing(false);
      setConfirmDelete(null);
    }
  };

  const columns: readonly TableColumn<ConnectionSummary>[] = [
    {
      key: "name",
      header: t("connections.column.name"),
      render: (connection) => connection.name,
    },
    {
      key: "dbType",
      header: t("connections.column.dbType"),
      render: (connection) => connection.dbType,
    },
    {
      key: "host",
      header: t("connections.column.host"),
      render: (connection) =>
        connection.port === null ? connection.host : `${connection.host}:${connection.port}`,
    },
    {
      key: "databaseName",
      header: t("connections.column.databaseName"),
      render: (connection) => connection.databaseName,
    },
    {
      key: "importedAt",
      header: t("connections.column.importedAt"),
      render: (connection) => {
        const value = importedAt.get(connection.id);
        return value ? new Date(value).toLocaleString() : t("connections.notImported");
      },
    },
    {
      key: "actions",
      header: t("connections.column.actions"),
      render: (connection) => (
        <span className={styles.actionsCell}>
          <Button size="sm" onClick={() => navigate(`/admin/connections/${connection.id}`)}>
            {t("connections.action.edit")}
          </Button>
          <Button
            size="sm"
            onClick={() => navigate(`/admin/connections/${connection.id}/permissions`)}
          >
            {t("connections.action.permissions")}
          </Button>
          <Button size="sm" variant="danger" onClick={() => setConfirmDelete(connection)}>
            {t("connections.action.delete")}
          </Button>
        </span>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>{t("connections.title")}</h1>
      <div className={styles.toolbar}>
        <Button variant="primary" onClick={() => navigate("/admin/connections/new")}>
          {t("connections.action.create")}
        </Button>
      </div>
      <Table
        columns={columns}
        rows={items}
        rowKey={(connection) => String(connection.id)}
        loading={loading}
        emptyState={<EmptyState message={tc("state.empty")} />}
      />
      <ConfirmDialog
        open={confirmDelete !== null}
        title={t("connections.confirm.deleteTitle")}
        message={
          confirmDelete
            ? t("connections.confirm.deleteMessage", { name: confirmDelete.name })
            : ""
        }
        tone="danger"
        processing={processing}
        onConfirm={() => {
          void runDelete();
        }}
        onCancel={() => setConfirmDelete(null)}
      />
    </div>
  );
}

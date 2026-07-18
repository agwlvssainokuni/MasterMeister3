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
  EmptyState,
  Pagination,
  SearchInput,
  Select,
  Table,
  useToast,
} from "../../../design-system/components";
import type { TableColumn } from "../../../design-system/components";
import { sampleUsers } from "../../data/sample";
import type { SampleUser, UserStatus } from "../../data/sample";
import styles from "./screens.module.css";

const statusTone = { pending: "warning", active: "success", disabled: "neutral" } as const;

export function UserListMock() {
  const { t } = useTranslation("mock");
  const { t: tc } = useTranslation();
  const { showToast } = useToast();
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | UserStatus>("all");
  const [confirm, setConfirm] = useState<{ user: SampleUser; kind: "approve" | "disable" } | null>(
    null,
  );
  const [page, setPage] = useState(1);

  const statusLabel: Record<UserStatus, string> = {
    pending: t("users.statusPending"),
    active: t("users.statusActive"),
    disabled: t("users.statusDisabled"),
  };

  const rows = useMemo(
    () =>
      sampleUsers.filter(
        (user) =>
          (statusFilter === "all" || user.status === statusFilter) &&
          (keyword === "" || user.email.includes(keyword) || user.displayName.includes(keyword)),
      ),
    [keyword, statusFilter],
  );

  const columns: readonly TableColumn<SampleUser>[] = [
    { key: "email", header: t("users.email"), render: (user) => user.email, sortable: true },
    { key: "displayName", header: t("users.displayName"), render: (user) => user.displayName },
    {
      key: "status",
      header: t("users.status"),
      render: (user) => <Badge tone={statusTone[user.status]}>{statusLabel[user.status]}</Badge>,
    },
    {
      key: "registeredAt",
      header: t("users.registeredAt"),
      render: (user) => user.registeredAt,
      sortable: true,
    },
    {
      key: "actions",
      header: t("users.actions"),
      render: (user) => (
        <>
          {user.status === "pending" ? (
            <Button size="sm" variant="primary" onClick={() => setConfirm({ user, kind: "approve" })}>
              {t("users.approve")}
            </Button>
          ) : null}{" "}
          {user.status !== "disabled" ? (
            <Button size="sm" variant="danger" onClick={() => setConfirm({ user, kind: "disable" })}>
              {t("users.disable")}
            </Button>
          ) : null}
        </>
      ),
    },
  ];

  return (
    <AppShell
      navItems={[
        { key: "users", label: t("users.title"), active: true },
        { key: "records", label: t("nav.recordEdit") },
        { key: "query", label: t("nav.queryRun") },
      ]}
    >
      <h1 className={styles.screenTitle}>{t("users.title")}</h1>
      <div className={styles.toolbar}>
        <span className={styles.searchBox}>
          <SearchInput
            placeholder={t("users.searchPlaceholder")}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </span>
        <Select
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as "all" | UserStatus)}
          style={{ width: "auto" }}
        >
          <option value="all">{t("users.statusAll")}</option>
          <option value="pending">{t("users.statusPending")}</option>
          <option value="active">{t("users.statusActive")}</option>
          <option value="disabled">{t("users.statusDisabled")}</option>
        </Select>
      </div>
      <Table
        columns={columns}
        rows={rows}
        rowKey={(user) => user.id}
        emptyState={
          <EmptyState
            action={
              <Button
                size="sm"
                onClick={() => {
                  setKeyword("");
                  setStatusFilter("all");
                }}
              >
                {tc("action.clearFilter")}
              </Button>
            }
          />
        }
      />
      <div className={styles.footerBar}>
        <span />
        <Pagination page={page} totalPages={3} onChange={setPage} />
      </div>
      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.kind === "approve" ? t("users.approveTitle") : t("users.disableTitle")}
        message={`${confirm?.user.displayName ?? ""}(${confirm?.user.email ?? ""})${
          confirm?.kind === "approve" ? t("users.approveMessage") : t("users.disableMessage")
        }`}
        tone={confirm?.kind === "disable" ? "danger" : "default"}
        confirmLabel={confirm?.kind === "approve" ? t("users.approve") : t("users.disable")}
        onConfirm={() => {
          if (confirm) {
            showToast(
              confirm.kind === "approve" ? "success" : "info",
              `${confirm.user.displayName}${
                confirm.kind === "approve" ? t("users.approved") : t("users.disabled")
              }`,
            );
          }
          setConfirm(null);
        }}
        onCancel={() => setConfirm(null)}
      />
    </AppShell>
  );
}

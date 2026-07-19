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
import { apiFetch } from "../../../app/apiClient";
import {
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
import type { BadgeTone, TableColumn } from "../../../design-system/components";
import styles from "./users.module.css";

interface UserSummary {
  id: number;
  email: string;
  displayName: string | null;
  role: "ADMIN" | "USER";
  status: "PENDING_APPROVAL" | "ACTIVE" | "REJECTED" | "DISABLED";
  language: string;
  lockedUntil: string | null;
  failedLoginCount: number;
  createdAt: string;
}

interface UserListResponse {
  items: UserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface UserActionResponse {
  user: UserSummary;
  mailSent: boolean;
}

type ActionKind = "approve" | "reject" | "unlock";

const statusTone: Record<UserSummary["status"], BadgeTone> = {
  PENDING_APPROVAL: "warning",
  ACTIVE: "success",
  REJECTED: "neutral",
  DISABLED: "neutral",
};

const PAGE_SIZE = 20;

function isLocked(user: UserSummary): boolean {
  return user.lockedUntil !== null && new Date(user.lockedUntil).getTime() > Date.now();
}

/**
 * ユーザ管理(US-003/006)。サーバページング・状態フィルタ・キーワード検索、
 * 承認・却下・ロック解除(確認ダイアログ付き)。
 * mailSent=false の場合は警告 Toast(操作自体は成立 — Q4=A)。
 */
export function UserListPage() {
  const { t } = useTranslation("admin");
  const { t: tc } = useTranslation();
  const { showToast } = useToast();
  const [items, setItems] = useState<UserSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [status, setStatus] = useState<string>("");
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(true);
  const [confirm, setConfirm] = useState<{ user: UserSummary; kind: ActionKind } | null>(null);
  const [processing, setProcessing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("size", String(PAGE_SIZE));
      if (status !== "") {
        params.set("status", status);
      }
      if (keyword !== "") {
        params.set("q", keyword);
      }
      const result = await apiFetch<UserListResponse>(`/api/admin/users?${params.toString()}`);
      setItems(result.items);
      setTotalPages(result.totalPages);
    } catch {
      showToast("danger", t("users.toast.failed"));
    } finally {
      setLoading(false);
    }
  }, [page, status, keyword, showToast, t]);

  useEffect(() => {
    // マイクロタスクに遅らせて effect 本体での同期 setState を避ける(react-hooks/set-state-in-effect)
    void Promise.resolve().then(() => load());
  }, [load]);

  const onSearch = (event: FormEvent) => {
    event.preventDefault();
    setPage(0);
    setKeyword(keywordInput.trim());
  };

  const runAction = async () => {
    if (!confirm) {
      return;
    }
    setProcessing(true);
    try {
      if (confirm.kind === "unlock") {
        await apiFetch<UserSummary>(`/api/admin/users/${confirm.user.id}/unlock`, {
          method: "POST",
        });
        showToast("success", t("users.toast.unlocked"));
      } else {
        const result = await apiFetch<UserActionResponse>(
          `/api/admin/users/${confirm.user.id}/${confirm.kind}`,
          { method: "POST" },
        );
        showToast(
          "success",
          confirm.kind === "approve" ? t("users.toast.approved") : t("users.toast.rejected"),
        );
        if (!result.mailSent) {
          showToast("warning", t("users.toast.mailFailed"));
        }
      }
      await load();
    } catch {
      showToast("danger", t("users.toast.failed"));
    } finally {
      setProcessing(false);
      setConfirm(null);
    }
  };

  const columns: readonly TableColumn<UserSummary>[] = [
    {
      key: "email",
      header: t("users.column.email"),
      render: (user) => user.email,
    },
    {
      key: "displayName",
      header: t("users.column.displayName"),
      render: (user) => user.displayName ?? "-",
    },
    {
      key: "role",
      header: t("users.column.role"),
      render: (user) => t(`users.role.${user.role}`),
    },
    {
      key: "status",
      header: t("users.column.status"),
      render: (user) => (
        <span className={styles.statusCell}>
          <Badge tone={statusTone[user.status]}>{t(`users.status.${user.status}`)}</Badge>
          {isLocked(user) ? <Badge tone="danger">{t("users.locked")}</Badge> : null}
        </span>
      ),
    },
    {
      key: "createdAt",
      header: t("users.column.createdAt"),
      render: (user) => new Date(user.createdAt).toLocaleString(),
    },
    {
      key: "actions",
      header: t("users.column.actions"),
      render: (user) => (
        <span className={styles.actionsCell}>
          {user.status === "PENDING_APPROVAL" ? (
            <>
              <Button
                size="sm"
                variant="primary"
                onClick={() => setConfirm({ user, kind: "approve" })}
              >
                {t("users.action.approve")}
              </Button>
              <Button size="sm" variant="danger" onClick={() => setConfirm({ user, kind: "reject" })}>
                {t("users.action.reject")}
              </Button>
            </>
          ) : null}
          {isLocked(user) ? (
            <Button size="sm" onClick={() => setConfirm({ user, kind: "unlock" })}>
              {t("users.action.unlock")}
            </Button>
          ) : null}
        </span>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>{t("users.title")}</h1>
      <form className={styles.filters} onSubmit={onSearch}>
        <label className={styles.filterLabel}>
          {t("users.filter.status")}
          <Select
            value={status}
            onChange={(event) => {
              setPage(0);
              setStatus(event.target.value);
            }}
          >
            <option value="">{t("users.filter.all")}</option>
            <option value="PENDING_APPROVAL">{t("users.status.PENDING_APPROVAL")}</option>
            <option value="ACTIVE">{t("users.status.ACTIVE")}</option>
            <option value="REJECTED">{t("users.status.REJECTED")}</option>
            <option value="DISABLED">{t("users.status.DISABLED")}</option>
          </Select>
        </label>
        <SearchInput
          placeholder={t("users.filter.keyword")}
          value={keywordInput}
          onChange={(event) => setKeywordInput(event.target.value)}
        />
        <Button type="submit">{tc("action.search")}</Button>
      </form>
      <Table
        columns={columns}
        rows={items}
        rowKey={(user) => String(user.id)}
        loading={loading}
        emptyState={<EmptyState message={tc("state.empty")} />}
      />
      {totalPages > 1 ? (
        <Pagination page={page + 1} totalPages={totalPages} onChange={(next) => setPage(next - 1)} />
      ) : null}
      <ConfirmDialog
        open={confirm !== null}
        title={confirm ? t(`users.confirm.${confirm.kind}Title`) : ""}
        message={confirm ? t(`users.confirm.${confirm.kind}Message`, { email: confirm.user.email }) : ""}
        tone={confirm?.kind === "reject" ? "danger" : "default"}
        processing={processing}
        onConfirm={() => {
          void runAction();
        }}
        onCancel={() => setConfirm(null)}
      />
    </div>
  );
}

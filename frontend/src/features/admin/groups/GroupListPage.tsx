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
import { ApiError } from "../../../app/apiClient";
import {
  Button,
  ConfirmDialog,
  EmptyState,
  Modal,
  SearchInput,
  Table,
  TextInput,
  useToast,
} from "../../../design-system/components";
import type { TableColumn } from "../../../design-system/components";
import {
  addGroupMember,
  createGroup,
  deleteGroup,
  listGroupMembers,
  listGroups,
  removeGroupMember,
  renameGroup,
  searchUserCandidates,
} from "./api";
import type { GroupMember, GroupSummary, UserCandidate } from "./api";
import styles from "./groups.module.css";

type NameDialog = { kind: "create" } | { kind: "rename"; group: GroupSummary };

/**
 * グループ管理(US-018/019)。作成・改名(モーダル)・削除(カスケード警告)、
 * メンバー管理(モーダル: 一覧・削除・ユーザ検索追加)。
 */
export function GroupListPage() {
  const { t } = useTranslation("admin");
  const { t: tc } = useTranslation();
  const { showToast } = useToast();
  const [items, setItems] = useState<GroupSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [nameDialog, setNameDialog] = useState<NameDialog | null>(null);
  const [nameInput, setNameInput] = useState("");
  const [nameError, setNameError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<GroupSummary | null>(null);
  const [processing, setProcessing] = useState(false);
  const [memberGroup, setMemberGroup] = useState<GroupSummary | null>(null);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [candidateKeyword, setCandidateKeyword] = useState("");
  const [candidates, setCandidates] = useState<UserCandidate[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listGroups());
    } catch {
      showToast("danger", t("groups.toast.failed"));
    } finally {
      setLoading(false);
    }
  }, [showToast, t]);

  useEffect(() => {
    void Promise.resolve().then(() => load());
  }, [load]);

  const openMembers = async (group: GroupSummary) => {
    setMemberGroup(group);
    setCandidateKeyword("");
    setCandidates([]);
    try {
      setMembers(await listGroupMembers(group.id));
    } catch {
      showToast("danger", t("groups.toast.failed"));
    }
  };

  const submitName = async (event: FormEvent) => {
    event.preventDefault();
    if (!nameDialog) {
      return;
    }
    setProcessing(true);
    setNameError(null);
    try {
      if (nameDialog.kind === "create") {
        await createGroup(nameInput.trim());
        showToast("success", t("groups.toast.created"));
      } else {
        await renameGroup(nameDialog.group.id, nameInput.trim());
        showToast("success", t("groups.toast.renamed"));
      }
      setNameDialog(null);
      await load();
    } catch (error) {
      setNameError(
        error instanceof ApiError && error.code === "GROUP_NAME_DUPLICATE"
          ? t("groups.error.duplicate")
          : t("groups.toast.failed"),
      );
    } finally {
      setProcessing(false);
    }
  };

  const runDelete = async () => {
    if (!confirmDelete) {
      return;
    }
    setProcessing(true);
    try {
      await deleteGroup(confirmDelete.id);
      showToast("success", t("groups.toast.deleted"));
      await load();
    } catch {
      showToast("danger", t("groups.toast.failed"));
    } finally {
      setProcessing(false);
      setConfirmDelete(null);
    }
  };

  const searchCandidates = async (event: FormEvent) => {
    event.preventDefault();
    try {
      const found = await searchUserCandidates(candidateKeyword.trim());
      const memberIds = new Set(members.map((member) => member.userId));
      setCandidates(found.filter((candidate) => !memberIds.has(candidate.id)));
    } catch {
      showToast("danger", t("groups.toast.failed"));
    }
  };

  const addMember = async (candidate: UserCandidate) => {
    if (!memberGroup) {
      return;
    }
    try {
      await addGroupMember(memberGroup.id, candidate.id);
      setMembers(await listGroupMembers(memberGroup.id));
      setCandidates((prev) => prev.filter((item) => item.id !== candidate.id));
      await load();
    } catch {
      showToast("danger", t("groups.toast.failed"));
    }
  };

  const removeMember = async (member: GroupMember) => {
    if (!memberGroup) {
      return;
    }
    try {
      await removeGroupMember(memberGroup.id, member.userId);
      setMembers(await listGroupMembers(memberGroup.id));
      await load();
    } catch {
      showToast("danger", t("groups.toast.failed"));
    }
  };

  const columns: readonly TableColumn<GroupSummary>[] = [
    {
      key: "name",
      header: t("groups.column.name"),
      render: (group) => group.name,
    },
    {
      key: "memberCount",
      header: t("groups.column.memberCount"),
      render: (group) => String(group.memberCount),
    },
    {
      key: "actions",
      header: t("groups.column.actions"),
      render: (group) => (
        <span className={styles.actionsCell}>
          <Button size="sm" onClick={() => void openMembers(group)}>
            {t("groups.action.members")}
          </Button>
          <Button
            size="sm"
            onClick={() => {
              setNameDialog({ kind: "rename", group });
              setNameInput(group.name);
              setNameError(null);
            }}
          >
            {t("groups.action.rename")}
          </Button>
          <Button size="sm" variant="danger" onClick={() => setConfirmDelete(group)}>
            {t("groups.action.delete")}
          </Button>
        </span>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>{t("groups.title")}</h1>
      <div className={styles.toolbar}>
        <Button
          variant="primary"
          onClick={() => {
            setNameDialog({ kind: "create" });
            setNameInput("");
            setNameError(null);
          }}
        >
          {t("groups.action.create")}
        </Button>
      </div>
      <Table
        columns={columns}
        rows={items}
        rowKey={(group) => String(group.id)}
        loading={loading}
        emptyState={<EmptyState message={tc("state.empty")} />}
      />
      <Modal
        open={nameDialog !== null}
        title={nameDialog?.kind === "create" ? t("groups.createTitle") : t("groups.renameTitle")}
        onClose={() => setNameDialog(null)}
      >
        <form className={styles.modalBody} onSubmit={submitName}>
          <TextInput
            value={nameInput}
            onChange={(event) => setNameInput(event.target.value)}
            placeholder={t("groups.namePlaceholder")}
            invalid={nameError !== null}
            required
            maxLength={100}
          />
          {nameError ? <span className={styles.muted}>{nameError}</span> : null}
          <Button type="submit" variant="primary" loading={processing}>
            {tc("action.save")}
          </Button>
        </form>
      </Modal>
      <Modal
        open={memberGroup !== null}
        title={memberGroup ? t("groups.membersTitle", { name: memberGroup.name }) : ""}
        onClose={() => setMemberGroup(null)}
      >
        <div className={styles.modalBody}>
          <div className={styles.memberList}>
            {members.length === 0 ? (
              <span className={styles.muted}>{t("groups.noMembers")}</span>
            ) : (
              members.map((member) => (
                <div key={member.userId} className={styles.memberRow}>
                  <span>
                    {member.email}
                    {member.displayName ? ` (${member.displayName})` : ""}
                  </span>
                  <Button size="sm" variant="danger" onClick={() => void removeMember(member)}>
                    {t("groups.action.removeMember")}
                  </Button>
                </div>
              ))
            )}
          </div>
          <form className={styles.addRow} onSubmit={searchCandidates}>
            <SearchInput
              placeholder={t("groups.searchPlaceholder")}
              value={candidateKeyword}
              onChange={(event) => setCandidateKeyword(event.target.value)}
            />
            <Button type="submit">{tc("action.search")}</Button>
          </form>
          <div className={styles.memberList}>
            {candidates.map((candidate) => (
              <div key={candidate.id} className={styles.memberRow}>
                <span>{candidate.email}</span>
                <Button size="sm" onClick={() => void addMember(candidate)}>
                  {t("groups.action.addMember")}
                </Button>
              </div>
            ))}
          </div>
        </div>
      </Modal>
      <ConfirmDialog
        open={confirmDelete !== null}
        title={t("groups.confirm.deleteTitle")}
        message={confirmDelete ? t("groups.confirm.deleteMessage", { name: confirmDelete.name }) : ""}
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

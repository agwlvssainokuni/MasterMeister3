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

import { apiFetch } from "../../../app/apiClient";

export interface GroupSummary {
  id: number;
  name: string;
  memberCount: number;
}

export interface GroupMember {
  userId: number;
  email: string;
  displayName: string | null;
}

/** メンバー追加候補(本 feature 専用に取得 — feature 間参照はしない)。 */
export interface UserCandidate {
  id: number;
  email: string;
  displayName: string | null;
}

export function listGroups(): Promise<GroupSummary[]> {
  return apiFetch<GroupSummary[]>("/api/admin/groups");
}

export function createGroup(name: string): Promise<GroupSummary> {
  return apiFetch<GroupSummary>("/api/admin/groups", { method: "POST", body: { name } });
}

export function renameGroup(id: number, name: string): Promise<void> {
  return apiFetch<void>(`/api/admin/groups/${id}`, { method: "PUT", body: { name } });
}

export function deleteGroup(id: number): Promise<void> {
  return apiFetch<void>(`/api/admin/groups/${id}`, { method: "DELETE" });
}

export function listGroupMembers(id: number): Promise<GroupMember[]> {
  return apiFetch<GroupMember[]>(`/api/admin/groups/${id}/members`);
}

export function addGroupMember(id: number, userId: number): Promise<void> {
  return apiFetch<void>(`/api/admin/groups/${id}/members`, {
    method: "POST",
    body: { userId },
  });
}

export function removeGroupMember(id: number, userId: number): Promise<void> {
  return apiFetch<void>(`/api/admin/groups/${id}/members/${userId}`, { method: "DELETE" });
}

export function searchUserCandidates(keyword: string): Promise<UserCandidate[]> {
  const params = new URLSearchParams();
  params.set("page", "0");
  params.set("size", "20");
  if (keyword) {
    params.set("q", keyword);
  }
  return apiFetch<{ items: UserCandidate[] }>(`/api/admin/users?${params.toString()}`).then(
    (response) => response.items,
  );
}

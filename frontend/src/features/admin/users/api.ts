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

export interface UserSummary {
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

export interface UserListResponse {
  items: UserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserActionResponse {
  user: UserSummary;
  mailSent: boolean;
}

export interface UserListQuery {
  page: number;
  size: number;
  status?: string;
  keyword?: string;
}

export function listUsers(query: UserListQuery): Promise<UserListResponse> {
  const params = new URLSearchParams();
  params.set("page", String(query.page));
  params.set("size", String(query.size));
  if (query.status) {
    params.set("status", query.status);
  }
  if (query.keyword) {
    params.set("q", query.keyword);
  }
  return apiFetch<UserListResponse>(`/api/admin/users?${params.toString()}`);
}

export function approveUser(id: number): Promise<UserActionResponse> {
  return apiFetch<UserActionResponse>(`/api/admin/users/${id}/approve`, { method: "POST" });
}

export function rejectUser(id: number): Promise<UserActionResponse> {
  return apiFetch<UserActionResponse>(`/api/admin/users/${id}/reject`, { method: "POST" });
}

export function unlockUser(id: number): Promise<UserSummary> {
  return apiFetch<UserSummary>(`/api/admin/users/${id}/unlock`, { method: "POST" });
}

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

export type PrincipalType = "USER" | "GROUP";
export type PermissionLevel = "NONE" | "READ" | "UPDATE";
export type AuxType = "CREATE" | "DELETE";

export interface MainEntry {
  schema: string;
  table: string;
  column: string;
  permission: PermissionLevel;
  orphan: boolean;
}

export interface AuxEntry {
  schema: string;
  table: string;
  auxType: AuxType;
  granted: boolean;
  orphan: boolean;
}

export interface PrincipalEntries {
  main: MainEntry[];
  aux: AuxEntry[];
}

export interface PermissionScope {
  schema: string;
  table?: string;
  column?: string;
}

export interface PrincipalRef {
  principalType: PrincipalType;
  principalId: number;
}

/** プリンシパル選択肢(本 feature 専用に取得 — feature 間参照はしない)。 */
export interface UserOption {
  id: number;
  email: string;
  status: string;
}

export interface GroupOption {
  id: number;
  name: string;
}

/** 権限設定対象のスキーマツリー(connections feature とは独立に取得)。 */
export interface SchemaTree {
  importedAt: string | null;
  schemas: {
    name: string;
    remarks: string | null;
    tables: {
      name: string;
      tableType: "TABLE" | "VIEW";
      remarks: string | null;
      columns: { name: string; primaryKeySeq: number | null }[];
    }[];
  }[];
}

export function fetchPermissions(
  connectionId: number,
  principal: PrincipalRef,
): Promise<PrincipalEntries> {
  const params = new URLSearchParams();
  params.set("principalType", principal.principalType);
  params.set("principalId", String(principal.principalId));
  return apiFetch<PrincipalEntries>(
    `/api/admin/connections/${connectionId}/permissions?${params.toString()}`,
  );
}

export function setMainPermission(
  connectionId: number,
  principal: PrincipalRef,
  scope: PermissionScope,
  permission: PermissionLevel,
): Promise<void> {
  return apiFetch<void>(`/api/admin/connections/${connectionId}/permissions/entry`, {
    method: "PUT",
    body: { ...principal, ...scope, permission },
  });
}

export function setAuxPermission(
  connectionId: number,
  principal: PrincipalRef,
  scope: PermissionScope,
  auxType: AuxType,
  granted: boolean,
): Promise<void> {
  return apiFetch<void>(`/api/admin/connections/${connectionId}/permissions/entry`, {
    method: "PUT",
    body: { ...principal, ...scope, auxType, granted },
  });
}

export function removeMainPermission(
  connectionId: number,
  principal: PrincipalRef,
  scope: PermissionScope,
): Promise<void> {
  return apiFetch<void>(`/api/admin/connections/${connectionId}/permissions/entry`, {
    method: "DELETE",
    body: { ...principal, ...scope },
  });
}

export function removeAuxPermission(
  connectionId: number,
  principal: PrincipalRef,
  scope: PermissionScope,
  auxType: AuxType,
): Promise<void> {
  return apiFetch<void>(`/api/admin/connections/${connectionId}/permissions/entry`, {
    method: "DELETE",
    body: { ...principal, ...scope, auxType },
  });
}

export function exportPermissionYaml(connectionId: number): Promise<string> {
  return apiFetch<string>(`/api/admin/connections/${connectionId}/permissions/yaml`, {
    responseType: "text",
  });
}

export function importPermissionYaml(
  connectionId: number,
  yaml: string,
): Promise<{ entries: number }> {
  return apiFetch<{ entries: number }>(`/api/admin/connections/${connectionId}/permissions/yaml`, {
    method: "PUT",
    body: yaml,
    contentType: "application/yaml",
  });
}

export function fetchSchemaTree(connectionId: number): Promise<SchemaTree> {
  return apiFetch<SchemaTree>(`/api/admin/connections/${connectionId}/schema`);
}

export function listUserOptions(): Promise<UserOption[]> {
  return apiFetch<{ items: { id: number; email: string; status: string }[] }>(
    "/api/admin/users?page=0&size=200",
  ).then((response) => response.items);
}

export function listGroupOptions(): Promise<GroupOption[]> {
  return apiFetch<{ id: number; name: string }[]>("/api/admin/groups");
}

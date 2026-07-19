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

export type DbType = "MYSQL" | "MARIADB" | "POSTGRESQL" | "H2";

export interface ConnectionSummary {
  id: number;
  name: string;
  dbType: DbType;
  host: string;
  port: number | null;
  databaseName: string;
  username: string;
  options: string | null;
  poolMaxSize: number;
  poolTimeoutMs: number;
}

export interface ConnectionInput {
  name: string;
  dbType: DbType;
  host: string;
  port: number | null;
  databaseName: string;
  username: string;
  /** 新規は必須。編集時 null = 既存値を維持 */
  password: string | null;
  options: string | null;
  poolMaxSize: number;
  poolTimeoutMs: number;
}

export interface ConnectionTestInput {
  id?: number;
  dbType: DbType;
  host: string;
  port: number | null;
  databaseName: string;
  username: string;
  password: string | null;
  options: string | null;
}

export interface ConnectionTestResult {
  success: boolean;
  reason: string | null;
}

export interface ColumnNode {
  name: string;
  ordinal: number;
  dataType: string;
  columnSize: number | null;
  decimalDigits: number | null;
  nullable: boolean;
  defaultValue: string | null;
  remarks: string | null;
  primaryKeySeq: number | null;
}

export interface TableNode {
  name: string;
  tableType: "TABLE" | "VIEW";
  remarks: string | null;
  columns: ColumnNode[];
}

export interface SchemaNode {
  name: string;
  remarks: string | null;
  tables: TableNode[];
}

export interface SchemaTree {
  importedAt: string | null;
  schemas: SchemaNode[];
}

export interface SchemaImportResult {
  schemas: number;
  tables: number;
  columns: number;
}

export interface ImportStatus {
  connectionId: number;
  importedAt: string;
}

export function listConnections(): Promise<ConnectionSummary[]> {
  return apiFetch<ConnectionSummary[]>("/api/admin/connections");
}

export function getConnection(id: number): Promise<ConnectionSummary> {
  return apiFetch<ConnectionSummary>(`/api/admin/connections/${id}`);
}

export function createConnection(input: ConnectionInput): Promise<ConnectionSummary> {
  return apiFetch<ConnectionSummary>("/api/admin/connections", { method: "POST", body: input });
}

export function updateConnection(id: number, input: ConnectionInput): Promise<void> {
  return apiFetch<void>(`/api/admin/connections/${id}`, { method: "PUT", body: input });
}

export function deleteConnection(id: number): Promise<void> {
  return apiFetch<void>(`/api/admin/connections/${id}`, { method: "DELETE" });
}

export function testConnection(input: ConnectionTestInput): Promise<ConnectionTestResult> {
  return apiFetch<ConnectionTestResult>("/api/admin/connections/test", {
    method: "POST",
    body: input,
  });
}

export function importSchema(id: number): Promise<SchemaImportResult> {
  return apiFetch<SchemaImportResult>(`/api/admin/connections/${id}/schema/import`, {
    method: "POST",
  });
}

export function fetchSchema(id: number): Promise<SchemaTree> {
  return apiFetch<SchemaTree>(`/api/admin/connections/${id}/schema`);
}

export function fetchImportStatus(): Promise<ImportStatus[]> {
  return apiFetch<ImportStatus[]>("/api/admin/metadata/import-status");
}

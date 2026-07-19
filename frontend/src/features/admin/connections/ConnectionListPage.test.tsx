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

import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../design-system/components";
import { ConnectionListPage } from "./ConnectionListPage";

const connection = {
  id: 1,
  name: "sales-db",
  dbType: "MYSQL",
  host: "db.example.com",
  port: 3306,
  databaseName: "sales",
  username: "app",
  options: null,
  poolMaxSize: 5,
  poolTimeoutMs: 5000,
};

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderPage() {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={["/admin/connections"]}>
        <ConnectionListPage />
      </MemoryRouter>
    </ToastProvider>,
  );
}

describe("ConnectionListPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
  });

  it("一覧と取込状況を表示する", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.startsWith("/api/admin/metadata/import-status")) {
          return Promise.resolve(
            jsonResponse(200, [{ connectionId: 1, importedAt: "2026-07-20T10:00:00" }]),
          );
        }
        return Promise.resolve(jsonResponse(200, [connection]));
      }),
    );
    renderPage();

    expect(await screen.findByText("sales-db")).toBeInTheDocument();
    expect(screen.getByText("db.example.com:3306")).toBeInTheDocument();
    expect(screen.queryByText("未取込")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "権限設定" })).toBeInTheDocument();
  });

  it("削除は確認ダイアログを経てDELETEする", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (init?.method === "DELETE") {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (url.startsWith("/api/admin/metadata/import-status")) {
        return Promise.resolve(jsonResponse(200, []));
      }
      return Promise.resolve(jsonResponse(200, [connection]));
    });
    vi.stubGlobal("fetch", fetchMock);
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "削除" }));
    expect(await screen.findByText(/sales-db を削除します/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "OK" }));

    expect(await screen.findByText("接続を削除しました")).toBeInTheDocument();
    const deleteCalls = fetchMock.mock.calls.filter((call) => call[1]?.method === "DELETE");
    expect(deleteCalls).toHaveLength(1);
    expect(String(deleteCalls[0][0])).toBe("/api/admin/connections/1");
  });
});

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
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../design-system/components";
import { PermissionPage } from "./PermissionPage";

const schemaTree = {
  importedAt: "2026-07-20T10:00:00",
  schemas: [
    {
      name: "PUBLIC",
      remarks: null,
      tables: [
        {
          name: "CUSTOMERS",
          tableType: "TABLE",
          remarks: null,
          columns: [
            { name: "ID", primaryKeySeq: 1 },
            { name: "NAME", primaryKeySeq: null },
          ],
        },
      ],
    },
  ],
};

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function stubApi(entries: { main: unknown[]; aux: unknown[] }) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const path = String(url);
    if (path.startsWith("/api/admin/users")) {
      return Promise.resolve(
        jsonResponse(200, { items: [{ id: 10, email: "user@example.com", status: "ACTIVE" }] }),
      );
    }
    if (path === "/api/admin/groups") {
      return Promise.resolve(jsonResponse(200, []));
    }
    if (path.endsWith("/schema")) {
      return Promise.resolve(jsonResponse(200, schemaTree));
    }
    if (path.includes("/permissions/entry")) {
      expect(init?.method === "PUT" || init?.method === "DELETE").toBe(true);
      return Promise.resolve(new Response(null, { status: 204 }));
    }
    if (path.includes("/permissions?")) {
      return Promise.resolve(jsonResponse(200, entries));
    }
    return Promise.resolve(jsonResponse(404, {}));
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function renderPage() {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={["/admin/connections/1/permissions"]}>
        <Routes>
          <Route path="/admin/connections/:id/permissions" element={<PermissionPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  );
}

async function selectUserPrincipal() {
  const principalSelect = await screen.findByLabelText("対象");
  fireEvent.change(principalSelect, { target: { value: "10" } });
}

describe("PermissionPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
  });

  it("プリンシパル選択でツリーとエントリを表示し設定変更をPUTする", async () => {
    const fetchMock = stubApi({
      main: [
        { schema: "PUBLIC", table: "CUSTOMERS", column: "NAME", permission: "READ", orphan: false },
      ],
      aux: [],
    });
    renderPage();
    await selectUserPrincipal();

    expect(await screen.findByText("CUSTOMERS")).toBeInTheDocument();
    expect(screen.getByText("NAME")).toBeInTheDocument();

    // NAME カラムの主権限セレクト(READ が選択済み)を UPDATE に変更 → PUT
    const readSelect = screen
      .getAllByRole("combobox")
      .find((element) => (element as HTMLSelectElement).value === "READ");
    expect(readSelect).toBeDefined();
    fireEvent.change(readSelect as HTMLSelectElement, { target: { value: "UPDATE" } });

    await vi.waitFor(() => {
      const putCall = fetchMock.mock.calls.find(
        (call) => call[1]?.method === "PUT" && String(call[0]).includes("/permissions/entry"),
      );
      expect(putCall).toBeDefined();
      const body = JSON.parse(String(putCall?.[1]?.body)) as Record<string, unknown>;
      expect(body.schema).toBe("PUBLIC");
      expect(body.table).toBe("CUSTOMERS");
      expect(body.column).toBe("NAME");
      expect(body.permission).toBe("UPDATE");
    });
  });

  it("孤児エントリは対象なしセクションに表示され削除できる", async () => {
    const fetchMock = stubApi({
      main: [{ schema: "PUBLIC", table: "GONE", column: "", permission: "READ", orphan: true }],
      aux: [],
    });
    renderPage();
    await selectUserPrincipal();

    expect(await screen.findByText("対象なしのエントリ")).toBeInTheDocument();
    expect(screen.getByText(/PUBLIC\.GONE/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "エントリ削除" }));
    await vi.waitFor(() => {
      const deleteCall = fetchMock.mock.calls.find(
        (call) => call[1]?.method === "DELETE" && String(call[0]).includes("/permissions/entry"),
      );
      expect(deleteCall).toBeDefined();
      const body = JSON.parse(String(deleteCall?.[1]?.body)) as Record<string, unknown>;
      expect(body.table).toBe("GONE");
    });
  });
});

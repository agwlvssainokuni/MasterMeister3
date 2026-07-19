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
import { ConnectionEditPage } from "./ConnectionEditPage";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderNew() {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={["/admin/connections/new"]}>
        <Routes>
          <Route path="/admin/connections/new" element={<ConnectionEditPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  );
}

function fillMinimalForm() {
  fireEvent.change(screen.getByLabelText(/^接続名/), { target: { value: "test-conn" } });
  fireEvent.change(screen.getByLabelText(/^ホスト/), { target: { value: "localhost" } });
  fireEvent.change(screen.getByLabelText(/^データベース名/), { target: { value: "testdb" } });
  fireEvent.change(screen.getByLabelText(/^接続ユーザ/), { target: { value: "app" } });
  fireEvent.change(screen.getByLabelText(/^パスワード必須$/), { target: { value: "secret" } });
}

describe("ConnectionEditPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
  });

  it("接続テストはフォームの現在値で実行し成功を表示する", async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      void _init;
      if (url === "/api/admin/connections/test") {
        return Promise.resolve(jsonResponse(200, { success: true, reason: null }));
      }
      return Promise.resolve(jsonResponse(200, {}));
    });
    vi.stubGlobal("fetch", fetchMock);
    renderNew();

    fillMinimalForm();
    fireEvent.click(screen.getByRole("button", { name: "接続テスト" }));

    expect(await screen.findByText("接続に成功しました")).toBeInTheDocument();
    const testCall = fetchMock.mock.calls.find(
      (call) => String(call[0]) === "/api/admin/connections/test",
    );
    expect(testCall).toBeDefined();
    const body = JSON.parse(String(testCall?.[1]?.body)) as Record<string, unknown>;
    expect(body.host).toBe("localhost");
    expect(body.password).toBe("secret");
  });

  it("認証失敗はAUTH_FAILEDの文言で表示する", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse(200, { success: false, reason: "AUTH_FAILED" })),
    );
    renderNew();

    fillMinimalForm();
    fireEvent.click(screen.getByRole("button", { name: "接続テスト" }));

    expect(await screen.findByText(/認証に失敗しました/)).toBeInTheDocument();
  });
});

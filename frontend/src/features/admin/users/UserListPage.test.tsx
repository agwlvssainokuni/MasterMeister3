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
import { afterEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../design-system/components";
import { UserListPage } from "./UserListPage";

const pendingUser = {
  id: 1,
  email: "pending@example.com",
  displayName: "承認 待子",
  role: "USER",
  status: "PENDING_APPROVAL",
  language: "ja",
  lockedUntil: null,
  failedLoginCount: 0,
  createdAt: "2026-07-19T10:00:00",
};

const lockedUser = {
  id: 2,
  email: "locked@example.com",
  displayName: null,
  role: "USER",
  status: "ACTIVE",
  language: "ja",
  lockedUntil: "2099-01-01T00:00:00",
  failedLoginCount: 5,
  createdAt: "2026-07-19T09:00:00",
};

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function listResponse(items: unknown[]) {
  return jsonResponse(200, {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: 1,
  });
}

function renderPage() {
  return render(
    <ToastProvider>
      <UserListPage />
    </ToastProvider>,
  );
}

describe("UserListPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
  });

  it("一覧を表示し状態バッジとロック中を示す", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(listResponse([pendingUser, lockedUser])),
    );
    renderPage();

    expect(await screen.findByText("pending@example.com")).toBeInTheDocument();
    expect(screen.getByText("locked@example.com")).toBeInTheDocument();
    // 「承認待ち」はフィルタの option とバッジの両方に現れる
    expect(screen.getAllByText("承認待ち").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("ロック中")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "承認" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "ロック解除" })).toBeInTheDocument();
  });

  it("承認は確認ダイアログを経てPOSTしmailSent_falseなら警告Toastを出す", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.startsWith("/api/admin/users/1/approve")) {
        expect(init?.method).toBe("POST");
        return Promise.resolve(
          jsonResponse(200, {
            user: { ...pendingUser, status: "ACTIVE" },
            mailSent: false,
          }),
        );
      }
      return Promise.resolve(listResponse([pendingUser]));
    });
    vi.stubGlobal("fetch", fetchMock);
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "承認" }));
    expect(await screen.findByText(/pending@example.com を承認します/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "OK" }));

    expect(await screen.findByText("承認しました")).toBeInTheDocument();
    expect(await screen.findByText(/通知メールの送信に失敗しました/)).toBeInTheDocument();
    const approveCalls = fetchMock.mock.calls.filter((call) =>
      String(call[0]).startsWith("/api/admin/users/1/approve"),
    );
    expect(approveCalls).toHaveLength(1);
  });

  it("状態フィルタ変更でクエリパラメータ付きで再取得する", async () => {
    const fetchMock = vi.fn().mockResolvedValue(listResponse([]));
    vi.stubGlobal("fetch", fetchMock);
    renderPage();

    await screen.findByText("ユーザ管理");
    fireEvent.change(screen.getByRole("combobox"), {
      target: { value: "PENDING_APPROVAL" },
    });

    await vi.waitFor(() => {
      const urls = fetchMock.mock.calls.map((call) => String(call[0]));
      expect(urls.some((url) => url.includes("status=PENDING_APPROVAL"))).toBe(true);
    });
  });
});

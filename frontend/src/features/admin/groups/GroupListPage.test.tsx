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

import { fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../design-system/components";
import { GroupListPage } from "./GroupListPage";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderPage() {
  return render(
    <ToastProvider>
      <GroupListPage />
    </ToastProvider>,
  );
}

describe("GroupListPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
  });

  it("一覧を表示しグループ作成をPOSTする", async () => {
    const groups: unknown[] = [{ id: 1, name: "sales", memberCount: 2 }];
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (String(url) === "/api/admin/groups" && init?.method === "POST") {
        groups.push({ id: 2, name: "dev", memberCount: 0 });
        return Promise.resolve(jsonResponse(201, { id: 2, name: "dev", memberCount: 0 }));
      }
      return Promise.resolve(jsonResponse(200, groups));
    });
    vi.stubGlobal("fetch", fetchMock);
    renderPage();

    expect(await screen.findByText("sales")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "グループ作成" }));
    fireEvent.change(screen.getByPlaceholderText("グループ名"), { target: { value: "dev" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(await screen.findByText("グループを作成しました")).toBeInTheDocument();
    expect(await screen.findByText("dev")).toBeInTheDocument();
  });

  it("メンバー管理モーダルで追加と削除ができる", async () => {
    let members: unknown[] = [
      { userId: 10, email: "member@example.com", displayName: null },
    ];
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const path = String(url);
      if (path === "/api/admin/groups/1/members" && init?.method === "POST") {
        members.push({ userId: 11, email: "new@example.com", displayName: null });
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (path === "/api/admin/groups/1/members/10" && init?.method === "DELETE") {
        members = members.filter(
          (member) => (member as { userId: number }).userId !== 10,
        );
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (path === "/api/admin/groups/1/members") {
        return Promise.resolve(jsonResponse(200, members));
      }
      if (path.startsWith("/api/admin/users")) {
        return Promise.resolve(
          jsonResponse(200, {
            items: [{ id: 11, email: "new@example.com", displayName: null }],
          }),
        );
      }
      return Promise.resolve(jsonResponse(200, [{ id: 1, name: "sales", memberCount: 1 }]));
    });
    vi.stubGlobal("fetch", fetchMock);
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "メンバー" }));
    expect(await screen.findByText("member@example.com")).toBeInTheDocument();

    // 検索して追加
    fireEvent.change(screen.getByPlaceholderText("メールアドレスで検索"), {
      target: { value: "new" },
    });
    fireEvent.click(screen.getByRole("button", { name: "検索" }));
    fireEvent.click(await screen.findByRole("button", { name: "追加" }));
    await vi.waitFor(() => {
      expect(screen.getAllByText("new@example.com").length).toBeGreaterThanOrEqual(1);
    });

    // 既存メンバーを行内の削除ボタンで削除(テーブルのグループ削除と区別する)
    const memberRow = screen.getByText("member@example.com").closest("div") as HTMLElement;
    fireEvent.click(within(memberRow).getByRole("button", { name: "削除" }));
    await vi.waitFor(() => {
      expect(screen.queryByText("member@example.com")).not.toBeInTheDocument();
    });
    expect(fetchMock.mock.calls.some((call) => call[1]?.method === "DELETE")).toBe(true);
  });
});

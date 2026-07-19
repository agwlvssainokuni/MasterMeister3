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

import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../design-system/components";
import { ThemeProvider } from "../../design-system/theme/ThemeProvider";
import { AuthProvider } from "./AuthProvider";
import { RequireAdmin, RequireAuth } from "./guards";
import { tokenStore } from "./tokenStore";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderGuarded(initialPath: string) {
  return render(
    <ThemeProvider>
      <ToastProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<div data-testid="login" />} />
              <Route element={<RequireAuth />}>
                <Route path="/" element={<div data-testid="home" />} />
                <Route element={<RequireAdmin />}>
                  <Route path="/admin/users" element={<div data-testid="admin" />} />
                </Route>
              </Route>
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </ToastProvider>
    </ThemeProvider>,
  );
}

describe("認証ガード", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
    window.localStorage?.clear();
  });

  it("未認証は/loginへリダイレクトされる", async () => {
    vi.stubGlobal("fetch", vi.fn());
    renderGuarded("/");

    expect(await screen.findByTestId("login")).toBeInTheDocument();
  });

  it("リフレッシュトークンからセッションを復元しUSERは_admin配下からホームへ戻される", async () => {
    tokenStore.save("old-access", "old-refresh");
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url === "/api/auth/refresh") {
          return Promise.resolve(
            jsonResponse(200, { accessToken: "new-access", refreshToken: "new-refresh" }),
          );
        }
        if (url === "/api/me") {
          return Promise.resolve(
            jsonResponse(200, {
              email: "user@example.com",
              displayName: null,
              role: "USER",
              language: "ja",
              theme: "system",
            }),
          );
        }
        return Promise.resolve(jsonResponse(404, {}));
      }),
    );

    renderGuarded("/admin/users");

    // 復元完了後、USER は RequireAdmin によりホームへ
    expect(await screen.findByTestId("home")).toBeInTheDocument();
    expect(screen.queryByTestId("admin")).not.toBeInTheDocument();
  });
});

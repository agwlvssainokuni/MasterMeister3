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
import { ToastProvider } from "../../design-system/components";
import { ThemeProvider } from "../../design-system/theme/ThemeProvider";
import { tokenStore } from "./tokenStore";
import { AuthProvider } from "./AuthProvider";
import { LoginPage } from "./LoginPage";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": status >= 400 ? "application/problem+json" : "application/json",
    },
  });
}

function renderLogin() {
  return render(
    <ThemeProvider>
      <ToastProvider>
        <MemoryRouter initialEntries={["/login"]}>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/" element={<div data-testid="home" />} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </ToastProvider>
    </ThemeProvider>,
  );
}

function fillAndSubmit(email: string, password: string) {
  fireEvent.change(screen.getByLabelText(/^メールアドレス/), { target: { value: email } });
  fireEvent.change(screen.getByLabelText(/^パスワード必須$/), { target: { value: password } });
  fireEvent.click(screen.getByRole("button", { name: "ログイン" }));
}

describe("LoginPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
    window.localStorage?.clear();
  });

  it("空欄で送信するとクライアント検証エラーを表示しAPIを呼ばない", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderLogin();

    fireEvent.click(screen.getByRole("button", { name: "ログイン" }));

    expect(await screen.findAllByText("入力してください")).toHaveLength(2);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("認証失敗_401は統一メッセージを表示する", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse(401, { code: "AUTH_INVALID_CREDENTIALS" })),
    );
    renderLogin();

    fillAndSubmit("user@example.com", "wrong-password");

    expect(
      await screen.findByText("メールアドレスまたはパスワードが正しくありません"),
    ).toBeInTheDocument();
  });

  it("ロック中_423はロックメッセージを表示する", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse(423, { code: "AUTH_LOCKED" })),
    );
    renderLogin();

    fillAndSubmit("user@example.com", "correct-password");

    expect(await screen.findByText(/一時的にロックされています/)).toBeInTheDocument();
  });

  it("ログイン成功でトークンを保存しホームへ遷移する", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(200, {
          accessToken: "access-1",
          refreshToken: "refresh-1",
          user: {
            email: "user@example.com",
            displayName: "利用者",
            role: "USER",
            language: "ja",
            theme: "system",
          },
        }),
      ),
    );
    renderLogin();

    fillAndSubmit("user@example.com", "correct-password");

    expect(await screen.findByTestId("home")).toBeInTheDocument();
    expect(tokenStore.getAccessToken()).toBe("access-1");
    expect(tokenStore.getRefreshToken()).toBe("refresh-1");
  });
});

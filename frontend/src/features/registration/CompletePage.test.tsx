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
import { CompletePage } from "./CompletePage";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": status >= 400 ? "application/problem+json" : "application/json",
    },
  });
}

function renderComplete(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <CompletePage />
    </MemoryRouter>,
  );
}

describe("CompletePage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("トークンなしのアクセスはエラー表示", () => {
    renderComplete("/register/complete");

    expect(screen.getByText(/リンクが不正です/)).toBeInTheDocument();
  });

  it("8文字未満と不一致はクライアント検証エラー", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderComplete("/register/complete?token=abc");

    fireEvent.change(screen.getByLabelText(/^パスワード必須$/), {
      target: { value: "short1" },
    });
    fireEvent.change(screen.getByLabelText(/^パスワード\(確認\)必須$/), {
      target: { value: "different" },
    });
    fireEvent.click(screen.getByRole("button", { name: "登録を完了する" }));

    expect(await screen.findByText("8文字以上で入力してください")).toBeInTheDocument();
    expect(await screen.findByText("パスワードが一致しません")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("無効トークンは理由統一メッセージを表示", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse(400, { code: "REGISTRATION_TOKEN_INVALID" })),
    );
    renderComplete("/register/complete?token=expired");

    fireEvent.change(screen.getByLabelText(/^パスワード必須$/), {
      target: { value: "long-enough-password" },
    });
    fireEvent.change(screen.getByLabelText(/^パスワード\(確認\)必須$/), {
      target: { value: "long-enough-password" },
    });
    fireEvent.click(screen.getByRole("button", { name: "登録を完了する" }));

    expect(await screen.findByText(/リンクが無効または期限切れです/)).toBeInTheDocument();
  });

  it("成功で完了メッセージを表示", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    renderComplete("/register/complete?token=valid");

    fireEvent.change(screen.getByLabelText(/^パスワード必須$/), {
      target: { value: "long-enough-password" },
    });
    fireEvent.change(screen.getByLabelText(/^パスワード\(確認\)必須$/), {
      target: { value: "long-enough-password" },
    });
    fireEvent.click(screen.getByRole("button", { name: "登録を完了する" }));

    expect(await screen.findByText("登録が完了しました")).toBeInTheDocument();
    expect(screen.getByText(/管理者の承認後にログインできる/)).toBeInTheDocument();
  });
});

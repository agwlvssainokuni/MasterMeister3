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

import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider, useToast } from "./Toast";
import { Button } from "./Button";

function Trigger() {
  const { showToast } = useToast();
  return (
    <>
      <Button onClick={() => showToast("success", "保存しました")}>成功</Button>
      <Button onClick={() => showToast("danger", "失敗しました")}>失敗</Button>
    </>
  );
}

describe("Toast", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("success は表示され自動で消える", async () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "成功" }));
    expect(screen.getByRole("status")).toHaveTextContent("保存しました");

    act(() => {
      vi.advanceTimersByTime(6000);
    });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("danger は自動で消えず手動クローズできる", async () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "失敗" }));
    expect(screen.getByRole("alert")).toHaveTextContent("失敗しました");

    act(() => {
      vi.advanceTimersByTime(10000);
    });
    expect(screen.getByRole("alert")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "閉じる" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

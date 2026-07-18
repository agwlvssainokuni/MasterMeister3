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
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Button } from "./Button";
import { Modal } from "./Modal";

describe("Modal", () => {
  it("open=false では表示されない", () => {
    render(
      <Modal open={false} title="確認" onClose={() => undefined}>
        本文
      </Modal>,
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("Esc で onClose が呼ばれる", async () => {
    const onClose = vi.fn();
    render(
      <Modal open title="確認" onClose={onClose}>
        本文
      </Modal>,
    );
    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("Tab がダイアログ内で循環する(フォーカストラップ)", async () => {
    render(
      <Modal
        open
        title="確認"
        onClose={() => undefined}
        footer={<Button variant="primary">OK</Button>}
      >
        本文
      </Modal>,
    );
    const closeButton = screen.getByRole("button", { name: "閉じる" });
    const okButton = screen.getByRole("button", { name: "OK" });
    expect(closeButton).toHaveFocus();

    await userEvent.tab();
    expect(okButton).toHaveFocus();

    // 最後の要素から Tab → 先頭へ循環
    await userEvent.tab();
    expect(closeButton).toHaveFocus();

    // 先頭から Shift+Tab → 末尾へ循環
    await userEvent.tab({ shift: true });
    expect(okButton).toHaveFocus();
  });

  it("aria-modal と aria-labelledby が設定される", () => {
    render(
      <Modal open title="確認ダイアログ" onClose={() => undefined}>
        本文
      </Modal>,
    );
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(dialog).toHaveAccessibleName("確認ダイアログ");
  });
});

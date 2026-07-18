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

import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { Tabs } from "./Tabs";

function Harness() {
  const [active, setActive] = useState("builder");
  return (
    <Tabs
      items={[
        { key: "builder", label: "ビルダー", content: <p>ビルダー画面</p> },
        { key: "sql", label: "SQL", content: <p>SQL 画面</p> },
      ]}
      activeKey={active}
      onChange={setActive}
    />
  );
}

describe("Tabs", () => {
  it("クリックでタブを切り替えられる", async () => {
    render(<Harness />);
    expect(screen.getByText("ビルダー画面")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: "SQL" }));
    expect(screen.getByText("SQL 画面")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "SQL" })).toHaveAttribute("aria-selected", "true");
  });

  it("矢印キーでタブを移動できる", async () => {
    render(<Harness />);
    screen.getByRole("tab", { name: "ビルダー" }).focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByText("SQL 画面")).toBeInTheDocument();
    await userEvent.keyboard("{ArrowLeft}");
    expect(screen.getByText("ビルダー画面")).toBeInTheDocument();
  });
});

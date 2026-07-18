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
import { describe, expect, it } from "vitest";
import { FormField } from "./FormField";
import { TextInput } from "./TextInput";

describe("FormField", () => {
  it("ラベルと入力が関連付けられる", () => {
    render(
      <FormField label="メールアドレス">
        <TextInput />
      </FormField>,
    );
    expect(screen.getByLabelText("メールアドレス")).toBeInTheDocument();
  });

  it("error があると aria-invalid と aria-describedby が接続される", () => {
    render(
      <FormField label="メールアドレス" error="形式が不正です">
        <TextInput />
      </FormField>,
    );
    const input = screen.getByLabelText("メールアドレス");
    expect(input).toHaveAttribute("aria-invalid", "true");
    const errorMessage = screen.getByRole("alert");
    expect(errorMessage).toHaveTextContent("形式が不正です");
    expect(input.getAttribute("aria-describedby")).toBe(errorMessage.id);
  });

  it("required のとき必須マークと aria-required が付く", () => {
    render(
      <FormField label="メールアドレス" required>
        <TextInput />
      </FormField>,
    );
    expect(screen.getByLabelText(/メールアドレス/)).toHaveAttribute("aria-required", "true");
    expect(screen.getByText("必須")).toBeInTheDocument();
  });
});

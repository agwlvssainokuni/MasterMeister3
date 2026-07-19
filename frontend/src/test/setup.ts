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

import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, beforeAll, vi } from "vitest";

// テストごとに DOM を破棄する(蓄積すると重複要素でクエリが失敗する)
afterEach(() => {
  cleanup();
});

// jsdom は matchMedia 未実装のためスタブする(ThemeProvider が使用)
if (typeof window !== "undefined" && !window.matchMedia) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}
// コンポーネントが useTranslation を使うため、テストでも i18n を初期化する(ja 固定)。
// アプリ辞書(auth / admin namespace)も ../i18n 経由で登録される。
import i18n from "../i18n";

beforeAll(async () => {
  await i18n.changeLanguage("ja");
});

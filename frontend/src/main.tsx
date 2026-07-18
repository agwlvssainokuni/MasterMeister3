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

import { StrictMode, Suspense, lazy } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { App } from "./App";
import { ThemeProvider } from "./design-system/theme/ThemeProvider";
import "./design-system/i18n";
import "@fontsource/noto-sans-jp/400.css";
import "@fontsource/noto-sans-jp/500.css";
import "@fontsource/noto-sans-jp/700.css";
import "@fontsource/noto-sans-mono/400.css";
import "@fontsource/noto-sans-mono/700.css";
import "./design-system/tokens/tokens.css";
import "./index.css";

// モックカタログは dev 専用(NFR-U2-02)。production ビルドでは定数畳み込みにより
// ルートごと除外され、mock チャンクは生成されない。
const MockCatalog = import.meta.env.DEV ? lazy(() => import("./mock/MockCatalog")) : null;

const rootElement = document.getElementById("root");
if (!rootElement) {
  throw new Error("root element not found");
}

createRoot(rootElement).render(
  <StrictMode>
    <ThemeProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<App />} />
          {MockCatalog ? (
            <Route
              path="/mock/*"
              element={
                <Suspense fallback={null}>
                  <MockCatalog />
                </Suspense>
              }
            />
          ) : null}
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
);

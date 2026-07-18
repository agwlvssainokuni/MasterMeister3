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

/*
 * FOUC 防止: React 起動前に data-theme を確定する(外部ファイル方式 — CSP は script-src 'self' で完結)。
 * ここでの決定は暫定で、以後の追従・切替は ThemeProvider が担う。
 */
(function () {
  var stored = null;
  try {
    stored = window.localStorage.getItem("mm.theme");
  } catch {
    /* localStorage 不可の環境では OS 設定に従う */
  }
  var theme = stored === "light" || stored === "dark" ? stored : null;
  if (theme === null) {
    theme = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  document.documentElement.setAttribute("data-theme", theme);
})();

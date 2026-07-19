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

import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useTheme } from "../../design-system/theme/ThemeProvider";
import { putPreferences } from "./api";
import { useAuth } from "./AuthProvider";

/**
 * 言語・テーマのサーバ統合(US-047/048、Q3=A)。
 * ログイン中にヘッダーの LanguageSwitcher / ThemeToggle で変更された値
 * (localStorage には②の仕組みで保存済み)をサーバにも保存する。
 * サーバ値の適用(ログイン時・復元時)は AuthProvider が行い、その際は
 * 値が一致するため PUT は発生しない。
 */
export function usePreferences(): void {
  const { user, updateUser } = useAuth();
  const { theme } = useTheme();
  const { i18n } = useTranslation();
  const language = i18n.language;
  const saving = useRef(false);

  useEffect(() => {
    if (!user || saving.current) {
      return;
    }
    if (user.language === language && user.theme === theme) {
      return;
    }
    saving.current = true;
    void putPreferences(language, theme)
      .then(() => {
        updateUser({ ...user, language, theme });
      })
      .catch(() => {
        /* 保存失敗時は localStorage の値で動作継続(次回変更時に再試行) */
      })
      .finally(() => {
        saving.current = false;
      });
  }, [user, language, theme, updateUser]);
}

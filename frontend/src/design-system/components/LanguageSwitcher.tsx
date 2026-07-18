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

import { useTranslation } from "react-i18next";
import { changeLanguage } from "../i18n";
import type { Language } from "../i18n";
import styles from "./HeaderControl.module.css";

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation();

  return (
    <label className={styles.control}>
      <span className={styles.label}>{t("language.label")}</span>
      <select
        className={styles.select}
        value={i18n.language}
        onChange={(event) => changeLanguage(event.target.value as Language)}
      >
        <option value="ja">{t("language.ja")}</option>
        <option value="en">{t("language.en")}</option>
      </select>
    </label>
  );
}

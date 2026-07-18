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

import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import { useTranslation } from "react-i18next";
import "./i18n-mock";
import { LanguageSwitcher, ThemeToggle, ToastProvider } from "../design-system/components";
import { TokensPage } from "./pages/TokensPage";
import { ComponentsPage } from "./pages/ComponentsPage";
import { LoginMock } from "./pages/screens/LoginMock";
import { UserListMock } from "./pages/screens/UserListMock";
import { RecordEditMock } from "./pages/screens/RecordEditMock";
import { QueryRunMock } from "./pages/screens/QueryRunMock";
import styles from "./MockCatalog.module.css";

function CatalogNavLink({ to, label }: { to: string; label: string }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        isActive ? `${styles.navLink} ${styles.navLinkActive}` : styles.navLink
      }
    >
      {label}
    </NavLink>
  );
}

export default function MockCatalog() {
  const { t } = useTranslation("mock");
  return (
    <ToastProvider>
      <div className={styles.layout}>
        <nav className={styles.nav}>
          <h1 className={styles.navTitle}>{t("catalog.title")}</h1>
          <p className={styles.navSubtitle}>{t("catalog.subtitle")}</p>
          <ul className={styles.navList}>
            <li>
              <CatalogNavLink to="/mock/tokens" label={t("nav.tokens")} />
            </li>
            <li>
              <CatalogNavLink to="/mock/components" label={t("nav.components")} />
            </li>
          </ul>
          <p className={styles.navGroup}>{t("nav.screens")}</p>
          <ul className={styles.navList}>
            <li>
              <CatalogNavLink to="/mock/screens/login" label={t("nav.login")} />
            </li>
            <li>
              <CatalogNavLink to="/mock/screens/user-list" label={t("nav.userList")} />
            </li>
            <li>
              <CatalogNavLink to="/mock/screens/record-edit" label={t("nav.recordEdit")} />
            </li>
            <li>
              <CatalogNavLink to="/mock/screens/query-run" label={t("nav.queryRun")} />
            </li>
          </ul>
        </nav>
        <div className={styles.main}>
          <div className={styles.topbar}>
            <LanguageSwitcher />
            <ThemeToggle />
          </div>
          <div className={styles.content}>
            <Routes>
              <Route index element={<Navigate to="/mock/tokens" replace />} />
              <Route path="tokens" element={<TokensPage />} />
              <Route path="components" element={<ComponentsPage />} />
              <Route path="screens/login" element={<LoginMock />} />
              <Route path="screens/user-list" element={<UserListMock />} />
              <Route path="screens/record-edit" element={<RecordEditMock />} />
              <Route path="screens/query-run" element={<QueryRunMock />} />
            </Routes>
          </div>
        </div>
      </div>
    </ToastProvider>
  );
}

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
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { IconButton } from "./Button";
import { LanguageSwitcher } from "./LanguageSwitcher";
import { ThemeToggle } from "./ThemeToggle";
import styles from "./AppShell.module.css";

export interface NavItem {
  key: string;
  label: ReactNode;
  icon?: ReactNode;
  active?: boolean;
  onSelect?: () => void;
}

export interface AppShellProps {
  navItems: readonly NavItem[];
  headerExtra?: ReactNode;
  children: ReactNode;
}

export function AppShell({ navItems, headerExtra, children }: AppShellProps) {
  const { t } = useTranslation();
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <IconButton
            aria-label="menu"
            aria-expanded={!collapsed}
            onClick={() => setCollapsed((current) => !current)}
          >
            ☰
          </IconButton>
          <span className={styles.appTitle}>{t("app.title")}</span>
        </div>
        <div className={styles.headerRight}>
          <LanguageSwitcher />
          <ThemeToggle />
          {headerExtra}
        </div>
      </header>
      <div className={styles.lower}>
        <nav
          className={`${styles.sidenav} ${collapsed ? styles.sidenavCollapsed : ""}`}
          aria-label="main"
        >
          <ul className={styles.navList}>
            {navItems.map((item) => (
              <li key={item.key}>
                <button
                  type="button"
                  className={`${styles.navItem} ${item.active ? styles.navItemActive : ""}`}
                  aria-current={item.active ? "page" : undefined}
                  onClick={item.onSelect}
                  title={collapsed ? String(item.label) : undefined}
                >
                  <span className={styles.navIcon} aria-hidden="true">
                    {item.icon ?? "▪"}
                  </span>
                  {!collapsed ? <span className={styles.navLabel}>{item.label}</span> : null}
                </button>
              </li>
            ))}
          </ul>
        </nav>
        <main className={styles.content}>{children}</main>
      </div>
    </div>
  );
}

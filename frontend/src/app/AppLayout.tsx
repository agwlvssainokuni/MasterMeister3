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
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { AppShell, Dropdown } from "../design-system/components";
import type { NavItem } from "../design-system/components";
import { useAuth } from "../features/auth/AuthProvider";
import { usePreferences } from "../features/auth/usePreferences";

/**
 * ログイン後の共通レイアウト(AppShell 統合)。
 * ナビゲーション(ホーム / ユーザ管理[ADMIN のみ])とユーザメニュー(ログアウト)。
 * usePreferences により言語・テーマの変更がサーバへも保存される(US-047/048)。
 */
export function AppLayout() {
  const { t } = useTranslation("auth");
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  usePreferences();

  const navItems: NavItem[] = [
    {
      key: "home",
      label: t("nav.home"),
      icon: "🏠",
      active: location.pathname === "/",
      onSelect: () => navigate("/"),
    },
    ...(user?.role === "ADMIN"
      ? [
          {
            key: "users",
            label: t("nav.users"),
            icon: "👥",
            active: location.pathname.startsWith("/admin/users"),
            onSelect: () => navigate("/admin/users"),
          },
          {
            key: "connections",
            label: t("nav.connections"),
            icon: "🔌",
            active: location.pathname.startsWith("/admin/connections"),
            onSelect: () => navigate("/admin/connections"),
          },
          {
            key: "groups",
            label: t("nav.groups"),
            icon: "🗂️",
            active: location.pathname.startsWith("/admin/groups"),
            onSelect: () => navigate("/admin/groups"),
          },
        ]
      : []),
  ];

  const onLogout = () => {
    void logout().then(() => navigate("/login"));
  };

  return (
    <AppShell
      navItems={navItems}
      headerExtra={
        <Dropdown
          trigger={<span>{user?.displayName ?? user?.email}</span>}
          items={[{ key: "logout", label: t("menu.logout"), onSelect: onLogout }]}
        />
      }
    >
      <Outlet />
    </AppShell>
  );
}

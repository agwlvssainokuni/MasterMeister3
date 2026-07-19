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

import { Navigate, Route, Routes } from "react-router-dom";
import { UserListPage } from "../features/admin/users/UserListPage";
import { LoginPage } from "../features/auth/LoginPage";
import { RequireAdmin, RequireAuth } from "../features/auth/guards";
import { CompletePage } from "../features/registration/CompletePage";
import { RequestPage } from "../features/registration/RequestPage";
import { AppLayout } from "./AppLayout";
import { HomePage } from "./HomePage";

/**
 * ルート構成(ユニット③)。
 * 公開: /login, /register, /register/complete。認証必須: / 以下(AppLayout)。
 * /admin/** は ADMIN のみ。モックカタログ(/mock)は main.tsx で dev 専用に追加される。
 */
export function AppRoutes({ extraRoutes }: { extraRoutes?: React.ReactNode }) {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RequestPage />} />
      <Route path="/register/complete" element={<CompletePage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route element={<RequireAdmin />}>
            <Route path="/admin/users" element={<UserListPage />} />
          </Route>
        </Route>
      </Route>
      {extraRoutes}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

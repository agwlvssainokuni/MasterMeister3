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
import { Card } from "../design-system/components";
import { useAuth } from "../features/auth/AuthProvider";

/**
 * ログイン後ホーム(③時点のプレースホルダ)。
 * データ保守のダッシュボード等はユニット⑤以降で置き換える。
 */
export function HomePage() {
  const { t } = useTranslation("auth");
  const { user } = useAuth();

  return (
    <Card title={t("home.title")}>
      <p>{t("home.welcome", { name: user?.displayName ?? user?.email })}</p>
      <p>{t("home.placeholder")}</p>
    </Card>
  );
}

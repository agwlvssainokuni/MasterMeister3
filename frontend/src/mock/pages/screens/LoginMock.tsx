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
import type { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import {
  Alert,
  Button,
  Card,
  FormField,
  PasswordInput,
  TextInput,
} from "../../../design-system/components";
import styles from "./screens.module.css";

export function LoginMock() {
  const { t } = useTranslation("mock");
  const { t: tc } = useTranslation();
  const [email, setEmail] = useState("");
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(false);
  const emailError = email !== "" && !email.includes("@") ? t("login.emailFormat") : undefined;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setFailed(false);
    // モック: 1 秒後に必ず失敗表示(エラー状態の確認用)
    window.setTimeout(() => {
      setLoading(false);
      setFailed(true);
    }, 1000);
  };

  return (
    <div className={styles.loginWrapper}>
      <div className={styles.loginCard}>
        <Card>
          <h1 className={styles.loginTitle}>{t("login.title")}</h1>
          {failed ? <Alert tone="danger">{t("login.failed")}</Alert> : null}
          <form className={styles.loginForm} onSubmit={onSubmit}>
            <FormField label={t("login.email")} required error={emailError}>
              <TextInput
                type="email"
                autoComplete="username"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </FormField>
            <FormField label={t("login.password")} required>
              <PasswordInput autoComplete="current-password" />
            </FormField>
            <Button type="submit" variant="primary" loading={loading}>
              {tc("action.login")}
            </Button>
          </form>
          <p className={styles.loginRegister}>
            <a href="#register" onClick={(event) => event.preventDefault()}>
              {t("login.register")}
            </a>
          </p>
        </Card>
      </div>
    </div>
  );
}

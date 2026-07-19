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
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../app/apiClient";
import {
  Alert,
  Button,
  Card,
  FormField,
  PasswordInput,
  TextInput,
} from "../../design-system/components";
import styles from "../../app/standalone.module.css";
import { useAuth } from "./AuthProvider";

/**
 * ログイン画面(US-005)。
 * 失敗理由は「メールアドレスまたはパスワードが正しくありません」に統一(列挙対策)。
 * ロック中(423)のみ別メッセージ(D-11)。
 */
export function LoginPage() {
  const { t } = useTranslation("auth");
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [errorCode, setErrorCode] = useState<"failed" | "locked" | "generic" | null>(null);
  const [loading, setLoading] = useState(false);

  if (user) {
    return <Navigate to="/" replace />;
  }

  const emailError = !submitted
    ? undefined
    : email === ""
      ? t("login.required")
      : !email.includes("@")
        ? t("login.emailFormat")
        : undefined;
  const passwordError = submitted && password === "" ? t("login.required") : undefined;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    setErrorCode(null);
    if (email === "" || !email.includes("@") || password === "") {
      return;
    }
    setLoading(true);
    login(email, password)
      .then(() => {
        const from = (location.state as { from?: string } | null)?.from;
        navigate(from ?? "/", { replace: true });
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.code === "AUTH_LOCKED") {
          setErrorCode("locked");
        } else if (error instanceof ApiError && error.status === 401) {
          setErrorCode("failed");
        } else {
          setErrorCode("generic");
        }
      })
      .finally(() => setLoading(false));
  };

  return (
    <div className={styles.wrapper}>
      <div className={styles.card}>
        <Card>
          <h1 className={styles.title}>{t("login.title")}</h1>
          {errorCode === "failed" ? <Alert tone="danger">{t("login.failed")}</Alert> : null}
          {errorCode === "locked" ? <Alert tone="warning">{t("login.locked")}</Alert> : null}
          {errorCode === "generic" ? <Alert tone="danger">{t("error.generic")}</Alert> : null}
          <form className={styles.form} onSubmit={onSubmit} noValidate>
            <FormField label={t("login.email")} required error={emailError}>
              <TextInput
                type="email"
                autoComplete="username"
                value={email}
                invalid={emailError !== undefined}
                onChange={(event) => setEmail(event.target.value)}
              />
            </FormField>
            <FormField label={t("login.password")} required error={passwordError}>
              <PasswordInput
                autoComplete="current-password"
                value={password}
                invalid={passwordError !== undefined}
                onChange={(event) => setPassword(event.target.value)}
              />
            </FormField>
            <Button type="submit" variant="primary" loading={loading}>
              {t("login.title")}
            </Button>
          </form>
          <p className={styles.links}>
            <Link to="/register">{t("login.register")}</Link>
          </p>
        </Card>
      </div>
    </div>
  );
}

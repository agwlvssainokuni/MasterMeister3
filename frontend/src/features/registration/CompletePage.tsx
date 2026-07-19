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
import { Link, useSearchParams } from "react-router-dom";
import { ApiError } from "../../app/apiClient";
import {
  Alert,
  Button,
  Card,
  FormField,
  PasswordInput,
  TextInput,
} from "../../design-system/components";
import styles from "../auth/auth.module.css";
import { completeRegistration } from "./api";

/**
 * 登録完了 = パスワード設定(US-002)。
 * メールのリンク /register/complete?token=... から遷移する。
 * トークン失敗理由は「無効または期限切れ」に統一(サーバ仕様に合わせる)。
 */
export function CompletePage() {
  const { t } = useTranslation("auth");
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [done, setDone] = useState(false);
  const [errorCode, setErrorCode] = useState<"invalidToken" | "generic" | null>(null);
  const [loading, setLoading] = useState(false);

  const passwordError = !submitted
    ? undefined
    : password === ""
      ? t("login.required")
      : password.length < 8
        ? t("complete.tooShort")
        : undefined;
  const confirmError =
    submitted && passwordConfirm !== password ? t("complete.mismatch") : undefined;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    setErrorCode(null);
    if (password === "" || password.length < 8 || passwordConfirm !== password) {
      return;
    }
    setLoading(true);
    completeRegistration(token, password, displayName === "" ? undefined : displayName)
      .then(() => setDone(true))
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.code === "REGISTRATION_TOKEN_INVALID") {
          setErrorCode("invalidToken");
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
          {done ? (
            <>
              <h1 className={styles.title}>{t("complete.doneTitle")}</h1>
              <Alert tone="success">{t("complete.done")}</Alert>
              <p className={styles.links}>
                <Link to="/login">{t("register.backToLogin")}</Link>
              </p>
            </>
          ) : token === "" ? (
            <>
              <h1 className={styles.title}>{t("complete.title")}</h1>
              <Alert tone="danger">{t("complete.missingToken")}</Alert>
              <p className={styles.links}>
                <Link to="/register">{t("register.title")}</Link>
              </p>
            </>
          ) : (
            <>
              <h1 className={styles.title}>{t("complete.title")}</h1>
              <p className={styles.description}>{t("complete.description")}</p>
              {errorCode === "invalidToken" ? (
                <Alert tone="danger">{t("complete.invalidToken")}</Alert>
              ) : null}
              {errorCode === "generic" ? <Alert tone="danger">{t("error.generic")}</Alert> : null}
              <form className={styles.form} onSubmit={onSubmit} noValidate>
                <FormField
                  label={t("complete.password")}
                  required
                  help={t("complete.passwordHelp")}
                  error={passwordError}
                >
                  <PasswordInput
                    autoComplete="new-password"
                    value={password}
                    invalid={passwordError !== undefined}
                    onChange={(event) => setPassword(event.target.value)}
                  />
                </FormField>
                <FormField label={t("complete.passwordConfirm")} required error={confirmError}>
                  <PasswordInput
                    autoComplete="new-password"
                    value={passwordConfirm}
                    invalid={confirmError !== undefined}
                    onChange={(event) => setPasswordConfirm(event.target.value)}
                  />
                </FormField>
                <FormField label={t("complete.displayName")} help={t("complete.displayNameHelp")}>
                  <TextInput
                    autoComplete="nickname"
                    value={displayName}
                    onChange={(event) => setDisplayName(event.target.value)}
                  />
                </FormField>
                <Button type="submit" variant="primary" loading={loading}>
                  {t("complete.submit")}
                </Button>
              </form>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}

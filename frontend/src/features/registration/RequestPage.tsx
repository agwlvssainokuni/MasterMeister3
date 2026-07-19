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
import { Link } from "react-router-dom";
import { Alert, Button, Card, FormField, TextInput } from "../../design-system/components";
import { requestRegistration } from "../auth/api";
import styles from "../auth/auth.module.css";

/**
 * ユーザ登録申請(US-001)。
 * サーバは登録状況にかかわらず常に 202 を返すため、完了表示も常に同一(列挙対策)。
 */
export function RequestPage() {
  const { t, i18n } = useTranslation("auth");
  const [email, setEmail] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [sent, setSent] = useState(false);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(false);

  const emailError = !submitted
    ? undefined
    : email === ""
      ? t("login.required")
      : !email.includes("@")
        ? t("login.emailFormat")
        : undefined;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    setFailed(false);
    if (email === "" || !email.includes("@")) {
      return;
    }
    setLoading(true);
    const language = i18n.language === "en" ? "en" : "ja";
    requestRegistration(email, language)
      .then(() => setSent(true))
      .catch(() => setFailed(true))
      .finally(() => setLoading(false));
  };

  return (
    <div className={styles.wrapper}>
      <div className={styles.card}>
        <Card>
          {sent ? (
            <>
              <h1 className={styles.title}>{t("register.sentTitle")}</h1>
              <Alert tone="success">{t("register.sent")}</Alert>
              <p className={styles.links}>
                <Link to="/login">{t("register.backToLogin")}</Link>
              </p>
            </>
          ) : (
            <>
              <h1 className={styles.title}>{t("register.title")}</h1>
              <p className={styles.description}>{t("register.description")}</p>
              {failed ? <Alert tone="danger">{t("error.generic")}</Alert> : null}
              <form className={styles.form} onSubmit={onSubmit} noValidate>
                <FormField label={t("register.email")} required error={emailError}>
                  <TextInput
                    type="email"
                    autoComplete="email"
                    value={email}
                    invalid={emailError !== undefined}
                    onChange={(event) => setEmail(event.target.value)}
                  />
                </FormField>
                <Button type="submit" variant="primary" loading={loading}>
                  {t("register.submit")}
                </Button>
              </form>
              <p className={styles.links}>
                <Link to="/login">{t("register.backToLogin")}</Link>
              </p>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}

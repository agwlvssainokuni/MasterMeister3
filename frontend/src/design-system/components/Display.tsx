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
import { Button } from "./Button";
import styles from "./display.module.css";

export type BadgeTone = "neutral" | "primary" | "success" | "warning" | "danger";

const badgeToneClass: Record<BadgeTone, string> = {
  neutral: styles.badgeNeutral,
  primary: styles.badgePrimary,
  success: styles.badgeSuccess,
  warning: styles.badgeWarning,
  danger: styles.badgeDanger,
};

export function Badge({ tone = "neutral", children }: { tone?: BadgeTone; children: ReactNode }) {
  return <span className={`${styles.badge} ${badgeToneClass[tone]}`}>{children}</span>;
}

export type AlertTone = "info" | "success" | "warning" | "danger";

const alertToneClass: Record<AlertTone, string> = {
  info: styles.alertInfo,
  success: styles.alertSuccess,
  warning: styles.alertWarning,
  danger: styles.alertDanger,
};

export function Alert({ tone = "info", children }: { tone?: AlertTone; children: ReactNode }) {
  return (
    <div className={`${styles.alert} ${alertToneClass[tone]}`} role="alert">
      {children}
    </div>
  );
}

export function Card({ title, children }: { title?: ReactNode; children: ReactNode }) {
  return (
    <section className={styles.card}>
      {title ? <header className={styles.cardHeader}>{title}</header> : null}
      <div className={styles.cardBody}>{children}</div>
    </section>
  );
}

export interface EmptyStateProps {
  message?: ReactNode;
  action?: ReactNode;
}

export function EmptyState({ message, action }: EmptyStateProps) {
  const { t } = useTranslation();
  return (
    <div className={styles.empty}>
      <span className={styles.emptyIcon} aria-hidden="true">
        &#128230;
      </span>
      <p>{message ?? t("state.empty")}</p>
      {action}
    </div>
  );
}

export interface CodeBlockProps {
  code: string;
  copyable?: boolean;
  wrappable?: boolean;
}

export function CodeBlock({ code, copyable = true, wrappable = true }: CodeBlockProps) {
  const { t } = useTranslation();
  const [wrap, setWrap] = useState(false);
  return (
    <div className={styles.code}>
      <div className={styles.codeActions}>
        {wrappable ? (
          <Button size="sm" variant="ghost" onClick={() => setWrap((current) => !current)}>
            {"↩"}
          </Button>
        ) : null}
        {copyable ? (
          <Button
            size="sm"
            variant="ghost"
            onClick={() => void navigator.clipboard.writeText(code)}
          >
            {t("action.copy")}
          </Button>
        ) : null}
      </div>
      <pre className={`${styles.codePre} ${wrap ? styles.codeWrap : ""}`}>
        <code>{code}</code>
      </pre>
    </div>
  );
}

export interface KeyValueItem {
  key: ReactNode;
  value: ReactNode;
}

export function KeyValueList({ items }: { items: readonly KeyValueItem[] }) {
  return (
    <dl className={styles.kv}>
      {items.map((item, index) => (
        <div key={index} style={{ display: "contents" }}>
          <dt className={styles.kvKey}>{item.key}</dt>
          <dd className={styles.kvValue}>{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}

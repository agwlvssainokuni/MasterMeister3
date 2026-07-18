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

import { cloneElement, useId } from "react";
import type { ReactElement, ReactNode } from "react";
import { useTranslation } from "react-i18next";
import styles from "./FormField.module.css";

interface FieldChildProps {
  id?: string;
  invalid?: boolean;
  "aria-describedby"?: string;
  "aria-required"?: boolean;
}

export interface FormFieldProps {
  label: ReactNode;
  required?: boolean;
  help?: ReactNode;
  error?: ReactNode;
  children: ReactElement<FieldChildProps>;
}

export function FormField({ label, required = false, help, error, children }: FormFieldProps) {
  const { t } = useTranslation();
  const id = useId();
  const helpId = `${id}-help`;
  const errorId = `${id}-error`;
  const describedBy =
    [help ? helpId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;

  const child = cloneElement(children, {
    id,
    invalid: Boolean(error) || children.props.invalid,
    "aria-describedby": describedBy,
    "aria-required": required || undefined,
  });

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
        {required ? <span className={styles.required}>{t("state.required")}</span> : null}
      </label>
      {child}
      {help ? (
        <p className={styles.help} id={helpId}>
          {help}
        </p>
      ) : null}
      {error ? (
        <p className={styles.error} id={errorId} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}

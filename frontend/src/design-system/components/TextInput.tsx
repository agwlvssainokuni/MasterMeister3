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
import type { InputHTMLAttributes, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { useTranslation } from "react-i18next";
import { IconButton } from "./Button";
import styles from "./inputs.module.css";

export interface TextInputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

export function TextInput({ invalid = false, className, type = "text", ...rest }: TextInputProps) {
  const classes = [styles.input, invalid ? styles.invalid : null, className]
    .filter(Boolean)
    .join(" ");
  return <input className={classes} type={type} aria-invalid={invalid || undefined} {...rest} />;
}

export function PasswordInput({ invalid = false, ...rest }: TextInputProps) {
  const { t } = useTranslation();
  const [visible, setVisible] = useState(false);
  return (
    <span className={styles.wrapper}>
      <TextInput invalid={invalid} type={visible ? "text" : "password"} {...rest} />
      <span className={styles.trailing}>
        <IconButton
          size="md"
          aria-label={visible ? t("form.hidePassword") : t("form.showPassword")}
          aria-pressed={visible}
          onClick={() => setVisible((current) => !current)}
        >
          {visible ? "\u{1F648}" : "\u{1F441}"}
        </IconButton>
      </span>
    </span>
  );
}

export interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
}

export function TextArea({ invalid = false, className, ...rest }: TextAreaProps) {
  const classes = [styles.input, styles.textarea, invalid ? styles.invalid : null, className]
    .filter(Boolean)
    .join(" ");
  return <textarea className={classes} aria-invalid={invalid || undefined} {...rest} />;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  invalid?: boolean;
}

export function Select({ invalid = false, className, children, ...rest }: SelectProps) {
  const classes = [styles.input, invalid ? styles.invalid : null, className]
    .filter(Boolean)
    .join(" ");
  return (
    <select className={classes} aria-invalid={invalid || undefined} {...rest}>
      {children}
    </select>
  );
}

export function SearchInput(props: TextInputProps) {
  return <TextInput type="search" {...props} />;
}

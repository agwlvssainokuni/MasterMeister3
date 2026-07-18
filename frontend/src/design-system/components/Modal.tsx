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

import { useEffect, useId, useRef } from "react";
import type { KeyboardEvent, ReactNode } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Button } from "./Button";
import styles from "./Modal.module.css";

export interface ModalProps {
  open: boolean;
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}

const FOCUSABLE = 'a[href], button:not([disabled]), input, select, textarea, [tabindex="0"]';

export function Modal({ open, title, onClose, children, footer }: ModalProps) {
  const { t } = useTranslation();
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const restoreFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    restoreFocusRef.current = document.activeElement as HTMLElement | null;
    const dialog = dialogRef.current;
    const first = dialog?.querySelector<HTMLElement>(FOCUSABLE);
    (first ?? dialog)?.focus();
    return () => {
      restoreFocusRef.current?.focus();
    };
  }, [open]);

  if (!open) {
    return null;
  }

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape") {
      event.stopPropagation();
      onClose();
      return;
    }
    if (event.key !== "Tab") {
      return;
    }
    // フォーカストラップ: ダイアログ内で循環させる
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE));
    if (focusable.length === 0) {
      event.preventDefault();
      return;
    }
    const firstElement = focusable[0];
    const lastElement = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === firstElement) {
      event.preventDefault();
      lastElement.focus();
    } else if (!event.shiftKey && document.activeElement === lastElement) {
      event.preventDefault();
      firstElement.focus();
    }
  };

  return createPortal(
    <div className={styles.overlay}>
      <div
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        ref={dialogRef}
        onKeyDown={onKeyDown}
        tabIndex={-1}
      >
        <header className={styles.header}>
          <h2 className={styles.title} id={titleId}>
            {title}
          </h2>
          <Button size="sm" variant="ghost" onClick={onClose} aria-label={t("action.close")}>
            ✕
          </Button>
        </header>
        <div className={styles.body}>{children}</div>
        {footer ? <footer className={styles.footer}>{footer}</footer> : null}
      </div>
    </div>,
    document.body,
  );
}

export interface ConfirmDialogProps {
  open: boolean;
  title: ReactNode;
  message: ReactNode;
  tone?: "default" | "danger";
  confirmLabel?: ReactNode;
  onConfirm: () => void;
  onCancel: () => void;
  processing?: boolean;
}

export function ConfirmDialog({
  open,
  title,
  message,
  tone = "default",
  confirmLabel,
  onConfirm,
  onCancel,
  processing = false,
}: ConfirmDialogProps) {
  const { t } = useTranslation();
  return (
    <Modal
      open={open}
      title={title}
      onClose={onCancel}
      footer={
        <>
          <Button variant="secondary" onClick={onCancel} disabled={processing}>
            {t("action.cancel")}
          </Button>
          <Button
            variant={tone === "danger" ? "danger" : "primary"}
            onClick={onConfirm}
            loading={processing}
          >
            {confirmLabel ?? t("action.ok")}
          </Button>
        </>
      }
    >
      {message}
    </Modal>
  );
}

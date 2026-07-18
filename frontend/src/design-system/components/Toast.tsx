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

import { createContext, useCallback, useContext, useRef, useState } from "react";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Button } from "./Button";
import styles from "./Toast.module.css";

export type ToastTone = "success" | "info" | "warning" | "danger";

export interface ToastItem {
  id: number;
  tone: ToastTone;
  message: ReactNode;
}

interface ToastContextValue {
  showToast: (tone: ToastTone, message: ReactNode) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const AUTO_DISMISS_MS = 5000;

const toneClass: Record<ToastTone, string> = {
  success: styles.success,
  info: styles.info,
  warning: styles.warning,
  danger: styles.danger,
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextIdRef = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback(
    (tone: ToastTone, message: ReactNode) => {
      const id = nextIdRef.current++;
      setToasts((current) => [...current, { id, tone, message }]);
      // danger は手動クローズのみ(見落とし防止)
      if (tone !== "danger") {
        window.setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
      }
    },
    [dismiss],
  );

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {createPortal(
        <div className={styles.container}>
          {toasts.map((toast) => (
            <div
              key={toast.id}
              className={`${styles.toast} ${toneClass[toast.tone]}`}
              role={toast.tone === "danger" ? "alert" : "status"}
            >
              <span className={styles.message}>{toast.message}</span>
              <Button
                size="sm"
                variant="ghost"
                aria-label={t("action.close")}
                onClick={() => dismiss(toast.id)}
              >
                ✕
              </Button>
            </div>
          ))}
        </div>,
        document.body,
      )}
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return context;
}

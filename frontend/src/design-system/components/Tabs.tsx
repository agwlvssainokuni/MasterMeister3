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

import { useId, useRef } from "react";
import type { KeyboardEvent, ReactNode } from "react";
import styles from "./Tabs.module.css";

export interface TabItem {
  key: string;
  label: ReactNode;
  content: ReactNode;
}

export interface TabsProps {
  items: readonly TabItem[];
  activeKey: string;
  onChange: (key: string) => void;
}

export function Tabs({ items, activeKey, onChange }: TabsProps) {
  const baseId = useId();
  const listRef = useRef<HTMLDivElement>(null);
  const activeItem = items.find((item) => item.key === activeKey) ?? items[0];

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "ArrowRight" && event.key !== "ArrowLeft") {
      return;
    }
    event.preventDefault();
    const currentIndex = items.findIndex((item) => item.key === activeItem?.key);
    const delta = event.key === "ArrowRight" ? 1 : -1;
    const next = items[(currentIndex + delta + items.length) % items.length];
    if (next) {
      onChange(next.key);
      listRef.current
        ?.querySelector<HTMLElement>(`[data-tab-key="${CSS.escape(next.key)}"]`)
        ?.focus();
    }
  };

  return (
    <div>
      <div className={styles.list} role="tablist" ref={listRef} onKeyDown={onKeyDown}>
        {items.map((item) => {
          const selected = item.key === activeItem?.key;
          return (
            <button
              key={item.key}
              type="button"
              role="tab"
              data-tab-key={item.key}
              id={`${baseId}-tab-${item.key}`}
              aria-selected={selected}
              aria-controls={`${baseId}-panel-${item.key}`}
              tabIndex={selected ? 0 : -1}
              className={`${styles.tab} ${selected ? styles.tabActive : ""}`}
              onClick={() => onChange(item.key)}
            >
              {item.label}
            </button>
          );
        })}
      </div>
      {activeItem ? (
        <div
          role="tabpanel"
          id={`${baseId}-panel-${activeItem.key}`}
          aria-labelledby={`${baseId}-tab-${activeItem.key}`}
          className={styles.panel}
        >
          {activeItem.content}
        </div>
      ) : null}
    </div>
  );
}

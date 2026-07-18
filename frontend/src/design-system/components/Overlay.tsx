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

import { useEffect, useId, useRef, useState } from "react";
import type { KeyboardEvent, ReactNode } from "react";
import styles from "./Overlay.module.css";

export interface DropdownItem {
  key: string;
  label: ReactNode;
  danger?: boolean;
  onSelect: () => void;
}

export interface DropdownProps {
  trigger: ReactNode;
  items: readonly DropdownItem[];
}

export function Dropdown({ trigger, items }: DropdownProps) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onOutsideClick = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", onOutsideClick);
    menuRef.current?.focus();
    return () => document.removeEventListener("mousedown", onOutsideClick);
  }, [open]);

  const onMenuKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape") {
      setOpen(false);
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((index) => (index + 1) % items.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((index) => (index - 1 + items.length) % items.length);
    } else if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      const item = items[activeIndex];
      if (item) {
        item.onSelect();
        setOpen(false);
      }
    }
  };

  return (
    <div className={styles.dropdown} ref={rootRef}>
      <button
        type="button"
        className={styles.trigger}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => {
          setActiveIndex(0);
          setOpen((current) => !current);
        }}
      >
        {trigger}
      </button>
      {open ? (
        <div
          className={styles.menu}
          role="menu"
          tabIndex={-1}
          ref={menuRef}
          onKeyDown={onMenuKeyDown}
        >
          {items.map((item, index) => (
            <button
              key={item.key}
              type="button"
              role="menuitem"
              className={[
                styles.item,
                index === activeIndex ? styles.itemActive : null,
                item.danger ? styles.itemDanger : null,
              ]
                .filter(Boolean)
                .join(" ")}
              tabIndex={-1}
              onMouseEnter={() => setActiveIndex(index)}
              onClick={() => {
                item.onSelect();
                setOpen(false);
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

export interface TooltipProps {
  content: ReactNode;
  children: ReactNode;
}

export function Tooltip({ content, children }: TooltipProps) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  return (
    <span
      className={styles.tooltipHost}
      aria-describedby={visible ? id : undefined}
      onMouseEnter={() => setVisible(true)}
      onMouseLeave={() => setVisible(false)}
      onFocus={() => setVisible(true)}
      onBlur={() => setVisible(false)}
    >
      {children}
      {visible ? (
        <span className={styles.tooltip} role="tooltip" id={id}>
          {content}
        </span>
      ) : null}
    </span>
  );
}

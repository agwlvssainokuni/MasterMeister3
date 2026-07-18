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

import { useTranslation } from "react-i18next";
import styles from "../MockCatalog.module.css";

const PALETTES = ["blue", "gray", "green", "amber", "red"] as const;
const STOPS = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900] as const;

const SEMANTIC_TOKENS = [
  "--mm-color-bg",
  "--mm-color-surface",
  "--mm-color-surface-raised",
  "--mm-color-text",
  "--mm-color-text-muted",
  "--mm-color-text-inverse",
  "--mm-color-border",
  "--mm-color-border-strong",
  "--mm-color-primary",
  "--mm-color-primary-hover",
  "--mm-color-primary-subtle",
  "--mm-color-success",
  "--mm-color-success-subtle",
  "--mm-color-warning",
  "--mm-color-warning-subtle",
  "--mm-color-danger",
  "--mm-color-danger-hover",
  "--mm-color-danger-subtle",
  "--mm-color-focus-ring",
] as const;

const FONT_SIZES = ["xs", "sm", "md", "lg", "xl", "2xl"] as const;
const SPACES = ["0_5", "1", "2", "3", "4", "5", "6", "8", "10"] as const;

export function TokensPage() {
  const { t } = useTranslation("mock");
  return (
    <div>
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>{t("catalog.palette")}</h2>
        {PALETTES.map((palette) => (
          <div key={palette}>
            <h3 className={styles.sectionSub}>{palette}</h3>
            <div className={styles.swatchGrid}>
              {STOPS.map((stop) => (
                <div
                  key={stop}
                  className={styles.swatch}
                  style={{
                    background: `var(--mm-palette-${palette}-${stop})`,
                    color: stop >= 500 ? "#fff" : "#111",
                  }}
                >
                  {stop}
                </div>
              ))}
            </div>
          </div>
        ))}
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>{t("catalog.semantic")}</h2>
        <div className={styles.semanticGrid}>
          {SEMANTIC_TOKENS.map((token) => (
            <div key={token} className={styles.semanticItem}>
              <span className={styles.semanticChip} style={{ background: `var(${token})` }} />
              <span className={styles.semanticName}>{token}</span>
            </div>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>{t("catalog.typography")}</h2>
        {FONT_SIZES.map((size) => (
          <p key={size} className={styles.row} style={{ margin: 0 }}>
            <span className={styles.mono}>--mm-font-size-{size}</span>
            <span style={{ fontSize: `var(--mm-font-size-${size})` }}>
              マスタデータの保守 / Master data maintenance 0123456789
            </span>
          </p>
        ))}
        <p className={styles.row} style={{ margin: 0 }}>
          <span className={styles.mono}>--mm-font-mono</span>
          <code>SELECT * FROM 顧客マスタ WHERE id = :id -- 等幅(Noto Sans Mono)</code>
        </p>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>{t("catalog.spacing")}</h2>
        {SPACES.map((space) => (
          <div key={space} className={styles.row}>
            <span className={styles.mono} style={{ width: 140 }}>
              --mm-space-{space}
            </span>
            <div className={styles.spacingBar} style={{ width: `var(--mm-space-${space})` }} />
          </div>
        ))}
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>{t("catalog.contrast")}</h2>
        <p className={styles.mono}>{t("catalog.contrastNote")}</p>
        <div className={styles.row}>
          <span className={styles.contrastSample} style={{ background: "var(--mm-color-surface)" }}>
            <span style={{ color: "var(--mm-color-text)" }}>text / surface</span>
          </span>
          <span className={styles.contrastSample} style={{ background: "var(--mm-color-surface)" }}>
            <span style={{ color: "var(--mm-color-text-muted)" }}>text-muted / surface</span>
          </span>
          <span className={styles.contrastSample} style={{ background: "var(--mm-color-primary)" }}>
            <span style={{ color: "var(--mm-color-text-inverse)" }}>inverse / primary</span>
          </span>
          <span className={styles.contrastSample} style={{ background: "var(--mm-color-danger)" }}>
            <span style={{ color: "var(--mm-color-text-inverse)" }}>inverse / danger</span>
          </span>
        </div>
      </section>
    </div>
  );
}

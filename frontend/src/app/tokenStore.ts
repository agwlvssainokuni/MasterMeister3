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

/**
 * トークン保管(sessionStorage)。
 * sessionStorage へのアクセスをこのモジュールに集約し、XSS 面の露出を局所化する
 * (Application Design のリスク受容 — nfr-design-patterns.md §7)。
 */

const ACCESS_KEY = "mm.accessToken";
const REFRESH_KEY = "mm.refreshToken";

function read(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

export const tokenStore = {
  getAccessToken(): string | null {
    return read(ACCESS_KEY);
  },

  getRefreshToken(): string | null {
    return read(REFRESH_KEY);
  },

  save(accessToken: string, refreshToken: string): void {
    try {
      window.sessionStorage.setItem(ACCESS_KEY, accessToken);
      window.sessionStorage.setItem(REFRESH_KEY, refreshToken);
    } catch {
      /* 保存不可の環境ではメモリ上の状態のみで動作(リロードで再ログイン) */
    }
  },

  clear(): void {
    try {
      window.sessionStorage.removeItem(ACCESS_KEY);
      window.sessionStorage.removeItem(REFRESH_KEY);
    } catch {
      /* 何もしない */
    }
  },
};

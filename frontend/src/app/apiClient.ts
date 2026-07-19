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

import { tokenStore } from "./tokenStore";

/**
 * API クライアント(nfr-design-patterns.md §7)。
 * - Bearer 付与
 * - 401 → リフレッシュのシングルフライト(進行中 Promise 共有)→ 再試行 1 回のみ
 * - Problem Details(RFC 9457)を ApiError に変換(code / invalid-params)
 */

export interface InvalidParam {
  name: string;
  reason: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly invalidParams: readonly InvalidParam[];

  constructor(status: number, code: string, invalidParams: readonly InvalidParam[] = []) {
    super(code);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.invalidParams = invalidParams;
  }
}

let sessionExpiredHandler: (() => void) | null = null;

/** リフレッシュ不能(セッション失効)時に呼ばれるハンドラを登録する(AuthProvider が使用)。 */
export function setSessionExpiredHandler(handler: (() => void) | null): void {
  sessionExpiredHandler = handler;
}

async function parseError(response: Response): Promise<ApiError> {
  let code = "UNKNOWN_ERROR";
  let invalidParams: InvalidParam[] = [];
  try {
    const data: unknown = await response.json();
    if (typeof data === "object" && data !== null) {
      const problem = data as Record<string, unknown>;
      if (typeof problem.code === "string") {
        code = problem.code;
      }
      const params = problem["invalid-params"];
      if (Array.isArray(params)) {
        invalidParams = params
          .filter(
            (p): p is { name: string; reason: string } =>
              typeof p === "object" &&
              p !== null &&
              typeof (p as Record<string, unknown>).name === "string" &&
              typeof (p as Record<string, unknown>).reason === "string",
          )
          .map((p) => ({ name: p.name, reason: p.reason }));
      }
    }
  } catch {
    /* 非 JSON 応答は UNKNOWN_ERROR のまま */
  }
  return new ApiError(response.status, code, invalidParams);
}

async function doRefresh(): Promise<boolean> {
  const refreshToken = tokenStore.getRefreshToken();
  if (!refreshToken) {
    return false;
  }
  const response = await fetch("/api/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!response.ok) {
    return false;
  }
  const data = (await response.json()) as { accessToken: string; refreshToken: string };
  tokenStore.save(data.accessToken, data.refreshToken);
  return true;
}

let refreshPromise: Promise<boolean> | null = null;

/**
 * リフレッシュのシングルフライト実行。並行する 401 は同じ Promise を共有し、
 * リフレッシュ API の二重呼び出し(= ローテーション済みトークンの再提示)を防ぐ。
 */
export function refreshSession(): Promise<boolean> {
  refreshPromise ??= doRefresh()
    .catch(() => false)
    .finally(() => {
      refreshPromise = null;
    });
  return refreshPromise;
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  /** false: 認証不要 API(Bearer 付与も 401 リフレッシュもしない) */
  auth?: boolean;
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, auth = true } = options;

  const doFetch = () => {
    const headers: Record<string, string> = {};
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    if (auth) {
      const accessToken = tokenStore.getAccessToken();
      if (accessToken) {
        headers.Authorization = `Bearer ${accessToken}`;
      }
    }
    return fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  };

  let response = await doFetch();
  if (response.status === 401 && auth) {
    const refreshed = await refreshSession();
    if (refreshed) {
      response = await doFetch(); // 再試行は 1 回のみ(無限ループ防止)
    }
    if (!refreshed || response.status === 401) {
      tokenStore.clear();
      sessionExpiredHandler?.();
      throw await parseError(response);
    }
  }
  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

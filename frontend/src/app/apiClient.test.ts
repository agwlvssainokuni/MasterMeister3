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

import { afterEach, describe, expect, it, vi } from "vitest";
import { tokenStore } from "./tokenStore";
import { ApiError, apiFetch, setSessionExpiredHandler } from "./apiClient";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": status >= 400 ? "application/problem+json" : "application/json",
    },
  });
}

function headersOf(call: unknown[]): Record<string, string> {
  return ((call[1] as RequestInit | undefined)?.headers ?? {}) as Record<string, string>;
}

describe("apiClient", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
    setSessionExpiredHandler(null);
  });

  it("Bearer を付与し JSON を返す", async () => {
    tokenStore.save("access-1", "refresh-1");
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { value: 42 }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await apiFetch<{ value: number }>("/api/things");

    expect(result.value).toBe(42);
    expect(headersOf(fetchMock.mock.calls[0]).Authorization).toBe("Bearer access-1");
  });

  it("401でリフレッシュして1回だけ再試行する", async () => {
    tokenStore.save("old-access", "old-refresh");
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === "/api/auth/refresh") {
        return Promise.resolve(
          jsonResponse(200, { accessToken: "new-access", refreshToken: "new-refresh" }),
        );
      }
      const headers = (init?.headers ?? {}) as Record<string, string>;
      if (headers.Authorization === "Bearer new-access") {
        return Promise.resolve(jsonResponse(200, { ok: true }));
      }
      return Promise.resolve(jsonResponse(401, { code: "AUTH_REQUIRED" }));
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await apiFetch<{ ok: boolean }>("/api/things");

    expect(result.ok).toBe(true);
    expect(tokenStore.getAccessToken()).toBe("new-access");
    expect(tokenStore.getRefreshToken()).toBe("new-refresh");
    const refreshCalls = fetchMock.mock.calls.filter((call) => call[0] === "/api/auth/refresh");
    expect(refreshCalls).toHaveLength(1);
  });

  it("並行する401はリフレッシュを1回に束ねる_シングルフライト", async () => {
    tokenStore.save("old-access", "old-refresh");
    let refreshCalls = 0;
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === "/api/auth/refresh") {
        refreshCalls += 1;
        return Promise.resolve(
          jsonResponse(200, { accessToken: "new-access", refreshToken: "new-refresh" }),
        );
      }
      const headers = (init?.headers ?? {}) as Record<string, string>;
      if (headers.Authorization === "Bearer new-access") {
        return Promise.resolve(jsonResponse(200, { ok: true }));
      }
      return Promise.resolve(jsonResponse(401, { code: "AUTH_REQUIRED" }));
    });
    vi.stubGlobal("fetch", fetchMock);

    await Promise.all([apiFetch("/api/a"), apiFetch("/api/b"), apiFetch("/api/c")]);

    expect(refreshCalls).toBe(1);
  });

  it("リフレッシュ失敗でセッション失効ハンドラを呼びトークンを破棄する", async () => {
    tokenStore.save("old-access", "old-refresh");
    const expired = vi.fn();
    setSessionExpiredHandler(expired);
    const fetchMock = vi.fn((url: string) => {
      if (url === "/api/auth/refresh") {
        return Promise.resolve(jsonResponse(401, { code: "AUTH_INVALID_TOKEN" }));
      }
      return Promise.resolve(jsonResponse(401, { code: "AUTH_REQUIRED" }));
    });
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiFetch("/api/things")).rejects.toBeInstanceOf(ApiError);
    expect(expired).toHaveBeenCalledTimes(1);
    expect(tokenStore.getAccessToken()).toBeNull();
    expect(tokenStore.getRefreshToken()).toBeNull();
  });

  it("ProblemDetailsのcodeとinvalid_paramsを解釈する", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(400, {
        code: "VALIDATION_ERROR",
        "invalid-params": [{ name: "email", reason: "Email" }],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const error = await apiFetch("/api/things", { auth: false }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe("VALIDATION_ERROR");
    expect(apiError.invalidParams).toEqual([{ name: "email", reason: "Email" }]);
  });

  it("auth_falseではBearerを付けず401でもリフレッシュしない", async () => {
    tokenStore.save("access-1", "refresh-1");
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(401, { code: "AUTH_REQUIRED" }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiFetch("/api/auth/login", { method: "POST", body: {}, auth: false }))
        .rejects.toBeInstanceOf(ApiError);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(headersOf(fetchMock.mock.calls[0]).Authorization).toBeUndefined();
  });
});

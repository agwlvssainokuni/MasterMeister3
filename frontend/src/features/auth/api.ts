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

import { apiFetch } from "../../app/apiClient";

export interface UserInfo {
  email: string;
  displayName: string | null;
  role: "ADMIN" | "USER";
  language: string;
  theme: string;
}

export interface LoginResult {
  accessToken: string;
  refreshToken: string;
  user: UserInfo;
}

export function loginRequest(email: string, password: string): Promise<LoginResult> {
  return apiFetch<LoginResult>("/api/auth/login", {
    method: "POST",
    body: { email, password },
    auth: false,
  });
}

export function logoutRequest(refreshToken: string): Promise<void> {
  return apiFetch<void>("/api/auth/logout", {
    method: "POST",
    body: { refreshToken },
    auth: false,
  });
}

export function fetchMe(): Promise<UserInfo> {
  return apiFetch<UserInfo>("/api/me");
}

export function putPreferences(language: string, theme: string): Promise<void> {
  return apiFetch<void>("/api/me/preferences", {
    method: "PUT",
    body: { language, theme },
  });
}

export function requestRegistration(email: string, language: string): Promise<void> {
  return apiFetch<void>("/api/registration/request", {
    method: "POST",
    body: { email, language },
    auth: false,
  });
}

export function completeRegistration(
  token: string,
  password: string,
  displayName: string | undefined,
): Promise<void> {
  return apiFetch<void>("/api/registration/complete", {
    method: "POST",
    body: { token, password, displayName },
    auth: false,
  });
}

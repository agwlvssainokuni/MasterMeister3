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

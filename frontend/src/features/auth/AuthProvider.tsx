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

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { refreshSession, setSessionExpiredHandler } from "../../app/apiClient";
import { changeLanguage } from "../../design-system/i18n";
import { useTheme } from "../../design-system/theme/ThemeProvider";
import { fetchMe, loginRequest, logoutRequest } from "./api";
import type { UserInfo } from "./api";
import { tokenStore } from "./tokenStore";

/**
 * 認証状態(US-005/009、Q3=A)。
 * 起動時はリフレッシュトークンからセッションを復元する。
 * ログイン成功時・復元時はサーバ保存の言語・テーマを適用する(サーバが正)。
 */
interface AuthContextValue {
  user: UserInfo | null;
  initializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  updateUser: (user: UserInfo) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [initializing, setInitializing] = useState(true);
  const { setTheme } = useTheme();

  const applyServerPreferences = useCallback(
    (info: UserInfo) => {
      if (info.language === "ja" || info.language === "en") {
        changeLanguage(info.language);
      }
      if (info.theme === "light" || info.theme === "dark" || info.theme === "system") {
        setTheme(info.theme);
      }
    },
    [setTheme],
  );

  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null));
    return () => setSessionExpiredHandler(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const restore = async () => {
      if (tokenStore.getRefreshToken()) {
        const refreshed = await refreshSession();
        if (refreshed) {
          try {
            const me = await fetchMe();
            if (!cancelled) {
              setUser(me);
              applyServerPreferences(me);
            }
          } catch {
            tokenStore.clear();
          }
        } else {
          tokenStore.clear();
        }
      }
      if (!cancelled) {
        setInitializing(false);
      }
    };
    void restore();
    return () => {
      cancelled = true;
    };
  }, [applyServerPreferences]);

  const login = useCallback(
    async (email: string, password: string) => {
      const result = await loginRequest(email, password);
      tokenStore.save(result.accessToken, result.refreshToken);
      setUser(result.user);
      applyServerPreferences(result.user);
    },
    [applyServerPreferences],
  );

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.getRefreshToken();
    tokenStore.clear();
    setUser(null);
    if (refreshToken) {
      try {
        await logoutRequest(refreshToken);
      } catch {
        /* ログアウトは冪等(US-009)— 失敗してもローカル状態は破棄済み */
      }
    }
  }, []);

  const updateUser = useCallback((info: UserInfo) => {
    setUser(info);
  }, []);

  return (
    <AuthContext.Provider value={{ user, initializing, login, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}

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
 * アプリケーション辞書(namespace: auth / admin)。
 * 基盤(common namespace・言語検出・切替)は design-system/i18n が提供する。
 */

import i18n from "../design-system/i18n";
import enAdmin from "./locales/en/admin.json";
import enAuth from "./locales/en/auth.json";
import jaAdmin from "./locales/ja/admin.json";
import jaAuth from "./locales/ja/auth.json";

i18n.addResourceBundle("ja", "auth", jaAuth);
i18n.addResourceBundle("en", "auth", enAuth);
i18n.addResourceBundle("ja", "admin", jaAdmin);
i18n.addResourceBundle("en", "admin", enAdmin);

export default i18n;

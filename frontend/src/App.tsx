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
 * ユニット①の動作確認ページ。
 * 本画面はビルドパイプライン(Vite ビルド → WAR 同梱配信)の貫通確認用であり、
 * 実画面はユニット②(design-system)以降で実装する。
 */
export function App() {
  return (
    <main data-testid="app-main">
      <h1 data-testid="app-title">MasterMeister</h1>
      <p data-testid="app-message">
        セットアップ確認ページ: このページが表示されていれば、フロントエンドのビルドと配信は正常です。
      </p>
    </main>
  );
}

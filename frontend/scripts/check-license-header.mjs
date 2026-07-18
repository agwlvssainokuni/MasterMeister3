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

// フロントエンドの全ソースファイルに Apache 2.0 ライセンスヘッダーがあることを検査する。
// 対象: src/ scripts/ 配下の .ts .tsx .css .mjs と、ルート直下の設定ファイル(.ts .js)。
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const TARGET_DIRS = ["src", "scripts", "public"];
const TARGET_ROOT_FILES = ["vite.config.ts", "eslint.config.js"];
const TARGET_EXTENSIONS = [".ts", ".tsx", ".css", ".mjs", ".js"];
const REQUIRED_LINES = ["Copyright", "Licensed under the Apache License, Version 2.0"];

function collectFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      return collectFiles(path);
    }
    return TARGET_EXTENSIONS.some((ext) => path.endsWith(ext)) ? [path] : [];
  });
}

const files = [...TARGET_DIRS.flatMap((dir) => collectFiles(dir)), ...TARGET_ROOT_FILES];
const missing = files.filter((path) => {
  const head = readFileSync(path, "utf8").slice(0, 1024);
  return !REQUIRED_LINES.every((line) => head.includes(line));
});

if (missing.length > 0) {
  console.error("License header missing in:");
  for (const path of missing) {
    console.error(`  ${path}`);
  }
  process.exit(1);
}
console.log(`License header OK (${files.length} files)`);

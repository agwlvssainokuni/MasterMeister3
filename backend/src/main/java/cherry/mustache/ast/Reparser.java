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
package cherry.mustache.ast;

/**
 * Lambdaの戻り値やパーシャル本文を、指定したデリミタでASTに再パースするためのコールバック。
 * {@code ast}パッケージが{@code parser}パッケージへ直接依存しないための抽象化。
 */
@FunctionalInterface
public interface Reparser {

    /**
     * @param template       再パース対象のテンプレート文字列
     * @param openDelimiter  再パース開始時点で有効な開始デリミタ
     * @param closeDelimiter 再パース開始時点で有効な終了デリミタ
     * @return 再パース結果のASTルートノード
     */
    Node reparse(String template, String openDelimiter, String closeDelimiter);
}

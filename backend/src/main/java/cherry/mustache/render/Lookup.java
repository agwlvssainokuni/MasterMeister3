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
package cherry.mustache.render;

/**
 * 1階層分のプロパティ解決結果。{@code found}で「見つかったか」と「見つかった値がnullか」を区別する。
 */
record Lookup(boolean found, Object value) {

    static final Lookup NOT_FOUND = new Lookup(false, null);

    static Lookup of(Object value) {
        return new Lookup(true, value);
    }
}

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

import java.lang.reflect.Array;
import java.util.Collection;

/**
 * BR-3で確定した真偽判定ルール。{@code null}/{@code false}/空のList・配列のみがfalsy。
 * 空文字列・数値0はtruthyとして扱う。
 */
final class Truthiness {

    private Truthiness() {
    }

    static boolean isTruthy(Object value) {
        if (value == null || Boolean.FALSE.equals(value)) {
            return false;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }

    static boolean isListLike(Object value) {
        return value instanceof Collection<?> || value.getClass().isArray();
    }
}

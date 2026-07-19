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
package cherry.mastermeister.permission;

/**
 * 主権限の 3 値(US-013)。宣言順が許可の強さ(NONE < READ < UPDATE)であり、
 * グループ合成の「より許可的な値」は ordinal 比較で求める(D-21)。
 */
public enum PermissionLevel {
    NONE,
    READ,
    UPDATE;

    /** より許可的な方を返す(グループ合成 — D-21)。 */
    public static PermissionLevel morePermissive(PermissionLevel a, PermissionLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
